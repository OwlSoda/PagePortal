package com.owlsoda.pageportal.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.UnifiedBookDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.core.database.entity.UnifiedBookEntity
import com.owlsoda.pageportal.core.matching.TitleMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MatchCandidate(
    val book: BookEntity,
    val potentialMatch: UnifiedBookEntity,
    val score: Float
)

data class MatchReviewState(
    val candidates: List<MatchCandidate> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class MatchReviewViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val unifiedBookDao: UnifiedBookDao
) : ViewModel() {

    private val _state = MutableStateFlow(MatchReviewState())
    val state: StateFlow<MatchReviewState> = _state.asStateFlow()

    init {
        loadCandidates()
    }

    fun loadCandidates() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val candidates = findCandidates()
                _state.value = _state.value.copy(
                    candidates = candidates,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private suspend fun findCandidates(): List<MatchCandidate> = withContext(Dispatchers.IO) {
        val unlinked = bookDao.getUnlinkedBooks()
        val unified = unifiedBookDao.getUnifiedBooksSnapshot()
        val candidates = mutableListOf<MatchCandidate>()
        
        // This is O(N*M) but N (unlinked) should be small ideally.
        // If library is huge, this might be slow.
        // Limit to first 50 candidates for performance?
        
        for (book in unlinked) {
            // Skip if manually linked (which means manually unlinked usually)
            if (book.isManuallyLinked) continue
            
            var bestMatch: UnifiedBookEntity? = null
            var bestScore = 0.0f
            
            for (uBook in unified) {
                val score = TitleMatcher.calculateMatchScore(
                    book.title,
                    listOf(book.authors), // Author JSON parsing is expensive if done inside loop repeatedly
                    uBook.title,
                    listOf(uBook.author)
                )
                
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = uBook
                }
            }
            
            // Candidate thresholds
            // Auto-match is usually > 0.85 or 0.9
            // Candidates: 0.6 to 0.9 (overlapping with auto-match to catch ones that failed strictly?)
            // Let's say < 0.85 (since >= 0.85 would have been auto-matched) and > 0.6
            
            if (bestMatch != null && bestScore > 0.6f && bestScore < 0.9f) {
                candidates.add(MatchCandidate(book, bestMatch, bestScore))
                if (candidates.size >= 50) break
            }
        }
        candidates
    }

    fun confirmMatch(candidate: MatchCandidate) {
        viewModelScope.launch {
            try {
                // Link the book
                val updated = candidate.book.copy(
                    unifiedBookId = candidate.potentialMatch.id,
                    isManuallyLinked = true
                )
                bookDao.updateBook(updated)
                
                // Remove from local list
                _state.value = _state.value.copy(
                    candidates = _state.value.candidates.filter { it != candidate }
                )
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun rejectMatch(candidate: MatchCandidate) {
        viewModelScope.launch {
            try {
                // Mark as manually linked (but with null ID) so it doesn't show up again
                val updated = candidate.book.copy(
                    isManuallyLinked = true
                )
                bookDao.updateBook(updated)
                
                // Remove from local list
                _state.value = _state.value.copy(
                    candidates = _state.value.candidates.filter { it != candidate }
                )
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}

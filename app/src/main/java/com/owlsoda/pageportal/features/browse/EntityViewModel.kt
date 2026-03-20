package com.owlsoda.pageportal.features.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import org.json.JSONArray

sealed class EntityType(val title: String) {
    object Authors : EntityType("Authors")
    object Series : EntityType("Series")
    object Tags : EntityType("Tags")
    object Collections : EntityType("Collections")
}

data class EntityItem(
    val id: String, // Name for Author/Series, UUID or Name for Collection
    val name: String,
    val count: Int,
    val coverUrl: String? = null // Representative cover
)

data class EntityUiState(
    val selectedType: EntityType = EntityType.Authors,
    val items: List<EntityItem> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class EntityViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val collectionDao: com.owlsoda.pageportal.core.database.dao.CollectionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntityUiState())
    val uiState: StateFlow<EntityUiState> = _uiState.asStateFlow()
    
    init {
        loadData(EntityType.Authors)
    }

    fun setType(type: EntityType) {
        _uiState.update { it.copy(selectedType = type, isLoading = true) }
        loadData(type)
    }

    private fun loadData(type: EntityType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            kotlinx.coroutines.flow.combine(
                bookDao.getAllBooks(),
                collectionDao.getAllCollections()
            ) { books, collections ->
                Pair(books, collections)
            }.collect { (books, collections) ->
                val items = when (type) {
                    EntityType.Authors -> processAuthors(books)
                    EntityType.Series -> processSeries(books)
                    EntityType.Tags -> processTags(books)
                    EntityType.Collections -> processCollections(books, collections)
                }
                
                _uiState.update { 
                    it.copy(
                        items = items.sortedBy { item -> item.name },
                        isLoading = false
                    ) 
                }
            }
        }
    }

    private fun processAuthors(books: List<BookEntity>): List<EntityItem> {
        val authorMap = mutableMapOf<String, MutableList<BookEntity>>()
        
        books.forEach { book ->
            try {
                // Parse authors JSON array string
                val jsonArray = JSONArray(book.authors)
                for (i in 0 until jsonArray.length()) {
                    val authorName = jsonArray.getString(i)
                    if (authorName.isNotBlank()) {
                        authorMap.getOrPut(authorName) { mutableListOf() }.add(book)
                    }
                }
            } catch (e: Exception) {
                // Fallback or single string handling if legacy
                val authorName = book.authors
                if (!authorName.startsWith("[") && authorName.isNotBlank()) {
                     authorMap.getOrPut(authorName) { mutableListOf() }.add(book)
                }
            }
        }
        
        return authorMap.map { (name, books) ->
            EntityItem(
                id = name,
                name = name,
                count = books.size,
                coverUrl = books.firstOrNull()?.coverUrl // Pick first book's cover
            )
        }
    }
    
    private fun processSeries(books: List<BookEntity>): List<EntityItem> {
        val seriesMap = mutableMapOf<String, MutableList<BookEntity>>()
        
        books.forEach { book ->
            val seriesName = book.series
            if (!seriesName.isNullOrBlank()) {
                seriesMap.getOrPut(seriesName) { mutableListOf() }.add(book)
            }
        }
        
         return seriesMap.map { (name, books) ->
            EntityItem(
                id = name,
                name = name,
                count = books.size,
                coverUrl = books.firstOrNull()?.coverUrl
            )
        }
    }

    private fun processTags(books: List<BookEntity>): List<EntityItem> {
        val tagMap = mutableMapOf<String, MutableList<BookEntity>>()
        
        books.forEach { book ->
            try {
                // Parse tags JSON array string
                val jsonArray = org.json.JSONArray(book.tags)
                for (i in 0 until jsonArray.length()) {
                    val tagName = jsonArray.getString(i)
                    if (tagName.isNotBlank()) {
                        tagMap.getOrPut(tagName) { mutableListOf() }.add(book)
                    }
                }
            } catch (e: Exception) {
                // No tags or invalid JSON
            }
        }
        
        return tagMap.map { (name, books) ->
            EntityItem(
                id = name,
                name = name,
                count = books.size,
                coverUrl = books.firstOrNull()?.coverUrl
            )
        }
    }

    private fun processCollections(books: List<BookEntity>, collections: List<com.owlsoda.pageportal.core.database.entity.CollectionEntity>): List<EntityItem> {
        val gson = com.google.gson.Gson()
        return collections.map { collection ->
            var coverUrl: String? = null
            var count = 0
            
            try {
                // Parse book IDs JSON
                val bookIds: List<String> = gson.fromJson(collection.bookIds, Array<String>::class.java).toList()
                count = bookIds.size
                
                // Find first book to get cover
                if (bookIds.isNotEmpty()) {
                    val firstId = bookIds.first()
                    coverUrl = books.find { 
                        it.serviceBookId == firstId && it.serverId == collection.serverId 
                    }?.coverUrl
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
            
            EntityItem(
                id = collection.id.toString(), // Use Database ID for collections
                name = collection.name,
                count = count,
                coverUrl = coverUrl
            )
        }
    }
}

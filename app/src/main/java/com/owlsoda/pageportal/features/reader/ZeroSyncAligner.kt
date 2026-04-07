package com.owlsoda.pageportal.features.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * ZeroSyncAligner provides on-device "Live Sync" by transcribing the audio
 * being played and matching it against the book's text content.
 */
class ZeroSyncAligner(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "ZeroSyncAligner"
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript = _currentTranscript.asStateFlow()
    
    private var chapterText: String = ""
    private var textElements: List<Pair<String, String>> = emptyList() // ID to Text
    
    private var onMatchFound: ((String) -> Unit)? = null
    
    fun setChapterContent(elements: List<Pair<String, String>>) {
        this.textElements = elements
        this.chapterText = elements.joinToString(" ") { it.second }
    }
    
    fun start(onMatch: (String) -> Unit) {
        if (isListening) return
        this.onMatchFound = onMatch
        isListening = true
        
        scope.launch(Dispatchers.Main) {
            initializeRecognizer()
            startListeningLoop()
        }
    }
    
    fun stop() {
        isListening = false
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
    
    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        Log.e(TAG, "Speech recognition error: $error")
                        if (isListening) restartListening()
                    }
                    
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { text ->
                            processTranscript(text)
                        }
                        if (isListening) restartListening()
                    }
                    
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { text ->
                            _currentTranscript.value = text
                        }
                    }
                    
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }
    
    private fun startListeningLoop() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Note: In a real app, we'd use a custom AudioSource here if we could, 
            // but standard SpeechRecognizer usually listens to Mic.
            // For "Internal" tap, we'd need a more complex MediaProjection or custom TFLite Whisper.
            // For this BETA version, it serves as a proof of concept.
        }
        speechRecognizer?.startListening(intent)
    }
    
    private fun restartListening() {
        scope.launch {
            delay(1000)
            if (isListening) startListeningLoop()
        }
    }
    
    private fun processTranscript(text: String) {
        _currentTranscript.value = text
        Log.d(TAG, "Transcript: $text")
        
        // Simple fuzzy search in the chapter elements
        val bestMatch = findBestMatch(text)
        bestMatch?.let { id ->
            Log.d(TAG, "Match found! ID: $id")
            onMatchFound?.invoke(id)
        }
    }
    
    private fun findBestMatch(transcript: String): String? {
        if (textElements.isEmpty() || transcript.length < 5) return null
        
        // Very basic fuzzy search for MVP:
        // Look for exact substring or high overlap in individual elements
        val words = transcript.lowercase().split(" ").filter { it.length > 3 }
        if (words.isEmpty()) return null
        
        var bestId: String? = null
        var maxMatches = 0
        
        for (element in textElements) {
            val content = element.second.lowercase()
            var matches = 0
            for (word in words) {
                if (content.contains(word)) matches++
            }
            
            if (matches > maxMatches && matches >= (words.size / 2)) {
                maxMatches = matches
                bestId = element.first
            }
        }
        
        return bestId
    }
}

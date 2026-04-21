package com.owlsoda.pageportal

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test
import org.junit.Assert.assertEquals

class SearchViewModelTest {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Test
    fun testDebounceBehavior() = runBlocking {
        val _searchQuery = MutableStateFlow("")
        val queriesReceived = mutableListOf<String>()
        val start = System.currentTimeMillis()
        
        val job = launch {
            _searchQuery
                .debounce(50)
                .filter { it.length >= 2 }
                .distinctUntilChanged()
                .collect { query ->
                    println("Searching for: $query at ${System.currentTimeMillis() - start}")
                    queriesReceived.add(query)
                    delay(500) // simulate slow search
                }
        }
        
        _searchQuery.value = "a"
        delay(10)
        _searchQuery.value = "ab"
        delay(10)
        _searchQuery.value = "abc"
        delay(200) // debounce fires for abc
        // While search for "abc" is running (takes 500ms), update query
        _searchQuery.value = "abcd"
        delay(700) 
        
        job.cancel()
        assertEquals(listOf("abc", "abcd"), queriesReceived)
        println("Success!")
    }
}

package com.owlsoda.pageportal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.owlsoda.pageportal.data.preferences.PreferencesRepository
import com.owlsoda.pageportal.ui.theme.PagePortalTheme
import com.owlsoda.pageportal.navigation.PagePortalNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferencesRepository: PreferencesRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by preferencesRepository.themeMode.collectAsState(initial = "SYSTEM")
            
            PagePortalTheme(mode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val audiobookViewModel: com.owlsoda.pageportal.features.player.AudiobookPlayerViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    PagePortalNavHost(audiobookPlayerViewModel = audiobookViewModel)
                }
            }
        }
    }
}

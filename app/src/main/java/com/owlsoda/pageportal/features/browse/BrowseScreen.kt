package com.owlsoda.pageportal.features.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.owlsoda.pageportal.ui.theme.PagePortalPurple

@Composable
fun BrowseScreen(
    onBack: () -> Unit,
    onEntityClick: (String, String) -> Unit, // type, id
    viewModel: EntityViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Authors", "Series", "Tags", "Collections")
    
    // Update ViewModel when tab changes
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            0 -> viewModel.setType(EntityType.Authors)
            1 -> viewModel.setType(EntityType.Series)
            2 -> viewModel.setType(EntityType.Tags)
            3 -> viewModel.setType(EntityType.Collections)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val icons = listOf(
                    Icons.Default.Person,
                    Icons.Default.Folder,
                    Icons.Default.LocalOffer, // Best icon for tags
                    Icons.Default.Star
                )
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { 
                            Text(
                                title,
                                style = MaterialTheme.typography.labelSmall
                            ) 
                        },
                        icon = { Icon(icons[index], contentDescription = title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PagePortalPurple,
                            selectedTextColor = PagePortalPurple,
                            indicatorColor = PagePortalPurple.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Re-using EntityGridScreen content logic directly might be easier if we strip the scaffold there,
            // or just embed it. EntityGridScreen currently has a Scaffold.
            // Let's refactor EntityGridScreen to NOT have a Scaffold if used here, 
            // OR just use it as the content and control the top bar/bottom bar here.
            
            // Actually, EntityGridScreen handles the list. 
            // We can just pass the current state to it or let it observe the filtered type.
            
            // The EntityGridScreen currently builds its own Scaffold.
            // We'll treat BrowseScreen as the host.
            // Let's modify EntityGridScreen to take a modifier and be a simple content composable.
            
            // FOR NOW: Just instantiating EntityGridScreen.
            // It has a Scaffold, so nested Scaffolds might be weird but acceptable if one is just for the grid.
            // However, EntityGridScreen takes a title based on UI state.
            
            EntityGridScreen(
                currentType = tabs[selectedTab],
                onBack = onBack,
                onEntityClick = onEntityClick,
                viewModel = viewModel
            )
        }
    }
}

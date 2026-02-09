package com.owlsoda.pageportal.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Adaptive layout that displays list and detail panes based on window size
 * 
 * @param windowSizeClass Current window size
 * @param listContent The navigation/category list pane
 * @param detailContent The detail/content pane
 * @param showDetail Whether to show detail pane (for COMPACT navigation state)
 */
@Composable
fun ListDetailLayout(
    windowSizeClass: WindowSizeClass,
    listContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
    showDetail: Boolean = false,
    modifier: Modifier = Modifier
) {
    when (windowSizeClass) {
        WindowSizeClass.COMPACT -> {
            // Single pane - show either list or detail
            Box(modifier = modifier.fillMaxSize()) {
                if (showDetail) {
                    detailContent()
                } else {
                    listContent()
                }
            }
        }
        WindowSizeClass.MEDIUM -> {
            // Two pane - 40% list, 60% detail
            Row(modifier = modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxHeight().weight(0.4f)) {
                    listContent()
                }
                Box(modifier = Modifier.fillMaxHeight().weight(0.6f)) {
                    detailContent()
                }
            }
        }
        WindowSizeClass.EXPANDED -> {
            // Two pane - 30% list, 70% detail with spacing
            Row(
                modifier = modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.3f)
                ) {
                    listContent()
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.7f)
                ) {
                    detailContent()
                }
            }
        }
    }
}

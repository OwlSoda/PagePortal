package com.owlsoda.pageportal.features.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    currentPreset: String = "Spoken Word",
    onPresetSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(
        "Normal",
        "Classical",
        "Jazz",
        "Pop",
        "Rock",
        "Spoken Word"
    )
    
    val presetDescriptions = mapOf(
        "Normal" to "Flat response, no adjustments",
        "Classical" to "Enhanced clarity for orchestral music",
        "Jazz" to "Warm mids and bright highs",
        "Pop" to "Boosted bass and treble",
        "Rock" to "Powerful bass and crisp highs",
        "Spoken Word" to "Optimized for voice clarity"
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Audio Equalizer",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                "Choose a preset for optimal audio quality",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            presets.forEach { preset ->
                PresetItem(
                    preset = preset,
                    description = presetDescriptions[preset] ?: "",
                    isSelected = preset == currentPreset,
                    onClick = {
                        onPresetSelected(preset)
                        onDismiss()
                    }
                )
            }
            
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PresetItem(
    preset: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else 
                    MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = preset,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
    
    Spacer(Modifier.height(8.dp))
}

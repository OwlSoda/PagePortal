package com.owlsoda.pageportal.features.settings
 
import android.content.Intent
import android.net.Uri

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.owlsoda.pageportal.ui.theme.BookCampPurple
import com.owlsoda.pageportal.ui.theme.BookCampTextSecondary
import com.owlsoda.pageportal.ui.components.ListDetailLayout
import com.owlsoda.pageportal.ui.components.WindowSizeClass
import com.owlsoda.pageportal.ui.components.rememberWindowSizeClass

/**
 * Settings category for navigation
 */
data class SettingsCategory(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val description: String
)

val settingsCategories = listOf(
    SettingsCategory("general", "General", Icons.Default.Settings, "App-wide preferences"),
    SettingsCategory("reading", "Reading", Icons.Default.MenuBook, "Reader customization"),
    SettingsCategory("audio", "Audio", Icons.Default.Headphones, "Playback settings"),
    SettingsCategory("library", "Library", Icons.Default.Dns, "Server management"),
    SettingsCategory("storage", "Storage", Icons.Default.SdStorage, "Cache and downloads"),
    SettingsCategory("accessibility", "Accessibility", Icons.Default.Accessibility, "Accessibility options"),
    SettingsCategory("about", "About", Icons.Default.Info, "Version and info")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onServersClick: () -> Unit = {},
    onMatchReviewClick: () -> Unit = {},
    onStorageClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val windowSizeClass = rememberWindowSizeClass()
    
    // Selected category state
    var selectedCategory by remember { 
        mutableStateOf(
            if (windowSizeClass == WindowSizeClass.COMPACT) null else "general"
        )
    }

    val context = LocalContext.current
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (windowSizeClass == WindowSizeClass.COMPACT && selectedCategory != null) {
                            settingsCategories.find { it.id == selectedCategory }?.title ?: "Settings"
                        } else {
                            "Settings"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (windowSizeClass == WindowSizeClass.COMPACT && selectedCategory != null) {
                            // On compact, back button goes to category list
                            selectedCategory = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        ListDetailLayout(
            windowSizeClass = windowSizeClass,
            showDetail = selectedCategory != null,
            modifier = Modifier.padding(padding),
            listContent = {
                SettingsCategoriesList(
                    categories = settingsCategories,
                    selectedCategory = selectedCategory,
                    onCategoryClick = { selectedCategory = it.id }
                )
            },
            detailContent = {
                selectedCategory?.let { categoryId ->
                    SettingsCategoryDetail(
                        category = categoryId,
                        state = state,
                        viewModel = viewModel,
                        onServersClick = onServersClick,
                        onMatchReviewClick = onMatchReviewClick,
                        onStorageClick = onStorageClick
                    )
                }
            }
        )
    }
}

@Composable
fun SettingsCategoriesList(
    categories: List<SettingsCategory>,
    selectedCategory: String?,
    onCategoryClick: (SettingsCategory) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(categories) { category ->
            ListItem(
                headlineContent = { 
                    Text(
                        category.title,
                        style = MaterialTheme.typography.titleMedium
                    ) 
                },
                supportingContent = { 
                    Text(
                        category.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BookCampTextSecondary
                    ) 
                },
                leadingContent = {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = BookCampPurple
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, 
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = BookCampTextSecondary.copy(alpha = 0.5f)
                    )
                },
                modifier = Modifier
                    .clickable { onCategoryClick(category) }
                    .padding(vertical = 4.dp),
                colors = if (selectedCategory == category.id) {
                    ListItemDefaults.colors(
                        containerColor = BookCampPurple.copy(alpha = 0.08f)
                    )
                } else {
                    ListItemDefaults.colors()
                }
            )
        }
    }
}

@Composable
fun SettingsCategoryDetail(
    category: String,
    state: SettingsState,
    viewModel: SettingsViewModel,
    onServersClick: () -> Unit,
    onMatchReviewClick: () -> Unit,
    onStorageClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        when (category) {
            "general" -> item { GeneralSettings(state, viewModel) }
            "reading" -> item { ReadingSettings(state, viewModel) }
            "audio" -> item { AudioSettings(state, viewModel) }
            "library" -> item { LibrarySettings(state, onServersClick, onMatchReviewClick) }
            "storage" -> item { StorageSettings(state, viewModel, onStorageClick) }
            "accessibility" -> item { AccessibilitySettings(state, viewModel) }
            "about" -> item { AboutSettings(state) }
        }
    }
}

// GENERAL SETTINGS
@Composable
fun GeneralSettings(state: SettingsState, viewModel: SettingsViewModel) {
    Column {
        var showThemeDialog by remember { mutableStateOf(false) }
        
        SettingsSectionHeader("Appearance")
        SettingsItem(
            icon = Icons.Default.DarkMode,
            title = "Theme",
            subtitle = when (state.themeMode) {
                "LIGHT" -> "Light"
                "DARK" -> "Dark"
                "AMOLED" -> "AMOLED Black"
                else -> "System Default"
            },
            onClick = { showThemeDialog = true }
        )
        
        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentTheme = state.themeMode,
                onThemeSelected = { viewModel.updateTheme(it) },
                onDismiss = { showThemeDialog = false }
            )
        }
        
        Spacer(Modifier.height(16.dp))
        SettingsSectionHeader("Library Display")
        
        var showGridDialog by remember { mutableStateOf(false) }
        SettingsItem(
            icon = Icons.Default.GridView,
            title = "Grid Item Size",
            subtitle = when {
                state.gridMinWidth < 110 -> "Small"
                state.gridMinWidth < 140 -> "Normal"
                state.gridMinWidth < 180 -> "Large"
                else -> "Extra Large"
            },
            onClick = { showGridDialog = true }
        )
        
        if (showGridDialog) {
            GridSizeDialog(
                currentWidth = state.gridMinWidth,
                onWidthChanged = { viewModel.setGridMinWidth(it) },
                onDismiss = { showGridDialog = false }
            )
        }
        
        SwitchSettingsItem(
            icon = Icons.Default.WifiOff,
            title = "Offline Mode",
            subtitle = "Disable network access",
            checked = state.isOfflineMode,
            onCheckedChange = { viewModel.toggleOfflineMode(it) }
        )
        
        Spacer(Modifier.height(16.dp))
        SettingsSectionHeader("Backup & Restore")
        
        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
            onResult = { uri -> uri?.let { viewModel.exportSettings(it) } }
        )

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri -> uri?.let { viewModel.importSettings(it) } }
        )
        
        SettingsItem(
            icon = Icons.Default.Upload,
            title = "Export Settings",
            subtitle = "Save preferences to a file",
            showChevron = false,
            onClick = { exportLauncher.launch("pageportal_settings_backup.json") }
        )
        
        SettingsItem(
            icon = Icons.Default.Download,
            title = "Import Settings",
            subtitle = "Restore preferences from a file",
            showChevron = false,
            onClick = { importLauncher.launch("application/json") }
        )
    }
}

// READING SETTINGS
@Composable
fun ReadingSettings(state: SettingsState, viewModel: SettingsViewModel) {
    Column {
        SettingsSectionHeader("Display")
        
        // Theme Selection
        Text(
            text = "Reading Theme",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val themes = listOf("Light", "Sepia", "Dark", "Black")
            themes.forEach { theme ->
                FilterChip(
                    selected = state.readerTheme.equals(theme, ignoreCase = true),
                    onClick = { viewModel.setReaderTheme(theme) },
                    label = { Text(theme) }
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Brightness
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Brightness", style = MaterialTheme.typography.titleMedium)
            
            val isAuto = state.readerBrightness < 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isAuto) "System Default" else "${(state.readerBrightness * 100).toInt()}%")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = isAuto,
                    onCheckedChange = { 
                        if (it) viewModel.setReaderBrightness(-1.0f)
                        else viewModel.setReaderBrightness(0.5f)
                    }
                )
            }
            
            if (!isAuto) {
                Slider(
                    value = state.readerBrightness,
                    onValueChange = { viewModel.setReaderBrightness(it) },
                    valueRange = 0.0f..1.0f
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        SettingsSectionHeader("Typography")
        
        // Font Family
        Text(
            text = "Font Family",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val fonts = listOf("Serif", "Sans", "Mono")
            fonts.forEach { font ->
                FilterChip(
                    selected = state.readerFontFamily.startsWith(font, ignoreCase = true),
                    onClick = { viewModel.setReaderFontFamily(if (font == "Sans") "Sans-Serif" else if (font == "Mono") "Monospace" else "Serif") },
                    label = { Text(font) }
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Font Size
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Font Size: ${(state.readerFontSize * 100).toInt()}%")
            Slider(
                value = state.readerFontSize,
                onValueChange = { viewModel.setReaderFontSize(it) },
                valueRange = 0.5f..2.0f,
                steps = 14
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Line Height
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
             Text("Line Spacing: ${String.format("%.1f", state.readerLineHeight)}")
             Slider(
                 value = state.readerLineHeight,
                 onValueChange = { viewModel.setReaderLineHeight(it) },
                 valueRange = 1.0f..2.5f,
                 steps = 14
             )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Text Alignment
        Text(
            text = "Alignment",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val alignments = listOf("LEFT", "JUSTIFY", "CENTER")
            alignments.forEach { align ->
                FilterChip(
                    selected = state.readerTextAlignment == align,
                    onClick = { viewModel.setReaderTextAlignment(align) },
                    label = { Text(align.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        SettingsSectionHeader("Layout")
        
        // Margins
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Margins: ${state.readerMargin}")
            Slider(
                value = state.readerMargin.toFloat(),
                onValueChange = { viewModel.setReaderMargin(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9
            )
        }
        
        // Paragraph Spacing
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Paragraph Spacing: ${String.format("%.1f", state.readerParagraphSpacing)}x")
            Slider(
                value = state.readerParagraphSpacing,
                onValueChange = { viewModel.setReaderParagraphSpacing(it) },
                valueRange = 0.0f..2.0f,
                steps = 19
            )
        }
        
        // Scroll Mode
        Row(
             modifier = Modifier
                 .fillMaxWidth()
                 .padding(horizontal = 16.dp, vertical = 8.dp),
             horizontalArrangement = Arrangement.SpaceBetween,
             verticalAlignment = Alignment.CenterVertically
         ) {
             Text("Vertical Scroll", style = MaterialTheme.typography.titleMedium)
             Switch(
                 checked = state.readerVerticalScroll,
                 onCheckedChange = { viewModel.setReaderVerticalScroll(it) }
             )
         }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        SettingsSectionHeader("Gestures")
        
        val gestureLabels = mapOf(
            "PREV" to "Previous Page",
            "NEXT" to "Next Page",
            "MENU" to "Toggle Menu",
            "NONE" to "None"
        )
        
        // Left Tap
        var showLeftTapDialog by remember { mutableStateOf(false) }
        SettingsItem(
            icon = Icons.Default.TouchApp,
            title = "Left Tap Action",
            subtitle = gestureLabels[state.gestureTapLeft] ?: state.gestureTapLeft,
            onClick = { showLeftTapDialog = true }
        )
        if (showLeftTapDialog) {
            GestureActionDialog(
                currentAction = state.gestureTapLeft,
                onActionSelected = { viewModel.setGestureTapLeft(it) },
                onDismiss = { showLeftTapDialog = false }
            )
        }
        
        // Center Tap
        var showCenterTapDialog by remember { mutableStateOf(false) }
        SettingsItem(
            icon = Icons.Default.TouchApp,
            title = "Center Tap Action",
            subtitle = gestureLabels[state.gestureTapCenter] ?: state.gestureTapCenter,
            onClick = { showCenterTapDialog = true }
        )
        if (showCenterTapDialog) {
            GestureActionDialog(
                currentAction = state.gestureTapCenter,
                onActionSelected = { viewModel.setGestureTapCenter(it) },
                onDismiss = { showCenterTapDialog = false }
            )
        }
        
        // Right Tap
        var showRightTapDialog by remember { mutableStateOf(false) }
        SettingsItem(
            icon = Icons.Default.TouchApp,
            title = "Right Tap Action",
            subtitle = gestureLabels[state.gestureTapRight] ?: state.gestureTapRight,
            onClick = { showRightTapDialog = true }
        )
        if (showRightTapDialog) {
            GestureActionDialog(
                currentAction = state.gestureTapRight,
                onActionSelected = { viewModel.setGestureTapRight(it) },
                onDismiss = { showRightTapDialog = false }
            )
        }
    }
}


// AUDIO SETTINGS
@Composable
fun AudioSettings(state: SettingsState, viewModel: SettingsViewModel) {
    Column {
        SettingsSectionHeader("Playback")
        
        var showSpeedDialog by remember { mutableStateOf(false) }
        SettingsItem(
            icon = Icons.Default.Speed,
            title = "Default Playback Speed",
            subtitle = String.format("%.2fx", state.playbackSpeed),
            onClick = { showSpeedDialog = true }
        )
        
        if (showSpeedDialog) {
            PlaybackSpeedDialog(
                currentSpeed = state.playbackSpeed,
                onSpeedChanged = { viewModel.setPlaybackSpeed(it) },
                onDismiss = { showSpeedDialog = false }
            )
        }
        
        var showTimerDialog by remember { mutableStateOf(false) }
        SettingsItem(
            icon = Icons.Default.Timer,
            title = "Default Sleep Timer",
            subtitle = if (state.sleepTimerMinutes == 0) "Disabled" else "${state.sleepTimerMinutes} minutes",
            onClick = { showTimerDialog = true }
        )
        
        if (showTimerDialog) {
            SleepTimerDialog(
                currentMinutes = state.sleepTimerMinutes,
                onMinutesSelected = { viewModel.setSleepTimerMinutes(it) },
                onDismiss = { showTimerDialog = false }
            )
        }
        
    }
}

// LIBRARY SETTINGS
@Composable
fun LibrarySettings(
    state: SettingsState,
    onServersClick: () -> Unit,
    onMatchReviewClick: () -> Unit
) {
    Column {
        SettingsSectionHeader("Management")
        SettingsItem(
            icon = Icons.Default.Dns,
            title = "Manage Servers",
            subtitle = "Connect specific services",
            onClick = onServersClick
        )
        SettingsItem(
            icon = Icons.Default.Merge,
            title = "Match Review",
            subtitle = "Fix incorrect book matches",
            onClick = onMatchReviewClick
        )
    }
}

// STORAGE SETTINGS
@Composable
fun StorageSettings(
    state: SettingsState,
    viewModel: SettingsViewModel,
    onStorageClick: () -> Unit
) {
    Column {
        SettingsSectionHeader("Cache")
        SettingsItem(
            icon = Icons.Default.DeleteSweep,
            title = "Clear Cache",
            subtitle = "${state.cacheSize} used",
            showChevron = false,
            onClick = { viewModel.clearCache() }
        )
        SettingsItem(
            icon = Icons.Default.SdStorage,
            title = "Storage Management",
            subtitle = "Manage downloads",
            onClick = onStorageClick
        )
    }
}

// ACCESSIBILITY SETTINGS
@Composable
fun AccessibilitySettings(state: SettingsState, viewModel: SettingsViewModel) {
    Column {
        SettingsSectionHeader("Display")
        
        SwitchSettingsItem(
            icon = Icons.Default.TextFields,
            title = "Bold Text",
            subtitle = "Use heavier font weights throughout the app",
            checked = state.boldTextEnabled,
            onCheckedChange = { viewModel.setBoldTextEnabled(it) }
        )
        
        SwitchSettingsItem(
            icon = Icons.Default.ScreenLockPortrait,
            title = "Keep Screen On",
            subtitle = "Prevent screen from dimming while reading or listening",
            checked = state.keepScreenOn,
            onCheckedChange = { viewModel.setKeepScreenOn(it) }
        )
        
        SwitchSettingsItem(
            icon = Icons.Default.Animation,
            title = "Reduce Animations",
            subtitle = "Minimize motion effects for comfort",
            checked = state.reduceAnimations,
            onCheckedChange = { viewModel.setReduceAnimations(it) }
        )
    }
}

// ABOUT SETTINGS
@Composable
fun AboutSettings(state: SettingsState) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    Column {
        SettingsSectionHeader("App Information")
        SettingsItem(
            icon = Icons.Default.Info,
            title = "Version",
            subtitle = versionName,
            showChevron = true,
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/OwlSoda/PagePortal/releases"))
                context.startActivity(intent)
            }
        )
    }
}

// DIALOGS
@Composable
fun ThemeSelectionDialog(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            Column {
                ThemeOption("System Default", currentTheme == "SYSTEM") {
                    onThemeSelected("SYSTEM")
                    onDismiss()
                }
                ThemeOption("Light", currentTheme == "LIGHT") {
                    onThemeSelected("LIGHT")
                    onDismiss()
                }
                ThemeOption("Dark", currentTheme == "DARK") {
                    onThemeSelected("DARK")
                    onDismiss()
                }
                ThemeOption("AMOLED Black", currentTheme == "AMOLED") {
                    onThemeSelected("AMOLED")
                    onDismiss()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column {
                Text(
                    text = String.format("%.2fx", currentSpeed),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Slider(
                    value = currentSpeed,
                    onValueChange = onSpeedChanged,
                    valueRange = 0.5f..3.0f,
                    steps = 49
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun GridSizeDialog(
    currentWidth: Int,
    onWidthChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grid Item Size") },
        text = {
            Column {
                Text(
                    text = "${currentWidth}dp",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    text = "Larger items = fewer columns",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
                )
                Slider(
                    value = currentWidth.toFloat(),
                    onValueChange = { onWidthChanged(it.toInt()) },
                    valueRange = 100f..300f,
                    steps = 19
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun SleepTimerDialog(
    currentMinutes: Int,
    onMinutesSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timerOptions = listOf(0, 15, 30, 45, 60, 90, 120)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Sleep Timer") },
        text = {
            Column {
                timerOptions.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onMinutesSelected(minutes)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (minutes == 0) "Disabled" else "$minutes minutes")
                        RadioButton(
                            selected = currentMinutes == minutes,
                            onClick = {
                                onMinutesSelected(minutes)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}



@Composable
fun GestureActionDialog(
    currentAction: String,
    onActionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "PREV" to "Previous Page",
        "NEXT" to "Next Page",
        "MENU" to "Toggle Menu",
        "NONE" to "None"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Action") },
        text = {
            Column {
                options.forEach { (action, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onActionSelected(action)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label)
                        RadioButton(
                            selected = currentAction == action,
                            onClick = {
                                onActionSelected(action)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// REUSABLE COMPONENTS
@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = BookCampPurple,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                title, 
                style = MaterialTheme.typography.bodyLarge
            ) 
        },
        supportingContent = subtitle?.let { { 
            Text(
                it, 
                style = MaterialTheme.typography.bodyMedium,
                color = BookCampTextSecondary
            ) 
        } },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BookCampPurple.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = if (showChevron) {
            { 
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, 
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = BookCampTextSecondary.copy(alpha = 0.3f)
                ) 
            }
        } else null,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SwitchSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                title, 
                style = MaterialTheme.typography.bodyLarge
            ) 
        },
        supportingContent = subtitle?.let { { 
            Text(
                it, 
                style = MaterialTheme.typography.bodyMedium,
                color = BookCampTextSecondary
            ) 
        } },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BookCampPurple.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Switch(
                checked = checked, 
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BookCampPurple
                )
            )
        },
        modifier = Modifier.clickable(onClick = { onCheckedChange(!checked) })
    )
}

@Composable
fun ThemeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title)
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}

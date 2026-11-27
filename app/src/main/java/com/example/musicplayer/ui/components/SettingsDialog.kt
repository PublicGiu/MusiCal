package com.example.musicplayer.ui.components

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.musicplayer.R
import com.example.musicplayer.data.AppLanguage
import com.example.musicplayer.data.backup.BackupErrorCode
import com.example.musicplayer.data.backup.BackupException
import com.example.musicplayer.data.backup.ImportSummary
import com.example.musicplayer.data.AudioSourceMode
import com.example.musicplayer.data.SectionViewMode
import com.example.musicplayer.data.ThemeMode
import com.example.musicplayer.data.update.GithubUpdateChecker
import com.example.musicplayer.ui.MusicViewModel
import com.example.musicplayer.ui.tagger.UnreleasedScanState
import kotlinx.coroutines.launch

/**
 * Finestra di dialogo a schermo intero per la gestione delle impostazioni dell'app:
 * tema, colori dinamici, autoplay continuo, scansione libreria (con scelta sorgenti) e integrazione Last.fm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onOpenLastFmDialog: () -> Unit,
    onPickCustomFolder: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    onOpenStats: () -> Unit = {}
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val tracksViewMode by viewModel.tracksViewMode.collectAsState()
    val albumsViewMode by viewModel.albumsViewMode.collectAsState()
    val playlistsViewMode by viewModel.playlistsViewMode.collectAsState()
    val isAutoplayEnabled by viewModel.isAutoplayEnabled.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isLastFmLoggedIn by viewModel.isLastFmLoggedIn.collectAsState()
    val lastFmUsername by viewModel.lastFmUsername.collectAsState()
    val lastFmAvatarPath by viewModel.lastFmAvatarPath.collectAsState()
    val pendingScrobblesCount by viewModel.pendingScrobblesCount.collectAsState()
    val audioSourceMode by viewModel.audioSourceMode.collectAsState()
    val customFolders by viewModel.customFolders.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateCheckError by viewModel.updateCheckError.collectAsState()
    val ignoredDuplicateKeys by viewModel.ignoredDuplicateKeys.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val unreleasedScanState by viewModel.unreleasedScanState.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isBackupBusy by remember { mutableStateOf(false) }
    var importSummaryState by remember { mutableStateOf<ImportSummary?>(null) }
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var isIgnoringBatteryOptimizations by remember(context) {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    var showBatteryGuideDialog by remember { mutableStateOf(false) }
    val openBatteryAppSettings = {
        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(appDetailsIntent)
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations =
                    powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            isBackupBusy = true
            viewModel.exportListeningData(uri) { result ->
                isBackupBusy = false
                coroutineScope.launch {
                    val msg = result.fold(
                        onSuccess = { context.getString(R.string.backup_export_success) },
                        onFailure = { err ->
                            context.getString(
                                R.string.backup_export_error,
                                mapBackupErrorMessage(context, err)
                            )
                        }
                    )
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isBackupBusy = true
            viewModel.importListeningData(uri) { result ->
                isBackupBusy = false
                result.fold(
                    onSuccess = { summary -> importSummaryState = summary },
                    onFailure = { err ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(
                                    R.string.backup_import_error,
                                    mapBackupErrorMessage(context, err)
                                )
                            )
                        }
                    }
                )
            }
        }
    }

    val offsetX = remember { Animatable(0f) }
    var containerWidthPx by remember { mutableFloatStateOf(1080f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dismissProgress = (offsetX.value / containerWidthPx).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * (1f - dismissProgress)))
                .onSizeChanged { containerWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        val target = (offsetX.value + delta).coerceAtLeast(0f)
                        coroutineScope.launch {
                            offsetX.snapTo(target)
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val threshold = containerWidthPx * 0.25f
                        if (offsetX.value >= threshold || (velocity > 1000f && offsetX.value > 30f)) {
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = containerWidthPx,
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                )
                                onDismiss()
                            }
                        } else {
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    }
                )
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offsetX.value
                        shadowElevation = if (offsetX.value > 0f) 16f else 0f
                    },
                topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.tab_settings),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            snackbarHost = {
                val density = LocalDensity.current
                val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
                val snackbarBottomPadding = if (navBarBottomPx > 0) 12.dp else 64.dp
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = snackbarBottomPadding)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // 1. Lingua dell'Applicazione
                SettingsSectionHeader(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.settings_section_language)
                )
                SettingsTileSelectorCard(
                    selectedOption = appLanguage,
                    onOptionSelected = { viewModel.setAppLanguage(it) },
                    options = listOf(
                        TileSelectorOption(
                            value = AppLanguage.ITALIAN,
                            label = stringResource(R.string.lang_short_it)
                        ),
                        TileSelectorOption(
                            value = AppLanguage.ENGLISH,
                            label = stringResource(R.string.lang_short_en)
                        ),
                        TileSelectorOption(
                            value = AppLanguage.SYSTEM,
                            label = stringResource(R.string.lang_short_system),
                            icon = Icons.Outlined.DesktopWindows
                        )
                    )
                )

                // 2. Aspetto & Visualizzazione
                SettingsSectionHeader(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.settings_appearance_short)
                )
                SettingsTileSelectorCard(
                    selectedOption = themeMode,
                    onOptionSelected = { viewModel.setThemeMode(it) },
                    options = listOf(
                        TileSelectorOption(
                            value = ThemeMode.DARK,
                            label = stringResource(R.string.theme_short_dark),
                            icon = Icons.Outlined.DarkMode
                        ),
                        TileSelectorOption(
                            value = ThemeMode.LIGHT,
                            label = stringResource(R.string.theme_short_light),
                            icon = Icons.Outlined.LightMode
                        ),
                        TileSelectorOption(
                            value = ThemeMode.SYSTEM,
                            label = stringResource(R.string.theme_short_system),
                            icon = Icons.Outlined.DesktopWindows
                        )
                    )
                )
                SettingsSectionLayoutsCard(
                    tracksViewMode = tracksViewMode,
                    albumsViewMode = albumsViewMode,
                    playlistsViewMode = playlistsViewMode,
                    onTracksViewModeChange = { viewModel.setTracksViewMode(it) },
                    onAlbumsViewModeChange = { viewModel.setAlbumsViewMode(it) },
                    onPlaylistsViewModeChange = { viewModel.setPlaylistsViewMode(it) }
                )

                // 3. Riproduzione & Audio
                SettingsSectionHeader(
                    icon = Icons.Default.Radio,
                    title = stringResource(R.string.settings_section_playback)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_autoplay_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = stringResource(R.string.settings_autoplay_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAutoplayEnabled,
                                onCheckedChange = { viewModel.setAutoplayEnabled(it) }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenEqualizer() }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_equalizer_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = stringResource(R.string.settings_equalizer_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSleepTimer() }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.sleep_timer),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = stringResource(R.string.settings_sleep_timer_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenStats() }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueryStats,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_stats_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = stringResource(R.string.settings_stats_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openBatteryAppSettings() }
                                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isIgnoringBatteryOptimizations) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.BatteryChargingFull
                                },
                                contentDescription = null,
                                tint = if (isIgnoringBatteryOptimizations) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_battery_background_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = stringResource(
                                        if (isIgnoringBatteryOptimizations) {
                                            R.string.settings_battery_unrestricted
                                        } else {
                                            R.string.settings_battery_optimized
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { showBatteryGuideDialog = true }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_info_outline),
                                    contentDescription = stringResource(R.string.settings_battery_info_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 4. Libreria Musicale & Fonti
                SettingsSectionHeader(
                    icon = Icons.Default.Folder,
                    title = stringResource(R.string.settings_section_library)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_audio_source_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = stringResource(R.string.settings_audio_source_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AudioSourceMode.entries.forEach { mode ->
                                val isSelected = audioSourceMode == mode
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setAudioSourceMode(mode) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.setAudioSourceMode(mode) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(mode.labelResId),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = stringResource(mode.descriptionResId),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Sottosezione Cartelle a Scelta
                        if (audioSourceMode == AudioSourceMode.CUSTOM_FOLDERS) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = stringResource(R.string.settings_custom_folders_title, customFolders.size),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (customFolders.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.settings_custom_folders_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    customFolders.forEach { folder ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FolderOpen,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = folder,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.removeCustomFolder(folder) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = stringResource(R.string.settings_remove_folder_desc),
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onPickCustomFolder,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_add_folder))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.settings_library_summary,
                                    tracks.size,
                                    albums.size,
                                    stats.values.sumOf { it.playCount }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                viewModel.loadTracks()
                                viewModel.loadPlaylists()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_reload_library))
                        }

                        if (ignoredDuplicateKeys.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearAllIgnoredDuplicates()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_restore_duplicates, ignoredDuplicateKeys.size))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.settings_unreleased_scan_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_unreleased_scan_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        when (val scanState = unreleasedScanState) {
                            is UnreleasedScanState.Idle -> {
                                OutlinedButton(
                                    onClick = { viewModel.scanAndOrganizeUnreleasedTracks() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings_unreleased_scan_button))
                                }
                            }
                            is UnreleasedScanState.Scanning -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        val progressFraction = if (scanState.total > 0) {
                                            (scanState.current.toFloat() / scanState.total.toFloat()).coerceIn(0f, 1f)
                                        } else {
                                            0f
                                        }
                                        Text(
                                            text = stringResource(
                                                R.string.settings_unreleased_scan_progress,
                                                scanState.current,
                                                scanState.total,
                                                scanState.currentTrackTitle
                                            ),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { progressFraction },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = { viewModel.cancelUnreleasedScan() }) {
                                                Text(stringResource(R.string.cancel))
                                            }
                                        }
                                    }
                                }
                            }
                            is UnreleasedScanState.Completed -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        val summaryMessage = when {
                                            scanState.scannedCount == 0 ->
                                                stringResource(R.string.settings_unreleased_scan_none_needed)
                                            scanState.newlyGroupedUnreleasedCount > 0 ->
                                                stringResource(
                                                    R.string.settings_unreleased_scan_result_grouped,
                                                    scanState.newlyGroupedUnreleasedCount,
                                                    scanState.affectedArtistsCount,
                                                    scanState.scannedCount
                                                )
                                            else ->
                                                stringResource(
                                                    R.string.settings_unreleased_scan_result_zero,
                                                    scanState.scannedCount
                                                )
                                        }
                                        Text(
                                            text = summaryMessage,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (scanState.officialMatches.isNotEmpty()) {
                                                Button(
                                                    onClick = {
                                                        if (viewModel.reviewOfficialMatchesFromScan()) {
                                                            onDismiss()
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AutoFixHigh,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        stringResource(
                                                            R.string.settings_unreleased_scan_review_official,
                                                            scanState.officialMatches.size
                                                        )
                                                    )
                                                }
                                            }
                                            TextButton(onClick = { viewModel.dismissUnreleasedScanSummary() }) {
                                                Text(stringResource(R.string.done))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Servizi Esterni (Last.fm)
                SettingsSectionHeader(
                    icon = null,
                    customIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lastfm),
                            contentDescription = "Last.fm",
                            modifier = Modifier.size(20.dp),
                            tint = Color.Unspecified
                        )
                    },
                    title = stringResource(R.string.lastfm_section_title)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLastFmLoggedIn) {
                                LastFmAvatar(
                                    avatarPath = lastFmAvatarPath,
                                    size = 36.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            pendingScrobblesCount > 0 -> Color(0xFFFF9800)
                                            isLastFmLoggedIn -> Color(0xFF4CAF50)
                                            else -> Color.Gray
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isLastFmLoggedIn) {
                                    stringResource(R.string.lastfm_connected_as, lastFmUsername ?: stringResource(R.string.lastfm_default_user))
                                } else {
                                    stringResource(R.string.lastfm_not_connected)
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        if (pendingScrobblesCount > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.lastfm_pending_settings_note, pendingScrobblesCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF9800)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = onOpenLastFmDialog,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isLastFmLoggedIn) stringResource(R.string.lastfm_manage_account) else stringResource(R.string.lastfm_login_button))
                        }
                    }
                }

                // 4b. Backup e Ripristino Locale
                SettingsSectionHeader(
                    icon = Icons.Default.Save,
                    title = stringResource(R.string.settings_section_backup)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.backup_section_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val defaultFileName = "musical_backup_${java.time.LocalDate.now()}.json"
                                exportBackupLauncher.launch(defaultFileName)
                            },
                            enabled = !isBackupBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_export_button))
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                importBackupLauncher.launch(
                                    arrayOf("application/json", "application/octet-stream", "*/*")
                                )
                            },
                            enabled = !isBackupBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_import_button))
                        }

                        if (isBackupBusy) {
                            Spacer(modifier = Modifier.height(10.dp))
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.CenterHorizontally),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                // 5. Informazioni sull'applicazione & Aggiornamenti GitHub
                SettingsSectionHeader(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_section_info)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "MusiCal",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.settings_version, com.example.musicplayer.BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.settings_arch_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // Sezione Aggiornamenti GitHub
                        Text(
                            text = stringResource(R.string.settings_updates_github_header),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        when {
                            isCheckingUpdate -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.settings_checking_updates),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            updateInfo != null -> {
                                if (updateInfo?.isUpdateAvailable == true) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.settings_new_version_available, updateInfo?.latestVersion ?: ""),
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    val url = updateInfo?.apkDownloadUrl ?: updateInfo?.htmlUrl ?: GithubUpdateChecker.GITHUB_REPO_URL
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(stringResource(R.string.settings_download_update))
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.settings_up_to_date),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                            }
                            updateCheckError != null -> {
                                Text(
                                    text = stringResource(R.string.settings_update_check_error, updateCheckError ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { viewModel.checkForUpdates(isManual = true) },
                            enabled = !isCheckingUpdate,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_check_updates))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GithubUpdateChecker.GITHUB_REPO_URL)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_github_repo))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        importSummaryState?.let { summary ->
            AlertDialog(
                onDismissRequest = { importSummaryState = null },
                title = {
                    Text(
                        text = stringResource(R.string.backup_import_summary_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.backup_import_summary_playlists_created,
                                summary.importedPlaylistsCount
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.backup_import_summary_playlists_updated,
                                summary.updatedPlaylistsCount
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.backup_import_summary_favorites_added,
                                summary.favoritesAddedCount
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.backup_import_summary_stats_restored,
                                summary.restoredStatsCount
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.backup_import_summary_scrobbles_restored,
                                summary.restoredPendingScrobblesCount
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (summary.preferencesRestored) {
                            Text(
                                text = stringResource(R.string.backup_import_summary_prefs_restored),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (summary.unmatchedTrackLabels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.backup_import_summary_unmatched_header,
                                    summary.totalUnmatchedCount
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            val previewItems = summary.unmatchedTrackLabels.take(12)
                            previewItems.forEach { label ->
                                Text(
                                    text = "• $label",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val remaining = summary.totalUnmatchedCount - previewItems.size
                            if (remaining > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.backup_import_summary_unmatched_more,
                                        remaining
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { importSummaryState = null }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        if (showBatteryGuideDialog) {
            val guideSteps = listOf(
                R.string.settings_battery_guide_step_1,
                R.string.settings_battery_guide_step_2,
                R.string.settings_battery_guide_step_3,
                R.string.settings_battery_guide_step_4
            )
            AlertDialog(
                onDismissRequest = { showBatteryGuideDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info_outline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.settings_battery_guide_title),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_battery_guide_intro),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        guideSteps.forEach { stepResId ->
                            Text(
                                text = stringResource(stepResId),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatteryGuideDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBatteryGuideDialog = false
                            openBatteryAppSettings()
                        }
                    ) {
                        Text(stringResource(R.string.settings_battery_open_settings))
                    }
                }
            )
        }
    }
}
}

private data class TileSelectorOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null
)

@Composable
private fun <T> SettingsTileSelectorCard(
    options: List<TileSelectorOption<T>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            options.forEach { option ->
                val selected = option.value == selectedOption
                val tileContainerColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "settingsTileContainer"
                )
                val tileBorderColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "settingsTileBorder"
                )
                val tileContentColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "settingsTileContent"
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = tileContainerColor,
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = tileBorderColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(78.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOptionSelected(option.value) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (option.icon != null) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = tileContentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Text(
                            text = option.label,
                            style = if (option.icon == null) {
                                MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp)
                            } else {
                                MaterialTheme.typography.labelLarge
                            },
                            fontWeight = if (selected || option.icon == null) FontWeight.SemiBold else FontWeight.Medium,
                            color = tileContentColor,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionLayoutsCard(
    tracksViewMode: SectionViewMode,
    albumsViewMode: SectionViewMode,
    playlistsViewMode: SectionViewMode,
    onTracksViewModeChange: (SectionViewMode) -> Unit,
    onAlbumsViewModeChange: (SectionViewMode) -> Unit,
    onPlaylistsViewModeChange: (SectionViewMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_layout_card_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = stringResource(R.string.settings_layout_card_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SectionLayoutRow(
                icon = Icons.Default.MusicNote,
                sectionTitle = stringResource(R.string.tab_tracks),
                currentMode = tracksViewMode,
                onModeSelected = onTracksViewModeChange
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SectionLayoutRow(
                icon = Icons.Default.Album,
                sectionTitle = stringResource(R.string.tab_albums),
                currentMode = albumsViewMode,
                onModeSelected = onAlbumsViewModeChange
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SectionLayoutRow(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                sectionTitle = stringResource(R.string.tab_playlists),
                currentMode = playlistsViewMode,
                onModeSelected = onPlaylistsViewModeChange
            )
        }
    }
}

@Composable
private fun SectionLayoutRow(
    icon: ImageVector,
    sectionTitle: String,
    currentMode: SectionViewMode,
    onModeSelected: (SectionViewMode) -> Unit
) {
    var rememberedGridMode by remember {
        mutableStateOf(if (currentMode.isGrid) currentMode else SectionViewMode.GRID_2)
    }
    if (currentMode.isGrid && rememberedGridMode != currentMode) {
        rememberedGridMode = currentMode
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LayoutOptionChip(
                    selected = !currentMode.isGrid,
                    label = stringResource(R.string.layout_mode_list),
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    onClick = { onModeSelected(SectionViewMode.LIST) }
                )
                LayoutOptionChip(
                    selected = currentMode.isGrid,
                    label = stringResource(R.string.layout_mode_grid),
                    icon = Icons.Default.GridView,
                    onClick = {
                        onModeSelected(if (currentMode.isGrid) currentMode else rememberedGridMode)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = currentMode.isGrid,
            enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.layout_grid_size_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val gridSizes = listOf(
                        SectionViewMode.GRID_2 to stringResource(R.string.layout_grid_2x2),
                        SectionViewMode.GRID_3 to stringResource(R.string.layout_grid_3x3),
                        SectionViewMode.GRID_4 to stringResource(R.string.layout_grid_4x4)
                    )
                    gridSizes.forEach { (gridMode, label) ->
                        LayoutOptionChip(
                            selected = currentMode == gridMode,
                            label = label,
                            icon = null,
                            onClick = { onModeSelected(gridMode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutOptionChip(
    selected: Boolean,
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
        },
        animationSpec = tween(durationMillis = 160),
        label = "layoutChipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(durationMillis = 160),
        label = "layoutChipBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 160),
        label = "layoutChipContent"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = borderColor
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: ImageVector?,
    title: String,
    customIcon: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    ) {
        if (customIcon != null) {
            customIcon()
            Spacer(modifier = Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun mapBackupErrorMessage(context: android.content.Context, error: Throwable): String {
    val code = (error as? BackupException)?.code
    val resId = when (code) {
        BackupErrorCode.FILE_TOO_LARGE -> R.string.backup_error_file_too_large
        BackupErrorCode.UNSUPPORTED_VERSION -> R.string.backup_error_unsupported_version
        BackupErrorCode.INVALID_FORMAT -> R.string.backup_error_invalid_format
        BackupErrorCode.IO_ERROR, null -> R.string.backup_error_io
    }
    return context.getString(resId)
}

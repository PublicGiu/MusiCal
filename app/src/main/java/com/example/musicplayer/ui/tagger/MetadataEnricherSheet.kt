package com.example.musicplayer.ui.tagger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import coil.compose.AsyncImage
import com.example.musicplayer.R
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.formatDurationMs
import com.example.musicplayer.data.tagger.model.DeezerSearchTrack
import com.example.musicplayer.data.tagger.model.EnrichedMetadata
import com.example.musicplayer.data.tagger.repository.EnricherOptions
import com.example.musicplayer.data.tagger.writer.loadResizedJpegBytesFromUri

sealed class EnricherUiState {
    object Idle : EnricherUiState()
    data class Loading(val message: String) : EnricherUiState()
    data class ReviewTrack(
        val track: AudioTrack,
        val enriched: EnrichedMetadata,
        val candidates: List<DeezerSearchTrack> = emptyList(),
        val options: EnricherOptions = EnricherOptions()
    ) : EnricherUiState()
    data class ReviewAlbum(
        val album: Album,
        val tracksEnriched: Map<Long, EnrichedMetadata>,
        val individualTracksEnriched: Map<Long, EnrichedMetadata> = tracksEnriched,
        val unifiedTracksEnriched: Map<Long, EnrichedMetadata> = tracksEnriched,
        val options: EnricherOptions = EnricherOptions(),
        val isSeparationMode: Boolean = false
    ) : EnricherUiState()
    data class Writing(val current: Int, val total: Int, val trackTitle: String) : EnricherUiState()
    data class Success(val message: String) : EnricherUiState()
    data class Error(val message: String) : EnricherUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEnricherSheet(
    state: EnricherUiState,
    onDismissRequest: () -> Unit,
    onApplyTrack: (AudioTrack, EnrichedMetadata, EnricherOptions) -> Unit,
    onApplyAlbum: (Album, Map<Long, EnrichedMetadata>, EnricherOptions) -> Unit,
    onSelectCandidate: (AudioTrack, DeezerSearchTrack, EnricherOptions) -> Unit = { _, _, _ -> },
    onCustomSearch: (AudioTrack, String, String) -> Unit = { _, _, _ -> },
    onToggleAlbumSeparationMode: ((Boolean) -> Unit)? = null,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state is EnricherUiState.Idle) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.autotag_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.autotag_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Contenuto in base allo stato
            when (state) {
                is EnricherUiState.Loading -> {
                    LoadingContent(message = state.message)
                }
                is EnricherUiState.ReviewTrack -> {
                    ReviewTrackContent(
                        track = state.track,
                        enriched = state.enriched,
                        candidates = state.candidates,
                        initialOptions = state.options,
                        onCancel = onDismissRequest,
                        onApply = { editedEnriched, options ->
                            onApplyTrack(state.track, editedEnriched, options)
                        },
                        onSelectCandidate = { candidate ->
                            onSelectCandidate(state.track, candidate, state.options)
                        },
                        onCustomSearch = { title, artist ->
                            onCustomSearch(state.track, title, artist)
                        }
                    )
                }
                is EnricherUiState.ReviewAlbum -> {
                    ReviewAlbumContent(
                        album = state.album,
                        tracksEnriched = state.tracksEnriched,
                        initialOptions = state.options,
                        isSeparationMode = state.isSeparationMode,
                        onToggleSeparationMode = onToggleAlbumSeparationMode,
                        onCancel = onDismissRequest,
                        onApply = { updatedTracks, options ->
                            onApplyAlbum(state.album, updatedTracks, options)
                        }
                    )
                }
                is EnricherUiState.Writing -> {
                    WritingContent(
                        current = state.current,
                        total = state.total,
                        trackTitle = state.trackTitle
                    )
                }
                is EnricherUiState.Success -> {
                    SuccessContent(
                        message = state.message,
                        onDone = onDismissRequest
                    )
                }
                is EnricherUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onDismiss = onDismissRequest,
                        onRetry = onRetry
                    )
                }
                EnricherUiState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun LoadingContent(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ReviewTrackContent(
    track: AudioTrack,
    enriched: EnrichedMetadata,
    candidates: List<DeezerSearchTrack>,
    initialOptions: EnricherOptions,
    onCancel: () -> Unit,
    onApply: (EnrichedMetadata, EnricherOptions) -> Unit,
    onSelectCandidate: (DeezerSearchTrack) -> Unit,
    onCustomSearch: (String, String) -> Unit
) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(initialOptions) }
    var showLyricsPreview by remember { mutableStateOf(false) }
    var isEditingDetails by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Gestione copertina personalizzata da galleria
    var customCoverBytes by remember(enriched) { mutableStateOf<ByteArray?>(enriched.coverBytes) }
    val trackCoverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bytes = loadResizedJpegBytesFromUri(context, uri)
            if (bytes != null) {
                customCoverBytes = bytes
                options = options.copy(embedCover = true)
            }
        }
    }

    // Campi modificabili dall'utente (Quick-Edit)
    var editedTitle by remember(enriched) { mutableStateOf(enriched.title) }
    var editedArtist by remember(enriched) { mutableStateOf(enriched.artist) }
    var editedAlbum by remember(enriched) { mutableStateOf(enriched.album) }
    var editedAlbumArtist by remember(enriched) { mutableStateOf(enriched.albumArtist) }
    var editedGenre by remember(enriched) { mutableStateOf(enriched.genre.orEmpty()) }
    var editedYear by remember(enriched) { mutableStateOf(enriched.releaseYear?.toString().orEmpty()) }
    var editedTrackNumber by remember(enriched) { mutableStateOf(enriched.trackNumber?.toString().orEmpty()) }
    var editedDiscNumber by remember(enriched) { mutableStateOf(enriched.discNumber?.toString() ?: "1") }

    // Ricerca manuale & candidati alternativi
    var showAlternativeSearch by remember { mutableStateOf(false) }
    var customSearchTitle by remember(track) { mutableStateOf(track.title) }
    var customSearchArtist by remember(track) { mutableStateOf(track.artist) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(scrollState)
            .animateContentSize()
    ) {
        // Anteprima Copertine (Attuale vs Nuova HD)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Copertina Attuale
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.autotag_current),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (track.albumArtUri != null) {
                        AsyncImage(
                            model = track.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            // Nuova Copertina HD / Galleria
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (customCoverBytes != null && customCoverBytes !== enriched.coverBytes) stringResource(R.string.autotag_from_gallery) else stringResource(R.string.autotag_new_hd),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (customCoverBytes != null && customCoverBytes !== enriched.coverBytes) Icons.Default.AddPhotoAlternate else Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            trackCoverPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val coverModel: Any? = customCoverBytes ?: enriched.coverUrl
                    if (coverModel != null) {
                        AsyncImage(
                            model = coverModel,
                            contentDescription = stringResource(R.string.autotag_new_hd),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        trackCoverPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (customCoverBytes != null && customCoverBytes !== enriched.coverBytes) stringResource(R.string.edit) else stringResource(R.string.autotag_gallery),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Scheda Diff Metadati con Quick-Edit
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditingDetails) stringResource(R.string.autotag_quick_edit_details) else stringResource(R.string.autotag_proposed_details),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { isEditingDetails = !isEditingDetails }) {
                        Icon(
                            imageVector = if (isEditingDetails) Icons.Default.Done else Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEditingDetails) stringResource(R.string.done) else stringResource(R.string.edit))
                    }
                }

                if (isEditingDetails) {
                    AutotagEditTextField(
                        value = editedTitle,
                        onValueChange = { editedTitle = it },
                        label = stringResource(R.string.autotag_tag_title),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        singleLine = true
                    )
                    AutotagEditTextField(
                        value = editedArtist,
                        onValueChange = { editedArtist = it },
                        label = stringResource(R.string.autotag_tag_artist),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        singleLine = true
                    )
                    AutotagEditTextField(
                        value = editedAlbum,
                        onValueChange = { editedAlbum = it },
                        label = stringResource(R.string.autotag_tag_album),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        singleLine = true
                    )
                    AutotagEditTextField(
                        value = editedAlbumArtist,
                        onValueChange = { editedAlbumArtist = it },
                        label = stringResource(R.string.autotag_tag_album_artist),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        singleLine = true
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        AutotagEditTextField(
                            value = editedGenre,
                            onValueChange = { editedGenre = it },
                            label = stringResource(R.string.autotag_tag_genre),
                            modifier = Modifier.weight(1f).padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editedYear,
                            onValueChange = { editedYear = it },
                            label = { Text(stringResource(R.string.autotag_tag_year)) },
                            modifier = Modifier.weight(0.6f).padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                            singleLine = true
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editedTrackNumber,
                            onValueChange = { editedTrackNumber = it },
                            label = { Text(stringResource(R.string.autotag_tag_track_num)) },
                            modifier = Modifier.weight(1f).padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editedDiscNumber,
                            onValueChange = { editedDiscNumber = it },
                            label = { Text(stringResource(R.string.autotag_tag_disc_num)) },
                            modifier = Modifier.weight(1f).padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                            singleLine = true
                        )
                    }
                } else {
                    DiffRow(label = stringResource(R.string.autotag_tag_title), oldValue = track.title, newValue = editedTitle)
                    DiffRow(label = stringResource(R.string.autotag_tag_artist), oldValue = track.artist, newValue = editedArtist)
                    DiffRow(label = stringResource(R.string.autotag_tag_album), oldValue = track.album, newValue = editedAlbum)
                    DiffRow(
                        label = stringResource(R.string.autotag_tag_album_artist_short),
                        oldValue = track.albumArtist.ifBlank { stringResource(R.string.autotag_not_set) },
                        newValue = editedAlbumArtist,
                        highlightBadge = stringResource(R.string.autotag_uniform_album_badge)
                    )
                    if (editedYear.isNotBlank()) {
                        DiffRow(label = stringResource(R.string.autotag_tag_year), oldValue = "-", newValue = editedYear)
                    }
                    if (editedTrackNumber.isNotBlank()) {
                        val discLabel = stringResource(R.string.autotag_disc_format, track.discNumber)
                        val newDiscLabel = stringResource(R.string.autotag_disc_format, editedDiscNumber.toIntOrNull() ?: 1)
                        val oldPosStr = if (track.trackNumber > 0) "${track.trackNumber} ($discLabel)" else stringResource(R.string.autotag_not_set_f)
                        val newPosStr = "$editedTrackNumber ($newDiscLabel)"
                        DiffRow(
                            label = stringResource(R.string.autotag_tag_track_disc),
                            oldValue = oldPosStr,
                            newValue = newPosStr,
                            highlightBadge = if (track.trackNumber <= 0 || track.trackNumber.toString() != editedTrackNumber) stringResource(R.string.autotag_position_corrected_badge) else null
                        )
                    }
                    if (editedGenre.isNotBlank()) {
                        DiffRow(label = stringResource(R.string.autotag_tag_genre), oldValue = track.genre.ifBlank { "-" }, newValue = editedGenre)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sezione Ricerca Manuale e Risultati Alternativi
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAlternativeSearch = !showAlternativeSearch },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.autotag_search_alt_prompt),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (showAlternativeSearch) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showAlternativeSearch) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customSearchTitle,
                                onValueChange = { customSearchTitle = it },
                                label = { Text(stringResource(R.string.autotag_tag_title)) },
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = customSearchArtist,
                                onValueChange = { customSearchArtist = it },
                                label = { Text(stringResource(R.string.autotag_tag_artist)) },
                                modifier = Modifier.weight(1f).padding(start = 4.dp),
                                singleLine = true
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onCustomSearch(customSearchTitle, customSearchArtist) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(R.string.autotag_search_deezer_button))
                        }

                        if (candidates.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.autotag_alt_results_header),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            candidates.take(6).forEach { candidate ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { onSelectCandidate(candidate) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!candidate.coverXl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = candidate.coverXl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.matchParentSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = candidate.titleShort.ifBlank { candidate.title },
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${candidate.artistName} • ${candidate.albumTitle} (${formatDurationMs(candidate.duration * 1000L)})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = { onSelectCandidate(candidate) },
                                            modifier = Modifier.padding(start = 6.dp)
                                        ) {
                                            Text(stringResource(R.string.autotag_use_candidate))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Badge e stato Testi
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when {
                enriched.hasSyncedLyrics -> Color(0xFF1B5E20).copy(alpha = 0.15f)
                enriched.hasPlainLyrics -> Color(0xFFE65100).copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (enriched.hasPlainLyrics || enriched.hasSyncedLyrics) {
                        showLyricsPreview = !showLyricsPreview
                    }
                }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        enriched.hasSyncedLyrics -> Color(0xFF4CAF50)
                                        enriched.hasPlainLyrics -> Color(0xFFFF9800)
                                        else -> Color.Gray
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                enriched.hasSyncedLyrics -> stringResource(R.string.autotag_lyrics_synced_ready)
                                enriched.hasPlainLyrics -> stringResource(R.string.autotag_lyrics_plain_ready)
                                else -> stringResource(R.string.autotag_lyrics_none)
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (enriched.hasPlainLyrics || enriched.hasSyncedLyrics) {
                        Icon(
                            imageVector = if (showLyricsPreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Anteprima versi espandibile
                AnimatedVisibility(visible = showLyricsPreview) {
                    val previewText = (enriched.syncedLyrics ?: enriched.plainLyrics).orEmpty()
                        .lines()
                        .take(6)
                        .joinToString("\n")

                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Opzioni di salvataggio
        OptionSwitch(
            label = stringResource(R.string.autotag_opt_normalize_title),
            subtitle = stringResource(R.string.autotag_opt_normalize_title_sub),
            checked = options.normalizeAlbum,
            onCheckedChange = { options = options.copy(normalizeAlbum = it) }
        )

        OptionSwitch(
            label = stringResource(R.string.autotag_opt_hd_cover),
            subtitle = stringResource(R.string.autotag_opt_hd_cover_sub),
            checked = options.embedCover,
            onCheckedChange = { options = options.copy(embedCover = it) }
        )

        OptionSwitch(
            label = stringResource(R.string.autotag_opt_lyrics),
            subtitle = stringResource(R.string.autotag_opt_lyrics_sub),
            checked = options.includeLyrics,
            onCheckedChange = { options = options.copy(includeLyrics = it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pulsanti Azione
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = {
                    val finalEnriched = enriched.copy(
                        title = editedTitle.trim().ifBlank { enriched.title },
                        artist = editedArtist.trim().ifBlank { enriched.artist },
                        album = editedAlbum.trim().ifBlank { enriched.album },
                        albumArtist = editedAlbumArtist.trim().ifBlank { enriched.albumArtist },
                        genre = editedGenre.trim().takeIf { it.isNotBlank() },
                        releaseYear = editedYear.trim().toIntOrNull(),
                        trackNumber = editedTrackNumber.trim().toIntOrNull(),
                        discNumber = editedDiscNumber.trim().toIntOrNull() ?: 1,
                        coverBytes = customCoverBytes ?: enriched.coverBytes,
                        coverUrl = if (customCoverBytes != null && customCoverBytes !== enriched.coverBytes) null else enriched.coverUrl
                    )
                    onApply(finalEnriched, options)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.autotag_apply_and_save))
            }
        }
    }
}

@Composable
private fun ReviewAlbumContent(
    album: Album,
    tracksEnriched: Map<Long, EnrichedMetadata>,
    initialOptions: EnricherOptions,
    isSeparationMode: Boolean = false,
    onToggleSeparationMode: ((Boolean) -> Unit)? = null,
    onCancel: () -> Unit,
    onApply: (Map<Long, EnrichedMetadata>, EnricherOptions) -> Unit
) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(initialOptions) }
    val scrollState = rememberScrollState()

    var customAlbumCoverBytes by remember { mutableStateOf<ByteArray?>(null) }
    var currentTracksEnriched by remember(tracksEnriched) { mutableStateOf(tracksEnriched) }

    val firstEnriched = remember(tracksEnriched) { tracksEnriched.values.firstOrNull() }

    var isEditingAlbumDetails by remember { mutableStateOf(false) }
    var editedAlbumTitle by remember(firstEnriched?.album) { mutableStateOf(firstEnriched?.album ?: album.title) }
    var editedAlbumArtist by remember(firstEnriched?.albumArtist) { mutableStateOf(firstEnriched?.albumArtist ?: album.artist) }
    var editedAlbumYear by remember(firstEnriched?.releaseYear) { mutableStateOf(firstEnriched?.releaseYear?.toString().orEmpty()) }
    var editedAlbumGenre by remember(firstEnriched?.genre) { mutableStateOf(firstEnriched?.genre.orEmpty()) }

    fun updateTracksWithCurrentCustomizations(
        title: String = editedAlbumTitle,
        artist: String = editedAlbumArtist,
        yearStr: String = editedAlbumYear,
        genreStr: String = editedAlbumGenre,
        coverBytes: ByteArray? = customAlbumCoverBytes
    ) {
        val yr = yearStr.trim().toIntOrNull()
        val gn = genreStr.trim().takeIf { it.isNotBlank() }
        currentTracksEnriched = currentTracksEnriched.mapValues { (_, meta) ->
            var updated = if (!isSeparationMode) {
                meta.copy(
                    album = title.trim().ifBlank { meta.album },
                    albumArtist = artist.trim().ifBlank { meta.albumArtist },
                    releaseYear = yr ?: meta.releaseYear,
                    genre = gn ?: meta.genre
                )
            } else {
                meta
            }
            if (coverBytes != null) {
                updated = updated.copy(coverBytes = coverBytes, coverUrl = null)
            }
            updated
        }
    }

    LaunchedEffect(tracksEnriched) {
        currentTracksEnriched = tracksEnriched
        updateTracksWithCurrentCustomizations()
    }

    val albumCoverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bytes = loadResizedJpegBytesFromUri(context, uri)
            if (bytes != null) {
                customAlbumCoverBytes = bytes
                options = options.copy(embedCover = true)
                updateTracksWithCurrentCustomizations(coverBytes = bytes)
            }
        }
    }

    val targetAlbumCount = remember(currentTracksEnriched) {
        currentTracksEnriched.values.map {
            "${it.album.trim().lowercase(java.util.Locale.ROOT)}___${it.albumArtist.trim().lowercase(java.util.Locale.ROOT)}"
        }.distinct().size
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = if (isSeparationMode) stringResource(R.string.autotag_header_separate, album.tracks.size)
            else stringResource(R.string.autotag_header_unify, album.tracks.size),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Selettore rapido modalità: Separazione vs Unificazione
        if (album.tracks.size > 1 && onToggleSeparationMode != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSeparationMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleSeparationMode(true) }
                ) {
                    Text(
                        text = stringResource(R.string.autotag_mode_separate),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSeparationMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (!isSeparationMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleSeparationMode(false) }
                ) {
                    Text(
                        text = stringResource(R.string.autotag_mode_unify),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (!isSeparationMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Scheda Copertina Album (con selezione da galleria per tutti i brani)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            albumCoverPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val coverModel: Any? = customAlbumCoverBytes
                        ?: firstEnriched?.coverBytes
                        ?: firstEnriched?.coverUrl
                        ?: album.albumArtUri
                    if (coverModel != null) {
                        AsyncImage(
                            model = coverModel,
                            contentDescription = stringResource(R.string.autotag_cover_album),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (customAlbumCoverBytes != null) stringResource(R.string.autotag_cover_from_gallery) else stringResource(R.string.autotag_cover_album),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (customAlbumCoverBytes != null) stringResource(R.string.autotag_custom_album_cover_applied, album.tracks.size)
                        else stringResource(R.string.autotag_custom_album_cover_pick),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            albumCoverPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (customAlbumCoverBytes != null) stringResource(R.string.autotag_change_image) else stringResource(R.string.autotag_choose_gallery),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (isSeparationMode) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.autotag_separate_created_count, targetAlbumCount),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.autotag_separate_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEditingAlbumDetails) stringResource(R.string.autotag_edit_album_details_title) else stringResource(R.string.autotag_proposed_album_details_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = { isEditingAlbumDetails = !isEditingAlbumDetails }) {
                            Icon(
                                imageVector = if (isEditingAlbumDetails) Icons.Default.Done else Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEditingAlbumDetails) stringResource(R.string.done) else stringResource(R.string.edit))
                        }
                    }

                    if (isEditingAlbumDetails) {
                        AutotagEditTextField(
                            value = editedAlbumTitle,
                            onValueChange = {
                                editedAlbumTitle = it
                                updateTracksWithCurrentCustomizations(title = it)
                            },
                            label = stringResource(R.string.autotag_tag_album),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            singleLine = true
                        )
                        AutotagEditTextField(
                            value = editedAlbumArtist,
                            onValueChange = {
                                editedAlbumArtist = it
                                updateTracksWithCurrentCustomizations(artist = it)
                            },
                            label = stringResource(R.string.autotag_tag_album_artist_unique),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            singleLine = true
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            AutotagEditTextField(
                                value = editedAlbumGenre,
                                onValueChange = {
                                    editedAlbumGenre = it
                                    updateTracksWithCurrentCustomizations(genreStr = it)
                                },
                                label = stringResource(R.string.autotag_tag_genre),
                                modifier = Modifier.weight(1f).padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editedAlbumYear,
                                onValueChange = {
                                    editedAlbumYear = it
                                    updateTracksWithCurrentCustomizations(yearStr = it)
                                },
                                label = { Text(stringResource(R.string.autotag_tag_year)) },
                                modifier = Modifier.weight(0.6f).padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                                singleLine = true
                            )
                        }
                    } else {
                        DiffRow(label = stringResource(R.string.autotag_tag_album), oldValue = album.title, newValue = editedAlbumTitle)
                        DiffRow(
                            label = stringResource(R.string.autotag_tag_album_artist_unique_badge),
                            oldValue = album.artist,
                            newValue = editedAlbumArtist,
                            highlightBadge = stringResource(R.string.autotag_group_all_tracks_badge)
                        )
                        if (editedAlbumYear.isNotBlank()) {
                            DiffRow(label = stringResource(R.string.autotag_tag_year), oldValue = "-", newValue = editedAlbumYear)
                        }
                        if (editedAlbumGenre.isNotBlank()) {
                            DiffRow(label = stringResource(R.string.autotag_tag_genre), oldValue = "-", newValue = editedAlbumGenre)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Lista delle tracce con nuovo numero di traccia
        Text(
            text = if (isSeparationMode) stringResource(R.string.autotag_dest_tracks_separate) else stringResource(R.string.autotag_dest_tracks_unify),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        val sortedDisplayTracks = remember(album.tracks, currentTracksEnriched) {
            album.tracks.sortedWith(
                compareBy<AudioTrack> { currentTracksEnriched[it.id]?.discNumber ?: it.discNumber }
                    .thenBy { currentTracksEnriched[it.id]?.trackNumber ?: it.trackNumber }
                    .thenBy { it.title }
            )
        }

        sortedDisplayTracks.forEach { track ->
            val enriched = currentTracksEnriched[track.id]
            val disc = enriched?.discNumber ?: track.discNumber
            val pos = enriched?.trackNumber ?: track.trackNumber
            val posChanged = track.trackNumber <= 0 || track.trackNumber != enriched?.trackNumber
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val positionText = if (disc > 1) "D$disc.$pos." else "$pos."
                    Text(
                        text = positionText,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(if (disc > 1) 46.dp else 28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = enriched?.title ?: track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isSeparationMode) {
                            Text(
                                text = stringResource(R.string.autotag_track_album_disc_format, enriched?.album ?: track.album, enriched?.albumArtist ?: track.artist),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = enriched?.artist ?: track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (posChanged && pos > 0) {
                        Text(
                            text = stringResource(R.string.autotag_track_position, pos.toString()),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (enriched?.hasSyncedLyrics == true) {
                        Text(
                            text = "LRC",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF4CAF50),
                            modifier = Modifier
                                .background(Color(0xFF4CAF50).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isSeparationMode) {
            OptionSwitch(
                label = stringResource(R.string.autotag_opt_normalize_title),
                subtitle = stringResource(R.string.autotag_opt_normalize_album_sub),
                checked = options.normalizeAlbum,
                onCheckedChange = { options = options.copy(normalizeAlbum = it) }
            )
        }

        OptionSwitch(
            label = if (isSeparationMode) stringResource(R.string.autotag_opt_cover_separate) else stringResource(R.string.autotag_opt_cover_unify),
            subtitle = if (isSeparationMode) stringResource(R.string.autotag_opt_cover_separate_sub) else stringResource(R.string.autotag_opt_cover_unify_sub),
            checked = options.embedCover,
            onCheckedChange = { options = options.copy(embedCover = it) }
        )

        OptionSwitch(
            label = stringResource(R.string.autotag_opt_lyrics_album),
            subtitle = stringResource(R.string.autotag_opt_lyrics_album_sub),
            checked = options.includeLyrics,
            onCheckedChange = { options = options.copy(includeLyrics = it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = { onApply(currentTracksEnriched, options) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isSeparationMode) stringResource(R.string.autotag_action_separate, targetAlbumCount)
                    else stringResource(R.string.autotag_action_unify, album.tracks.size)
                )
            }
        }
    }
}

@Composable
private fun WritingContent(current: Int, total: Int, trackTitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(8.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.autotag_writing_files),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.autotag_writing_progress, trackTitle, current, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SuccessContent(message: String, onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(850L)
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.autotag_completed_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(stringResource(R.string.close))
        }
    }
}

@Composable
private fun ErrorContent(message: String, onDismiss: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.autotag_error_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
            Spacer(modifier = Modifier.width(10.dp))
            FilledTonalButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun DiffRow(
    label: String,
    oldValue: String,
    newValue: String,
    highlightBadge: String? = null
) {
    val isChanged = !oldValue.equals(newValue, ignoreCase = true)
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (highlightBadge != null && isChanged) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = highlightBadge,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFF2E7D32),
                    modifier = Modifier
                        .background(Color(0xFF81C784).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isChanged && oldValue.isNotBlank() && oldValue != "-") {
                Text(
                    text = oldValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = " ➔ ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = newValue,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isChanged) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isChanged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OptionSwitch(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun AutotagEditTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true
) {
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            val safeSelection = TextRange(textFieldValue.selection.end.coerceIn(0, value.length))
            textFieldValue = textFieldValue.copy(text = value, selection = safeSelection)
        }
    }

    val density = LocalDensity.current
    val pxPerChar = remember(density) { with(density) { 5.5.dp.toPx() } }
    val focusRequester = remember { FocusRequester() }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            onValueChange(newValue.text)
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        modifier = modifier
            .focusRequester(focusRequester)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    var isHorizontalDrag = false
                    var accumulatedDragX = 0f
                    var lastPositionX = down.position.x

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val deltaX = change.position.x - down.position.x
                        val deltaY = change.position.y - down.position.y

                        if (!isHorizontalDrag) {
                            if (abs(deltaX) > 8f && abs(deltaX) > abs(deltaY) * 1.2f) {
                                isHorizontalDrag = true
                                focusRequester.requestFocus()
                                accumulatedDragX = 0f
                                lastPositionX = change.position.x
                            }
                        }

                        if (isHorizontalDrag) {
                            change.consume()
                            val stepX = change.position.x - lastPositionX
                            lastPositionX = change.position.x
                            accumulatedDragX += stepX

                            val charOffset = (accumulatedDragX / pxPerChar).toInt()
                            if (charOffset != 0) {
                                val currentCursor = textFieldValue.selection.end
                                val newCursor = (currentCursor + charOffset).coerceIn(0, textFieldValue.text.length)
                                if (newCursor != currentCursor) {
                                    textFieldValue = textFieldValue.copy(selection = TextRange(newCursor))
                                }
                                accumulatedDragX -= charOffset * pxPerChar
                            }
                        }
                    }
                }
            }
    )
}

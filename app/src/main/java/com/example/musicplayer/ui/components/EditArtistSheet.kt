package com.example.musicplayer.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.musicplayer.R
import com.example.musicplayer.data.Album
import kotlinx.coroutines.launch

/**
 * Bottom sheet compatto e minimale per la personalizzazione dell'artista (foto da galleria o copertina album,
 * e modifica/ricerca mirata della biografia). Privo di scritte o istruzioni ridondanti.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditArtistSheet(
    artistName: String,
    currentPhotoUrl: String?,
    currentBio: String?,
    isCustomPhoto: Boolean,
    isCustomBio: Boolean,
    artistAlbums: List<Album>,
    onDismissRequest: () -> Unit,
    onSavePhoto: ((photoUri: Uri?, albumArtUri: Uri?, resetPhoto: Boolean) -> Unit),
    onSaveBio: ((newBio: String?, resetBio: Boolean) -> Unit),
    onSearchWikipedia: suspend (query: String) -> String?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAlbumArtUri by remember { mutableStateOf<Uri?>(null) }
    var resetPhotoFlag by remember { mutableStateOf(false) }

    var bioText by remember { mutableStateOf(currentBio.orEmpty()) }
    var resetBioFlag by remember { mutableStateOf(false) }

    var wikiQuery by remember { mutableStateOf("") }
    var isSearchingWiki by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedPhotoUri = uri
            selectedAlbumArtUri = null
            resetPhotoFlag = false
        }
    }

    val displayPhotoModel: Any? = when {
        selectedPhotoUri != null -> selectedPhotoUri
        selectedAlbumArtUri != null -> selectedAlbumArtUri
        resetPhotoFlag -> null
        !currentPhotoUrl.isNullOrBlank() -> currentPhotoUrl
        else -> null
    }

    // Elenco album con copertine disponibili
    val albumsWithArt = remember(artistAlbums) {
        artistAlbums.filter { it.albumArtUri != null }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Header: Chiudi, Titolo, Salva
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }

                Text(
                    text = stringResource(R.string.edit_artist_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = {
                        onSavePhoto(selectedPhotoUri, selectedAlbumArtUri, resetPhotoFlag)
                        onSaveBio(bioText, resetBioFlag)
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.save),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar Anteprima Circolare
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (displayPhotoModel != null) {
                        AsyncImage(
                            model = displayPhotoModel,
                            contentDescription = artistName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pulsante Galleria e opzione Ripristina Foto
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.edit_artist_gallery))
                    }

                    if (isCustomPhoto || selectedPhotoUri != null || selectedAlbumArtUri != null) {
                        TextButton(
                            onClick = {
                                selectedPhotoUri = null
                                selectedAlbumArtUri = null
                                resetPhotoFlag = true
                            }
                        ) {
                            Text(text = stringResource(R.string.edit_artist_reset_photo))
                        }
                    }
                }

                // Carosello orizzontale copertine album dell'artista (se disponibili)
                if (albumsWithArt.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.edit_artist_album_covers),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(albumsWithArt, key = { it.id }) { album ->
                            val isSelected = selectedAlbumArtUri == album.albumArtUri
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        } else Modifier
                                    )
                                    .clickable {
                                        selectedAlbumArtUri = album.albumArtUri
                                        selectedPhotoUri = null
                                        resetPhotoFlag = false
                                    }
                            ) {
                                AsyncImage(
                                    model = album.albumArtUri,
                                    contentDescription = album.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Campo Biografia
                OutlinedTextField(
                    value = bioText,
                    onValueChange = {
                        bioText = it
                        resetBioFlag = false
                    },
                    placeholder = { Text(stringResource(R.string.edit_artist_bio_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp, max = 220.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                if (isCustomBio || bioText != (currentBio.orEmpty())) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                bioText = ""
                                resetBioFlag = true
                            }
                        ) {
                            Text(text = stringResource(R.string.edit_artist_reset_bio))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Barra ricerca mirata su Wikipedia (per omonimie o nomi alternativi)
                OutlinedTextField(
                    value = wikiQuery,
                    onValueChange = { wikiQuery = it },
                    placeholder = { Text(stringResource(R.string.edit_artist_search_wiki_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (wikiQuery.isNotBlank() && !isSearchingWiki) {
                                keyboardController?.hide()
                                isSearchingWiki = true
                                coroutineScope.launch {
                                    val result = onSearchWikipedia(wikiQuery)
                                    if (!result.isNullOrBlank()) {
                                        bioText = result
                                        resetBioFlag = false
                                    }
                                    isSearchingWiki = false
                                }
                            }
                        }
                    ),
                    trailingIcon = {
                        if (isSearchingWiki) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    if (wikiQuery.isNotBlank() && !isSearchingWiki) {
                                        keyboardController?.hide()
                                        isSearchingWiki = true
                                        coroutineScope.launch {
                                            val result = onSearchWikipedia(wikiQuery)
                                            if (!result.isNullOrBlank()) {
                                                bioText = result
                                                resetBioFlag = false
                                            }
                                            isSearchingWiki = false
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

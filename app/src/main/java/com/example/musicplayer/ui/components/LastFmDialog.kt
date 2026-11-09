package com.example.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.example.musicplayer.data.lastfm.LastFmConfig
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Dialog per la gestione dell'account Last.fm e lo stato dello Scrobbling.
 */
@Composable
fun LastFmDialog(
    isLoggedIn: Boolean,
    username: String?,
    avatarPath: String? = null,
    isLoading: Boolean,
    errorMessage: String?,
    lastStatusMessage: String?,
    pendingCount: Int = 0,
    initialApiKey: String = "",
    initialSharedSecret: String = "",
    hasBuiltInKeys: Boolean = LastFmConfig.hasBuildConfigKeys,
    onLogin: (username: String, password: String, apiKey: String?, sharedSecret: String?) -> Unit,
    onSaveApiCredentials: (apiKey: String, sharedSecret: String) -> Unit = { _, _ -> },
    onLogout: () -> Unit,
    onSyncPending: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var inputUsername by remember { mutableStateOf("") }
    var inputPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var inputApiKey by remember(initialApiKey) { mutableStateOf(initialApiKey) }
    var inputSharedSecret by remember(initialSharedSecret) { mutableStateOf(initialSharedSecret) }
    var secretVisible by remember { mutableStateOf(false) }
    val isMissingApiKeys = !hasBuiltInKeys && (initialApiKey.isBlank() || initialSharedSecret.isBlank())
    var showCustomApiFields by remember(hasBuiltInKeys, initialApiKey) {
        mutableStateOf(!hasBuiltInKeys || initialApiKey.isNotBlank())
    }
    var showLoggedInApiFields by remember(isMissingApiKeys) {
        mutableStateOf(isMissingApiKeys)
    }
    val hasValidApiKeys = hasBuiltInKeys || (inputApiKey.isNotBlank() && inputSharedSecret.isNotBlank())

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD51007)), // Colore caratteristico Last.fm
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Last.fm Scrobbler",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoggedIn) {
                    // Stato Connesso
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LastFmAvatar(
                                avatarPath = avatarPath,
                                size = 48.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = username ?: "Utente Connesso",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isMissingApiKeys) Color(0xFFF57C00) else Color(0xFF4CAF50))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isMissingApiKeys) "In attesa di chiavi API" else "Scrobbling attivo",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (isMissingApiKeys) Color(0xFFE65100) else Color(0xFF2E7D32),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "I brani riprodotti vengono automaticamente registrati sul tuo profilo dopo il 50% della durata o 4 minuti.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Coda offline pendente
                    if (pendingCount > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Cache offline: $pendingCount in attesa",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Verranno inviati al ripristino della rete",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(onClick = onSyncPending) {
                                    Text("Sincronizza")
                                }
                            }
                        }
                    }

                    // Ultimo messaggio o attività
                    if (!lastStatusMessage.isNullOrBlank()) {
                        val isErrorOrWarning = lastStatusMessage.contains("mancanti", ignoreCase = true) ||
                            lastStatusMessage.contains("non riuscita", ignoreCase = true) ||
                            lastStatusMessage.contains("errore", ignoreCase = true) ||
                            lastStatusMessage.contains("assente", ignoreCase = true)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isErrorOrWarning) {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isErrorOrWarning) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isErrorOrWarning) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = lastStatusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isErrorOrWarning) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }

                    // Configurazione chiavi API direttamente da connesso (senza bisogno di disconnettersi)
                    if (!showLoggedInApiFields) {
                        TextButton(
                            onClick = { showLoggedInApiFields = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Configura API Key personalizzate")
                        }
                    } else {
                        if (isMissingApiKeys) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Chiavi API mancanti in questo APK: inserisci API Key e Shared Secret per riattivare lo scrobbling senza disconnetterti.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Chiavi API Last.fm personalizzate:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = inputApiKey,
                            onValueChange = { inputApiKey = it },
                            label = { Text("API Key Last.fm") },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = inputSharedSecret,
                            onValueChange = { inputSharedSecret = it },
                            label = { Text("Shared Secret Last.fm") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = { secretVisible = !secretVisible }) {
                                    Icon(
                                        imageVector = if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (secretVisible) "Nascondi secret" else "Mostra secret"
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (inputApiKey.isNotBlank() && inputSharedSecret.isNotBlank()) {
                                        onSaveApiCredentials(inputApiKey, inputSharedSecret)
                                        showLoggedInApiFields = false
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                onSaveApiCredentials(inputApiKey, inputSharedSecret)
                                showLoggedInApiFields = false
                            },
                            enabled = inputApiKey.isNotBlank() && inputSharedSecret.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Salva chiavi e sincronizza")
                        }
                    }
                } else {
                    // Stato Non Connesso - Modulo Login
                    Text(
                        text = "Accedi con il tuo account Last.fm per registrare automaticamente i tuoi ascolti e mostrare lo stato 'In riproduzione'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = inputUsername,
                        onValueChange = { inputUsername = it },
                        label = { Text("Nome utente Last.fm") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Nascondi password" else "Mostra password"
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (inputUsername.isNotBlank() && inputPassword.isNotBlank() && hasValidApiKeys) {
                                    onLogin(
                                        inputUsername,
                                        inputPassword,
                                        inputApiKey.takeIf { showCustomApiFields },
                                        inputSharedSecret.takeIf { showCustomApiFields }
                                    )
                                }
                            }
                        ),
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (hasBuiltInKeys && !showCustomApiFields) {
                        TextButton(
                            onClick = { showCustomApiFields = true },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Configura API Key personalizzate")
                        }
                    }

                    if (showCustomApiFields) {
                        Text(
                            text = "Chiavi API Last.fm (creabili gratis su last.fm/api/account/create):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = inputApiKey,
                            onValueChange = { inputApiKey = it },
                            label = { Text("API Key Last.fm") },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = inputSharedSecret,
                            onValueChange = { inputSharedSecret = it },
                            label = { Text("Shared Secret Last.fm") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = { secretVisible = !secretVisible }) {
                                    Icon(
                                        imageVector = if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (secretVisible) "Nascondi secret" else "Mostra secret"
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (inputUsername.isNotBlank() && inputPassword.isNotBlank() && hasValidApiKeys) {
                                        onLogin(
                                            inputUsername,
                                            inputPassword,
                                            inputApiKey,
                                            inputSharedSecret
                                        )
                                    }
                                }
                            ),
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isLoggedIn) {
                Button(onClick = onDismiss) {
                    Text("Chiudi")
                }
            } else {
                Button(
                    onClick = {
                        onLogin(
                            inputUsername,
                            inputPassword,
                            inputApiKey.takeIf { showCustomApiFields },
                            inputSharedSecret.takeIf { showCustomApiFields }
                        )
                    },
                    enabled = !isLoading && inputUsername.isNotBlank() && inputPassword.isNotBlank() && hasValidApiKeys
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Accedi")
                    }
                }
            }
        },
        dismissButton = {
            if (isLoggedIn) {
                OutlinedButton(
                    onClick = onLogout,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnetti")
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isLoading
                ) {
                    Text("Annulla")
                }
            }
        }
    )
}

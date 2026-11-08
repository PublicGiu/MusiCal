package com.example.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicplayer.playback.SleepTimerState
import kotlin.math.roundToInt

@Composable
fun SleepTimerDialog(
    sleepTimerState: SleepTimerState,
    onSetMinutes: (Int) -> Unit,
    onSetEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismissRequest: () -> Unit,
    onDismiss: () -> Unit = onDismissRequest
) {
    var customMinutes by remember { mutableFloatStateOf(20f) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Timer di spegnimento",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Se il timer è attualmente attivo: banner informativo e pulsante di arresto
                AnimatedVisibility(visible = sleepTimerState is SleepTimerState.Active) {
                    val active = sleepTimerState as? SleepTimerState.Active
                    if (active != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = if (active.isEndOfTrack) "ATTIVO: ALLA FINE DEL BRANO" else "TIMER IN CORSO",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (active.isEndOfTrack) {
                                        "La musica si fermerà dolcemente al termine della canzone attuale."
                                    } else {
                                        val totalSeconds = (active.remainingMillis / 1000L).coerceAtLeast(0L)
                                        val min = totalSeconds / 60
                                        val sec = totalSeconds % 60
                                        "Spegnimento tra %02d:%02d".format(min, sec)
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onCancelTimer,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Disattiva timer")
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "OPZIONI RAPIDE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            onSetMinutes(15)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("15 min")
                    }
                    FilledTonalButton(
                        onClick = {
                            onSetMinutes(30)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("30 min")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            onSetMinutes(45)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("45 min")
                    }
                    FilledTonalButton(
                        onClick = {
                            onSetMinutes(60)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("60 min")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        onSetEndOfTrack()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Al termine del brano corrente")
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "DURATA PERSONALIZZATA (${customMinutes.roundToInt()} min)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Slider(
                    value = customMinutes,
                    onValueChange = { customMinutes = it },
                    valueRange = 5f..120f,
                    steps = 22
                )

                Button(
                    onClick = {
                        onSetMinutes(customMinutes.roundToInt())
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Avvia timer (${customMinutes.roundToInt()} min)")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "La musica effettuerà un fade-out graduale prima di fermarsi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

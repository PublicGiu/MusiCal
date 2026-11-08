package com.example.musicplayer.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.playback.EqualizerManager

@Composable
fun EqualizerDialog(
    onDismissRequest: () -> Unit,
    onDismiss: () -> Unit = onDismissRequest
) {
    val eqState by EqualizerManager.state.collectAsStateWithLifecycle()
    val isEnabled = eqState.isEnabled && eqState.isAvailable

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Equalizzatore",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Switch(
                    checked = eqState.isEnabled,
                    onCheckedChange = { EqualizerManager.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (!eqState.isAvailable) {
                    Text(
                        text = "L'equalizzatore audio hardware non è supportato su questo dispositivo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    // Presets
                    if (eqState.presets.isNotEmpty()) {
                        Text(
                            text = "PRESET AUDIO",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            eqState.presets.forEachIndexed { index, presetName ->
                                val isSelected = eqState.currentPresetIndex == index
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { EqualizerManager.usePreset(index) },
                                    label = { Text(presetName) },
                                    enabled = isEnabled,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Bande di frequenza
                    Text(
                        text = "BANDE DI FREQUENZA",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    eqState.bands.forEach { band ->
                        val freqFormatted = if (band.centerFreqHz >= 1000) {
                            val khz = band.centerFreqHz / 1000f
                            if (khz % 1.0f == 0.0f) "${khz.toInt()} kHz" else "%.1f kHz".format(khz)
                        } else {
                            "${band.centerFreqHz} Hz"
                        }
                        val db = band.levelMb / 100
                        val dbFormatted = if (db > 0) "+$db dB" else "$db dB"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = freqFormatted,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(64.dp)
                            )

                            Slider(
                                value = band.levelMb.toFloat(),
                                onValueChange = { newLevel ->
                                    EqualizerManager.setBandLevel(band.bandIndex, newLevel.toInt())
                                },
                                valueRange = eqState.minLevelMb.toFloat()..eqState.maxLevelMb.toFloat(),
                                enabled = isEnabled,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Text(
                                text = dbFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(52.dp)
                            )
                        }
                    }

                    // Effetto Bass Boost
                    if (eqState.isBassBoostSupported) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "BASS BOOST",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

                        val percent = (eqState.bassBoostStrength / 10).coerceIn(0, 100)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = eqState.bassBoostStrength.toFloat(),
                                onValueChange = { EqualizerManager.setBassBoostStrength(it.toInt()) },
                                valueRange = 0f..1000f,
                                enabled = isEnabled,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(42.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    // Effetto Virtualizer
                    if (eqState.isVirtualizerSupported) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AUDIO SURROUND (VIRTUALIZZATORE)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

                        val percent = (eqState.virtualizerStrength / 10).coerceIn(0, 100)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = eqState.virtualizerStrength.toFloat(),
                                onValueChange = { EqualizerManager.setVirtualizerStrength(it.toInt()) },
                                valueRange = 0f..1000f,
                                enabled = isEnabled,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(42.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        },
        dismissButton = {
            if (eqState.isAvailable) {
                TextButton(
                    onClick = { EqualizerManager.resetToFlat() },
                    enabled = isEnabled
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Reimposta")
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

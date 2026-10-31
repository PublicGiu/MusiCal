package com.example.musicplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Crea una [NestedScrollConnection] riutilizzabile che rileva lo scorrimento
 * con soglia di accumulo (isteresi) per contrarre ed espandere header comprimibili.
 *
 * Usa [rememberUpdatedState] per i callback di contrazione/espansione così da non
 * ricreare la connessione (né azzerare `scrollAccumulator`) in caso di ricomposizioni.
 *
 * @param collapseThreshold Soglia di scroll verso il basso (default -20f).
 * @param expandThreshold Soglia di scroll verso l'alto (default 35f).
 * @param onCollapse Callback invocato quando l'utente scorre verso il basso oltre la soglia.
 * @param onExpand Callback invocato quando l'utente scorre verso l'alto oltre la soglia.
 */
@Composable
fun rememberCollapsingScrollConnection(
    collapseThreshold: Float = -20f,
    expandThreshold: Float = 35f,
    onCollapse: () -> Unit,
    onExpand: () -> Unit
): NestedScrollConnection {
    val currentOnCollapse by rememberUpdatedState(onCollapse)
    val currentOnExpand by rememberUpdatedState(onExpand)

    return remember(collapseThreshold, expandThreshold) {
        object : NestedScrollConnection {
            private var scrollAccumulator = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                // Considera lo scroll verticale se Y è predominante
                if (kotlin.math.abs(delta) >= kotlin.math.abs(available.x)) {
                    if (delta < 0f) {
                        if (scrollAccumulator > 0f) scrollAccumulator = 0f
                        scrollAccumulator += delta
                        if (scrollAccumulator < collapseThreshold) {
                            currentOnCollapse()
                            scrollAccumulator = 0f
                        }
                    } else if (delta > 0f) {
                        if (scrollAccumulator < 0f) scrollAccumulator = 0f
                        scrollAccumulator += delta
                        if (scrollAccumulator > expandThreshold) {
                            currentOnExpand()
                            scrollAccumulator = 0f
                        }
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                // Cattura il trascinamento verso il basso quando la lista ha già raggiunto la cima (scroll non consumato)
                if (delta > 0f && kotlin.math.abs(delta) >= kotlin.math.abs(available.x)) {
                    if (scrollAccumulator < 0f) scrollAccumulator = 0f
                    scrollAccumulator += delta
                    if (scrollAccumulator > expandThreshold) {
                        currentOnExpand()
                        scrollAccumulator = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }
}

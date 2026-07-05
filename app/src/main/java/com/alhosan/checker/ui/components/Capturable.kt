package com.alhosan.checker.ui.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Capture composable content into a [GraphicsLayer] so it can be exported
 * as a Bitmap later (replaces html2canvas from the HTML reference).
 *
 * Usage:
 *   val layer = remember { GraphicsLayer() }
 *   Box(modifier = Modifier.captureToLayer(layer)) { ... }
 *   // later:
 *   val bitmap = layer.toImageBitmap().asAndroidBitmap()
 *
 * Implementation: records the content draw into the layer, then draws the
 * layer onto the actual canvas so the user still sees the content on screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.captureToLayer(layer: GraphicsLayer): Modifier = this.drawWithCache {
    onDrawWithContent {
        val w = size.width.roundToInt().coerceAtLeast(1)
        val h = size.height.roundToInt().coerceAtLeast(1)
        layer.record(IntSize(w, h)) {
            drawContent()
        }
        drawLayer(layer)
    }
}

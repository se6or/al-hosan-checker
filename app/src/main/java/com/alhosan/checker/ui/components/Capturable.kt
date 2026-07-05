package com.alhosan.checker.ui.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize

/**
 * Capture composable content into a [GraphicsLayer] so it can be exported
 * as a Bitmap later (replaces html2canvas from the HTML reference).
 *
 * Compose UI 1.7+ API:
 *  - Use `rememberGraphicsLayer()` to obtain an instance (constructor is internal)
 *  - `record(size) { ... }` captures the content draw
 *  - `toImageBitmap()` (suspend) converts it to a Bitmap
 *
 * Inside `record { ... }`, the receiver is `DrawScope`, not `ContentDrawScope`,
 * so we must qualify `drawContent()` with the outer `onDrawWithContent` label.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.captureToLayer(layer: GraphicsLayer): Modifier = this.drawWithCache {
    onDrawWithContent {
        // Capture the content draw into the layer
        layer.record(size) {
            this@onDrawWithContent.drawContent()
        }
        // Then draw the layer onto the actual canvas so the user still sees it
        drawLayer(layer)
    }
}

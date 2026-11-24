package com.wdtt.client.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Export and import: an arrow out and an arrow in over a tray, drawn like the
 * «save to file» icon (the same tray bracket), so the two read as one family.
 */
val TransferIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Transfer",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            // Tray.
            "M19 12v7H5v-7H3v7c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2v-7h-2z" +
                // Out: up from the tray.
                "M9 3L5.8 6.6H8V15h2V6.6h2.2L9 3z" +
                // In: down into the tray.
                "M15 16.5l3.2-3.6H16V4h-2v8.9h-2.2L15 16.5z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

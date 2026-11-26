package com.wdtt.client.ui.components.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wdtt.client.ui.components.surface.ArdttFloatingShell
import com.wdtt.client.ui.components.surface.GlassBackdrop
import com.wdtt.client.ui.components.surface.LiquidGlass
import com.wdtt.client.ui.components.surface.LiquidGlassPanel
import com.wdtt.client.ui.components.surface.glassBackdropSource
import com.wdtt.client.ui.theme.ArdttShapes
import com.wdtt.client.ui.theme.isDarkSurface

/**
 * Tab page whose content scrolls under a floating header. While nothing is under it the
 * header is just the title; once content passes beneath, a plate fades in behind the
 * title: liquid glass refracting the content when glass is on, the translucent shell otherwise.
 *
 * [content] gets the top padding to start below the header; [scrolled] says whether
 * content is currently under the header.
 */
@Composable
fun GlassHeaderPage(
    title: String,
    scrolled: Boolean,
    backdrop: GlassBackdrop?,
    actions: (@Composable RowScope.() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable (top: Dp) -> Unit,
) {
    var headerPx by remember { mutableStateOf(0) }
    val top = with(LocalDensity.current) { headerPx.toDp() }
    val plate by animateFloatAsState(if (scrolled) 1f else 0f, tween(260), label = "plate")
    // Scrolled: the content fades out under the status bar, so the lines passing up there do
    // not show above the header. The glass plate still refracts it whole.
    val statusTop = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    val fadeEnd = with(LocalDensity.current) { 14.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (plate > 0.01f) Modifier.fadeTop(statusTop, statusTop + fadeEnd, plate) else Modifier)
                .glassBackdropSource(backdrop, fill = MaterialTheme.colorScheme.background),
        ) { content(top) }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // Whole height, status bar included: the content starts below it.
                .onSizeChanged { headerPx = it.height }
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (plate > 0.01f) HeaderPlate(backdrop, plate)
            ArdttPageHeader(title = title, actions = actions)
        }
        overlay()
    }
}

@Composable
private fun BoxScope.HeaderPlate(backdrop: GlassBackdrop?, alpha: Float) {
    val colors = MaterialTheme.colorScheme
    val dark = isDarkSurface()
    // Slides down a little as it appears.
    val m = Modifier
        .matchParentSize()
        .graphicsLayer {
            this.alpha = alpha
            translationY = (1f - alpha) * -8.dp.toPx()
        }
    if (backdrop != null && LiquidGlass.supported) {
        LiquidGlassPanel(
            backdrop = backdrop,
            cornerRadius = 24.dp,
            // Thicker veil than the tab bar: the title sits right over scrolling text.
            tint = colors.surface.copy(alpha = (backdrop.veil(dark) + 0.25f).coerceAtMost(0.92f)),
            dark = dark,
            blur = backdrop.frost(),
            modifier = m,
        ) {}
    } else {
        // Without glass the plate is nearly opaque: the translucent shell let the list
        // text show through the title.
        Box(
            m.clip(fitShape(com.wdtt.client.ui.theme.ArdttRadius.Section, inset = 10.dp))
                .background(colors.surface.copy(alpha = 0.96f))
                .border(ArdttFloatingShell.shellBorder(), fitShape(com.wdtt.client.ui.theme.ArdttRadius.Section, inset = 10.dp)),
        )
    }
}

/** Fades what is drawn out towards the top: hidden by [strength] above [from], whole below [to]. */
private fun Modifier.fadeTop(from: Float, to: Float, strength: Float): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (to <= 0f) return@drawWithContent
        val faint = androidx.compose.ui.graphics.Color.Black.copy(alpha = 1f - strength)
        val whole = androidx.compose.ui.graphics.Color.Black
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to faint,
                (from / to).coerceIn(0f, 1f) to faint,
                1f to whole,
                startY = 0f,
                endY = to,
            ),
            size = androidx.compose.ui.geometry.Size(size.width, to),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }

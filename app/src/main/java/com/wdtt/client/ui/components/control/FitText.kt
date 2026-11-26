package com.wdtt.client.ui.components.control

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

/**
 * Labels that keep one size: in a row of chips, when one label has to shrink to fit, the
 * others shrink with it, so the row does not show the same kind of label in two sizes.
 */
class FitGroup {
    internal val needs = mutableStateMapOf<Any, Float>()
    internal val settled = mutableStateMapOf<Any, Boolean>()
    internal val scale: Float get() = needs.values.minOrNull() ?: 1f
    internal val allSettled: Boolean get() = settled.values.all { it }
}

internal val LocalFitGroup = compositionLocalOf<FitGroup?> { null }

/** The [FitText] labels inside [content] keep one size. */
@Composable
fun FitTogether(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFitGroup provides remember { FitGroup() }, content = content)
}

/**
 * Text for controls of fixed width: with a large system font a label that does not fit
 * is scaled down a little (to [minScale] at most) instead of being cut or broken mid-word.
 * Inside [FitTogether] the labels shrink together.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = 1,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Center,
    minScale: Float = 0.62f,
) {
    val base: TextUnit = style.fontSize.takeIf { it.isSpecified } ?: LocalTextStyle.current.fontSize
    // How far this label itself has to shrink; in a group the smallest of them is shown.
    var scale by remember(text, base) { mutableFloatStateOf(1f) }
    var ready by remember(text, base) { mutableStateOf(false) }
    val group = LocalFitGroup.current
    val key = remember { Any() }
    if (group != null) {
        SideEffect {
            group.needs[key] = scale
            group.settled[key] = ready
        }
        DisposableEffect(group) {
            onDispose {
                group.needs.remove(key)
                group.settled.remove(key)
            }
        }
    }
    val shown = if (group != null) minOf(scale, group.scale) else scale
    Text(
        text,
        modifier = modifier.drawWithContent { if (group?.allSettled ?: ready) drawContent() },
        style = style.copy(fontSize = base * shown),
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        onTextLayout = { layout ->
            if (doesNotFit(layout, text) && shown > minScale) {
                scale = shown - 0.05f
                ready = false
            } else {
                ready = true
            }
        },
    )
}

private fun doesNotFit(layout: TextLayoutResult, text: String): Boolean {
    if (layout.hasVisualOverflow) return true
    // A word broken across lines also counts as not fitting.
    for (line in 0 until layout.lineCount - 1) {
        val end = layout.getLineEnd(line)
        if (end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace() && text[end - 1] != '-') return true
    }
    return false
}

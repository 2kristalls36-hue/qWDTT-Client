package com.wdtt.client.ui.components.surface

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Halo around glyphs (icons and text) drawn over clear glass: a soft border of [color]
 * that follows the outline of every glyph, like labels on a map. The glyphs are drawn
 * as they are on top of it.
 *
 * Built from the glyphs themselves: their silhouette in [color], blurred by [radiusPx]
 * and made denser, so the border is solid near the glyph and fades out further away.
 */
@RequiresApi(Build.VERSION_CODES.S)
fun glyphHalo(color: Color, radiusPx: Float, strength: Float): androidx.compose.ui.graphics.RenderEffect {
    fun matrix(vararg v: Float) = ColorMatrixColorFilter(ColorMatrix(v))
    // Silhouette: every covered pixel in the halo colour, antialiased edges made solid.
    val silhouette = RenderEffect.createColorFilterEffect(
        matrix(
            0f, 0f, 0f, 0f, color.red * 255f,
            0f, 0f, 0f, 0f, color.green * 255f,
            0f, 0f, 0f, 0f, color.blue * 255f,
            0f, 0f, 0f, 3f, 0f,
        ),
    )
    val spread = RenderEffect.createBlurEffect(radiusPx, radiusPx, silhouette, Shader.TileMode.DECAL)
    // Denser: solid close to the glyph, soft at the outer edge.
    val dense = RenderEffect.createColorFilterEffect(
        matrix(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 6f, 0f,
        ),
        spread,
    )
    val halo = RenderEffect.createColorFilterEffect(
        matrix(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, strength.coerceIn(0f, 1f), 0f,
        ),
        dense,
    )
    // The glyphs themselves (an empty offset is the source as it is) over the halo.
    val glyphs = RenderEffect.createOffsetEffect(0f, 0f)
    return RenderEffect.createBlendModeEffect(halo, glyphs, android.graphics.BlendMode.SRC_OVER).asComposeRenderEffect()
}

package com.wdtt.client.ui.components.surface

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Liquid glass (beta). The screen content is recorded into a [GraphicsLayer] by
 * [glassBackdropSource]; a [LiquidGlass] panel draws the part of it that lies behind
 * itself through an AGSL lens: content bends at the rim like a thick glass edge,
 * colors split slightly there (a rainbow fringe that slowly travels around the edge),
 * and a thin specular rim catches a light that drifts with time.
 *
 * Android 13+ only (RuntimeShader). Callers check [LiquidGlass.supported] and keep
 * the plain translucent shell otherwise.
 */
object LiquidGlass {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/** Where the backdrop layer was recorded, in root coordinates. */
class GlassBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    /** User setting: 0 — frosted, 1 — almost clear. */
    var transparency by mutableStateOf(0.5f)

    /** Veil strength over the glass; 0.5 gives the tuned default (0.34 light, 0.55 dark). */
    fun veil(dark: Boolean): Float = if (dark) (1.1f * (1f - transparency)).coerceIn(0.04f, 0.95f)
        else (0.68f * (1f - transparency)).coerceIn(0.03f, 0.9f)
}

val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/**
 * Records everything this node draws into [backdrop] and then draws it as usual.
 * [fill] is painted under the recording only (not on screen): a nested source that
 * does not contain the page background still gives the glass something opaque to bend.
 */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop?, fill: Color? = null): Modifier =
    if (backdrop == null) this else this
        .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            backdrop.layer.record {
                if (fill != null) drawRect(fill)
                this@drawWithContent.drawContent()
            }
            if (fill != null) drawContent() else drawLayer(backdrop.layer)
        }

/**
 * Glass panel with rounded corners. [content] is drawn on top of the glass.
 * [tint] is a thin veil for legibility (alpha is its strength).
 */
@Composable
fun LiquidGlassPanel(
    backdrop: GlassBackdrop,
    cornerRadius: Dp,
    tint: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val t = rememberInfiniteTransition(label = "glass")
    val time by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "glassTime")
    val shader = remember { if (LiquidGlass.supported) GlassShader.create() else null }

    Box(modifier.onGloballyPositioned { position = it.positionInRoot() }) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val s = shader ?: return@graphicsLayer
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        renderEffect = GlassShader.effect(
                            s, size.width, size.height, radiusPx, density.density, time, tint, dark,
                        ).asComposeRenderEffect()
                    }
                }
                .drawBehind {
                    val offset = position - backdrop.origin
                    translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
                },
        )
        content()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object GlassShader {
    // Rounded-rect lens. All lengths in px; `dp` converts design values.
    private const val SRC = """
        uniform shader content;
        uniform float2 size;
        uniform float radius;
        uniform float dp;
        uniform float time;
        uniform half4 tint;
        uniform float dark;

        float sdRoundRect(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half3 sat(half3 c, float k) {
            float l = dot(c, half3(0.2126, 0.7152, 0.0722));
            return mix(half3(l), c, k);
        }

        half4 main(float2 coord) {
            float2 half_size = size * 0.5;
            float2 p = coord - half_size;
            float d = sdRoundRect(p, half_size, radius);
            float a = clamp(0.5 - d, 0.0, 1.0);
            if (a <= 0.0) return half4(0.0);
            float inside = -d;

            float e = 1.0;
            float2 n = float2(
                sdRoundRect(p + float2(e, 0.0), half_size, radius) - sdRoundRect(p - float2(e, 0.0), half_size, radius),
                sdRoundRect(p + float2(0.0, e), half_size, radius) - sdRoundRect(p - float2(0.0, e), half_size, radius));
            n = n / max(length(n), 0.0001);

            // Convex bezel: strongest bend right at the rim, none past the bezel width.
            float bezel = 16.0 * dp;
            float x = clamp(1.0 - inside / bezel, 0.0, 1.0);
            float bend = 1.0 - sqrt(1.0 - x * x);
            float2 off = -n * bend * 13.0 * dp;

            // Chromatic split grows toward the edge.
            float disp = 0.22 * bend;
            half4 cg = content.eval(coord + off);
            half r = content.eval(coord + off * (1.0 + disp)).r;
            half b = content.eval(coord + off * (1.0 - disp)).b;
            half3 col = half3(r, cg.g, b);
            col = sat(col, 1.18);

            // Veil for legibility.
            col = mix(col, tint.rgb, tint.a);

            // Light drifts around slowly; specular is brightest where the rim faces it.
            float ang = 6.2831853 * time;
            float2 light = float2(cos(ang - 2.3), sin(ang - 2.3));
            float spec = pow(max(dot(n, light), 0.0), 2.0);
            float spec2 = pow(max(dot(n, -light), 0.0), 3.0) * 0.45;

            // Iridescent fringe inside the bezel, hue travelling along the rim.
            float edgeAngle = atan(n.y, n.x) / 6.2831853;
            half3 iri = 0.5 + 0.5 * cos(6.2831853 * (edgeAngle * 1.5 + time) + half3(0.0, 2.094, 4.188));
            float fringe = smoothstep(bezel * 0.75, 0.0, inside) * (dark > 0.5 ? 0.24 : 0.30);
            col += iri * fringe * (0.45 + 0.55 * (spec + spec2));

            // Thin bright rim, stronger on the lit side.
            float rimLine = smoothstep(1.6 * dp, 0.0, inside);
            float rimGlow = smoothstep(5.0 * dp, 0.0, inside) * 0.35;
            float lit = 0.28 + 0.72 * (spec + spec2);
            col += half3(1.0) * (rimLine * 0.55 + rimGlow * 0.25) * lit * (dark > 0.5 ? 0.85 : 1.0);

            return half4(clamp(col, 0.0, 1.0) * a, a);
        }
    """

    fun create() = RuntimeShader(SRC)

    fun effect(
        shader: RuntimeShader,
        w: Float, h: Float, radius: Float, dp: Float, time: Float, tint: Color, dark: Boolean,
    ): RenderEffect {
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("radius", radius.coerceAtMost(minOf(w, h) / 2f))
        shader.setFloatUniform("dp", dp)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
        shader.setFloatUniform("dark", if (dark) 1f else 0f)
        val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val blur = RenderEffect.createBlurEffect(4f * dp, 4f * dp, Shader.TileMode.CLAMP)
        return RenderEffect.createChainEffect(lens, blur)
    }
}

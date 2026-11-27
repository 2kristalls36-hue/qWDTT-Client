package com.wdtt.client.ui.components.surface

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid glass (beta). The screen content is recorded into a [GraphicsLayer] by
 * [glassBackdropSource]; a [LiquidGlass] panel draws the part of it that lies behind
 * itself through an AGSL lens: content bends at the rim like a thick glass edge,
 * colors split slightly there (a rainbow fringe that slowly travels around the edge),
 * and a thin specular rim catches a light that drifts with time.
 *
 * The lens needs Android 13 (RuntimeShader). Before it (Android 8 to 12, and HarmonyOS,
 * which runs apps as Android 10 to 12) the panel is a simpler glass drawn without it: what
 * is behind, magnified a little and frosted where the system can blur (Android 12), under
 * the veil, the slab's light and shade, the coloured fringe and the rim with its drifting
 * light. The edge does not bend.
 */
object LiquidGlass {
    /** Glass at all: the lens or the simpler glass. */
    val supported: Boolean get() = true
    /** The full lens: the edge bends what is behind and splits its colours. */
    val refracts: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    /** What is behind the glass can be frosted (blurred). */
    val frosts: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/** Where the backdrop layer was recorded, in root coordinates. */
class GlassBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    /** User setting: 0 — frosted, 1 — clear. */
    var transparency by mutableStateOf(0.5f)
    /** Colour of the rim fringe (the power level); null — the rainbow of the maximum level. */
    var rim by mutableStateOf<Color?>(null)

    /** Veil strength over the glass; 0.5 gives the tuned default (0.34 light, 0.55 dark), 1 — none. */
    fun veil(dark: Boolean): Float = if (dark) (1.1f * (1f - transparency)).coerceIn(0f, 0.95f)
        else (0.68f * (1f - transparency)).coerceIn(0f, 0.9f)

    /** Frost (blur) of what is behind; gone at full transparency. */
    fun frost(): Dp = (7f * (1f - transparency)).coerceIn(0f, 7f).dp

    /**
     * How much labels on the glass need help to stay legible: 0 up to the default
     * transparency (the veil and the frost are enough), 1 at fully clear glass.
     */
    fun legibility(): Float = ((transparency - 0.5f) / 0.5f).coerceIn(0f, 1f)
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
    /** Width of the refracting rim. */
    bezel: Dp = 16.dp,
    /** How far the rim pulls the image inward; with a smooth rim keep it under bezel / 2. */
    depth: Dp = 7.dp,
    /** Circular rim profile that wraps the image (small lenses); false — smooth, no folding. */
    steepRim: Boolean = false,
    /** Colour split at the rim, 1 — the old strong value. */
    dispersion: Float = 0.2f,
    /** Magnification of the middle, 0 — none. */
    zoom: Float = 0f,
    blur: Dp = 4.dp,
    /** Painted under the backdrop, for parts of the panel that reach past the recorded area. */
    underlay: Color? = null,
    /** Strength of the rainbow edge and color split, 1 — panel default. */
    fringe: Float = 1f,
    /**
     * Share of the rim bend across the top and bottom edges, 1 — the same as at the sides.
     * Lower keeps a wide lens from pulling what lies under its middle up to those edges.
     */
    verticalBend: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    // Read only while drawing the layer: a step redraws the glass without recomposing it.
    val time by rememberGlassTime()
    val shader = remember { if (LiquidGlass.refracts) GlassShader.create() else null }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)

    Box(modifier.onGloballyPositioned { position = it.positionInRoot() }) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val s = shader
                    if (s == null) {
                        // The simpler glass: frosted where the system can, kept to its shape.
                        clip = true
                        this.shape = shape
                        val blurPx = with(density) { blur.toPx() }
                        if (LiquidGlass.frosts && blurPx >= 0.5f) {
                            renderEffect = androidx.compose.ui.graphics.BlurEffect(blurPx, blurPx, androidx.compose.ui.graphics.TileMode.Clamp)
                        }
                        return@graphicsLayer
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        renderEffect = GlassShader.effect(
                            s, size.width, size.height, radiusPx, density.density, time, tint, dark,
                            bezelPx = with(density) { bezel.toPx() },
                            depthPx = with(density) { depth.toPx() },
                            zoom = zoom,
                            blurPx = with(density) { blur.toPx() },
                            fringeK = fringe,
                            rim = backdrop.rim,
                            steep = steepRim,
                            dispersion = dispersion,
                            verticalBend = verticalBend,
                        ).asComposeRenderEffect()
                    }
                }
                .drawBehind {
                    if (underlay != null) drawRect(underlay)
                    val offset = position - backdrop.origin
                    if (shader == null) {
                        // Without the lens a glass still magnifies a little (the droplet as
                        // much as it asks for).
                        val k = 1f + zoom + 0.03f
                        scale(k, k, pivot = center) {
                            translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
                        }
                    } else {
                        translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
                    }
                },
        )
        if (shader == null) {
            // Over the frost, sharp: the veil, the slab's light and shade, the fringe, the rim.
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                simpleGlass(
                    radius = with(density) { cornerRadius.toPx() },
                    dp = density.density,
                    time = time,
                    tint = tint,
                    dark = dark,
                    bezel = with(density) { bezel.toPx() },
                    fringeK = fringe,
                    rim = backdrop.rim,
                )
            }
        }
        content()
    }
}

/**
 * The simpler glass drawn over what is behind it, where there is no lens: the veil (denser
 * where nothing is frosted, so what is on the glass stays legible), the slab lit along its
 * upper edge and shaded along the lower one, the coloured fringe inside the rim (the
 * rainbow travelling round, or the colour of the power level) and the thin bright rim,
 * brightest where the drifting light falls on it.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.simpleGlass(
    radius: Float, dp: Float, time: Float, tint: Color, dark: Boolean, bezel: Float, fringeK: Float, rim: Color?,
) {
    val r = radius.coerceAtMost(minOf(size.width, size.height) / 2f)
    fun outline(inset: Float) = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                inset, inset, size.width - inset, size.height - inset,
                androidx.compose.ui.geometry.CornerRadius((r - inset).coerceAtLeast(0f)),
            ),
        )
    }
    val body = outline(0f)
    clipPath(body) {
        val veil = if (LiquidGlass.frosts) tint else tint.copy(alpha = (tint.alpha + 0.18f).coerceAtMost(0.95f))
        drawRect(veil)
        // The slab: light along the upper edge, a little shade along the lower one.
        val band = (bezel / size.height).coerceIn(0.05f, 0.45f)
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.10f else 0.20f),
                band to Color.Transparent,
                1f - band to Color.Transparent,
                1f to Color.Black.copy(alpha = if (dark) 0.10f else 0.06f),
            ),
        )
        // The fringe, travelling round the rim (the gradient turns, the outline stays).
        val ang = 360f * time
        val hues = if (rim != null) listOf(rim, rim.copy(alpha = 0.5f), rim, rim.copy(alpha = 0.5f), rim)
            else listOf(Color(0xFFFF5A8C), Color(0xFFFFD25A), Color(0xFF5AE0A0), Color(0xFF5AB4FF), Color(0xFFB45AFF), Color(0xFFFF5A8C))
        val fringe = (if (dark) 0.30f else 0.38f) * fringeK * 0.55f
        val sweep = turnedSweep(center, ang, hues)
        val w = (bezel * 0.5f).coerceAtLeast(2f * dp)
        drawPath(outline(w / 2f), sweep, alpha = fringe * 0.6f, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        drawPath(outline(w / 4f), sweep, alpha = fringe, style = androidx.compose.ui.graphics.drawscope.Stroke(w / 2f))
        // The rim: brightest where the light falls, a weaker glint opposite.
        val k = if (dark) 0.85f else 1f
        val light = turnedSweep(
            center, ang + Math.toDegrees(-2.3).toFloat(),
            listOf(0.75f, 0.3f, 0.14f, 0.4f, 0.14f, 0.3f, 0.75f).map { Color.White.copy(alpha = it * k) },
            listOf(0f, 0.15f, 0.35f, 0.5f, 0.65f, 0.85f, 1f),
        )
        drawPath(outline(2f * dp), light, alpha = 0.35f, style = androidx.compose.ui.graphics.drawscope.Stroke(4f * dp))
        drawPath(outline(0.6f * dp), light, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f * dp))
    }
}

/** One turn of the light around the glass rims. */
private const val GLASS_LIGHT_PERIOD_MS = 14_000L
/**
 * The light moves in steps this long, not every frame: at 3° a step the motion looks the
 * same, while the glass is redrawn about 8 times a second instead of at the screen rate.
 */
private const val GLASS_TICK_MS = 125L

/**
 * Phase of the light, 0..1. Every panel reads the same clock and wakes on the same step
 * boundary, so all of them are redrawn in one frame.
 */
@Composable
private fun rememberGlassTime(): androidx.compose.runtime.State<Float> =
    androidx.compose.runtime.produceState(glassPhase()) {
        while (true) {
            kotlinx.coroutines.delay(GLASS_TICK_MS - android.os.SystemClock.uptimeMillis() % GLASS_TICK_MS)
            value = glassPhase()
        }
    }

private fun glassPhase(): Float =
    (android.os.SystemClock.uptimeMillis() % GLASS_LIGHT_PERIOD_MS) / GLASS_LIGHT_PERIOD_MS.toFloat()

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
        uniform float bezelPx;
        uniform float depthPx;
        uniform float zoom;
        uniform float fringeK;
        uniform float steep;
        uniform float dispK;
        uniform float vBend;
        uniform half4 rimColor;
        uniform float rimMix;

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
            float nl = length(n);
            n = nl > 0.0001 ? n / nl : float2(0.0, -1.0);

            // Convex bezel: strongest bend right at the rim, none past the bezel width.
            // Kept clear of the middle: there the rim normal flips and would tear the image.
            float bezel = min(bezelPx, min(half_size.x, half_size.y) * 0.7);
            float x = clamp(1.0 - inside / bezel, 0.0, 1.0);
            // Steep (small lenses): circular profile, the rim wraps the image around.
            // Smooth (panels): x², and the bend never exceeds half the rim width, so the
            // image near the edge is squeezed but never folded into a mirror copy.
            float bend = steep > 0.5 ? 1.0 - sqrt(1.0 - x * x) : x * x;
            // Pull samples inward (the rim magnifies what lies under the middle) and add a
            // little overall magnification so even a calm backdrop visibly bends.
            float2 off = -n * float2(1.0, vBend) * bend * depthPx - p * (zoom + 0.04 * x);

            // Chromatic split grows toward the edge.
            // A coloured rim keeps the colour split faint, so no rainbow shows at the edge.
            float disp = 0.32 * bend * dispK * (1.0 - 0.8 * rimMix);
            half4 cg = content.eval(coord + off);
            half r = content.eval(coord + off * (1.0 + disp)).r;
            half b = content.eval(coord + off * (1.0 - disp)).b;
            half3 col = half3(r, cg.g, b);
            col = sat(col, 1.18);

            // Veil for legibility.
            col = mix(col, tint.rgb, tint.a);

            // Thickness: the bezel catches light on the upper side and darkens a bit on the
            // lower one, so the edge reads as a rounded slab even over a flat backdrop.
            float slope = bend;
            float up = clamp(-n.y, 0.0, 1.0);
            float down = clamp(n.y, 0.0, 1.0);
            col += half3(1.0) * slope * up * (dark > 0.5 ? 0.10 : 0.22);
            col -= half3(1.0) * slope * down * (dark > 0.5 ? 0.10 : 0.07);

            // Light drifts around slowly; specular is brightest where the rim faces it.
            float ang = 6.2831853 * time;
            float2 light = float2(cos(ang - 2.3), sin(ang - 2.3));
            float spec = pow(max(dot(n, light), 0.0), 2.0);
            float spec2 = pow(max(dot(n, -light), 0.0), 3.0) * 0.45;

            // Iridescent fringe inside the bezel, hue travelling along the rim.
            // Guarded: at the very centre of a round panel the normal is zero and atan is undefined.
            float edgeAngle = atan(n.y, n.x + 0.00001) / 6.2831853;
            half3 rainbow = 0.5 + 0.5 * cos(6.2831853 * (edgeAngle * 1.5 + time) + half3(0.0, 2.094, 4.188));
            half3 iri = mix(rainbow, rimColor.rgb, rimMix);
            float fringe = smoothstep(bezel * 0.7, 0.0, inside) * (dark > 0.5 ? 0.30 : 0.38);
            col += iri * fringe * fringeK * (0.45 + 0.55 * (spec + spec2));

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
        bezelPx: Float, depthPx: Float, zoom: Float, blurPx: Float, fringeK: Float, rim: Color?,
        steep: Boolean, dispersion: Float, verticalBend: Float,
    ): RenderEffect {
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("radius", radius.coerceAtMost(minOf(w, h) / 2f))
        shader.setFloatUniform("dp", dp)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
        shader.setFloatUniform("dark", if (dark) 1f else 0f)
        shader.setFloatUniform("bezelPx", bezelPx)
        shader.setFloatUniform("depthPx", depthPx)
        shader.setFloatUniform("zoom", zoom)
        shader.setFloatUniform("fringeK", fringeK)
        shader.setFloatUniform("steep", if (steep) 1f else 0f)
        shader.setFloatUniform("dispK", dispersion)
        shader.setFloatUniform("vBend", verticalBend)
        val rc = rim ?: Color.White
        shader.setFloatUniform("rimColor", rc.red, rc.green, rc.blue, 1f)
        shader.setFloatUniform("rimMix", if (rim == null) 0f else 1f)
        val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
        if (blurPx < 0.5f) return lens
        val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        return RenderEffect.createChainEffect(lens, blur)
    }
}

/**
 * A backdrop of a screen's own content, for glass that floats inside that screen (header,
 * search bar). Such glass cannot sample the root layer: it is itself drawn into it.
 * Null when liquid glass is off.
 */
@Composable
fun rememberScreenGlass(): GlassBackdrop? {
    val root = LocalGlassBackdrop.current
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val local = remember(layer) { GlassBackdrop(layer) }
    if (root == null) return null
    local.transparency = root.transparency
    local.rim = root.rim
    return local
}

/** A sweep gradient round [center] turned by [degrees] (only the colours turn, not what is drawn). */
private fun turnedSweep(center: Offset, degrees: Float, colors: List<Color>, stops: List<Float>? = null) =
    object : androidx.compose.ui.graphics.ShaderBrush() {
        override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader =
            androidx.compose.ui.graphics.SweepGradientShader(center, colors, stops).also {
                it.setLocalMatrix(android.graphics.Matrix().apply { setRotate(degrees, center.x, center.y) })
            }
    }

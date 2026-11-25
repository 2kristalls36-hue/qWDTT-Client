package com.wdtt.client.ui.festive

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wdtt.client.lite.PowerLevel
import com.wdtt.client.ui.components.surface.LiquidGlass
import com.wdtt.client.ui.components.surface.LocalGlassBackdrop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal val SnowWhite = Color(0xFFFBFDFF)
internal val IceBlue = Color(0xFFB9D3F2)

/** Garland bulbs, in the order they hang. */
val GarlandColors = listOf(
    Color(0xFFFF5A5A), Color(0xFFFFC94D), Color(0xFF4CD97B), Color(0xFF5AC8FA), Color(0xFFFF7AD9),
)

@Composable
private fun darkGround(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
private fun glassMode(): Boolean = LocalGlassBackdrop.current != null && LiquidGlass.supported

/** Small snowflakes falling over the whole screen; touches pass through. */
@Composable
fun SnowfallOverlay(modifier: Modifier = Modifier) {
    val f = LocalFestive.current
    if (!f.active) return
    val color = if (darkGround()) Color.White else Color(0xFF9DB0CC)
    Canvas(modifier) {
        val t = f.time()
        val dp = density
        val span = size.height + 20f * dp
        for (i in 0 until FLAKES) {
            val near = hash01(11, i)
            // Bigger flakes are nearer: they fall faster and sway more.
            val radius = (1.0f + 1.8f * near) * dp
            val speed = (14f + 30f * near) * dp
            val sway = (4f + 12f * near) * dp
            val y = ((hash01(23, i) * span + t * speed) % span) - 10f * dp
            val x = hash01(29, i) * size.width + sin(t * (0.15f + 0.3f * hash01(17, i)) * 2f * PI.toFloat() + hash01(19, i) * 6.28f) * sway
            drawCircle(color, radius, Offset(x, y), alpha = 0.4f + 0.45f * hash01(31, i))
        }
    }
}

private const val FLAKES = 44

/**
 * Snow lying on the top edge of a control, following its rounded corners. Put it before
 * the clip of the control: the snow sits above its edge. Not in the glass mode: there the
 * snow is inside the glass (see [SnowGlobe]).
 */
@Composable
fun Modifier.snowCap(corner: Dp, seed: Int = 0, depth: Dp = 6.dp): Modifier {
    if (!LocalFestive.current.active || glassMode()) return this
    val shade = if (darkGround()) Color.Black.copy(alpha = 0.22f) else Color(0xFF34507A).copy(alpha = 0.16f)
    return this.drawWithCache {
        val r = min(corner.toPx(), min(size.width, size.height) / 2f)
        val path = snowCapPath(size.width, r, depth.toPx(), 1.5.dp.toPx(), seed)
        val tint = Brush.verticalGradient(
            listOf(Color.Transparent, IceBlue.copy(alpha = 0.45f)),
            startY = -depth.toPx(), endY = 2.dp.toPx(),
        )
        onDrawWithContent {
            drawContent()
            translate(top = 1.2.dp.toPx()) { drawPath(path, shade) }
            drawPath(path, SnowWhite)
            drawPath(path, tint)
        }
    }
}

/** Outline of the snow: lumpy on top, tapering at the ends, its base on the edge. */
internal fun snowCapPath(w: Float, r: Float, depth: Float, inner: Float, seed: Int): Path {
    fun edge(x: Float): Float {
        val dx = when {
            x < r -> r - x
            x > w - r -> x - (w - r)
            else -> 0f
        }
        return if (dx > 0f) r - sqrt(max(0f, r * r - dx * dx)) else 0f
    }
    fun smooth(e0: Float, e1: Float, v: Float): Float {
        val k = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }
    val x0 = r * 0.3f
    val x1 = w - r * 0.3f
    val n = 64
    val a = hash01(seed, 1) * 6.28f
    val b = hash01(seed, 2) * 6.28f
    val p = Path()
    for (i in 0..n) {
        val u = i / n.toFloat()
        val x = x0 + (x1 - x0) * u
        val taper = smooth(0f, 0.1f, u) * (1f - smooth(0.9f, 1f, u))
        val lumps = 0.62f + 0.24f * sin(u * 19f + a) + 0.14f * sin(u * 47f + b)
        val y = edge(x) - depth * taper * lumps
        if (i == 0) p.moveTo(x, edge(x) + inner) else p.lineTo(x, y)
    }
    for (i in n downTo 0) {
        val x = x0 + (x1 - x0) * i / n.toFloat()
        p.lineTo(x, edge(x) + inner)
    }
    p.close()
    return p
}

/** How much frost the power level brings: more power, more frost. */
fun frostAmount(level: PowerLevel): Float = when (level) {
    PowerLevel.Stable -> 0.4f
    PowerLevel.Balanced -> 0.7f
    PowerLevel.Maximum -> 1f
}

/**
 * Small frost crystals scattered over a card or a glass, behind its content. Each one has
 * its own place, size, turn and arms, so no two look alike; their number grows with the
 * power level.
 */
@Composable
fun Modifier.frost(seed: Int = 0, strength: Float = 1f): Modifier {
    val f = LocalFestive.current
    if (!f.active) return this
    val amount = frostAmount(f.level) * strength
    val dark = darkGround()
    val ice = if (dark) Color(0xFFDDEBFF) else Color(0xFF6F8DB8)
    val alpha = if (dark) 0.3f else 0.34f
    return this.drawWithCache {
        val path = frostPath(size, amount, seed, density)
        val stroke = Stroke(width = 0.8.dp.toPx(), cap = StrokeCap.Round)
        onDrawBehind { drawPath(path, ice, alpha = alpha, style = stroke) }
    }
}

internal fun frostPath(size: Size, amount: Float, seed: Int, density: Float): Path {
    val p = Path()
    if (size.width <= 0f || size.height <= 0f) return p
    // About one crystal per 6000 dp² at the maximum level, at least two.
    val area = size.width * size.height / (density * density)
    val count = max(2, (area / 6000f * amount).toInt()).coerceAtMost(24)
    // The seed also takes the size, so cards of one kind but other sizes differ too.
    val s = seed * 7919 + (size.width / density).toInt() * 31 + (size.height / density).toInt()
    for (c in 0 until count) {
        val x = hash01(s, c * 4) * size.width
        // Chaotic, but kept off the middle band, where text usually runs.
        val yRaw = hash01(s, c * 4 + 1)
        val y = (if (yRaw < 0.5f) yRaw * 0.7f else 0.3f + yRaw * 0.7f) * size.height
        val radius = (5f + 8f * hash01(s, c * 4 + 2)) * density
        val turn = hash01(s, c * 4 + 3) * PI.toFloat()
        val arms = if (hash01(s, c * 7 + 5) < 0.5f) 6 else 5
        crystal(p, x, y, radius, turn, arms, s + c * 13)
    }
    return p
}

/** One crystal: arms out from the centre with short side twigs, a little uneven. */
private fun crystal(p: Path, cx: Float, cy: Float, radius: Float, turn: Float, arms: Int, seed: Int) {
    for (a in 0 until arms) {
        val angle = turn + a * 2f * PI.toFloat() / arms + (hash01(seed, a) - 0.5f) * 0.25f
        val len = radius * (0.7f + 0.3f * hash01(seed, a + 10))
        p.moveTo(cx, cy)
        p.lineTo(cx + cos(angle) * len, cy + sin(angle) * len)
        // Two pairs of twigs along the arm.
        for (t in 1..2) {
            val at = len * (0.35f + 0.25f * t)
            val bx = cx + cos(angle) * at
            val by = cy + sin(angle) * at
            val twig = len * (0.32f - 0.08f * t)
            for (side in listOf(-1f, 1f)) {
                val ta = angle + side * 0.75f
                p.moveTo(bx, by)
                p.lineTo(bx + cos(ta) * twig, by + sin(ta) * twig)
            }
        }
    }
}

/**
 * The one garland of the maximum level: hung across the top of the screen under the
 * status bar, in two shallow sags, with a few lights that glow and gently brighten and
 * dim, each at its own pace. It hangs over the page background, not inside any element.
 */
@Composable
fun TopGarland(modifier: Modifier = Modifier) {
    val f = LocalFestive.current
    if (!f.active || f.level != PowerLevel.Maximum) return
    val wireColor = if (darkGround()) Color(0xFF55624F) else Color(0xFF3B4A3C)
    Canvas(modifier) {
        val geo = garlandGeometry(size.width, density, 2.dp.toPx(), spans = 2, perSpan = 4, sagDp = 9f)
        val bulb = 2.6.dp.toPx()
        val glow = 8.dp.toPx()
        val t = f.time()
        drawPath(geo.wire, wireColor, alpha = 0.85f, style = Stroke(width = 1.dp.toPx()))
        geo.bulbs.forEachIndexed { i, b ->
            val c = GarlandColors[i % GarlandColors.size]
            val period = 2.6f + 2f * hash01(3, i)
            val tw = if (f.moving) 0.72f + 0.28f * (0.5f + 0.5f * sin(t * 2f * PI.toFloat() / period + hash01(5, i) * 6.28f)) else 1f
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.5f * tw), Color.Transparent), center = b, radius = glow), glow, b)
            drawCircle(c, bulb, b, alpha = 0.78f + 0.22f * tw)
            drawCircle(Color.White, bulb * 0.38f, b - Offset(bulb * 0.3f, bulb * 0.3f), alpha = 0.65f * tw)
        }
    }
}

internal class GarlandGeometry(val wire: Path, val bulbs: List<Offset>)

internal fun garlandGeometry(w: Float, density: Float, top: Float, spans: Int, perSpan: Int, sagDp: Float): GarlandGeometry {
    val spanW = w / spans
    val sag = sagDp * density
    val wire = Path()
    val bulbs = ArrayList<Offset>()
    for (s in 0 until spans) {
        val x0 = s * spanW
        // A shallow parabola between two hooks.
        val n = 16
        for (i in 0..n) {
            val u = i / n.toFloat()
            val x = x0 + spanW * u
            val y = top + sag * 4f * u * (1f - u)
            if (s == 0 && i == 0) wire.moveTo(x, y) else wire.lineTo(x, y)
        }
        for (k in 0 until perSpan) {
            val u = (k + 0.5f) / perSpan
            bulbs += Offset(x0 + spanW * u, top + sag * 4f * u * (1f - u) + 2.4f * density)
        }
    }
    return GarlandGeometry(wire, bulbs)
}

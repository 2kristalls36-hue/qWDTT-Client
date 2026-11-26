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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wdtt.client.lite.PowerLevel
import com.wdtt.client.ui.components.surface.LiquidGlass
import com.wdtt.client.ui.components.surface.LocalGlassBackdrop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
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

/**
 * Snowflakes falling over the whole screen; touches pass through. Each is a six-armed
 * crystal of one of a few kinds, turning slowly as it falls and tipping over now and then
 * (seen edge-on it narrows); the nearer ones larger, faster and swaying more, the far ones
 * small and faint.
 */
@Composable
fun SnowfallOverlay(modifier: Modifier = Modifier) {
    val f = LocalFestive.current
    if (!f.active) return
    val color = if (darkGround()) Color.White else Color(0xFF9DB0CC)
    Canvas(modifier) {
        val t = f.time()
        val dp = density
        val span = size.height + 40f * dp
        for (i in 0 until FLAKES) {
            val near = hash01(11, i)
            // Bigger flakes are nearer: they fall faster and sway more.
            val radius = (2.2f + 6.5f * near * near) * dp
            val speed = (12f + 34f * near) * dp
            val sway = (4f + 14f * near) * dp
            val y = ((hash01(23, i) * span + t * speed) % span) - 20f * dp
            val x = hash01(29, i) * size.width + sin(t * (0.15f + 0.3f * hash01(17, i)) * 2f * PI.toFloat() + hash01(19, i) * 6.28f) * sway
            val spin = (hash01(37, i) * 360f + t * (hash01(41, i) - 0.5f) * 70f)
            val tip = cos(t * (0.2f + 0.5f * hash01(43, i)) + hash01(47, i) * 6.28f)
            val squash = 0.3f + 0.7f * kotlin.math.abs(tip)
            val alpha = (0.45f + 0.45f * near) * (0.75f + 0.25f * hash01(31, i))
            val kind = SNOWFLAKES[(hash01(53, i) * SNOWFLAKES.size).toInt().coerceIn(0, SNOWFLAKES.size - 1)]
            // A soft glow round it, then the crystal itself.
            drawCircle(color, radius * 0.8f, Offset(x, y), alpha = 0.1f * alpha)
            val width = max(0.9f * dp, radius * 0.13f)
            withTransform({
                translate(x, y)
                rotate(spin, Offset.Zero)
                scale(radius, radius * squash, Offset.Zero)
            }) {
                drawPath(kind, color, alpha = alpha, style = Stroke(width = width / radius, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

private const val FLAKES = 40

/**
 * The kinds of snowflake, a unit across from the middle: six arms each, and on them
 * branches (a stellar dendrite, a fern), a small plate in the middle, forked tips, a ring.
 */
private val SNOWFLAKES: List<Path> by lazy {
    fun crystal(arm: Path.(Float) -> Unit, middle: Path.() -> Unit = {}) = Path().apply {
        for (k in 0 until 6) {
            val a = k * PI.toFloat() / 3f
            val sub = Path().apply { arm(a) }
            addPath(sub)
        }
        middle()
    }
    fun Path.ray(a: Float, from: Float, to: Float) {
        moveTo(cos(a) * from, sin(a) * from)
        lineTo(cos(a) * to, sin(a) * to)
    }
    fun Path.branch(a: Float, at: Float, length: Float) {
        val bx = cos(a) * at
        val by = sin(a) * at
        for (side in listOf(-1f, 1f)) {
            val b = a + side * PI.toFloat() / 3f
            moveTo(bx, by)
            lineTo(bx + cos(b) * length, by + sin(b) * length)
        }
    }
    listOf(
        // A stellar dendrite: two pairs of branches on each arm.
        crystal({ a -> ray(a, 0f, 1f); branch(a, 0.42f, 0.3f); branch(a, 0.7f, 0.2f) }),
        // A fern: three pairs of short branches.
        crystal({ a -> ray(a, 0f, 1f); branch(a, 0.32f, 0.18f); branch(a, 0.55f, 0.2f); branch(a, 0.77f, 0.14f) }),
        // A plate in the middle, arms with forked tips.
        crystal({ a -> ray(a, 0.28f, 1f); branch(a, 0.8f, 0.2f) }) {
            for (k in 0..6) {
                val a = k * PI.toFloat() / 3f + PI.toFloat() / 6f
                val x = cos(a) * 0.3f
                val y = sin(a) * 0.3f
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
        },
        // A simple star.
        crystal({ a -> ray(a, 0f, 1f); branch(a, 0.62f, 0.26f) }),
        // Arms through a ring, a branch pair outside it.
        crystal({ a -> ray(a, 0f, 1f); branch(a, 0.72f, 0.22f) }) {
            for (k in 0..6) {
                val a = k * PI.toFloat() / 3f
                val x = cos(a) * 0.45f
                val y = sin(a) * 0.45f
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
        },
    )
}

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
 * status bar, in two shallow sags. Once a second its lights change colour one after
 * another along the wire, each blinking as it changes (see [GarlandChase]); with
 * animations off they stay as they are. It hangs over the page background, not inside
 * any element.
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
        val t = if (f.moving) f.time() else null
        drawPath(geo.wire, wireColor, alpha = 0.85f, style = Stroke(width = 1.dp.toPx()))
        geo.bulbs.forEachIndexed { i, b ->
            val c = GarlandColors[if (t != null) GarlandChase.colour(i, t) else GarlandChase.still(i)]
            val lit = if (t != null) GarlandChase.brightness(i, t) else 1f
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.5f * lit), Color.Transparent), center = b, radius = glow), glow, b)
            drawCircle(c, bulb, b, alpha = 0.3f + 0.7f * lit)
            drawCircle(Color.White, bulb * 0.38f, b - Offset(bulb * 0.3f, bulb * 0.3f), alpha = 0.65f * lit)
        }
    }
}

/**
 * The garland's lights running: once a [STEP] the lights change colour one after another
 * along the wire, [RUN] apart, each dimming for a moment ([BLINK]) and coming back in the
 * colour the light before it had, so the colours run along the wire. Two lights side by
 * side never show the same colour, not even while the change runs along: where it has
 * passed and where it has not yet, they are two colours apart.
 */
internal object GarlandChase {
    const val STEP = 1f
    const val RUN = 0.07f
    const val BLINK = 0.18f
    private const val DIM = 0.25f

    /** The colour of light [i] (an index into [GarlandColors]) at [t] seconds. */
    fun colour(i: Int, t: Float): Int {
        val step = floor((t - i * RUN) / STEP).toInt()
        return Math.floorMod(step - i, GarlandColors.size)
    }

    /** The colour of light [i] with animations off. */
    fun still(i: Int): Int = Math.floorMod(-i, GarlandColors.size)

    /** How bright light [i] is at [t]: 1, dipping to [DIM] as its colour changes. */
    fun brightness(i: Int, t: Float): Float {
        val since = (t - i * RUN).mod(STEP)
        val off = min(since, STEP - since) / (BLINK / 2f)
        if (off >= 1f) return 1f
        return DIM + (1f - DIM) * off * off * (3f - 2f * off)
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

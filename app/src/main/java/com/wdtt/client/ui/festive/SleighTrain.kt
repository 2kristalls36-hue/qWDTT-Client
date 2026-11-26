package com.wdtt.client.ui.festive

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * New Year: the four tabs are one train going right, drawn large in place of the tab icons
 * and labels: a trailer of presents (home), a bullfinch on its lantern, Santa in his
 * sleigh, and two reindeer, the lead one with a red nose. The trailer is coupled to the
 * sleigh by a rigid bar; the sleigh and the reindeer are joined by reins and traces sagging
 * across the gaps between the tabs.
 *
 * Each figure is drawn twice over: in the plain colour of the tab, a silhouette with its
 * details cut out of it (planks, ribbons, belt, collar, eyes), and in its own colours. The
 * colours show only under the droplet, as the accent colour of the other tabs does, so the
 * figure takes its colours as the droplet passes over it.
 *
 * Figures are on a [WIDE] by [TALL] grid, made of parts hung on joints (a leg on its hip,
 * the hat on the head on Santa), which [TrainPose] turns and moves (see [SleighMotion]).
 */
object SleighTrain {
    const val WIDE = 36f
    const val TALL = 24f

    /** The tallest the figures are drawn. */
    val height = 46.dp

    /** At most this share of its tab is taken by a figure, the rest is the gap to the next. */
    private const val FILL = 0.88f

    /** The figures, tab by tab, and the bullfinch. */
    internal val figures: List<FigureData> by lazy {
        listOf(trailer(), sleigh(), reindeer(leads = false), reindeer(leads = true))
    }
    internal val bird: FigureData by lazy { bullfinch() }

    const val count = 4

    /** The figure's height in a tab [width] wide, given the tallest it may be. */
    fun figureHeight(width: Float, tallest: Float): Float = minOf(tallest, width * FILL * TALL / WIDE)

    internal const val COUPLING_Y = 19.6f
    internal const val REINS_Y = 12f
    /** Where the bullfinch sits on the trailer (its feet on the lantern's arm), grid units. */
    internal val PERCH = Offset(31.9f, 9.8f)
    /** Where the reins leave Santa's mitten, in the arm's own place. */
    internal val MITTEN = Offset(21f, 11.7f)
    /** The ground under the runners and hooves. */
    internal const val GROUND = 22.6f
}

/** Joints of the figures: each figure uses its own ones of these. A parent comes first. */
internal object J {
    const val ROOT = 0
    // The trailer.
    const val LANTERN = 1
    // The sleigh.
    const val SANTA = 1
    const val HEAD = 2
    const val HAT = 3
    const val HAT_TIP = 4
    const val ARM = 5
    const val SANTA_EYE = 6
    // A reindeer.
    const val NECK = 1
    const val EAR = 2
    const val EYE = 3
    const val PUPIL = 4
    const val TAIL = 5
    const val HIND_FAR = 6
    const val HOCK_FAR = 7
    const val HIND_NEAR = 8
    const val HOCK_NEAR = 9
    const val FORE_FAR = 10
    const val KNEE_FAR = 11
    const val FORE_NEAR = 12
    const val KNEE_NEAR = 13
    const val SCARF = 14
    // The bullfinch.
    const val BIRD_HEAD = 1
    const val BIRD_WING = 2
    const val BIRD_TAIL = 3
    const val BIRD_EYE = 4
    const val BIRD_LEGS = 5

    const val COUNT = 16
}

/**
 * Where everything of the train is just now: for each figure (the tabs, then the bird as
 * [BIRD]) each joint's turn (degrees, clockwise), shift (grid units) and stretch; how fast
 * the train goes and how far it has gone; the reins, the bird's flight.
 */
class TrainPose {
    internal val joints = Array(FIGURES) { FloatArray(J.COUNT * 5) }

    /** 0 standing, 1 at full speed. */
    var speed = 0f
    /** How far the train has gone, in tab widths (the trees and the wind go by with it). */
    var distance = 0f
    /** Seconds, for the snow thrown up behind and the wind. */
    var time = 0f
    /** How the reins hang: 1 slack, 0 taut. */
    var reinSlack = 1f
    /** A flick running along the reins (Santa's command), its height in grid units. */
    var reinFlick = 0f
    var reinPhase = 0f
    /** How brightly the lead reindeer's nose glows, 0..1 more than usual. */
    var noseGlow = 0f
    /** The bullfinch: its feet in the trailer's grid units, its size, which way it faces. */
    var birdX = SleighTrain.PERCH.x
    var birdY = SleighTrain.PERCH.y
    var birdScale = 1f
    var birdFacing = 1f
    var birdAlpha = 1f

    init {
        rest()
    }

    /** Everything as at rest. */
    fun rest() {
        for (f in joints) for (j in 0 until J.COUNT) {
            f[j * 5] = 0f
            f[j * 5 + 1] = 0f
            f[j * 5 + 2] = 0f
            f[j * 5 + 3] = 1f
            f[j * 5 + 4] = 1f
        }
        speed = 0f
        reinSlack = 1f
        reinFlick = 0f
        noseGlow = 0f
        birdX = SleighTrain.PERCH.x
        birdY = SleighTrain.PERCH.y
        birdScale = 1f
        birdFacing = 1f
        birdAlpha = 1f
    }

    internal fun set(figure: Int, joint: Int, turn: Float = 0f, dx: Float = 0f, dy: Float = 0f, sx: Float = 1f, sy: Float = 1f) {
        val f = joints[figure]
        val k = joint * 5
        f[k] = turn
        f[k + 1] = dx
        f[k + 2] = dy
        f[k + 3] = sx
        f[k + 4] = sy
    }

    internal fun add(figure: Int, joint: Int, turn: Float = 0f, dx: Float = 0f, dy: Float = 0f) {
        val f = joints[figure]
        val k = joint * 5
        f[k] += turn
        f[k + 1] += dx
        f[k + 2] += dy
    }

    companion object {
        const val BIRD = 4
        internal const val FIGURES = 5
        /** At rest, for the still pictures. */
        val Rest = TrainPose()
    }
}

/** How a part shows in the silhouette. */
internal enum class Mono {
    /** Filled with the plain colour. */
    Fill,
    /** Cut out of what is under it. */
    Cut,
    /** Not there: only a colour detail. */
    Skip,
    /** In its own colour even so (the red nose). */
    Own,
}

/**
 * One part: its [path] filled (or stroked [stroke] wide) with [color]; [gap] wide around it
 * the parts under it are cut away in the silhouette, and outlined in a darker shade in
 * colour, so it stands apart from them. It moves with [joint].
 */
internal class Part(
    val path: Path,
    val color: Color?,
    val mono: Mono,
    val stroke: Float,
    val gap: Float,
    val dash: Float,
    val joint: Int,
)

/** A figure: its parts in drawing order and its joints (pivot, parent). */
internal class FigureData(
    val parts: Array<Part>,
    val pivotX: FloatArray,
    val pivotY: FloatArray,
    val parent: IntArray,
)

private val Gold = Color(0xFFE6B740)
private val GoldDeep = Color(0xFFB98A2C)
private val Iron = Color(0xFF555B66)
private val Wood = Color(0xFFA0683A)
private val WoodDark = Color(0xFF5E3A1C)
private val SleighRed = Color(0xFFC8232C)
private val SleighRedDeep = Color(0xFF8E1A21)
private val CoatRed = Color(0xFFE23A3F)
private val Fur = Color(0xFFF8F5EF)
private val Skin = Color(0xFFF3C7A5)
private val Rosy = Color(0xFFEA8C86)
private val Belt = Color(0xFF29221F)
private val Mitten = Color(0xFF2E8B57)
private val Burlap = Color(0xFFAF8452)
private val Twine = Color(0xFFDDB86E)
private val Leather = Color(0xFF8A4E2B)
private val Deer = Color(0xFFA56C42)
private val DeerDeep = Color(0xFF7C4E2F)
private val DeerLight = Color(0xFFDDBF97)
private val Cream = Color(0xFFF2E8D6)
private val Antler = Color(0xFFE3CB9F)
private val Hoof = Color(0xFF3A2418)
private val Eye = Color(0xFF1C120C)
private val Harness = Color(0xFFC62828)
private val Lamp = Color(0xFFFFD66B)
private val Flame = Color(0xFFFF9A3C)
private val Silver = Color(0xFFE6EBF2)
private val PresentGreen = Color(0xFF2E9E5B)
private val PresentBlue = Color(0xFF3D7BE0)
private val PresentGold = Color(0xFFF2C14E)
private val Teddy = Color(0xFFB98149)
private val Holly = Color(0xFF2F8A4A)
private val Tartan = Color(0xFF2F7D4F)
private val Fir = Color(0xFF2F6E4A)
private val FirTrunk = Color(0xFF6B4A2E)
private val BirdGrey = Color(0xFF8C95A3)
private val BirdRed = Color(0xFFE8553D)
private val BirdBlack = Color(0xFF24272D)
private val Bulbs = listOf(CandyRed, Color(0xFFFFD54F), Color(0xFF43C46A), Color(0xFF4FA3FF))

private fun Color.shade(f: Float) = lerp(this, Color.Black, f)
private fun Color.tint(f: Float) = lerp(this, Color.White, f)

private class Figure {
    val parts = ArrayList<Part>()
    val pivotX = FloatArray(J.COUNT)
    val pivotY = FloatArray(J.COUNT)
    val parent = IntArray(J.COUNT) { -1 }
    private var at = J.ROOT

    fun root(px: Float, py: Float) {
        pivotX[J.ROOT] = px
        pivotY[J.ROOT] = py
    }

    /** The parts made in [body] hang on joint [id], turning about ([px], [py]). */
    fun joint(id: Int, px: Float, py: Float, body: Figure.() -> Unit) {
        require(id > at) { "a joint comes after its parent" }
        pivotX[id] = px
        pivotY[id] = py
        parent[id] = at
        val was = at
        at = id
        body()
        at = was
    }

    fun fill(color: Color?, mono: Mono = Mono.Fill, gap: Float = 0f, body: Path.() -> Unit) {
        parts += Part(Path().apply(body), color, mono, 0f, gap, 0f, at)
    }

    fun line(color: Color?, width: Float, mono: Mono = Mono.Fill, gap: Float = 0f, dash: Float = 0f, body: Path.() -> Unit) {
        parts += Part(Path().apply(body), color, mono, width, gap, dash, at)
    }

    fun build() = FigureData(parts.toTypedArray(), pivotX, pivotY, parent)
}

private fun figure(body: Figure.() -> Unit): FigureData = Figure().apply(body).build()

private fun Path.rect(x: Float, y: Float, w: Float, h: Float) = addRect(Rect(x, y, x + w, y + h))
private fun Path.round(x: Float, y: Float, w: Float, h: Float, r: Float) =
    addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(r)))
private fun Path.circle(cx: Float, cy: Float, r: Float) = addOval(Rect(cx - r, cy - r, cx + r, cy + r))
private fun Path.oval(cx: Float, cy: Float, rx: Float, ry: Float) = addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry))
private fun Path.poly(vararg p: Float) {
    moveTo(p[0], p[1])
    for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1])
    close()
}
private fun Path.seg(x0: Float, y0: Float, x1: Float, y1: Float) {
    moveTo(x0, y0)
    lineTo(x1, y1)
}
/** A five-pointed star of radius [r] at ([cx], [cy]). */
private fun Path.star(cx: Float, cy: Float, r: Float) {
    for (k in 0 until 10) {
        val a = -PI / 2 + k * PI / 5
        val rr = if (k % 2 == 0) r else r * 0.45f
        val x = cx + (cos(a) * rr).toFloat()
        val y = cy + (sin(a) * rr).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A sled runner along the bottom, curled at the back, sweeping up in front. */
private fun Figure.runner() {
    fill(Gold) {
        moveTo(0.6f, 20.2f)
        cubicTo(0.8f, 21.6f, 1.8f, 22.4f, 3.2f, 22.4f)
        lineTo(28.6f, 22.4f)
        cubicTo(31.6f, 22.4f, 33.8f, 20.6f, 33.8f, 17.8f)
        lineTo(33.8f, 17.2f)
        lineTo(32.6f, 17.2f)
        lineTo(32.6f, 17.8f)
        cubicTo(32.6f, 19.9f, 30.9f, 21.3f, 28.6f, 21.3f)
        lineTo(3.2f, 21.3f)
        cubicTo(2.4f, 21.3f, 1.9f, 20.9f, 1.7f, 20.1f)
        close()
    }
    // The shine along it, and the knobs at its ends.
    line(Gold.tint(0.55f), 0.25f, Mono.Skip) { seg(3.4f, 21.75f, 28.4f, 21.75f) }
    fill(Gold, gap = 0.2f) {
        circle(33.2f, 16.7f, 0.85f)
        circle(1.05f, 19.75f, 0.6f)
    }
    fill(GoldDeep, Mono.Cut) { circle(33.2f, 16.7f, 0.32f) }
}

private fun Figure.struts(vararg xs: Float) {
    for (x in xs) fill(GoldDeep) { rect(x, 18.4f, 0.9f, 3f) }
}

/** Two loops of a bow meeting at ([x], [y]), [s] wide each. */
private fun Path.bow(x: Float, y: Float, s: Float) {
    moveTo(x, y)
    cubicTo(x - 0.4f * s, y - 1.7f * s, x - 1.6f * s, y - 1.6f * s, x - 1.3f * s, y - 0.5f * s)
    cubicTo(x - 1.1f * s, y + 0.1f * s, x - 0.5f * s, y + 0.1f * s, x, y)
    close()
    moveTo(x, y)
    cubicTo(x + 0.4f * s, y - 1.7f * s, x + 1.6f * s, y - 1.6f * s, x + 1.3f * s, y - 0.5f * s)
    cubicTo(x + 1.1f * s, y + 0.1f * s, x + 0.5f * s, y + 0.1f * s, x, y)
    close()
}

/**
 * A present [w] by [h] at ([x], [y]): a box and a lid a little wider with snow on it, tied
 * both ways with a ribbon through [rx] and [ry], a bow on top; [pattern] adds its print.
 */
private fun Figure.present(
    x: Float, y: Float, w: Float, h: Float, box: Color, ribbon: Color, rx: Float, ry: Float, bowSize: Float,
    pattern: Figure.() -> Unit = {},
) {
    val band = 0.8f
    val lid = minOf(1.6f, h * 0.24f)
    fill(box, gap = 0.3f) { rect(x, y + lid, w, h - lid) }
    pattern()
    fill(box.tint(0.12f), gap = 0.3f) { round(x - 0.3f, y, w + 0.6f, lid, 0.25f) }
    // Snow on the lid, running over its edge here and there.
    fill(SnowWhite, Mono.Skip) {
        round(x - 0.3f, y - 0.2f, w + 0.6f, 0.5f, 0.25f)
        circle(x + w * 0.2f, y + 0.35f, 0.28f)
        circle(x + w * 0.78f, y + 0.3f, 0.22f)
    }
    fill(ribbon, Mono.Cut) {
        rect(rx, y, band, h)
        rect(x, ry, w, band)
    }
    line(ribbon.shade(0.25f), 0.12f, Mono.Skip) {
        seg(rx + 0.15f, y + lid, rx + 0.15f, y + h)
        seg(x, ry + 0.15f, x + w, ry + 0.15f)
    }
    fill(ribbon, gap = 0.2f) { bow(rx + band / 2f, y + 0.05f, bowSize) }
    fill(ribbon.shade(0.2f)) { circle(rx + band / 2f, y + 0.05f, 0.3f * bowSize) }
}

/** Home: a trailer of presents with a lantern and a teddy, coupled to the sleigh in front. */
private fun trailer() = figure {
    root(17f, 18f)
    runner()
    struts(4.4f, 12.2f, 20f, 27.4f)
    // The coupling bar going on to the sleigh.
    fill(Iron) { rect(29.6f, SleighTrain.COUPLING_Y - 0.45f, 6.4f, 0.9f) }
    fill(Iron.shade(0.3f), Mono.Skip) { circle(31.2f, SleighTrain.COUPLING_Y, 0.25f) }
    // The lantern on a post at the front, lit; it swings on its hook.
    fill(WoodDark) {
        rect(30.2f, 9.8f, 0.7f, 6.2f)
        rect(30.2f, 9.8f, 3.2f, 0.55f)
    }
    joint(J.LANTERN, 32.8f, 10.3f) {
        line(Iron, 0.25f) { seg(32.8f, 10.3f, 32.8f, 11f) }
        fill(Iron) {
            poly(31.9f, 11.75f, 33.7f, 11.75f, 32.8f, 10.85f)
            rect(32f, 11.6f, 1.6f, 2.3f)
            rect(31.85f, 13.8f, 1.9f, 0.45f)
        }
        fill(Lamp, Mono.Cut) { rect(32.3f, 11.95f, 1f, 1.6f) }
        fill(Flame, Mono.Skip) {
            moveTo(32.8f, 12.2f)
            cubicTo(33.2f, 12.7f, 33.15f, 13.3f, 32.8f, 13.4f)
            cubicTo(32.45f, 13.3f, 32.4f, 12.7f, 32.8f, 12.2f)
            close()
        }
        line(Iron, 0.18f, Mono.Skip) { seg(32.8f, 11.95f, 32.8f, 13.55f) }
    }
    // A candy cane peeking out between the presents.
    val cane: Path.() -> Unit = {
        moveTo(19.4f, 15f)
        lineTo(19.4f, 8.4f)
        cubicTo(19.4f, 6.9f, 21.4f, 6.9f, 21.4f, 8.4f)
        lineTo(21.4f, 9f)
    }
    line(Color.White, 0.85f, gap = 0.2f, body = cane)
    line(CandyRed, 0.85f, Mono.Skip, dash = 0.55f, body = cane)
    // A teddy bear peeking over the presents at the front.
    fill(Teddy, gap = 0.25f) {
        circle(26.3f, 9.9f, 0.55f)
        circle(28.5f, 9.9f, 0.55f)
        circle(27.4f, 11f, 1.35f)
    }
    fill(Teddy.tint(0.35f), Mono.Skip) {
        circle(26.3f, 9.9f, 0.25f)
        circle(28.5f, 9.9f, 0.25f)
        oval(27.6f, 11.55f, 0.62f, 0.45f)
    }
    fill(Eye, Mono.Cut) {
        circle(26.95f, 10.75f, 0.17f)
        circle(28f, 10.75f, 0.17f)
    }
    fill(Eye, Mono.Skip) { oval(27.65f, 11.35f, 0.22f, 0.15f) }
    // The bed, of planks, with a rim, iron at its ends and rivets.
    fill(Wood) { round(1.8f, 15.6f, 29.4f, 3.2f, 0.5f) }
    line(WoodDark, 0.3f, Mono.Cut) {
        for (k in 1..7) seg(1.8f + k * 3.675f, 16.6f, 1.8f + k * 3.675f, 18.3f)
        seg(2.4f, 16.3f, 30.6f, 16.3f)
    }
    fill(Iron, Mono.Skip) {
        round(1.8f, 15.6f, 1.2f, 3.2f, 0.4f)
        round(30f, 15.6f, 1.2f, 3.2f, 0.4f)
    }
    fill(Iron.tint(0.4f), Mono.Skip) {
        for (y in floatArrayOf(16.3f, 18.1f)) {
            circle(2.4f, y, 0.17f)
            circle(30.6f, y, 0.17f)
        }
    }
    // A string of lights along the bed.
    line(Holly.shade(0.3f), 0.15f, Mono.Skip) {
        moveTo(3.2f, 16.9f)
        for (k in 0 until 7) {
            val x0 = 3.2f + k * 3.675f
            cubicTo(x0 + 1.2f, 17.5f, x0 + 2.4f, 17.5f, x0 + 3.675f, 16.9f)
        }
    }
    for (k in 0 until 8) {
        val x = 3.65f + k * 3.675f
        fill(Bulbs[k % Bulbs.size], Mono.Cut) { oval(x, 17.65f, 0.33f, 0.42f) }
    }
    // The presents: a big red one with a tag, a green one with dots, a blue one with spots,
    // a small gold one with stripes.
    present(2.8f, 7.6f, 8.6f, 8f, CandyRed, Gold, 6.7f, 11.4f, 1.7f) {
        fill(CandyRed.shade(0.12f), Mono.Skip) {
            for (k in 0 until 4) for (m in 0 until 3) star(3.9f + k * 2.2f, 10.2f + m * 2f, 0.32f)
        }
    }
    present(11.8f, 9.8f, 7.4f, 5.8f, PresentGreen, CandyRed, 15.1f, 12.6f, 1.3f) {
        fill(Color.White.copy(alpha = 0.75f), Mono.Skip) {
            for (k in 0 until 5) for (m in 0 until 3) circle(12.5f + k * 1.5f + (m % 2) * 0.75f, 11.9f + m * 1.3f, 0.18f)
        }
    }
    present(19.6f, 11.2f, 5.8f, 4.4f, PresentBlue, Silver, 22.1f, 13.3f, 1.05f) {
        fill(Color.White.copy(alpha = 0.6f), Mono.Skip) {
            for (k in 0 until 4) for (m in 0 until 2) circle(20.3f + k * 1.45f + (m % 2) * 0.7f, 12.7f + m * 1.6f, 0.3f)
        }
    }
    present(25.8f, 12.8f, 4f, 2.8f, PresentGold, CandyRed, 27.4f, 13.9f, 0.8f) {
        line(GoldDeep, 0.25f, Mono.Skip) {
            for (k in 0 until 5) seg(25.8f + k * 0.9f, 15.6f, 26.6f + k * 0.9f, 13.5f)
        }
    }
    // A tag on the big present's bow.
    line(Fur.shade(0.3f), 0.12f, Mono.Skip) { seg(7.1f, 7.7f, 4.6f, 9.2f) }
    fill(Fur, gap = 0.12f) { poly(3.6f, 9.1f, 4.8f, 8.8f, 5.1f, 9.9f, 3.9f, 10.2f) }
    fill(CandyRed, Mono.Cut) { circle(4.55f, 9.15f, 0.12f) }
}

/** Santa in his sleigh, a sack of presents behind him, the reins in his hand. */
private fun sleigh() = figure {
    root(18f, 18f)
    runner()
    struts(6.4f, 14f, 21.6f)
    // Bells along the bottom of the sleigh.
    for (x in floatArrayOf(8.4f, 12.4f, 16.4f, 20.4f)) {
        line(GoldDeep, 0.15f) { seg(x, 18.6f, x, 19.1f) }
        fill(Gold, gap = 0.15f) { circle(x, 19.45f, 0.42f) }
        line(GoldDeep, 0.12f, Mono.Cut) { seg(x - 0.25f, 19.6f, x + 0.25f, 19.6f) }
    }
    // The brace from the front of the sleigh to the runner.
    line(GoldDeep, 0.7f) {
        moveTo(26.4f, 16.2f)
        cubicTo(28.6f, 17f, 30.2f, 18.6f, 30.8f, 20.8f)
    }
    // The coupling bar from the trailer.
    fill(Iron) { rect(0f, SleighTrain.COUPLING_Y - 0.45f, 4.2f, 0.9f) }
    // The high back, curling over at the top, edged in gold.
    fill(SleighRedDeep) {
        moveTo(3f, 15f)
        lineTo(3f, 8.4f)
        cubicTo(3f, 6.4f, 5.6f, 5.8f, 6.4f, 7.5f)
        cubicTo(6.8f, 8.5f, 6.1f, 9.4f, 5.3f, 9.1f)
        lineTo(5.3f, 15f)
        close()
    }
    line(Gold, 0.22f, Mono.Skip) {
        moveTo(3.25f, 15f)
        lineTo(3.25f, 8.4f)
        cubicTo(3.25f, 6.7f, 5.4f, 6.2f, 6.1f, 7.6f)
    }
    line(Gold, 0.3f, Mono.Cut) { circle(5.2f, 7.9f, 0.55f) }
    // The sack, tied at the top, presents showing at its mouth, a patch on it.
    fill(CandyRed, gap = 0.25f) { poly(5.3f, 5.4f, 7.9f, 4.6f, 8.7f, 7.9f, 6f, 8.5f) }
    line(Gold, 0.4f, Mono.Cut) { seg(6.6f, 5f, 7.35f, 8.2f) }
    fill(PresentGreen, gap = 0.25f) { poly(7.9f, 6f, 10.4f, 5.5f, 10.7f, 8.1f, 8.1f, 8.4f) }
    line(CandyRed, 0.4f, Mono.Cut) { seg(9.15f, 5.75f, 9.4f, 8.25f) }
    fill(Burlap, gap = 0.3f) {
        moveTo(4.4f, 14.6f)
        cubicTo(3.6f, 12f, 3.9f, 9.4f, 5.4f, 8.2f)
        lineTo(9.8f, 8f)
        cubicTo(11f, 9.6f, 11f, 12.3f, 10.3f, 14.6f)
        close()
    }
    fill(Twine, Mono.Cut) { round(5f, 7.7f, 5.2f, 0.9f, 0.45f) }
    line(Burlap.shade(0.25f), 0.25f, Mono.Skip) {
        seg(5.6f, 9.6f, 5.3f, 13.4f)
        seg(8.8f, 9.4f, 9.4f, 13f)
    }
    fill(Burlap.shade(0.15f), Mono.Skip) { poly(6.2f, 10.4f, 8f, 10.2f, 8.2f, 12f, 6.4f, 12.2f) }
    line(Twine, 0.12f, Mono.Skip, dash = 0.3f) { poly(6.35f, 10.55f, 7.85f, 10.38f, 8.03f, 11.85f, 6.53f, 12.03f) }
    // Santa, leaning a little as he sits.
    joint(J.SANTA, 13.6f, 14.8f) {
        // His coat with a fur edge down the front and a belt with a buckle.
        fill(CoatRed, gap = 0.3f) {
            moveTo(10f, 15.2f)
            cubicTo(9.8f, 11.4f, 11.4f, 9.4f, 13.6f, 9.4f)
            cubicTo(15.9f, 9.4f, 17.4f, 11.4f, 17.4f, 15.2f)
            close()
        }
        fill(Fur, Mono.Skip) { rect(13.25f, 10f, 0.8f, 5f) }
        fill(Belt, Mono.Cut) { rect(10.1f, 12.5f, 7.2f, 0.8f) }
        fill(Gold) { rect(13.05f, 12.3f, 1.2f, 1.2f) }
        fill(Belt, Mono.Cut) { rect(13.4f, 12.65f, 0.5f, 0.5f) }
        // The head: face, rosy cheek, nose, spectacles, a bushy brow, beard and moustache.
        joint(J.HEAD, 13.9f, 9.3f) {
            fill(Skin) { circle(13.9f, 7.2f, 1.8f) }
            joint(J.SANTA_EYE, 14.7f, 6.8f) {
                fill(Eye, Mono.Cut) { circle(14.7f, 6.8f, 0.27f) }
                fill(Color.White, Mono.Skip) { circle(14.78f, 6.72f, 0.09f) }
            }
            fill(Rosy, Mono.Skip) { circle(14.9f, 7.85f, 0.45f) }
            line(Gold.shade(0.1f), 0.14f, Mono.Skip) {
                circle(14.75f, 6.85f, 0.55f)
                seg(15.3f, 6.85f, 15.75f, 6.95f)
                seg(14.2f, 6.8f, 12.6f, 6.4f)
            }
            fill(Fur, Mono.Skip) {
                moveTo(14.1f, 6.05f)
                cubicTo(14.5f, 5.7f, 15.2f, 5.75f, 15.5f, 6.1f)
                cubicTo(15.1f, 6.05f, 14.6f, 6.1f, 14.1f, 6.05f)
                close()
            }
            fill(Fur, gap = 0.3f) {
                moveTo(12f, 7.8f)
                cubicTo(11.6f, 10.6f, 12.8f, 12f, 14f, 12f)
                cubicTo(15.2f, 12f, 16.2f, 10.8f, 16f, 7.8f)
                cubicTo(15f, 8.8f, 13f, 8.8f, 12f, 7.8f)
                close()
            }
            line(Fur.shade(0.12f), 0.15f, Mono.Skip) {
                moveTo(13.2f, 9.6f)
                cubicTo(13.3f, 10.4f, 13.6f, 11f, 13.9f, 11.4f)
                moveTo(14.9f, 9.5f)
                cubicTo(14.9f, 10.3f, 14.7f, 10.9f, 14.4f, 11.3f)
            }
            fill(Fur, Mono.Skip) {
                moveTo(13.4f, 8.6f)
                cubicTo(14.2f, 7.9f, 15.4f, 7.9f, 16.1f, 8.3f)
                cubicTo(15.5f, 9f, 14.4f, 9.1f, 13.4f, 8.6f)
                close()
            }
            fill(Skin.shade(0.08f), gap = 0.15f) { circle(15.6f, 7.55f, 0.5f) }
            // The cap: flopping back, a pompom at its tip, a fur brim.
            joint(J.HAT, 13.95f, 5.6f) {
                fill(CoatRed) {
                    moveTo(12f, 5.2f)
                    cubicTo(11.9f, 3.4f, 13.1f, 2.4f, 14.3f, 2.5f)
                    cubicTo(15.4f, 2.6f, 16f, 3.6f, 15.9f, 5.2f)
                    close()
                }
                joint(J.HAT_TIP, 12.6f, 3.2f) {
                    fill(CoatRed) {
                        moveTo(12.8f, 2.9f)
                        cubicTo(11.4f, 2.1f, 9.8f, 2.6f, 9.3f, 4.1f)
                        lineTo(10.4f, 4.4f)
                        cubicTo(10.8f, 3.7f, 11.6f, 3.5f, 12.5f, 4.1f)
                        close()
                    }
                    fill(Fur, gap = 0.2f) { circle(9.7f, 4.6f, 0.85f) }
                }
                fill(Fur, gap = 0.25f) { round(11.7f, 5f, 4.5f, 1.1f, 0.55f) }
                line(Fur.shade(0.1f), 0.12f, Mono.Skip) {
                    for (k in 0 until 6) seg(12.2f + k * 0.7f, 5.3f, 12.4f + k * 0.7f, 5.8f)
                }
            }
        }
        // The arm held out, a fur cuff, a mitten on the reins.
        joint(J.ARM, 15.6f, 10.6f) {
            fill(CoatRed, gap = 0.3f) {
                moveTo(15f, 9.8f)
                cubicTo(16.8f, 10f, 18.4f, 10.4f, 19.6f, 10.9f)
                lineTo(19.7f, 12.3f)
                cubicTo(18.2f, 12.4f, 16.8f, 12.6f, 15.4f, 12.8f)
                close()
            }
            fill(Fur, gap = 0.15f) { round(19.1f, 10.6f, 1.1f, 1.9f, 0.4f) }
            fill(Mitten, gap = 0.2f) {
                circle(20.7f, 11.6f, 0.95f)
                circle(20.9f, 10.7f, 0.4f)
            }
            line(Mitten.tint(0.4f), 0.15f, Mono.Skip) { seg(20f, 11.2f, 21.3f, 11.4f) }
        }
    }
    // The body of the sleigh, low at the seat, its front curling up; a gold edge and swirl.
    fill(SleighRed) {
        moveTo(2.6f, 13.4f)
        lineTo(21.4f, 14f)
        cubicTo(24.4f, 14f, 25.9f, 12.1f, 26.7f, 10.2f)
        cubicTo(27.4f, 8.6f, 29.4f, 8f, 30.4f, 9.2f)
        cubicTo(31.2f, 10.2f, 30.8f, 11.6f, 29.6f, 11.6f)
        cubicTo(28.8f, 11.6f, 28.6f, 12.4f, 28.3f, 13.4f)
        cubicTo(27.6f, 16.6f, 25.4f, 18.8f, 21.6f, 18.8f)
        lineTo(3.8f, 18.8f)
        cubicTo(3f, 18.8f, 2.6f, 18.2f, 2.6f, 17.4f)
        close()
    }
    line(Gold, 0.5f, Mono.Cut) {
        moveTo(2.8f, 13.45f)
        lineTo(21.4f, 14.05f)
        cubicTo(24.4f, 14.05f, 25.9f, 12.15f, 26.7f, 10.25f)
    }
    line(Gold, 0.4f, Mono.Cut) {
        moveTo(4.4f, 16.6f)
        lineTo(19.6f, 16.6f)
        cubicTo(22.6f, 16.6f, 24.6f, 15.2f, 25.8f, 13f)
        cubicTo(26.4f, 11.9f, 27.6f, 12f, 27.4f, 13f)
    }
    line(Gold, 0.22f, Mono.Skip) {
        moveTo(4.4f, 17.7f)
        lineTo(20.4f, 17.7f)
        cubicTo(22.2f, 17.7f, 23.4f, 17.1f, 24.2f, 16.2f)
    }
    line(Gold, 0.35f, Mono.Cut) { circle(29.2f, 9.9f, 0.55f) }
    fill(Gold, Mono.Cut) { star(6.4f, 15.6f, 0.8f) }
    // A tartan blanket over Santa's knees, hanging over the side.
    fill(Tartan, gap = 0.25f) {
        moveTo(9.6f, 13.55f)
        lineTo(17.6f, 13.8f)
        lineTo(17.3f, 16.6f)
        cubicTo(16f, 17.2f, 14.6f, 16.8f, 13.4f, 17.2f)
        cubicTo(12.2f, 17.6f, 10.8f, 17f, 9.9f, 17.3f)
        close()
    }
    line(CandyRed, 0.3f, Mono.Cut) {
        seg(9.7f, 15.1f, 17.45f, 15.3f)
        seg(12.4f, 13.6f, 12.3f, 17.3f)
        seg(15.2f, 13.7f, 15.1f, 16.9f)
    }
    line(Gold, 0.12f, Mono.Skip) {
        seg(9.7f, 15.6f, 17.4f, 15.8f)
        seg(13.6f, 13.65f, 13.5f, 17.2f)
    }
    line(Tartan.tint(0.3f), 0.14f, Mono.Skip) {
        for (k in 0 until 11) {
            val x = 10.1f + k * 0.7f
            seg(x, 17.1f + 0.15f * sin(k * 1.3f), x, 17.7f + 0.15f * sin(k * 1.3f))
        }
    }
    fill(Gold, Mono.Skip) {
        for (x in floatArrayOf(5f, 19f, 22f)) circle(x, 17.75f, 0.32f)
    }
    // A bell under the curl.
    line(Gold, 0.2f) { seg(29.6f, 11.6f, 29.9f, 12.4f) }
    fill(Gold, gap = 0.2f) { circle(30f, 12.9f, 0.6f) }
}

/**
 * A reindeer standing, facing right; the lead one has a red nose and a scarf, and no traces
 * in front of it.
 */
private fun reindeer(leads: Boolean) = figure {
    root(16f, 13f)
    // A leg: the thigh or forearm on [hip], the lower leg on [knee], a hoof with a tuft.
    fun leg(hip: Int, knee: Int, dx: Float, fore: Boolean, coat: Color, gap: Float) {
        val hx = (if (fore) 23.4f else 11f) + dx
        val kx = (if (fore) 23.45f else 10.95f) + dx
        val ky = if (fore) 18f else 18.6f
        joint(hip, hx, 14.2f) {
            fill(coat, gap = gap) {
                if (fore) poly(21.9f + dx, 13.8f, 24.9f + dx, 13.8f, 24.2f + dx, 18f, 22.8f + dx, 18f, 22.3f + dx, 16f)
                else poly(9.4f + dx, 13.2f, 12.7f + dx, 13.8f, 11.6f + dx, 18.6f, 10.3f + dx, 18.6f, 9.8f + dx, 16.2f)
            }
            joint(knee, kx, ky) {
                fill(coat, gap = gap) {
                    if (fore) poly(22.85f + dx, 17.8f, 24.05f + dx, 17.8f, 24.2f + dx, 21.5f, 23.1f + dx, 21.6f)
                    else poly(10.3f + dx, 18.4f, 11.6f + dx, 18.4f, 11.85f + dx, 21.5f, 10.85f + dx, 21.6f)
                    circle(kx, ky, 0.64f)
                }
                val fx = (if (fore) 23.5f else 11.4f) + dx
                fill(Cream, Mono.Skip) { poly(fx - 0.7f, 20.85f, fx + 0.6f, 20.85f, fx + 0.75f, 21.4f, fx - 0.75f, 21.4f) }
                fill(Hoof, gap = 0.2f) { poly(fx - 0.8f, 21.3f, fx + 0.8f, 21.3f, fx + 1.1f, 22.6f, fx - 1f, 22.6f) }
            }
        }
    }
    // The far legs, a shade darker, then the near ones.
    leg(J.HIND_FAR, J.HOCK_FAR, -1.6f, fore = false, coat = DeerDeep, gap = 0f)
    leg(J.FORE_FAR, J.KNEE_FAR, 1.1f, fore = true, coat = DeerDeep, gap = 0f)
    leg(J.HIND_NEAR, J.HOCK_NEAR, 0f, fore = false, coat = Deer, gap = 0.3f)
    leg(J.FORE_NEAR, J.KNEE_NEAR, 0f, fore = true, coat = Deer, gap = 0.3f)
    // A short white tail.
    joint(J.TAIL, 7.2f, 10.9f) {
        fill(Cream, gap = 0.15f) {
            moveTo(7.3f, 10.6f)
            cubicTo(6.1f, 9.3f, 4.7f, 9.5f, 4.4f, 10.5f)
            cubicTo(5.2f, 10.8f, 6.1f, 11.6f, 6.9f, 12.1f)
            close()
        }
    }
    // The body, its belly lighter, a pale patch on the rump.
    fill(Deer) {
        moveTo(9.6f, 9.8f)
        cubicTo(13f, 9.3f, 19f, 9.1f, 22.4f, 9.6f)
        cubicTo(25f, 10f, 26.2f, 11.8f, 25.8f, 13.6f)
        cubicTo(25.4f, 15.4f, 23.8f, 16.2f, 22f, 16f)
        cubicTo(18f, 15.6f, 14f, 15.8f, 10.6f, 16f)
        cubicTo(8f, 16.2f, 6.6f, 14.8f, 6.8f, 12.8f)
        cubicTo(7f, 11f, 8f, 10.1f, 9.6f, 9.8f)
        close()
    }
    fill(DeerLight, Mono.Skip) {
        moveTo(10f, 15.9f)
        cubicTo(14f, 15.3f, 18f, 15.1f, 22f, 15.4f)
        cubicTo(23.8f, 15.5f, 25f, 14.6f, 25.5f, 13.2f)
        cubicTo(25.6f, 14.8f, 24f, 16.2f, 22f, 16f)
        cubicTo(18f, 15.7f, 14f, 15.9f, 10.6f, 16f)
        close()
        oval(8.2f, 11.9f, 1.1f, 1.3f)
    }
    line(DeerDeep, 0.35f, Mono.Skip) {
        moveTo(10.5f, 10f)
        cubicTo(14f, 9.5f, 18.5f, 9.35f, 21.5f, 9.75f)
    }
    // The harness: a saddle pad edged in gold with tassels, the girth strap (cut) with a
    // buckle; on the lead's pad a gold star, on the other one a sprig of holly.
    fill(Harness, Mono.Skip) { round(13.2f, 9.1f, 5.6f, 3.3f, 0.8f) }
    line(Gold, 0.3f, Mono.Skip) { round(13.35f, 9.25f, 5.3f, 3f, 0.7f) }
    line(Gold, 0.16f, Mono.Skip) {
        for (k in 0 until 6) seg(13.7f + k * 0.95f, 12.4f, 13.7f + k * 0.95f, 13f)
    }
    fill(Harness.shade(0.15f), Mono.Cut) { rect(15.6f, 9f, 0.9f, 7.2f) }
    fill(Gold) { rect(15.5f, 13.3f, 1.1f, 1f) }
    if (leads) {
        fill(Gold, Mono.Skip) { star(17.65f, 10.7f, 0.75f) }
    } else {
        fill(Holly, Mono.Skip) {
            oval(17.2f, 10.5f, 0.7f, 0.32f)
            oval(18.1f, 10.9f, 0.7f, 0.32f)
        }
        fill(CandyRed, Mono.Skip) {
            circle(17.6f, 10.95f, 0.22f)
            circle(17.95f, 10.55f, 0.22f)
        }
    }
    // The traces from behind along its side up to the collar.
    line(Leather, 0.45f) { seg(0f, SleighTrain.REINS_Y, 7.2f, 12.1f) }
    line(Leather, 0.4f, Mono.Skip) {
        seg(7.2f, 12.1f, 15.6f, 11.9f)
        seg(16.5f, 11.85f, 22.2f, 10.1f)
    }
    // The neck and head: they lift, lower and turn together.
    joint(J.NECK, 22.8f, 10f) {
        fill(Deer) {
            moveTo(20.8f, 10.4f)
            cubicTo(22.4f, 8.4f, 23.8f, 6.4f, 24.8f, 4.8f)
            lineTo(28.2f, 5.8f)
            cubicTo(27.6f, 8.2f, 27f, 10.6f, 25.8f, 13f)
            close()
        }
        fill(Cream) {
            poly(
                28.4f, 6.4f, 27.4f, 7.4f, 28f, 7.8f, 27f, 9f, 27.6f, 9.4f, 26.6f, 10.6f, 27.1f, 11f, 26f, 12.6f,
                25.4f, 12.4f, 26f, 9.6f, 26.8f, 6.6f,
            )
        }
        if (leads) {
            // A green scarf with white stripes, its ends flying back.
            fill(Mitten, gap = 0.2f) { poly(24.2f, 6.2f, 27.9f, 7.1f, 27.4f, 8.3f, 23.6f, 7.4f) }
            joint(J.SCARF, 24.2f, 7f) {
                fill(Mitten, gap = 0.18f) {
                    poly(24.4f, 6.6f, 21.6f, 7f, 21.2f, 7.9f, 24.2f, 7.7f)
                    poly(24.1f, 7.3f, 22.2f, 8.4f, 22.3f, 9.2f, 24.4f, 7.9f)
                }
                line(Color.White, 0.2f, Mono.Skip) {
                    seg(22.6f, 6.95f, 22.4f, 7.8f)
                    seg(23.4f, 6.85f, 23.2f, 7.75f)
                    seg(22.9f, 8.1f, 23.2f, 8.65f)
                }
            }
            line(Color.White, 0.2f, Mono.Skip) {
                seg(25.1f, 6.45f, 24.7f, 7.6f)
                seg(26.3f, 6.75f, 25.9f, 7.9f)
                seg(27.4f, 7f, 27.0f, 8.15f)
            }
        }
        // Antlers: two branching beams.
        line(Antler, 0.62f, gap = 0.12f) {
            moveTo(26.5f, 3.6f)
            cubicTo(25.9f, 2.2f, 24.7f, 1.3f, 22.9f, 1f)
            seg(25.4f, 2f, 25.8f, 0.4f)
            seg(24.2f, 1.4f, 23.9f, 0.4f)
            seg(23.6f, 1.15f, 22.6f, 0.4f)
            moveTo(27.7f, 3.6f)
            cubicTo(28f, 2.2f, 29.2f, 1.2f, 30.8f, 0.8f)
            seg(28.6f, 1.9f, 28.2f, 0.4f)
            seg(29.8f, 1.2f, 30.1f, 0.3f)
            seg(28f, 2.9f, 29.5f, 2.7f)
            seg(30.4f, 0.95f, 31.4f, 0.4f)
        }
        // The ear laid back.
        joint(J.EAR, 26f, 4.4f) {
            fill(DeerDeep, gap = 0.15f) {
                moveTo(26.3f, 3.9f)
                cubicTo(25.3f, 2.9f, 24.1f, 2.7f, 23.5f, 3.1f)
                cubicTo(24.1f, 3.9f, 25.1f, 4.5f, 25.9f, 4.7f)
                close()
            }
            fill(Rosy, Mono.Skip) {
                moveTo(25.8f, 4.1f)
                cubicTo(25.1f, 3.5f, 24.5f, 3.3f, 24.2f, 3.4f)
                cubicTo(24.7f, 3.9f, 25.2f, 4.2f, 25.7f, 4.4f)
                close()
            }
        }
        // The head: muzzle forward, a bridle, a smile, a blush, a big eye.
        fill(Deer.tint(0.06f)) {
            moveTo(24.4f, 4.8f)
            cubicTo(24.8f, 3.4f, 26.4f, 2.9f, 27.6f, 3.3f)
            lineTo(31.8f, 4.9f)
            cubicTo(32.9f, 5.3f, 33.1f, 6.6f, 32.2f, 7.1f)
            lineTo(30.3f, 7.9f)
            cubicTo(29.1f, 8.3f, 28.1f, 7.7f, 27.7f, 6.9f)
            lineTo(25.6f, 6.2f)
            close()
        }
        fill(DeerLight, Mono.Skip) { oval(31.1f, 6.3f, 1.5f, 0.95f) }
        line(Hoof, 0.15f, Mono.Skip) {
            moveTo(30.4f, 7.15f)
            cubicTo(30.9f, 7.5f, 31.5f, 7.45f, 31.9f, 7.1f)
        }
        fill(Rosy.copy(alpha = 0.7f), Mono.Skip) { oval(29.4f, 6.4f, 0.55f, 0.38f) }
        line(Harness, 0.28f, Mono.Skip) {
            moveTo(30.1f, 4.75f)
            lineTo(30.6f, 7.6f)
            moveTo(30.3f, 5.9f)
            lineTo(27.2f, 4.1f)
        }
        fill(Gold, Mono.Skip) { circle(30.32f, 5.9f, 0.22f) }
        joint(J.EYE, 28.55f, 4.95f) {
            fill(Color.White, Mono.Cut) { circle(28.55f, 4.95f, 0.48f) }
            joint(J.PUPIL, 28.65f, 4.95f) {
                fill(Eye, Mono.Skip) { circle(28.68f, 4.98f, 0.31f) }
                fill(Color.White, Mono.Skip) { circle(28.78f, 4.86f, 0.1f) }
            }
            line(Eye, 0.12f, Mono.Skip) {
                seg(28.25f, 4.5f, 28.05f, 4.2f)
                seg(28.55f, 4.45f, 28.5f, 4.1f)
                seg(28.85f, 4.5f, 28.95f, 4.2f)
            }
        }
        if (leads) {
            // The red nose, glowing, in its colour even in the silhouette.
            fill(CandyRed.copy(alpha = 0.3f), Mono.Own) { circle(32.5f, 5.9f, 2.1f) }
            fill(CandyRed, Mono.Own) { circle(32.5f, 5.9f, 0.95f) }
            fill(Color.White.copy(alpha = 0.75f), Mono.Own) { circle(32.2f, 5.6f, 0.3f) }
        } else {
            fill(Hoof) { circle(32.4f, 5.95f, 0.65f) }
            fill(Color.White.copy(alpha = 0.5f), Mono.Skip) { circle(32.2f, 5.75f, 0.18f) }
        }
    }
    // The collar with three bells.
    fill(Harness, Mono.Cut) { poly(21.8f, 9.6f, 22.6f, 8.5f, 27f, 10.3f, 26.4f, 11.5f) }
    line(Gold, 0.2f, Mono.Skip) { seg(22.1f, 9.25f, 26.7f, 11.1f) }
    fill(Gold, gap = 0.15f) {
        circle(23f, 10f, 0.5f)
        circle(24.4f, 10.65f, 0.5f)
        circle(25.8f, 11.3f, 0.5f)
    }
    line(GoldDeep, 0.1f, Mono.Skip) {
        seg(22.75f, 10.2f, 23.25f, 10.2f)
        seg(24.15f, 10.85f, 24.65f, 10.85f)
        seg(25.55f, 11.5f, 26.05f, 11.5f)
    }
    if (!leads) {
        // The traces going on to the lead reindeer.
        line(Leather, 0.45f) { seg(26.8f, 11f, SleighTrain.WIDE, SleighTrain.REINS_Y) }
    }
}

/**
 * A bullfinch, facing right, its feet at (0, 0): a round grey back, a rosy breast and
 * cheeks, a black cap, wings and tail, a stubby beak.
 */
private fun bullfinch() = figure {
    root(0f, -1.6f)
    joint(J.BIRD_TAIL, -1.2f, -1.3f) {
        fill(BirdBlack, gap = 0.12f) { poly(-1.1f, -1.7f, -3f, -1.15f, -3f, -0.55f, -1f, -0.9f) }
    }
    joint(J.BIRD_LEGS, 0f, -0.45f) {
        line(Color(0xFF5B3A29), 0.2f) {
            seg(-0.35f, -0.5f, -0.4f, 0f)
            seg(0.25f, -0.5f, 0.3f, 0f)
            seg(-0.7f, 0f, 0f, 0f)
            seg(-0.1f, 0f, 0.6f, 0f)
        }
    }
    fill(BirdGrey, gap = 0.15f) { oval(0f, -1.6f, 1.55f, 1.25f) }
    fill(BirdRed, Mono.Skip) { oval(0.35f, -1.3f, 1.2f, 0.98f) }
    fill(Color.White, Mono.Skip) { oval(-0.75f, -0.6f, 0.55f, 0.28f) }
    joint(J.BIRD_WING, -0.2f, -2f) {
        fill(BirdBlack, gap = 0.12f) {
            moveTo(-0.1f, -2.35f)
            cubicTo(-1f, -2.65f, -2.15f, -2f, -2.35f, -1.15f)
            cubicTo(-1.6f, -0.95f, -0.6f, -1.15f, -0.1f, -1.6f)
            close()
        }
        line(Color(0xFFD9DEE6), 0.22f, Mono.Cut) { seg(-0.45f, -1.95f, -1.75f, -1.55f) }
    }
    joint(J.BIRD_HEAD, 0.6f, -2.5f) {
        fill(BirdRed, gap = 0.12f) { circle(0.8f, -3f, 0.95f) }
        fill(BirdBlack, Mono.Skip) {
            moveTo(-0.15f, -3.25f)
            cubicTo(-0.05f, -4.2f, 1.5f, -4.4f, 1.75f, -3.2f)
            lineTo(1.35f, -3.05f)
            cubicTo(0.9f, -3.45f, 0.3f, -3.45f, -0.15f, -3.05f)
            close()
        }
        fill(BirdBlack) { poly(1.6f, -3.2f, 2.3f, -2.88f, 1.6f, -2.6f) }
        joint(J.BIRD_EYE, 1.1f, -2.95f) {
            fill(BirdBlack, Mono.Cut) { circle(1.1f, -2.95f, 0.2f) }
            fill(Color.White, Mono.Skip) { circle(1.15f, -3.01f, 0.07f) }
        }
    }
}

// ------------------------------------------------------------------------------ drawing

private val strokeRound = Stroke(width = 1f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** The joints' places just now, for one figure at a time (drawing is on one thread). */
private class Placed {
    val a = FloatArray(J.COUNT)
    val b = FloatArray(J.COUNT)
    val c = FloatArray(J.COUNT)
    val d = FloatArray(J.COUNT)
    val e = FloatArray(J.COUNT)
    val f = FloatArray(J.COUNT)
    val still = BooleanArray(J.COUNT)
    val matrix = Array(J.COUNT) { Matrix() }

    fun place(fig: FigureData, pose: FloatArray) {
        for (j in 0 until J.COUNT) {
            val k = j * 5
            val turn = pose[k]
            val dx = pose[k + 1]
            val dy = pose[k + 2]
            val sx = pose[k + 3]
            val sy = pose[k + 4]
            val px = fig.pivotX[j]
            val py = fig.pivotY[j]
            val r = turn * (PI / 180.0).toFloat()
            val co = cos(r)
            val si = sin(r)
            val la = co * sx
            val lb = si * sx
            val lc = -si * sy
            val ld = co * sy
            val le = px + dx - (la * px + lc * py)
            val lf = py + dy - (lb * px + ld * py)
            val p = fig.parent[j]
            if (j == J.ROOT || p < 0) {
                a[j] = la; b[j] = lb; c[j] = lc; d[j] = ld; e[j] = le; f[j] = lf
            } else {
                a[j] = a[p] * la + c[p] * lb
                b[j] = b[p] * la + d[p] * lb
                c[j] = a[p] * lc + c[p] * ld
                d[j] = b[p] * lc + d[p] * ld
                e[j] = a[p] * le + c[p] * lf + e[p]
                f[j] = b[p] * le + d[p] * lf + f[p]
            }
            still[j] = a[j] == 1f && b[j] == 0f && c[j] == 0f && d[j] == 1f && e[j] == 0f && f[j] == 0f
            val v = matrix[j].values
            v[0] = a[j]; v[1] = b[j]; v[4] = c[j]; v[5] = d[j]; v[12] = e[j]; v[13] = f[j]
        }
    }

    fun x(j: Int, px: Float, py: Float) = a[j] * px + c[j] * py + e[j]
    fun y(j: Int, px: Float, py: Float) = b[j] * px + d[j] * py + f[j]
}

private val placed = Placed()
private val neighbour = Placed()

private fun DrawScope.part(p: Part, coloured: Boolean, plain: Color, alpha: Float = 1f) {
    val own = coloured || p.mono == Mono.Own
    if (!own && p.mono == Mono.Skip) return
    val color = (if (own) p.color ?: return else plain).let { if (alpha < 1f) it.copy(alpha = it.alpha * alpha) else it }
    val effect = if (p.dash > 0f) PathEffect.dashPathEffect(floatArrayOf(p.dash, p.dash)) else null
    val style = if (p.stroke > 0f) Stroke(p.stroke, cap = strokeRound.cap, join = strokeRound.join, pathEffect = effect) else Fill
    if (!own && p.mono == Mono.Cut) {
        drawPath(p.path, Color.Black, style = style, blendMode = BlendMode.Clear)
        return
    }
    if (p.gap > 0f) {
        val ring = Stroke(p.stroke + 2f * p.gap, cap = strokeRound.cap, join = strokeRound.join)
        if (own) drawPath(p.path, color.shade(0.45f).copy(alpha = color.alpha), style = ring)
        else drawPath(p.path, Color.Black, style = ring, blendMode = BlendMode.Clear)
    }
    drawPath(p.path, color, style = style)
}

private fun DrawScope.parts(fig: FigureData, at: Placed, coloured: Boolean, plain: Color, alpha: Float = 1f) {
    for (p in fig.parts) {
        if (at.still[p.joint]) part(p, coloured, plain, alpha)
        else withTransform({ transform(at.matrix[p.joint]) }) { part(p, coloured, plain, alpha) }
    }
}

/**
 * Draws tab [index] of the train in a tab [width] by [height] (pixels), the figure at most
 * [tallest] tall in the middle of it, posed as [pose]: the joints to the neighbours, the
 * bullfinch wherever it is, and, while the train goes, the trees and the wind going by and
 * the snow thrown up behind. From [colourFrom] to [colourTo] along x it is in its colours,
 * elsewhere a silhouette in [plain]. The cut-out details clear what is under them, so this
 * is drawn on a layer of its own (the tab's).
 */
fun DrawScope.drawSleighTab(
    index: Int,
    width: Float,
    height: Float,
    tallest: Float,
    plain: Color,
    colourFrom: Float,
    colourTo: Float,
    pose: TrainPose = TrainPose.Rest,
) {
    val count = SleighTrain.count
    val fig = SleighTrain.figures[index]
    val tall = SleighTrain.figureHeight(width, tallest)
    val unit = tall / SleighTrain.TALL
    val wide = SleighTrain.WIDE * unit
    val left = (width - wide) / 2f
    val top = (height - tall) / 2f
    placed.place(fig, pose.joints[index])

    /** Where a point of figure [i] (grid units, after its root moves) is in this tab. */
    fun anchor(i: Int, gx: Float, gy: Float, out: FloatArray) {
        val at = if (i == index) placed else neighbour.also { it.place(SleighTrain.figures[i], pose.joints[i]) }
        out[0] = left + (i - index) * width + at.x(J.ROOT, gx, gy) * unit
        out[1] = top + at.y(J.ROOT, gx, gy) * unit
    }

    val from = FloatArray(2)
    val to = FloatArray(2)
    fun hitch(coloured: Boolean) {
        for (joint in listOf(index - 1, index)) {
            if (joint < 0 || joint >= count - 1) continue
            val y = if (joint == 0) SleighTrain.COUPLING_Y else SleighTrain.REINS_Y
            anchor(joint, SleighTrain.WIDE, y, from)
            anchor(joint + 1, 0f, y, to)
            if (joint == 0) {
                drawLine(if (coloured) Iron else plain, Offset(from[0], from[1]), Offset(to[0], to[1]), strokeWidth = 0.9f * unit)
                continue
            }
            // A rope sagging between the two figures, less as the train pulls it taut.
            val gap = to[0] - from[0]
            val sag = gap * (0.03f + 0.09f * pose.reinSlack)
            val path = Path()
            for (k in 0..12) {
                val t = k / 12f
                val x = from[0] + (to[0] - from[0]) * t
                val yy = from[1] + (to[1] - from[1]) * t + 4f * sag * t * (1f - t)
                if (k == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            drawPath(path, if (coloured) Leather else plain, style = Stroke(width = 0.45f * unit, cap = StrokeCap.Round))
        }
    }

    fun reins(coloured: Boolean) {
        // From Santa's mitten over the front of the sleigh to the first reindeer.
        val mx = placed.x(J.ARM, SleighTrain.MITTEN.x, SleighTrain.MITTEN.y)
        val my = placed.y(J.ARM, SleighTrain.MITTEN.x, SleighTrain.MITTEN.y)
        val ex = placed.x(J.ROOT, SleighTrain.WIDE, SleighTrain.REINS_Y)
        val ey = placed.y(J.ROOT, SleighTrain.WIDE, SleighTrain.REINS_Y)
        val sag = 0.25f + 0.9f * pose.reinSlack
        val path = Path()
        for (k in 0..16) {
            val t = k / 16f
            var x = mx + (ex - mx) * t
            var y = my + (ey - my) * t + 4f * sag * t * (1f - t)
            // Santa's flick runs along the reins.
            if (pose.reinFlick != 0f) y += pose.reinFlick * sin(t * 9f - pose.reinPhase) * (1f - t)
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val color = if (coloured) Leather else plain
        if (!coloured) drawPath(path, Color.Black, style = Stroke(0.95f, cap = StrokeCap.Round), blendMode = BlendMode.Clear)
        else drawPath(path, Leather.shade(0.45f), style = Stroke(0.95f, cap = StrokeCap.Round))
        drawPath(path, color, style = Stroke(0.45f, cap = StrokeCap.Round))
    }

    fun puffs(coloured: Boolean) {
        // Snow thrown up behind the runners or the hind hooves.
        val sp = pose.speed
        if (sp < 0.05f) return
        val (x0, y0) = if (index < 2) 1.2f to 21.9f else 5f to 22.3f
        val n = 7
        for (k in 0 until n) {
            val life = ((pose.time * (1.6f + 1.4f * sp) + k / n.toFloat()) % 1f)
            val seed = floor(pose.time * (1.6f + 1.4f * sp) + k / n.toFloat()).toInt() * 7 + k
            val drift = 0.6f + 0.8f * hash01(index * 31 + 5, seed)
            val x = x0 - life * 5.5f * drift
            val y = y0 - life * 1.4f * drift - sin(life * PI.toFloat()) * 0.8f
            val r = (0.3f + life * 0.9f) * (0.7f + 0.5f * hash01(index * 31 + 9, seed))
            val a = (1f - life) * 0.65f * min(1f, sp * 2f)
            drawCircle(if (coloured) SnowWhite.copy(alpha = a) else plain.copy(alpha = a * 0.6f), r, Offset(x, y))
        }
    }

    fun bird(coloured: Boolean) {
        if (pose.birdAlpha <= 0.01f) return
        // The bird's feet in this tab: from the trailer's grid (tab 0) to here.
        val bx = left - index * width + pose.birdX * unit
        val by = top + pose.birdY * unit
        val s = pose.birdScale * BIRD_SIZE * unit
        if (bx < -6f * s || bx > width + 6f * s) return
        neighbour.place(SleighTrain.bird, pose.joints[TrainPose.BIRD])
        translate(bx, by) {
            scale(s * pose.birdFacing, s, pivot = Offset.Zero) {
                parts(SleighTrain.bird, neighbour, coloured, plain, pose.birdAlpha)
            }
        }
    }

    fun scenery(coloured: Boolean) {
        val sp = pose.speed
        if (sp < 0.02f) return
        val fade = min(1f, sp * 1.6f)
        // Trees going by behind the train, near ones faster, far ones slower and smaller;
        // drawn behind what is there already.
        for (layer in 0..1) {
            val far = layer == 1
            val cell = if (far) 1.5f else 2.3f
            val run = pose.distance * (if (far) 0.55f else 1f)
            val k0 = floor((run - 1.2f) / cell).toInt()
            val k1 = floor((run + count + 1.2f) / cell).toInt()
            for (k in k0..k1) {
                if (hash01(if (far) 61 else 67, k) > (if (far) 0.55f else 0.5f)) continue
                val at = k * cell + hash01(if (far) 71 else 73, k) * cell * 0.6f
                // In tab widths from tab 0's left edge: it comes in on the right.
                val barX = at - run + 0.4f
                val x = (barX - index) * width
                val size = (if (far) 9f else 15f) * (0.8f + 0.35f * hash01(79, k + layer * 1000)) * unit
                if (x < -size || x > width + size) continue
                val base = top + (if (far) 20.4f else SleighTrain.GROUND) * unit
                val a = fade * (if (far) 0.45f else 0.8f)
                tree(x, base, size, coloured, plain, a, far)
            }
        }
        // The wind: thin streaks rushing by.
        val windRun = pose.distance * 2.4f
        val cell = 0.45f
        val k0 = floor((windRun - 1f) / cell).toInt()
        val k1 = floor((windRun + count + 1f) / cell).toInt()
        for (k in k0..k1) {
            if (hash01(83, k) > 0.42f) continue
            val at = k * cell + hash01(89, k) * cell
            val barX = at - windRun + 0.3f
            val len = (0.12f + 0.18f * hash01(97, k)) * width
            val x = (barX - index) * width
            if (x + len < 0f || x > width) continue
            val y = top + (3f + 15f * hash01(101, k)) * unit
            // Each streak comes and goes as it passes.
            val life = ((barX + 0.3f) / (count + 0.6f)).coerceIn(0f, 1f)
            val a = fade * 0.4f * sin(life * PI.toFloat())
            drawLine(plain.copy(alpha = a), Offset(x, y), Offset(x + len, y), strokeWidth = 0.22f * unit, cap = StrokeCap.Round, blendMode = BlendMode.DstOver)
        }
    }

    fun pass(coloured: Boolean) {
        clipRect(left = 0f, top = -height, right = width, bottom = height * 2f) {
            hitch(coloured)
            translate(left, top) {
                scale(unit, unit, pivot = Offset.Zero) {
                    parts(fig, placed, coloured, plain)
                    if (index == 1) reins(coloured)
                    if (index == 3 && pose.noseGlow > 0f) {
                        // The lead's nose glowing brighter (it is looking at it).
                        val nx = placed.x(J.NECK, 32.5f, 5.9f)
                        val ny = placed.y(J.NECK, 32.5f, 5.9f)
                        drawCircle(CandyRed.copy(alpha = 0.32f * pose.noseGlow), 2.2f + 1.3f * pose.noseGlow, Offset(nx, ny))
                        drawCircle(Color(0xFFFF6B7A).copy(alpha = 0.8f * pose.noseGlow), 0.95f, Offset(nx, ny))
                        drawCircle(Color.White.copy(alpha = 0.85f * pose.noseGlow), 0.38f, Offset(nx - 0.3f, ny - 0.3f))
                    }
                    puffs(coloured)
                }
            }
            bird(coloured)
            scenery(coloured)
        }
    }

    val reach = height
    when {
        colourTo <= 0f || colourFrom >= width -> pass(coloured = false)
        colourFrom <= 0f && colourTo >= width -> pass(coloured = true)
        else -> {
            clipRect(left = -width, top = -reach, right = colourFrom, bottom = height + reach) { pass(coloured = false) }
            clipRect(left = colourTo, top = -reach, right = width * 2f, bottom = height + reach) { pass(coloured = false) }
            clipRect(left = colourFrom, top = -reach, right = colourTo, bottom = height + reach) { pass(coloured = true) }
        }
    }
}

/** How large the bullfinch is against the grid of the figures. */
private const val BIRD_SIZE = 1.05f

/** A fir tree: a trunk and three tiers, snow on each in colour; drawn behind the train. */
private fun DrawScope.tree(x: Float, base: Float, size: Float, coloured: Boolean, plain: Color, alpha: Float, far: Boolean) {
    val w = size * 0.62f
    val body = Path().apply {
        rect(x - size * 0.05f, base - size * 0.14f, size * 0.1f, size * 0.14f)
        for (t in 0 until 3) {
            val y0 = base - size * (0.12f + 0.26f * t)
            val half = w / 2f * (1f - 0.24f * t)
            moveTo(x - half, y0)
            lineTo(x + half, y0)
            lineTo(x, y0 - size * 0.42f)
            close()
        }
    }
    if (!coloured) {
        drawPath(body, plain.copy(alpha = alpha * (if (far) 0.25f else 0.35f)), blendMode = BlendMode.DstOver)
        return
    }
    val snow = Path().apply {
        for (t in 0 until 3) {
            val y0 = base - size * (0.12f + 0.26f * t)
            val half = w / 2f * (1f - 0.24f * t)
            val tip = y0 - size * 0.42f
            moveTo(x, tip)
            lineTo(x + half * 0.42f, tip + size * 0.17f)
            lineTo(x + half * 0.15f, tip + size * 0.14f)
            lineTo(x - half * 0.2f, tip + size * 0.19f)
            lineTo(x - half * 0.45f, tip + size * 0.16f)
            close()
        }
    }
    // Drawn behind: the snow first, then the tree under it.
    drawPath(snow, SnowWhite.copy(alpha = alpha), blendMode = BlendMode.DstOver)
    drawPath(body, (if (far) Fir.tint(0.25f) else Fir).copy(alpha = alpha), blendMode = BlendMode.DstOver)
    drawRect(FirTrunk.copy(alpha = alpha), Offset(x - size * 0.05f, base - size * 0.14f), androidx.compose.ui.geometry.Size(size * 0.1f, size * 0.14f), blendMode = BlendMode.DstOver)
}

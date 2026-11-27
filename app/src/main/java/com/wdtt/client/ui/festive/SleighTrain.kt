package com.wdtt.client.ui.festive

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    /** At night an owl sits there instead of the bullfinch. */
    internal val owl: FigureData by lazy { owl() }

    const val count = 4

    /** The figure's height in a tab [width] wide, given the tallest it may be. */
    fun figureHeight(width: Float, tallest: Float): Float = minOf(tallest, width * FILL * TALL / WIDE)

    /**
     * Where the figures stand, pixels from the top of a bar [barWidth] by [barHeight] with
     * the tabs [padX], [padY] inside it: the snow there comes up to the runners and hooves,
     * a little over their bottoms.
     */
    fun groundIn(barWidth: Float, barHeight: Float, padX: Float, padY: Float, tallest: Float): Float {
        val slot = (barWidth - 2f * padX) / count
        val tabHeight = barHeight - 2f * padY
        val tall = figureHeight(slot, tallest)
        return padY + (tabHeight - tall) / 2f + tall * (GROUND - 0.35f) / TALL
    }

    internal const val COUPLING_Y = 19.6f
    internal const val REINS_Y = 12f
    /** Where the bullfinch sits on the trailer (its feet on the lantern's arm), grid units. */
    internal val PERCH = Offset(31.9f, 9.8f)
    /** Where the reins leave Santa's mitten, in the hand's own place. */
    internal val MITTEN = Offset(19.5f, 11.8f)
    /** Where Santa lays the reins down on the side of the sleigh. */
    internal val REINS_LAID = Offset(19.5f, 12.9f)
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
    const val HAND = 7
    const val CLOCK = 8
    // A reindeer.
    const val NECK = 1
    const val DEER_HEAD = 2
    const val EAR = 3
    const val EYE = 4
    const val PUPIL = 5
    const val TAIL = 6
    const val HIND_FAR = 7
    const val HOCK_FAR = 8
    const val HIND_NEAR = 9
    const val HOCK_NEAR = 10
    const val FORE_FAR = 11
    const val KNEE_FAR = 12
    const val FORE_NEAR = 13
    const val KNEE_NEAR = 14
    const val SCARF = 15
    const val FETLOCK_HIND_FAR = 16
    const val FETLOCK_FORE_FAR = 17
    const val FETLOCK_HIND_NEAR = 18
    const val FETLOCK_FORE_NEAR = 19
    // The bullfinch.
    const val BIRD_HEAD = 1
    const val BIRD_WING = 2
    const val BIRD_TAIL = 3
    const val BIRD_EYE = 4
    const val BIRD_LEGS = 5

    const val COUNT = 20
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
    /** How much the figures are in their colours outside the droplet (all of them, going). */
    var colour = 0f
    /** Santa's big clock is out (it is drawn only then). */
    var clock = 0f
    /** The reins lie on the side of the sleigh instead of in Santa's mitten. */
    var reinsLaid = 0f
    /** Snow thrown up by the hooves and runners: figure, place (grid units), size, age 0..1. */
    internal val dustFigure = IntArray(MAX_DUST) { -1 }
    internal val dustX = FloatArray(MAX_DUST)
    internal val dustY = FloatArray(MAX_DUST)
    internal val dustSize = FloatArray(MAX_DUST)
    internal val dustAge = FloatArray(MAX_DUST)
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
        colour = 0f
        clock = 0f
        reinsLaid = 0f
        dustFigure.fill(-1)
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
        internal const val MAX_DUST = 96
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
 * colour, so it stands apart from them; [outline] wide around it a dark edge in both (so it
 * stands apart from the snow it is on). It moves with [joint].
 */
internal class Part(
    val path: Path,
    val color: Color?,
    val mono: Mono,
    val stroke: Float,
    val gap: Float,
    val dash: Float,
    val joint: Int,
    val outline: Float = 0f,
    /** A light (the lantern, the bulbs, the red nose, an owl's eyes): bright at night too. */
    val lit: Boolean = false,
    /**
     * Parts of one limb (a leg's segments): drawn as one piece, outlined round the whole of
     * it and not at each joint, without light and shade of their own (they would mark the
     * joints like a wooden toy's).
     */
    val group: Int = -1,
    /** In a group, but with light and shade of its own all the same (a reindeer's body). */
    val edges: Boolean = false,
    /**
     * The shape its light and shade are taken from, when not its own: where a part is set
     * into another (a neck into the body) its edge there is not an edge and gets none.
     */
    val edgeShape: Path? = null,
) {
    /** Its colour by moonlight. */
    val nightColor: Color? by lazy { color?.let { if (lit) it else moon(it) } }

    /**
     * The edges of the part the light falls on and the side away from it, by day (the sun
     * up on the left) and by night (the moon up on the right): the part less itself moved
     * a little away from the light, or towards it. Only for parts large enough to show them.
     */
    val dayLit: Path? by lazy { edge(LIGHT_X, LIGHT_Y) }
    val dayShaded: Path? by lazy { edge(-SHADE_X, -SHADE_Y) }
    val nightLit: Path? by lazy { edge(-LIGHT_X, LIGHT_Y) }
    val nightShaded: Path? by lazy { edge(SHADE_X, -SHADE_Y) }

    private fun edge(dx: Float, dy: Float): Path? {
        if (stroke > 0f || color == null || mono == Mono.Cut || lit || (group >= 0 && !edges)) return null
        val b = path.getBounds()
        if (b.width < 1.4f && b.height < 1.4f) return null
        val base = edgeShape ?: path
        val moved = Path().apply { addPath(base, Offset(dx, dy)) }
        val band = Path().apply { op(base, moved, androidx.compose.ui.graphics.PathOperation.Difference) }
        return if (edgeShape == null) band else Path().apply { op(band, path, androidx.compose.ui.graphics.PathOperation.Intersect) }
    }
}

/** How far the lit edge reaches in from the side of the light, and the shaded one from the other side, grid units. */
private const val LIGHT_X = 0.32f
private const val LIGHT_Y = 0.28f
private const val SHADE_X = 0.42f
private const val SHADE_Y = 0.36f

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
private val ClockFace = Color(0xFFFFF4DC)
/** The drift going by under the train: its shade by day, its top and shade by moonlight. */
private val GroundShade = Color(0xFFC3D1E6)
private val NightGroundTop = Color(0xFFCFDCF5)
private val NightGroundShade = Color(0xFF8DA2CA)
private val ClockInk = Color(0xFF2A1C14)
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
private val OwlBrown = Color(0xFF8A6A4C)
private val OwlLight = Color(0xFFE2CFAE)
private val OwlDark = Color(0xFF4A3628)
private val OwlIris = Color(0xFFFFB02E)
private val OwlBeak = Color(0xFF3A2A20)

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

    private var groupNow = -1

    /** The parts made in [body] are one limb (see [Part.group]). */
    fun group(id: Int, body: Figure.() -> Unit) {
        val was = groupNow
        groupNow = id
        body()
        groupNow = was
    }

    fun fill(color: Color?, mono: Mono = Mono.Fill, gap: Float = 0f, outline: Float = 0f, lit: Boolean = false, edges: Boolean = false, edgeShape: (Path.() -> Unit)? = null, body: Path.() -> Unit) {
        parts += Part(Path().apply(body), color, mono, 0f, gap, 0f, at, outline, lit, groupNow, edges, edgeShape?.let { Path().apply(it) })
    }

    fun line(color: Color?, width: Float, mono: Mono = Mono.Fill, gap: Float = 0f, dash: Float = 0f, body: Path.() -> Unit) {
        parts += Part(Path().apply(body), color, mono, width, gap, dash, at, group = groupNow)
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
    // A dark edge, so the runner stands apart from the snow it slides on.
    fill(Gold, outline = 0.2f) {
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
    fill(Gold, outline = 0.2f) {
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
        fill(Lamp, Mono.Cut, lit = true) { rect(32.3f, 11.95f, 1f, 1.6f) }
        fill(Flame, Mono.Skip, lit = true) {
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
        fill(Bulbs[k % Bulbs.size], Mono.Cut, lit = true) { oval(x, 17.65f, 0.33f, 0.42f) }
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
        // The arm, its elbow kept in at his side, a fur cuff, a mitten on the reins; drawn
        // before the head, so it comes out from under the beard. The cuff and mitten on a
        // joint of their own: when the arm reaches up and straightens, only the sleeve grows.
        joint(J.ARM, 15.6f, 10.6f) {
            fill(CoatRed, gap = 0.3f) {
                moveTo(15f, 9.8f)
                cubicTo(16.3f, 10f, 17.3f, 10.45f, 18.1f, 10.95f)
                lineTo(18.15f, 12.45f)
                cubicTo(17.1f, 12.6f, 16.2f, 12.7f, 15.4f, 12.85f)
                close()
            }
            joint(J.HAND, 17.6f, 11.7f) {
                fill(Fur, gap = 0.15f) { round(17.6f, 10.75f, 1.1f, 1.9f, 0.4f) }
                fill(Mitten, gap = 0.2f) {
                    circle(19.2f, 11.7f, 0.95f)
                    circle(19.4f, 10.8f, 0.4f)
                }
                line(Mitten.tint(0.4f), 0.15f, Mono.Skip) { seg(18.5f, 11.3f, 19.8f, 11.5f) }
                // A big round wall clock, out only while he looks at it, held from behind (the
                // mitten behind it hardly shows); turned to him, so its face is seen from the
                // side: a narrow cream oval with the hands (five to midnight) in a brass rim,
                // the depth of the case behind it, a ring to hang it by.
                joint(J.CLOCK, 19.2f, 11.7f) {
                    val cx = 18.75f
                    val cy = 11.7f
                    // How narrow the round face looks from the side.
                    val k = 0.42f
                    line(GoldDeep, 0.2f) { oval(cx + 0.15f, cy - 2.32f, 0.13f, 0.3f) }
                    fill(GoldDeep, gap = 0.2f) {
                        oval(cx + 0.5f, cy, 2.05f * k, 2.05f)
                        rect(cx, cy - 2.05f, 0.5f, 4.1f)
                    }
                    fill(Gold, gap = 0.15f) { oval(cx, cy, 2.05f * k, 2.05f) }
                    fill(ClockFace, Mono.Cut) { oval(cx, cy, 1.62f * k, 1.62f) }
                    fill(ClockInk, Mono.Skip) {
                        for (h in 0 until 12) {
                            val a = h * PI.toFloat() / 6f
                            val r = if (h % 3 == 0) 0.13f else 0.07f
                            oval(cx + 1.3f * k * sin(a), cy - 1.3f * cos(a), r * 0.6f, r)
                        }
                    }
                    // The hour hand just short of twelve, the minute hand at eleven.
                    val hour = (-2.5f) * PI.toFloat() / 180f
                    val minute = (-30f) * PI.toFloat() / 180f
                    line(ClockInk, 0.22f) { seg(cx, cy, cx + 0.85f * k * sin(hour), cy - 0.85f * cos(hour)) }
                    line(ClockInk, 0.14f) { seg(cx, cy, cx + 1.28f * k * sin(minute), cy - 1.28f * cos(minute)) }
                    fill(CandyRed, Mono.Own) { oval(cx, cy, 0.1f, 0.16f) }
                    line(Color.White.copy(alpha = 0.7f), 0.12f, Mono.Skip) {
                        moveTo(cx - 0.55f, cy - 0.5f)
                        cubicTo(cx - 0.55f, cy - 1f, cx - 0.4f, cy - 1.3f, cx - 0.2f, cy - 1.45f)
                    }
                }
            }
        }
        // A fur collar showing beside the beard.
        fill(Fur, gap = 0.15f) {
            moveTo(11.4f, 10.6f)
            cubicTo(11.6f, 9.7f, 12.4f, 9.3f, 13.2f, 9.4f)
            lineTo(13f, 10.6f)
            cubicTo(12.4f, 10.5f, 11.9f, 10.7f, 11.4f, 10.6f)
            close()
        }
        // The head, turned a little towards us: a round face with rosy cheeks, two bright
        // eyes behind round spectacles, bushy brows, a round red nose, a curly moustache
        // and a big curly beard, the cap.
        joint(J.HEAD, 13.9f, 9.3f) {
            // White hair behind the ear.
            fill(Fur, gap = 0.2f) {
                moveTo(12.5f, 5.9f)
                cubicTo(11.6f, 6.3f, 11.4f, 7.8f, 11.9f, 8.7f)
                cubicTo(12.2f, 9.2f, 12.8f, 9f, 12.95f, 8.4f)
                lineTo(13.1f, 6.1f)
                close()
            }
            fill(Skin) { oval(14.45f, 7.15f, 1.95f, 1.85f) }
            fill(Skin.shade(0.06f), gap = 0.12f) { oval(12.9f, 7.35f, 0.42f, 0.6f) }
            fill(Rosy, Mono.Skip) { oval(12.93f, 7.38f, 0.2f, 0.34f) }
            fill(Rosy.copy(alpha = 0.85f), Mono.Skip) {
                oval(15.2f, 7.9f, 0.55f, 0.36f)
                oval(16.45f, 7.85f, 0.25f, 0.2f)
            }
            joint(J.SANTA_EYE, 15.2f, 6.75f) {
                fill(Eye, Mono.Cut) {
                    oval(15.2f, 6.75f, 0.2f, 0.28f)
                    oval(16.12f, 6.7f, 0.16f, 0.24f)
                }
                fill(Color.White, Mono.Skip) {
                    circle(15.26f, 6.65f, 0.075f)
                    circle(16.16f, 6.61f, 0.06f)
                }
            }
            line(Gold.shade(0.1f), 0.13f, Mono.Skip) {
                circle(15.2f, 6.8f, 0.48f)
                circle(16.12f, 6.75f, 0.36f)
                seg(15.68f, 6.78f, 15.76f, 6.76f)
                seg(14.72f, 6.72f, 13.3f, 6.45f)
            }
            fill(Fur, Mono.Skip) {
                moveTo(14.5f, 6.25f)
                cubicTo(14.75f, 5.7f, 15.5f, 5.65f, 15.8f, 6.05f)
                cubicTo(15.4f, 5.93f, 14.9f, 6.02f, 14.5f, 6.25f)
                close()
                moveTo(15.85f, 6.1f)
                cubicTo(16f, 5.78f, 16.42f, 5.78f, 16.55f, 6f)
                cubicTo(16.32f, 5.96f, 16.06f, 6f, 15.85f, 6.1f)
                close()
            }
            // The beard: curls all round its edge.
            fill(Fur, gap = 0.3f) {
                moveTo(12.6f, 7.4f)
                cubicTo(12.2f, 9f, 12.35f, 10.4f, 12.95f, 11.2f)
                cubicTo(13.25f, 11.95f, 13.9f, 12.05f, 14.1f, 11.7f)
                cubicTo(14.3f, 12.55f, 15.15f, 12.55f, 15.3f, 11.8f)
                cubicTo(15.75f, 12.1f, 16.35f, 11.6f, 16.2f, 11f)
                cubicTo(16.85f, 10.6f, 16.95f, 9.6f, 16.5f, 9.1f)
                cubicTo(16.9f, 8.5f, 16.6f, 7.8f, 16.1f, 7.95f)
                cubicTo(15.4f, 8.55f, 14f, 8.5f, 13.35f, 7.7f)
                close()
            }
            line(Fur.shade(0.14f), 0.14f, Mono.Skip) {
                moveTo(13.1f, 9.4f)
                cubicTo(13.3f, 9.9f, 13.8f, 9.95f, 13.95f, 9.55f)
                moveTo(14.5f, 10.3f)
                cubicTo(14.7f, 10.8f, 15.2f, 10.8f, 15.35f, 10.4f)
                moveTo(13.2f, 10.8f)
                cubicTo(13.4f, 11.2f, 13.8f, 11.2f, 13.95f, 10.9f)
                moveTo(15.7f, 9.5f)
                cubicTo(15.9f, 9.9f, 16.3f, 9.85f, 16.35f, 9.5f)
            }
            line(Fur.shade(0.2f), 0.12f, Mono.Cut) {
                moveTo(13.6f, 10.55f)
                cubicTo(13.9f, 11.05f, 14.4f, 11.05f, 14.6f, 10.65f)
                moveTo(15f, 10.15f)
                cubicTo(15.3f, 10.65f, 15.8f, 10.55f, 15.9f, 10.15f)
            }
            line(Belt, 0.14f, Mono.Skip) {
                moveTo(15.25f, 9.05f)
                cubicTo(15.55f, 9.3f, 15.95f, 9.25f, 16.15f, 9f)
            }
            // The moustache: two curling lobes.
            fill(Fur, gap = 0.15f) {
                moveTo(15.9f, 8.1f)
                cubicTo(15.3f, 7.9f, 14.4f, 8.2f, 14f, 8.9f)
                cubicTo(13.7f, 9.3f, 13.9f, 9.6f, 14.3f, 9.3f)
                cubicTo(14.8f, 9f, 15.4f, 8.9f, 15.9f, 8.75f)
                cubicTo(16.4f, 8.9f, 16.9f, 9.1f, 17.1f, 8.9f)
                cubicTo(17.3f, 8.6f, 17f, 8.2f, 16.5f, 8.1f)
                close()
            }
            line(Fur.shade(0.15f), 0.1f, Mono.Skip) {
                moveTo(14.6f, 8.75f)
                cubicTo(15f, 8.5f, 15.4f, 8.45f, 15.8f, 8.5f)
            }
            // The nose: round, rosy, shining.
            fill(Skin.shade(0.04f), gap = 0.15f) { circle(16.25f, 7.55f, 0.58f) }
            fill(Rosy.copy(alpha = 0.6f), Mono.Skip) { circle(16.35f, 7.65f, 0.4f) }
            fill(Color.White.copy(alpha = 0.7f), Mono.Skip) { circle(16.08f, 7.35f, 0.12f) }
            // The cap, plush, in its colours even in the silhouette: the red crown rising and
            // folding over backwards, its tip hanging down with a fluffy pompom, a thick fur
            // brim with a soft, uneven edge.
            joint(J.HAT, 14.4f, 5.2f) {
                fill(CoatRed, Mono.Own, gap = 0.12f) {
                    moveTo(12.1f, 4.7f)
                    cubicTo(12f, 3.2f, 13.3f, 1.7f, 14.9f, 1.45f)
                    cubicTo(16.3f, 1.6f, 16.95f, 3f, 16.7f, 4.6f)
                    close()
                }
                // The velvet's sheen and a fold.
                line(CoatRed.tint(0.3f), 0.22f, Mono.Skip) {
                    moveTo(15.55f, 1.95f)
                    cubicTo(16.05f, 2.5f, 16.3f, 3.3f, 16.25f, 4.2f)
                }
                line(CoatRed.shade(0.3f), 0.16f, Mono.Skip) {
                    moveTo(13.4f, 2.6f)
                    cubicTo(13.9f, 3.1f, 14.1f, 3.8f, 14.05f, 4.4f)
                }
                joint(J.HAT_TIP, 13.6f, 1.9f) {
                    fill(CoatRed, Mono.Own, gap = 0.12f) {
                        moveTo(14.7f, 1.5f)
                        cubicTo(13.2f, 1.3f, 11.75f, 1.85f, 10.95f, 2.95f)
                        lineTo(10.35f, 3.8f)
                        cubicTo(11.2f, 3.35f, 12.1f, 3.05f, 13.1f, 3.2f)
                        cubicTo(13.6f, 2.6f, 14.1f, 2f, 14.7f, 1.5f)
                        close()
                    }
                    line(CoatRed.shade(0.32f), 0.14f, Mono.Skip) {
                        moveTo(13.5f, 2.35f)
                        cubicTo(12.6f, 2.45f, 11.8f, 2.8f, 11.1f, 3.4f)
                    }
                    // The pompom: a fluffy ball.
                    fill(Fur, Mono.Own, gap = 0.15f) {
                        circle(10.25f, 4.15f, 0.78f)
                        circle(9.62f, 3.85f, 0.42f)
                        circle(10.85f, 3.7f, 0.38f)
                        circle(10.8f, 4.65f, 0.42f)
                        circle(9.75f, 4.65f, 0.4f)
                        circle(10.25f, 3.35f, 0.36f)
                    }
                }
                // The fur brim: a thick soft band, uneven at its edges.
                fill(Fur, Mono.Own, gap = 0.2f) {
                    round(11.85f, 4.45f, 5.15f, 1.35f, 0.67f)
                    for (k in 0 until 9) {
                        val x = 12.1f + k * 0.58f
                        circle(x, 4.5f + 0.08f * (k % 2), 0.34f)
                        circle(x + 0.29f, 5.78f - 0.06f * (k % 3), 0.3f)
                    }
                }
                line(Fur.shade(0.13f), 0.1f, Mono.Skip) {
                    for (k in 0 until 10) {
                        val x = 12.3f + k * 0.5f
                        moveTo(x, 4.75f)
                        cubicTo(x + 0.18f, 4.98f, x + 0.04f, 5.25f, x + 0.22f, 5.5f)
                    }
                }
            }
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
    // A garland of fir along the rim with red berries, and a sprig of holly at the front.
    line(Holly, 0.45f, Mono.Skip) {
        moveTo(3.2f, 13.75f)
        for (k in 0 until 6) {
            val x0 = 3.2f + k * 3.1f
            cubicTo(x0 + 1f, 14.45f, x0 + 2.1f, 14.5f, x0 + 3.1f, 13.85f + k * 0.04f)
        }
    }
    fill(CandyRed, Mono.Skip) {
        for (k in 0 until 6) circle(4.75f + k * 3.1f, 14.35f, 0.22f)
    }
    fill(Holly, Mono.Skip) {
        oval(23.3f, 15.2f, 0.75f, 0.34f)
        oval(24.3f, 15.55f, 0.75f, 0.34f)
    }
    fill(CandyRed, Mono.Skip) {
        circle(23.75f, 15.6f, 0.24f)
        circle(24.1f, 15.2f, 0.24f)
        circle(23.55f, 15.85f, 0.2f)
    }
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
    // A leg: the haunch and gaskin (or the forearm) on [hip], the cannon on [knee], the
    // pastern with the hoof on [fetlock]. Standing, a hind leg makes its natural zigzag.
    // The coat of the leg and its hoof are one piece in [gid] (outlined round the whole of
    // it). A segment and the round of its joint are separate shapes: drawn as one, their
    // overlap would be left empty (they wind opposite ways).
    fun leg(hip: Int, knee: Int, fetlock: Int, dx: Float, fore: Boolean, coat: Color, gap: Float, gid: Int) {
        if (fore) {
            joint(hip, 23.3f + dx, 13.2f) {
                group(gid) {
                    fill(coat, gap = gap) {
                        moveTo(21f + dx, 12.2f)
                        lineTo(25.4f + dx, 12.2f)
                        cubicTo(25.9f + dx, 13.6f, 25.2f + dx, 15f, 24.5f + dx, 15.9f)
                        cubicTo(24.2f + dx, 16.6f, 24f + dx, 17.3f, 23.95f + dx, 18f)
                        lineTo(22.85f + dx, 18f)
                        cubicTo(22.75f + dx, 17.2f, 22.5f + dx, 16.4f, 22.1f + dx, 15.8f)
                        cubicTo(21.2f + dx, 14.9f, 20.8f + dx, 13.6f, 21f + dx, 12.2f)
                        close()
                    }
                }
                joint(knee, 23.4f + dx, 18f) {
                    group(gid) {
                        fill(coat, gap = gap) { poly(22.95f + dx, 17.9f, 23.9f + dx, 17.9f, 23.78f + dx, 21f, 23.05f + dx, 21f) }
                        fill(coat, gap = gap) { circle(23.42f + dx, 18f, 0.48f) }
                    }
                    joint(fetlock, 23.4f + dx, 21f) {
                        group(gid) {
                            fill(coat, gap = gap) { poly(23.05f + dx, 20.9f, 23.75f + dx, 20.9f, 24.15f + dx, 21.7f, 23.45f + dx, 21.8f) }
                            fill(coat, gap = gap) { circle(23.41f + dx, 21f, 0.36f) }
                            fill(Hoof, outline = 0.2f) { poly(23.15f + dx, 21.6f, 24.45f + dx, 21.5f, 24.8f + dx, 22.6f, 23.1f + dx, 22.6f) }
                        }
                    }
                }
            }
        } else {
            joint(hip, 10.6f + dx, 13.4f) {
                group(gid) {
                    fill(coat, gap = gap) {
                        moveTo(8f + dx, 12.4f)
                        lineTo(12.8f + dx, 12.6f)
                        cubicTo(13.4f + dx, 13.6f, 13.1f + dx, 15f, 12.5f + dx, 15.9f)
                        cubicTo(11.9f + dx, 16.9f, 11.4f + dx, 17.8f, 11.2f + dx, 18.9f)
                        lineTo(10f + dx, 18.95f)
                        cubicTo(9.7f + dx, 17.7f, 9.3f + dx, 16.7f, 9f + dx, 15.8f)
                        cubicTo(8.5f + dx, 14.6f, 7.9f + dx, 13.4f, 8f + dx, 12.4f)
                        close()
                    }
                }
                joint(knee, 10.6f + dx, 18.9f) {
                    group(gid) {
                        fill(coat, gap = gap) { poly(10.05f + dx, 18.8f, 11.1f + dx, 18.8f, 10.95f + dx, 21f, 10.2f + dx, 21f) }
                        fill(coat, gap = gap) { circle(10.58f + dx, 18.9f, 0.53f) }
                    }
                    joint(fetlock, 10.58f + dx, 21f) {
                        group(gid) {
                            fill(coat, gap = gap) { poly(10.2f + dx, 20.9f, 10.9f + dx, 20.9f, 11.3f + dx, 21.7f, 10.6f + dx, 21.8f) }
                            fill(coat, gap = gap) { circle(10.57f + dx, 21f, 0.36f) }
                            fill(Hoof, outline = 0.2f) { poly(10.3f + dx, 21.6f, 11.6f + dx, 21.5f, 11.95f + dx, 22.6f, 10.25f + dx, 22.6f) }
                        }
                    }
                }
            }
        }
    }
    // The far legs, a shade darker, behind the body; each outlined thinly.
    leg(J.HIND_FAR, J.HOCK_FAR, J.FETLOCK_HIND_FAR, -1.6f, fore = false, coat = DeerDeep, gap = 0.18f, gid = 100)
    leg(J.FORE_FAR, J.KNEE_FAR, J.FETLOCK_FORE_FAR, 1.1f, fore = true, coat = DeerDeep, gap = 0.18f, gid = 101)
    // A short white tail.
    joint(J.TAIL, 7.2f, 10.9f) {
        fill(Cream, gap = 0.15f) {
            moveTo(7.3f, 10.6f)
            cubicTo(6.1f, 9.3f, 4.7f, 9.5f, 4.4f, 10.5f)
            cubicTo(5.2f, 10.8f, 6.1f, 11.6f, 6.9f, 12.1f)
            close()
        }
    }
    // The body, its belly lighter, a pale patch on the rump; then the near legs over it,
    // all one piece: their outline is drawn first and the body over it, so the legs come out
    // of the body (outlined only outside it) instead of being set under its edge.
    group(102) {
    // A reindeer's build, not a box: the withers high, a gentle dip in the back, the chest
    // rounded out in front of the forelegs and deep between them, the belly rising to the
    // flank, a round rump falling into the buttock.
    fill(Deer, edges = true) {
        moveTo(10.2f, 9.95f)
        cubicTo(13.6f, 10.35f, 17.6f, 10.25f, 20.4f, 9.55f)
        cubicTo(22.6f, 9f, 25.4f, 9.9f, 26.4f, 12.2f)
        cubicTo(27.1f, 13.9f, 26.6f, 15.9f, 25f, 16.9f)
        cubicTo(23.9f, 17.5f, 22.3f, 17.4f, 21f, 17f)
        cubicTo(18.4f, 16.6f, 15.4f, 16.2f, 13.4f, 15.6f)
        cubicTo(12.4f, 15.9f, 11.6f, 16.5f, 10.3f, 16.5f)
        cubicTo(8.8f, 16.5f, 7.4f, 15.6f, 7f, 13.9f)
        cubicTo(6.7f, 12.3f, 7.4f, 10.6f, 8.6f, 10.15f)
        cubicTo(9.1f, 10f, 9.6f, 9.95f, 10.2f, 9.95f)
        close()
    }
    // The pale underside from the chest to the flank, a pale patch on the rump.
    fill(DeerLight, Mono.Skip) {
        moveTo(13.5f, 15.7f)
        cubicTo(15.5f, 16.2f, 18.5f, 16.6f, 21f, 17f)
        cubicTo(22.3f, 17.4f, 23.9f, 17.5f, 25f, 16.9f)
        cubicTo(26f, 16.3f, 26.6f, 15.3f, 26.8f, 14.2f)
        cubicTo(26.3f, 15.6f, 25.2f, 16.4f, 23.6f, 16.6f)
        cubicTo(20.8f, 16.7f, 17f, 15.9f, 13.5f, 15.7f)
        close()
        oval(8.1f, 12.3f, 1.05f, 1.3f)
    }
    line(DeerDeep, 0.35f, Mono.Skip) {
        moveTo(10.5f, 10.15f)
        cubicTo(14f, 10.45f, 18f, 10.25f, 21f, 9.45f)
    }
    leg(J.HIND_NEAR, J.HOCK_NEAR, J.FETLOCK_HIND_NEAR, 0f, fore = false, coat = Deer, gap = 0.3f, gid = 102)
    leg(J.FORE_NEAR, J.KNEE_NEAR, J.FETLOCK_FORE_NEAR, 0f, fore = true, coat = Deer, gap = 0.3f, gid = 102)
    }
    // The harness: a saddle pad edged in gold with tassels, the girth strap (cut) with a
    // buckle; on the lead's pad a gold star, on the other one a sprig of holly.
    fill(Harness, Mono.Skip) { round(13.2f, 9.85f, 5.6f, 3.3f, 0.8f) }
    line(Gold, 0.3f, Mono.Skip) { round(13.35f, 10f, 5.3f, 3f, 0.7f) }
    line(Gold, 0.16f, Mono.Skip) {
        for (k in 0 until 6) seg(13.7f + k * 0.95f, 13.15f, 13.7f + k * 0.95f, 13.75f)
    }
    fill(Harness.shade(0.15f), Mono.Cut) { rect(15.6f, 9.75f, 0.9f, 6.75f) }
    fill(Gold) { rect(15.5f, 13.95f, 1.1f, 1f) }
    if (leads) {
        fill(Gold, Mono.Skip) { star(17.65f, 11.45f, 0.75f) }
    } else {
        fill(Holly, Mono.Skip) {
            oval(17.2f, 11.25f, 0.7f, 0.32f)
            oval(18.1f, 11.65f, 0.7f, 0.32f)
        }
        fill(CandyRed, Mono.Skip) {
            circle(17.6f, 11.7f, 0.22f)
            circle(17.95f, 11.3f, 0.22f)
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
        // Set into the body: no light or shade along its foot.
        fill(Deer, edgeShape = {
            moveTo(20.8f, 10.4f)
            cubicTo(22.4f, 8.4f, 23.8f, 6.4f, 24.8f, 4.8f)
            lineTo(28.2f, 5.8f)
            cubicTo(27.6f, 8.2f, 27f, 10.6f, 25.8f, 13f)
            lineTo(26f, 20f)
            lineTo(19.5f, 20f)
            close()
        }) {
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
        // The head nods on the neck.
        joint(J.DEER_HEAD, 26.2f, 5.6f) {
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
            fill(CandyRed.copy(alpha = 0.3f), Mono.Own, lit = true) { circle(32.5f, 5.9f, 2.1f) }
            fill(CandyRed, Mono.Own, lit = true) { circle(32.5f, 5.9f, 0.95f) }
            fill(Color.White.copy(alpha = 0.75f), Mono.Own, lit = true) { circle(32.2f, 5.6f, 0.3f) }
        } else {
            fill(Hoof) { circle(32.4f, 5.95f, 0.65f) }
            fill(Color.White.copy(alpha = 0.5f), Mono.Skip) { circle(32.2f, 5.75f, 0.18f) }
        }
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

/**
 * An owl, facing right, its feet at (0, 0): a round brown body with a pale speckled breast,
 * a big round head with ear tufts and a pale face, two big amber eyes, a hooked beak.
 */
private fun owl() = figure {
    root(0f, -2f)
    joint(J.BIRD_TAIL, -0.8f, -0.8f) {
        fill(OwlDark, gap = 0.12f) { poly(-0.7f, -1.2f, -1.9f, -0.3f, -1.5f, 0.05f, -0.5f, -0.6f) }
    }
    joint(J.BIRD_LEGS, 0f, -0.4f) {
        fill(OwlLight) {
            oval(-0.35f, -0.28f, 0.32f, 0.3f)
            oval(0.35f, -0.28f, 0.32f, 0.3f)
        }
        line(OwlDark, 0.12f) {
            seg(-0.62f, 0f, -0.48f, -0.16f)
            seg(-0.3f, 0.02f, -0.28f, -0.16f)
            seg(0.1f, 0f, 0.22f, -0.16f)
            seg(0.48f, 0.02f, 0.46f, -0.16f)
        }
    }
    fill(OwlBrown, gap = 0.15f) { oval(0f, -2.2f, 1.45f, 1.95f) }
    fill(OwlLight, Mono.Skip) { oval(0.42f, -1.95f, 0.95f, 1.45f) }
    line(OwlDark, 0.11f, Mono.Skip) {
        for (r in 0 until 4) for (k in 0 until 2) {
            val x = 0.15f + 0.5f * k + 0.25f * (r % 2)
            val y = -2.95f + 0.55f * r
            moveTo(x - 0.15f, y - 0.1f)
            lineTo(x, y)
            lineTo(x + 0.15f, y - 0.1f)
        }
    }
    joint(J.BIRD_WING, -0.3f, -3f) {
        fill(OwlBrown.shade(0.18f), gap = 0.12f) {
            moveTo(-0.1f, -3.5f)
            cubicTo(-1.2f, -3.3f, -1.7f, -2f, -1.5f, -0.9f)
            cubicTo(-1.1f, -0.7f, -0.7f, -0.9f, -0.5f, -1.2f)
            cubicTo(-0.2f, -2f, 0.1f, -2.8f, -0.1f, -3.5f)
            close()
        }
        line(OwlDark, 0.1f, Mono.Skip) {
            seg(-0.9f, -2.4f, -1.3f, -1.5f)
            seg(-0.6f, -2.2f, -0.9f, -1.2f)
        }
        fill(OwlLight, Mono.Skip) {
            circle(-0.8f, -2.9f, 0.1f)
            circle(-1.1f, -2.4f, 0.09f)
            circle(-0.6f, -1.9f, 0.09f)
        }
    }
    joint(J.BIRD_HEAD, 0.2f, -3.4f) {
        // Ear tufts.
        fill(OwlBrown.shade(0.1f), gap = 0.1f) {
            poly(-0.75f, -5f, -0.95f, -6.1f, -0.25f, -5.35f)
            poly(0.95f, -5.15f, 1.2f, -6.15f, 1.45f, -5f)
        }
        fill(OwlBrown, gap = 0.12f) { circle(0.3f, -4.3f, 1.35f) }
        // The face: a pale disc, a brow over the eyes.
        fill(OwlLight, Mono.Skip) { oval(0.55f, -4.25f, 1f, 0.9f) }
        line(OwlDark, 0.12f, Mono.Skip) {
            moveTo(-0.25f, -4.9f)
            lineTo(0.5f, -4.55f)
            lineTo(1.3f, -4.95f)
        }
        joint(J.BIRD_EYE, 0.55f, -4.35f) {
            fill(OwlIris, Mono.Cut, lit = true) {
                circle(0.95f, -4.35f, 0.42f)
                circle(0.05f, -4.35f, 0.36f)
            }
            fill(BirdBlack, Mono.Skip) {
                circle(1f, -4.35f, 0.22f)
                circle(0.1f, -4.35f, 0.19f)
            }
            fill(Color.White, Mono.Skip, lit = true) {
                circle(1.08f, -4.45f, 0.08f)
                circle(0.16f, -4.44f, 0.07f)
            }
        }
        fill(OwlBeak) { poly(0.42f, -4f, 0.72f, -4f, 0.57f, -3.5f) }
    }
}

// ------------------------------------------------------------------------------ drawing

/** Drawing by night (the dark theme): colours by moonlight, the lights bright. */
private var nightNow = false

/**
 * A colour by moonlight: darker and bluer, its own hue kept and a little richer (a red coat
 * deep red, a reindeer warm brown, white snow pale blue; not grey). The blue is a filter,
 * not a wash over everything: a wash would dull the warm colours to grey. The lights are not
 * changed (see [Part.lit]).
 */
private fun moon(c: Color): Color {
    val l = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    fun ch(v: Float, k: Float, add: Float) = ((v + (v - l) * 0.15f) * k + add).coerceIn(0f, 1f)
    return Color(ch(c.red, 0.72f, 0.01f), ch(c.green, 0.72f, 0.02f), ch(c.blue, 0.88f, 0.06f), c.alpha)
}

/** The moon's light on the lit edges. */
private val MoonRim = Color(0xFFD6E4FF)
/** A shadow on the snow by moonlight. */
private val MoonShadow = Color(0xFF0A1226)

/** The sun's light on the lit edges, and the shadows on the snow by day (blue, as on snow). */
private val SunRim = Color(0xFFFFF2D2)
private val SunShadow = Color(0xFF4A6390)

/**
 * A shadow cast on the snow: a point h above the ground lands run·h along from its foot and
 * drop·h nearer, below the ground line. By night the moon is low on the right, the shadows
 * long to the left; by day the sun is high on the left, the shadows short to the right.
 */
private fun castMatrix(run: Float, drop: Float) = Matrix().apply {
    val g = SleighTrain.GROUND
    values[0] = 1f
    values[1] = 0f
    values[4] = -run
    values[5] = -drop
    values[12] = run * g
    values[13] = (1f + drop) * g
}
private val nightCast = castMatrix(-0.55f, 0.12f)
private val dayCast = castMatrix(0.3f, 0.09f)

/** [c] as it is seen now: by moonlight at night. */
private fun seen(c: Color): Color = if (nightNow) moon(c) else c

/** A soft glow of [color] round ([cx], [cy]), [radius] out. */
private fun DrawScope.glow(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
            center = Offset(cx, cy), radius = radius,
        ),
        radius, Offset(cx, cy),
    )
}

private val strokeRound = Stroke(width = 1f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** The joints' places just now, for one figure at a time (drawing is on one thread). */
internal class JointPlaces {
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

private val placed = JointPlaces()
private val neighbour = JointPlaces()

/** Draw a part whole, only its outline ring, or only its body (for limbs, see [Part.group]). */
private const val WHOLE = 0
private const val RING = 1
private const val BODY = 2

private fun DrawScope.part(p: Part, coloured: Boolean, plain: Color, alpha: Float = 1f, mode: Int = WHOLE) {
    val own = coloured || p.mono == Mono.Own
    if (!own && p.mono == Mono.Skip) return
    val color = (if (own) (if (nightNow) p.nightColor else p.color) ?: return else plain)
        .let { if (alpha < 1f) it.copy(alpha = it.alpha * alpha) else it }
    val effect = if (p.dash > 0f) PathEffect.dashPathEffect(floatArrayOf(p.dash, p.dash)) else null
    val style = if (p.stroke > 0f) Stroke(p.stroke, cap = strokeRound.cap, join = strokeRound.join, pathEffect = effect) else Fill
    if (!own && p.mono == Mono.Cut) {
        if (mode != RING) drawPath(p.path, Color.Black, style = style, blendMode = BlendMode.Clear)
        return
    }
    if (p.gap > 0f && mode != BODY) {
        val ring = Stroke(p.stroke + 2f * p.gap, cap = strokeRound.cap, join = strokeRound.join)
        if (own) drawPath(p.path, color.shade(0.45f).copy(alpha = color.alpha), style = ring)
        else drawPath(p.path, Color.Black, style = ring, blendMode = BlendMode.Clear)
    }
    if (p.outline > 0f && mode != BODY) {
        val edge = Stroke(p.stroke + 2f * p.outline, cap = strokeRound.cap, join = strokeRound.join)
        val ink = if (own) color.shade(0.55f) else plain.shade(0.7f)
        drawPath(p.path, ink.copy(alpha = color.alpha), style = edge)
    }
    if (mode == RING) return
    drawPath(p.path, color, style = style)
    // In colour each larger part has a side away from the light, in a deeper shade of its
    // own colour, and an edge the light falls on: the moon's cool light by night, the sun's
    // warm light by day.
    if (coloured) {
        (if (nightNow) p.nightShaded else p.dayShaded)?.let {
            drawPath(it, color.shade(0.32f).copy(alpha = (if (nightNow) 0.5f else 0.42f) * alpha))
        }
        (if (nightNow) p.nightLit else p.dayLit)?.let {
            drawPath(it, (if (nightNow) MoonRim.copy(alpha = 0.42f * alpha) else SunRim.copy(alpha = 0.38f * alpha)))
        }
    }
}

/** The silhouette of a part, for its shadow (cut details and colour-only ones left out). */
private fun DrawScope.shade(p: Part) {
    if (p.color == null || p.mono == Mono.Cut || p.mono == Mono.Skip) return
    val style = if (p.stroke > 0f) Stroke(p.stroke, cap = strokeRound.cap, join = strokeRound.join) else Fill
    drawPath(p.path, if (nightNow) MoonShadow else SunShadow, style = style)
}

private fun DrawScope.parts(fig: FigureData, at: JointPlaces, coloured: Boolean, plain: Color, alpha: Float = 1f, hide: Int = -1) {
    fun one(p: Part, mode: Int) {
        // What is put away (Santa's clock) is not drawn.
        if (p.joint == hide) return
        if (at.still[p.joint]) part(p, coloured, plain, alpha, mode)
        else withTransform({ transform(at.matrix[p.joint]) }) { part(p, coloured, plain, alpha, mode) }
    }
    val all = fig.parts
    var i = 0
    while (i < all.size) {
        val g = all[i].group
        if (g < 0) {
            one(all[i], WHOLE)
            i++
            continue
        }
        // A limb: the outline round all of it first, then its segments over it, so no
        // line is left across it at the joints.
        var j = i
        while (j < all.size && all[j].group == g) j++
        for (k in i until j) one(all[k], RING)
        for (k in i until j) one(all[k], BODY)
        i = j
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
    /** Which of it: the figures, what is behind them (ropes, trees, wind), or both. */
    layers: Int = SleighLayers.ALL,
    /**
     * By night (the dark theme): the colours by moonlight, the lantern, the bulbs and the
     * red nose glowing, an owl instead of the bullfinch.
     */
    night: Boolean = false,
) {
    nightNow = night
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
                drawLine(if (coloured) seen(Iron) else plain, Offset(from[0], from[1]), Offset(to[0], to[1]), strokeWidth = 0.9f * unit)
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
            drawPath(path, if (coloured) seen(Leather) else plain, style = Stroke(width = 0.45f * unit, cap = StrokeCap.Round))
        }
    }

    fun reins(coloured: Boolean) {
        // From Santa's mitten over the front of the sleigh to the first reindeer.
        // In the mitten, or laid on the side of the sleigh while his hands are busy.
        val laid = pose.reinsLaid
        val mx = placed.x(J.HAND, SleighTrain.MITTEN.x, SleighTrain.MITTEN.y) * (1f - laid) +
            placed.x(J.ROOT, SleighTrain.REINS_LAID.x, SleighTrain.REINS_LAID.y) * laid
        val my = placed.y(J.HAND, SleighTrain.MITTEN.x, SleighTrain.MITTEN.y) * (1f - laid) +
            placed.y(J.ROOT, SleighTrain.REINS_LAID.x, SleighTrain.REINS_LAID.y) * laid
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
        // Hung over the side of the sleigh: the loose end hangs down outside it.
        if (laid > 0f) {
            path.moveTo(mx, my)
            path.cubicTo(mx + 0.15f, my + 0.35f, mx - 0.05f, my + 0.75f, mx - 0.3f, my + 1.05f * laid)
        }
        val color = if (coloured) seen(Leather) else plain
        if (!coloured) drawPath(path, Color.Black, style = Stroke(0.95f, cap = StrokeCap.Round), blendMode = BlendMode.Clear)
        else drawPath(path, seen(Leather).shade(0.45f), style = Stroke(0.95f, cap = StrokeCap.Round))
        drawPath(path, color, style = Stroke(0.45f, cap = StrokeCap.Round))
    }

    fun dust() {
        // Snow thrown up where a hoof strikes or a runner slides: white puffs with a soft blue
        // shade under them, snow in the silhouette too. This figure's, and those drifting in
        // from the next one on the right.
        for (i in index..minOf(index + 1, count - 1)) {
            translate(left + (i - index) * width, top) {
                scale(unit, unit, pivot = Offset.Zero) {
                    for (k in 0 until TrainPose.MAX_DUST) {
                        if (pose.dustFigure[k] != i) continue
                        val age = pose.dustAge[k]
                        val a = (1f - age) * (1f - age) * 0.85f
                        val r = pose.dustSize[k] * (1f + 1.2f * age)
                        val c = Offset(pose.dustX[k], pose.dustY[k])
                        drawCircle(seen(IceBlue).copy(alpha = a * 0.55f), r * 1.1f, c + Offset(0f, r * 0.25f))
                        drawCircle(seen(SnowWhite).copy(alpha = a), r, c)
                    }
                }
            }
        }
    }

    fun bird(coloured: Boolean) {
        if (pose.birdAlpha <= 0.01f) return
        // The bird's feet in this tab: from the trailer's grid (tab 0) to here.
        val bx = left - index * width + pose.birdX * unit
        val by = top + pose.birdY * unit
        val s = pose.birdScale * BIRD_SIZE * unit
        if (bx < -6f * s || bx > width + 6f * s) return
        val who = if (nightNow) SleighTrain.owl else SleighTrain.bird
        neighbour.place(who, pose.joints[TrainPose.BIRD])
        translate(bx, by) {
            scale(s * pose.birdFacing, s, pivot = Offset.Zero) {
                parts(who, neighbour, coloured, plain, pose.birdAlpha)
                if (nightNow && coloured) {
                    // The owl's eyes shine a little in the dark.
                    for (ex in floatArrayOf(0.95f, 0.05f)) {
                        glow(neighbour.x(J.BIRD_EYE, ex, -4.35f), neighbour.y(J.BIRD_EYE, ex, -4.35f), 0.75f, OwlIris, 0.35f * pose.birdAlpha)
                    }
                }
            }
        }
    }

    fun snowGoingBy() {
        // Going: the drift under the train goes by with the ground (as fast as the nearest
        // trees): its top in soft waves, streaks and glints in it. Snow in its own colours,
        // in the silhouette too.
        val sp = pose.speed
        if (sp < 0.02f) return
        val fade = min(1f, sp * 1.6f)
        val surface = top + (SleighTrain.GROUND - 0.35f) * unit
        val lump = if (nightNow) NightGroundTop else SnowWhite
        val shade = if (nightNow) NightGroundShade else GroundShade
        val run = pose.distance
        fun along(cell: Float, seed: Int, most: Float, each: (x: Float, h: Float, key: Int) -> Unit) {
            val k0 = floor((run + index - 0.3f) / cell).toInt()
            val k1 = floor((run + index + 1.3f) / cell).toInt()
            for (k in k0..k1) {
                val n = (hash01(seed, k) * (most + 0.999f)).toInt()
                for (m in 0 until n) {
                    val key = k * 7 + m
                    // In tab widths from tab 0's left edge: it goes by to the left.
                    val barX = (k + hash01(seed + 2, key)) * cell - run
                    val x = (barX - index) * width
                    if (x < -2f * unit || x > width + 2f * unit) continue
                    each(x, hash01(seed + 4, key), key)
                }
            }
        }
        // The top of the drift in soft waves going by, each shaded on its side away from
        // the light, so the drift is seen to move under the train.
        val away = if (nightNow) -1f else 1f
        fun crest(x: Float): Float {
            val u = index + x / width + run
            val a = 0.45f * sin(u * 2f * PI.toFloat() * 1.3f + 0.7f) +
                0.3f * sin(u * 2f * PI.toFloat() * 3.1f + 2.1f) +
                0.12f * sin(u * 2f * PI.toFloat() * 6.7f + 4.4f)
            return surface - (0.45f + a) * unit
        }
        fun wave(dx: Float, dy: Float) = Path().apply {
            val step = 3f
            var x = -4f * unit
            moveTo(x, surface + 1.4f * unit)
            while (x <= width + 4f * unit) {
                lineTo(x + dx, crest(x) + dy)
                x += step
            }
            lineTo(width + 4f * unit, surface + 1.4f * unit)
            close()
        }
        drawPath(wave(0.5f * unit * away, 0.4f * unit), shade, alpha = 0.6f * fade)
        drawPath(wave(0f, 0f), lump, alpha = fade)
        // Streaks in the drift, the snow's grain.
        along(0.16f, 83, 1f) { x, h, _ ->
            val y = surface + (1f + 1.8f * h) * unit
            val len = (1.5f + 2.5f * h) * unit
            drawLine(shade, Offset(x, y), Offset(x + len, y), strokeWidth = 0.3f * unit, cap = StrokeCap.Round, alpha = 0.5f * fade)
        }
        // Glints.
        along(0.11f, 97, 1f) { x, h, key ->
            val y = surface + (0.4f + 2.2f * h) * unit
            val glint = 0.5f + 0.5f * sin(pose.time * 5f + key * 1.7f)
            drawCircle(Color.White, 0.18f * unit, Offset(x, y), alpha = 0.85f * fade * glint)
        }
    }

    fun scenery(coloured: Boolean) {
        val sp = pose.speed
        if (sp < 0.02f) return
        val fade = min(1f, sp * 1.6f)
        // Trees going by behind the train, here and there, at all depths: the near ones
        // larger and faster, the far ones smaller, slower and fainter; firs mostly, a snowy
        // bush, a bare tree; swaying a little. Drawn before the figures, the far ones first.
        for (layer in TREE_LAYERS - 1 downTo 0) {
            val p = TREE_PARALLAX[layer]
            val cell = TREE_CELL[layer]
            val run = pose.distance * p
            val k0 = floor((run - count - 1.2f) / cell).toInt()
            val k1 = floor((run + 0.6f) / cell).toInt()
            for (k in k0..k1) {
                val seed = layer * 1009 + k
                val n = (hash01(61, seed) * (TREE_MOST[layer] + 0.999f)).toInt()
                for (m in 0 until n) {
                    val at = (k + hash01(67, seed * 5 + m)) * cell
                    // In tab widths from tab 0's left edge: it comes in on the right.
                    val barX = at - run + count + 0.4f
                    val x = (barX - index) * width
                    val pick = hash01(71, seed * 5 + m)
                    val size = (TREE_SIZE[layer] * (0.75f + 0.5f * hash01(73, seed * 5 + m))) * unit
                    if (x < -size || x > width + size) continue
                    val base = top + (SleighTrain.GROUND + 0.2f - 0.25f * layer) * unit
                    val a = fade * TREE_ALPHA[layer]
                    val sway = 3f * sp * sin(pose.time * (1.1f + hash01(79, seed * 5 + m)) + 6.28f * pick)
                    withTransform({ rotate(sway, Offset(x, base)) }) {
                        when {
                            layer < 2 && pick < 0.15f -> bush(x, base, size * 0.32f, coloured, plain, a * 0.8f)
                            pick > 0.85f -> bareTree(x, base, size, coloured, plain, a)
                            else -> tree(x, base, size, coloured, plain, a, far = layer >= 2)
                        }
                    }
                }
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
            drawLine(plain.copy(alpha = a), Offset(x, y), Offset(x + len, y), strokeWidth = 0.22f * unit, cap = StrokeCap.Round)
        }
    }

    fun shadows() {
        // This figure's shadow and the ends of its neighbours' (by night they fall to the
        // left, by day to the right), all on one layer so overlapping parts do not darken
        // each other.
        val canvas = drawContext.canvas
        val cast = if (nightNow) nightCast else dayCast
        canvas.saveLayer(Rect(-width, -height, width * 2f, height * 2f), androidx.compose.ui.graphics.Paint().apply { alpha = if (nightNow) 0.42f else 0.3f })
        for (i in maxOf(0, index - 1)..minOf(index + 1, count - 1)) {
            val figure = SleighTrain.figures[i]
            val at = if (i == index) placed else neighbour.also { it.place(figure, pose.joints[i]) }
            translate(left + (i - index) * width, top) {
                scale(unit, unit, pivot = Offset.Zero) {
                    withTransform({ transform(cast) }) {
                        for (p in figure.parts) {
                            if (i == 1 && p.joint == J.CLOCK && pose.clock <= 0f) continue
                            if (at.still[p.joint]) shade(p)
                            else withTransform({ transform(at.matrix[p.joint]) }) { shade(p) }
                        }
                    }
                }
            }
        }
        canvas.restore()
    }

    fun pass(coloured: Boolean) {
        clipRect(left = 0f, top = -height, right = width, bottom = height * 2f) {
            if (layers and SleighLayers.BACKDROP != 0) {
                scenery(coloured)
                snowGoingBy()
                shadows()
                dust()
                hitch(coloured)
            }
            if (layers and SleighLayers.FIGURE == 0) return@clipRect
            translate(left, top) {
                scale(unit, unit, pivot = Offset.Zero) {
                    parts(fig, placed, coloured, plain, hide = if (index == 1 && pose.clock <= 0f) J.CLOCK else -1)
                    if (nightNow && coloured) {
                        // By night the lights glow round them.
                        if (index == 0) {
                            glow(placed.x(J.LANTERN, 32.8f, 12.75f), placed.y(J.LANTERN, 32.8f, 12.75f), 4.6f, Lamp, 0.5f)
                            for (k in 0 until 8) {
                                val bx = 3.65f + k * 3.675f
                                glow(placed.x(J.ROOT, bx, 17.65f), placed.y(J.ROOT, bx, 17.65f), 1.2f, Bulbs[k % Bulbs.size], 0.55f)
                            }
                        }
                        if (index == 3) {
                            glow(placed.x(J.DEER_HEAD, 32.5f, 5.9f), placed.y(J.DEER_HEAD, 32.5f, 5.9f), 3.4f, CandyRed, 0.45f)
                        }
                    }
                    if (index == 1) reins(coloured)
                    if (index == 3 && pose.noseGlow > 0f) {
                        // The lead's nose glowing brighter (it is looking at it).
                        val nx = placed.x(J.DEER_HEAD, 32.5f, 5.9f)
                        val ny = placed.y(J.DEER_HEAD, 32.5f, 5.9f)
                        drawCircle(CandyRed.copy(alpha = 0.32f * pose.noseGlow), 2.2f + 1.3f * pose.noseGlow, Offset(nx, ny))
                        drawCircle(Color(0xFFFF6B7A).copy(alpha = 0.8f * pose.noseGlow), 0.95f, Offset(nx, ny))
                        drawCircle(Color.White.copy(alpha = 0.85f * pose.noseGlow), 0.38f, Offset(nx - 0.3f, ny - 0.3f))
                    }
                }
            }
            bird(coloured)
        }
    }

    // Outside the droplet: the silhouette, its colours coming up over it as the train goes.
    fun outside() {
        val c = pose.colour
        if (c >= 0.999f) {
            pass(coloured = true)
            return
        }
        pass(coloured = false)
        if (c <= 0.001f) return
        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(-width, -height, width * 2f, height * 2f), androidx.compose.ui.graphics.Paint().apply { alpha = c })
        pass(coloured = true)
        canvas.restore()
    }

    val reach = height
    when {
        colourTo <= 0f || colourFrom >= width -> outside()
        colourFrom <= 0f && colourTo >= width -> pass(coloured = true)
        else -> {
            clipRect(left = -width, top = -reach, right = colourFrom, bottom = height + reach) { outside() }
            clipRect(left = colourTo, top = -reach, right = width * 2f, bottom = height + reach) { outside() }
            clipRect(left = colourFrom, top = -reach, right = colourTo, bottom = height + reach) { pass(coloured = true) }
        }
    }
}

/** Where the bottom of a reindeer's hoof is when it stands (grid units). */
internal fun SleighTrain.hoofRest(fore: Boolean, far: Boolean): Offset {
    val dx = if (far) (if (fore) 1.1f else -1.6f) else 0f
    return Offset((if (fore) 23.95f else 11.1f) + dx, 22.6f)
}

/** Where the bottom of a hoof is (grid units of the figure, its joints placed). */
internal fun hoofAt(fig: FigureData, pose: FloatArray, fetlock: Int, fore: Boolean, far: Boolean, out: FloatArray) {
    hoofPlaces.place(fig, pose)
    val dx = if (far) (if (fore) 1.1f else -1.6f) else 0f
    val hx = (if (fore) 23.95f else 11.1f) + dx
    out[0] = hoofPlaces.x(fetlock, hx, 22.6f)
    out[1] = hoofPlaces.y(fetlock, hx, 22.6f)
}

private val hoofPlaces = JointPlaces()

/** The depths trees stand at, near to far: how fast they go by, how far apart, how many
 * together at most, how tall (grid units), how strongly drawn. */
private const val TREE_LAYERS = 4
private val TREE_PARALLAX = floatArrayOf(1f, 0.74f, 0.5f, 0.3f)
private val TREE_CELL = floatArrayOf(2.6f, 2f, 1.5f, 1.1f)
private val TREE_MOST = intArrayOf(2, 2, 3, 3)
private val TREE_SIZE = floatArrayOf(16f, 11f, 7.5f, 5f)
private val TREE_ALPHA = floatArrayOf(0.85f, 0.65f, 0.45f, 0.3f)

/** A snowy bush: a few round clumps with snow on top. */
private fun DrawScope.bush(x: Float, base: Float, size: Float, coloured: Boolean, plain: Color, alpha: Float) {
    val body = Path().apply {
        circle(x - size * 0.35f, base - size * 0.32f, size * 0.38f)
        circle(x + size * 0.3f, base - size * 0.3f, size * 0.36f)
        circle(x, base - size * 0.5f, size * 0.42f)
    }
    if (!coloured) {
        drawPath(body, plain.copy(alpha = alpha * 0.35f))
        return
    }
    val snow = Path().apply {
        oval(x, base - size * 0.82f, size * 0.36f, size * 0.14f)
        oval(x - size * 0.4f, base - size * 0.62f, size * 0.24f, size * 0.1f)
        oval(x + size * 0.36f, base - size * 0.58f, size * 0.22f, size * 0.1f)
    }
    drawPath(body, seen(Fir.shade(0.15f)).copy(alpha = alpha))
    drawPath(snow, seen(SnowWhite).copy(alpha = alpha))
}

/** A bare winter tree: a trunk forking into branches, snow along them in colour. */
private fun DrawScope.bareTree(x: Float, base: Float, size: Float, coloured: Boolean, plain: Color, alpha: Float) {
    val w = size * 0.035f
    val limbs = Path().apply {
        moveTo(x, base)
        lineTo(x, base - size * 0.55f)
        lineTo(x - size * 0.22f, base - size * 0.92f)
        moveTo(x, base - size * 0.55f)
        lineTo(x + size * 0.2f, base - size * 0.95f)
        moveTo(x, base - size * 0.4f)
        lineTo(x + size * 0.26f, base - size * 0.62f)
        moveTo(x, base - size * 0.32f)
        lineTo(x - size * 0.24f, base - size * 0.55f)
        moveTo(x - size * 0.12f, base - size * 0.74f)
        lineTo(x - size * 0.3f, base - size * 0.8f)
        moveTo(x + size * 0.1f, base - size * 0.76f)
        lineTo(x + size * 0.28f, base - size * 0.82f)
    }
    val stroke = Stroke(width = w * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (!coloured) {
        drawPath(limbs, plain.copy(alpha = alpha * 0.35f), style = stroke)
        return
    }
    val snow = Path().apply {
        moveTo(x - size * 0.2f, base - size * 0.9f)
        lineTo(x - size * 0.05f, base - size * 0.66f)
        moveTo(x + size * 0.18f, base - size * 0.93f)
        lineTo(x + size * 0.05f, base - size * 0.7f)
        moveTo(x + size * 0.24f, base - size * 0.6f)
        lineTo(x + size * 0.08f, base - size * 0.48f)
    }
    drawPath(limbs, seen(FirTrunk).copy(alpha = alpha), style = stroke)
    drawPath(snow, seen(SnowWhite).copy(alpha = alpha), style = Stroke(width = w * 1.4f, cap = StrokeCap.Round))
}

/** The parts of a tab of the train, to draw apart (the figures under a legibility border). */
object SleighLayers {
    /** The trees and the wind going by, the ropes and the bar between the figures. */
    const val BACKDROP = 1
    /** The figures, the bird, the snow thrown up. */
    const val FIGURE = 2
    const val ALL = 3
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
        drawPath(body, plain.copy(alpha = alpha * (if (far) 0.25f else 0.35f)))
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
    drawRect(seen(FirTrunk).copy(alpha = alpha), Offset(x - size * 0.05f, base - size * 0.14f), androidx.compose.ui.geometry.Size(size * 0.1f, size * 0.14f))
    drawPath(body, seen(if (far) Fir.tint(0.25f) else Fir).copy(alpha = alpha))
    drawPath(snow, seen(SnowWhite).copy(alpha = alpha))
}

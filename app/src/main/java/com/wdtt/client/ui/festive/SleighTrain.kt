package com.wdtt.client.ui.festive

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * New Year: the four tabs are one train going right, drawn large in place of the tab icons
 * and labels: a trailer of presents (home), Santa in his sleigh, and two reindeer, the lead
 * one with a red nose. The trailer is coupled to the sleigh by a rigid bar; the sleigh and
 * the reindeer are joined by reins and traces sagging across the gaps between the tabs.
 *
 * Each figure is drawn twice over: in the plain colour of the tab, a silhouette with its
 * details cut out of it (planks, ribbons, belt, collar, eyes), and in its own colours. The
 * colours show only under the droplet, as the accent colour of the other tabs does, so the
 * figure takes its colours as the droplet passes over it.
 *
 * Figures are on a [WIDE] by [TALL] grid, of separate parts (each leg, the body, the harness)
 * so that they can be moved one by one.
 */
object SleighTrain {
    const val WIDE = 36f
    const val TALL = 24f

    /** The tallest the figures are drawn. */
    val height = 46.dp

    /** At most this share of its tab is taken by a figure, the rest is the gap to the next. */
    private const val FILL = 0.88f

    /** The figures, tab by tab. */
    internal val figures: List<List<Part>> by lazy {
        listOf(trailer(), sleigh(), reindeer(leads = false), reindeer(leads = true))
    }

    val count get() = 4

    /** Height of the joint between tab [left] and the next one, in grid units. */
    internal fun hitchY(left: Int): Float = if (left == 0) COUPLING_Y else REINS_Y

    /** The rigid bar between the trailer and the sleigh; reins and traces elsewhere. */
    internal fun rigid(left: Int): Boolean = left == 0

    /** The figure's height in a tab [width] wide, given the tallest it may be. */
    fun figureHeight(width: Float, tallest: Float): Float = minOf(tallest, width * FILL * TALL / WIDE)

    internal const val COUPLING_Y = 19.6f
    internal const val REINS_Y = 12f
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
 * colour, so it stands apart from them.
 */
internal class Part(
    val path: Path,
    val color: Color?,
    val mono: Mono,
    val stroke: Float,
    val gap: Float,
    val dash: Float,
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
private val Silver = Color(0xFFE6EBF2)
private val PresentGreen = Color(0xFF2E9E5B)
private val PresentBlue = Color(0xFF3D7BE0)
private val PresentGold = Color(0xFFF2C14E)

private fun Color.shade(f: Float) = lerp(this, Color.Black, f)
private fun Color.tint(f: Float) = lerp(this, Color.White, f)

private class Figure {
    val parts = ArrayList<Part>()

    fun fill(color: Color?, mono: Mono = Mono.Fill, gap: Float = 0f, body: Path.() -> Unit) {
        parts += Part(Path().apply(body), color, mono, 0f, gap, 0f)
    }

    fun line(color: Color?, width: Float, mono: Mono = Mono.Fill, gap: Float = 0f, dash: Float = 0f, body: Path.() -> Unit) {
        parts += Part(Path().apply(body), color, mono, width, gap, dash)
    }
}

private fun figure(body: Figure.() -> Unit): List<Part> = Figure().apply(body).parts

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

/** A sled runner along the bottom, turned up a little at the back, curling up in front. */
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
    // The shine along it, and the knob at its tip.
    line(Gold.tint(0.55f), 0.25f, Mono.Skip) {
        moveTo(3.4f, 21.75f)
        lineTo(28.4f, 21.75f)
    }
    fill(Gold, gap = 0.2f) { circle(33.2f, 16.7f, 0.85f) }
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
 * A present [w] by [h] at ([x], [y]): a box and a lid a little wider, tied both ways with a
 * ribbon through [rx] and [ry], a bow on top.
 */
private fun Figure.present(x: Float, y: Float, w: Float, h: Float, box: Color, ribbon: Color, rx: Float, ry: Float, bowSize: Float) {
    val band = 0.8f
    val lid = minOf(1.6f, h * 0.24f)
    fill(box, gap = 0.3f) { rect(x, y + lid, w, h - lid) }
    fill(box.tint(0.12f), gap = 0.3f) { round(x - 0.3f, y, w + 0.6f, lid, 0.25f) }
    fill(ribbon, Mono.Cut) {
        rect(rx, y, band, h)
        rect(x, ry, w, band)
    }
    fill(ribbon, gap = 0.2f) { bow(rx + band / 2f, y + 0.05f, bowSize) }
    fill(ribbon.shade(0.2f)) { circle(rx + band / 2f, y + 0.05f, 0.3f * bowSize) }
}

/** Home: a trailer of presents with a lantern, coupled to the sleigh in front. */
private fun trailer() = figure {
    runner()
    struts(4.4f, 12.2f, 20f, 27.4f)
    // The coupling bar going on to the sleigh.
    fill(Iron) { rect(29.6f, SleighTrain.COUPLING_Y - 0.45f, 6.4f, 0.9f) }
    // The lantern on a post at the front, lit.
    fill(WoodDark) {
        rect(30.2f, 9.8f, 0.7f, 6.2f)
        rect(30.2f, 9.8f, 3.2f, 0.55f)
    }
    line(Iron, 0.25f) { seg(32.8f, 10.3f, 32.8f, 11f) }
    fill(Iron) {
        poly(31.9f, 11.75f, 33.7f, 11.75f, 32.8f, 10.85f)
        rect(32f, 11.6f, 1.6f, 2.3f)
        rect(31.85f, 13.8f, 1.9f, 0.45f)
    }
    fill(Lamp, Mono.Cut) { rect(32.3f, 11.95f, 1f, 1.6f) }
    // A candy cane peeking out behind the presents.
    val cane: Path.() -> Unit = {
        moveTo(11.3f, 15f)
        lineTo(11.3f, 6.4f)
        cubicTo(11.3f, 4.9f, 13.3f, 4.9f, 13.3f, 6.4f)
        lineTo(13.3f, 7f)
    }
    line(Color.White, 0.85f, gap = 0.2f, body = cane)
    line(CandyRed, 0.85f, Mono.Skip, dash = 0.55f, body = cane)
    // The bed, of planks, with a rim.
    fill(Wood) { round(1.8f, 15.6f, 29.4f, 3.2f, 0.5f) }
    line(WoodDark, 0.3f, Mono.Cut) {
        for (k in 1..7) seg(1.8f + k * 3.675f, 16.6f, 1.8f + k * 3.675f, 18.3f)
        seg(2.4f, 16.3f, 30.6f, 16.3f)
    }
    fill(Iron, Mono.Skip) {
        round(1.8f, 15.6f, 1.2f, 3.2f, 0.4f)
        round(30f, 15.6f, 1.2f, 3.2f, 0.4f)
    }
    present(2.8f, 7.6f, 8.6f, 8f, CandyRed, Gold, 6.7f, 11.4f, 1.7f)
    present(11.8f, 9.8f, 7.4f, 5.8f, PresentGreen, CandyRed, 15.1f, 12.6f, 1.3f)
    present(19.6f, 11.2f, 5.8f, 4.4f, PresentBlue, Silver, 22.1f, 13.3f, 1.05f)
    present(25.8f, 12.8f, 4f, 2.8f, PresentGold, CandyRed, 27.4f, 13.9f, 0.8f)
}

/** Santa in his sleigh, a sack of presents behind him, the reins in his hand. */
private fun sleigh() = figure {
    runner()
    struts(6.4f, 14f, 21.6f)
    // The brace from the front of the sleigh to the runner.
    line(GoldDeep, 0.7f) {
        moveTo(26.4f, 16.2f)
        cubicTo(28.6f, 17f, 30.2f, 18.6f, 30.8f, 20.8f)
    }
    // The coupling bar from the trailer.
    fill(Iron) { rect(0f, SleighTrain.COUPLING_Y - 0.45f, 4.2f, 0.9f) }
    // The high back, curling over at the top.
    fill(SleighRedDeep) {
        moveTo(3f, 15f)
        lineTo(3f, 8.4f)
        cubicTo(3f, 6.4f, 5.6f, 5.8f, 6.4f, 7.5f)
        cubicTo(6.8f, 8.5f, 6.1f, 9.4f, 5.3f, 9.1f)
        lineTo(5.3f, 15f)
        close()
    }
    line(Gold, 0.3f, Mono.Cut) { circle(5.2f, 7.9f, 0.55f) }
    // The sack, tied at the top, presents showing at its mouth.
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
    // Santa: his coat with a fur edge down the front and a belt with a buckle.
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
    // The face: an eye, a rosy cheek, a nose; the beard and the moustache.
    fill(Skin) { circle(13.9f, 7.2f, 1.8f) }
    fill(Eye, Mono.Cut) { circle(14.7f, 6.8f, 0.27f) }
    fill(Rosy, Mono.Skip) { circle(14.9f, 7.85f, 0.45f) }
    fill(Fur, gap = 0.3f) {
        moveTo(12f, 7.8f)
        cubicTo(11.6f, 10.6f, 12.8f, 12f, 14f, 12f)
        cubicTo(15.2f, 12f, 16.2f, 10.8f, 16f, 7.8f)
        cubicTo(15f, 8.8f, 13f, 8.8f, 12f, 7.8f)
        close()
    }
    fill(Fur, Mono.Skip) {
        moveTo(13.4f, 8.6f)
        cubicTo(14.2f, 7.9f, 15.4f, 7.9f, 16.1f, 8.3f)
        cubicTo(15.5f, 9f, 14.4f, 9.1f, 13.4f, 8.6f)
        close()
    }
    fill(Skin.shade(0.08f), gap = 0.15f) { circle(15.6f, 7.55f, 0.5f) }
    // The cap: flopping back, a pompom at its tip, a fur brim.
    fill(CoatRed) {
        moveTo(12f, 5.2f)
        cubicTo(11.9f, 3.4f, 13.1f, 2.4f, 14.3f, 2.5f)
        cubicTo(15.4f, 2.6f, 16f, 3.6f, 15.9f, 5.2f)
        close()
        moveTo(12.8f, 2.9f)
        cubicTo(11.4f, 2.1f, 9.8f, 2.6f, 9.3f, 4.1f)
        lineTo(10.4f, 4.4f)
        cubicTo(10.8f, 3.7f, 11.6f, 3.5f, 12.5f, 4.1f)
        close()
    }
    fill(Fur, gap = 0.2f) { circle(9.7f, 4.6f, 0.85f) }
    fill(Fur, gap = 0.25f) { round(11.7f, 5f, 4.5f, 1.1f, 0.55f) }
    // The arm held out, a fur cuff, a mitten on the reins.
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
    line(Gold, 0.35f, Mono.Cut) { circle(29.2f, 9.9f, 0.55f) }
    fill(Gold, Mono.Skip) {
        for (x in floatArrayOf(7.6f, 11.6f, 15.6f)) circle(x, 17.75f, 0.35f)
    }
    // A bell under the curl.
    line(Gold, 0.2f) { seg(29.6f, 11.6f, 29.9f, 12.4f) }
    fill(Gold, gap = 0.2f) { circle(30f, 12.9f, 0.6f) }
    // The reins, going on to the reindeer.
    line(Leather, 0.45f, gap = 0.25f) {
        moveTo(21.3f, 11.7f)
        lineTo(SleighTrain.WIDE, SleighTrain.REINS_Y)
    }
}

/** A reindeer trotting right; the lead one has a red nose and no traces in front of it. */
private fun reindeer(leads: Boolean) = figure {
    // The far legs, a shade darker: a hind leg pushing back, a foreleg lifted.
    fill(DeerDeep) { poly(8.2f, 13.6f, 10.8f, 14.2f, 8.4f, 18f, 6f, 21f, 5f, 20.4f, 7f, 17.6f) }
    fill(Hoof, gap = 0.2f) { poly(4.6f, 20.2f, 6.2f, 21.2f, 5.6f, 22.1f, 4f, 21.2f) }
    fill(DeerDeep) { poly(21.2f, 14.4f, 23.4f, 14.6f, 25.4f, 16.8f, 25f, 19.4f, 24f, 19.2f, 24.2f, 17.4f, 21.8f, 15.8f) }
    fill(Hoof, gap = 0.2f) { poly(23.8f, 19f, 25.2f, 19.3f, 25f, 20.4f, 23.6f, 20.2f) }
    // The near legs, standing.
    fill(Deer, gap = 0.3f) { poly(9.6f, 13.8f, 12.6f, 14.4f, 11.6f, 18.4f, 12f, 21.6f, 10.9f, 21.7f, 10.4f, 18.4f) }
    fill(Hoof, gap = 0.2f) { poly(10.7f, 21.4f, 12.2f, 21.4f, 12.5f, 22.6f, 10.5f, 22.6f) }
    fill(Deer, gap = 0.3f) { poly(22.2f, 14.6f, 24.6f, 14.6f, 23.9f, 18.4f, 24.2f, 21.6f, 23.1f, 21.7f, 22.7f, 18.4f) }
    fill(Hoof, gap = 0.2f) { poly(22.9f, 21.4f, 24.4f, 21.4f, 24.7f, 22.6f, 22.7f, 22.6f) }
    // A short white tail.
    fill(Cream, gap = 0.15f) {
        moveTo(7.3f, 10.6f)
        cubicTo(6.1f, 9.3f, 4.7f, 9.5f, 4.4f, 10.5f)
        cubicTo(5.2f, 10.8f, 6.1f, 11.6f, 6.9f, 12.1f)
        close()
    }
    // The body, its belly lighter.
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
    }
    // The harness: a saddle pad edged in gold, the girth strap (cut) with a buckle.
    fill(Harness, Mono.Skip) { round(13.2f, 9.1f, 5.6f, 3.3f, 0.8f) }
    line(Gold, 0.3f, Mono.Skip) { round(13.35f, 9.25f, 5.3f, 3f, 0.7f) }
    fill(Harness.shade(0.15f), Mono.Cut) { rect(15.6f, 9f, 0.9f, 7.2f) }
    fill(Gold) { rect(15.5f, 13.3f, 1.1f, 1f) }
    // The traces from behind along its side up to the collar.
    line(Leather, 0.45f) { seg(0f, SleighTrain.REINS_Y, 7.2f, 12.1f) }
    line(Leather, 0.4f, Mono.Skip) {
        seg(7.2f, 12.1f, 15.6f, 11.9f)
        seg(16.5f, 11.85f, 22.2f, 10.1f)
    }
    // The neck with a fluffy ruff down the throat.
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
    // The collar with three bells.
    fill(Harness, Mono.Cut) { poly(21.8f, 9.6f, 22.6f, 8.5f, 27f, 10.3f, 26.4f, 11.5f) }
    line(Gold, 0.2f, Mono.Skip) { seg(22.1f, 9.25f, 26.7f, 11.1f) }
    fill(Gold, gap = 0.15f) {
        circle(23f, 10f, 0.5f)
        circle(24.4f, 10.65f, 0.5f)
        circle(25.8f, 11.3f, 0.5f)
    }
    // Antlers: two branching beams.
    line(Antler, 0.62f, gap = 0.12f) {
        moveTo(26.5f, 3.6f)
        cubicTo(25.9f, 2.2f, 24.7f, 1.3f, 22.9f, 1f)
        seg(25.4f, 2f, 25.8f, 0.4f)
        seg(24.2f, 1.4f, 23.9f, 0.4f)
        moveTo(27.7f, 3.6f)
        cubicTo(28f, 2.2f, 29.2f, 1.2f, 30.8f, 0.8f)
        seg(28.6f, 1.9f, 28.2f, 0.4f)
        seg(29.8f, 1.2f, 30.1f, 0.3f)
        seg(28f, 2.9f, 29.5f, 2.7f)
    }
    // The ear laid back, and the head: muzzle forward, an eye.
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
    fill(Eye, Mono.Cut) { circle(28.5f, 4.95f, 0.4f) }
    fill(Color.White, Mono.Skip) { circle(28.62f, 4.82f, 0.13f) }
    if (leads) {
        // The red nose, glowing, in its colour even in the silhouette.
        fill(CandyRed.copy(alpha = 0.3f), Mono.Own) { circle(32.5f, 5.9f, 2.1f) }
        fill(CandyRed, Mono.Own) { circle(32.5f, 5.9f, 0.95f) }
        fill(Color.White.copy(alpha = 0.75f), Mono.Own) { circle(32.2f, 5.6f, 0.3f) }
    } else {
        fill(Hoof) { circle(32.4f, 5.95f, 0.65f) }
        // The traces going on to the lead reindeer.
        line(Leather, 0.45f) { seg(26.8f, 11f, SleighTrain.WIDE, SleighTrain.REINS_Y) }
    }
}

private val round = Stroke(width = 1f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.part(p: Part, coloured: Boolean, plain: Color) {
    val own = coloured || p.mono == Mono.Own
    if (!own && p.mono == Mono.Skip) return
    val color = if (own) p.color ?: return else plain
    val effect = if (p.dash > 0f) PathEffect.dashPathEffect(floatArrayOf(p.dash, p.dash)) else null
    val style = if (p.stroke > 0f) Stroke(p.stroke, cap = round.cap, join = round.join, pathEffect = effect) else Fill
    if (!own && p.mono == Mono.Cut) {
        drawPath(p.path, Color.Black, style = style, blendMode = BlendMode.Clear)
        return
    }
    if (p.gap > 0f) {
        val ring = Stroke(p.stroke + 2f * p.gap, cap = round.cap, join = round.join)
        if (own) drawPath(p.path, color.shade(0.45f).copy(alpha = color.alpha), style = ring)
        else drawPath(p.path, Color.Black, style = ring, blendMode = BlendMode.Clear)
    }
    drawPath(p.path, color, style = style)
}

/**
 * Draws tab [index] of the train in a tab [width] by [height] (pixels), the figure at most
 * [tallest] tall in the middle of it, and this tab's half of the joints to the neighbours
 * (the neighbour draws the other half). From [colourFrom] to [colourTo] along x the figure
 * is in its colours, elsewhere a silhouette in [plain]. The cut-out details clear what is
 * under them, so this is drawn on a layer of its own (the tab's).
 */
fun DrawScope.drawSleighTab(
    index: Int,
    width: Float,
    height: Float,
    tallest: Float,
    plain: Color,
    colourFrom: Float,
    colourTo: Float,
) {
    val parts = SleighTrain.figures[index]
    val count = SleighTrain.count
    val tall = SleighTrain.figureHeight(width, tallest)
    val unit = tall / SleighTrain.TALL
    val wide = SleighTrain.WIDE * unit
    val left = (width - wide) / 2f
    val top = (height - tall) / 2f

    fun hitch(coloured: Boolean, plainColor: Color) {
        fun half(joint: Int, fromX: Float, toX: Float, firstHalf: Boolean) {
            val y = top + SleighTrain.hitchY(joint) * unit
            if (SleighTrain.rigid(joint)) {
                drawLine(if (coloured) Iron else plainColor, Offset(fromX, y), Offset(toX, y), strokeWidth = 0.9f * unit)
                return
            }
            // A rope sagging between the two figures: a parabola over the whole gap, this half.
            val sag = left * 2f * 0.12f
            val path = Path()
            val steps = 10
            for (k in 0..steps) {
                val t = (if (firstHalf) 0f else 0.5f) + 0.5f * k / steps
                val x = fromX + (toX - fromX) * k / steps
                val yy = y + 4f * sag * t * (1f - t)
                if (k == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            drawPath(path, if (coloured) Leather else plainColor, style = Stroke(width = 0.45f * unit, cap = StrokeCap.Round))
        }
        if (index < count - 1) half(index, left + wide, width, firstHalf = true)
        if (index > 0) half(index - 1, 0f, left, firstHalf = false)
    }

    fun pass(coloured: Boolean) {
        hitch(coloured, plain)
        translate(left, top) {
            scale(unit, unit, pivot = Offset.Zero) {
                for (p in parts) part(p, coloured, plain)
            }
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

package com.wdtt.client.ui.festive

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A snow globe the shape of the tab bar: liquid, and snow that sinks through it and lies
 * on whichever wall is down. Lengths are in bar heights (the bar is [aspect] long and 1
 * high, y down); times in seconds.
 *
 * Every flake lives all the time — none is ever removed or made — so nothing vanishes or
 * appears from nowhere:
 * - In the liquid a flake is carried by the flow and sinks slowly along gravity, swaying
 *   as it falls; a shake throws it about (heavier than the liquid, it lags behind).
 * - Touching the snow on the wall that is down, it stops where it touched and lies on
 *   top of that column (a stack per column, the newest on top). There is a drift on the
 *   bottom and one on the top wall: turn the phone over and the snow lets go of the wall
 *   that is now up, flake by flake, and sinks to the other.
 * - The drift is loose and crumbly: a slope holds up to a steeper angle, then gives way;
 *   the grains on top break off one after another and tumble down in hops until the slope
 *   is back at its angle of repose. A tilt does that, measured against gravity.
 * - The liquid lifts the grains on top where it runs fast over them; a shake tears the
 *   drift up into a blizzard.
 * - The liquid is a coarse grid (stable fluids: forces, advection, pressure projection,
 *   damping); a moving droplet drags it along and it whirls round the droplet's ends.
 * - When nothing moves, [step] says so and the caller stops calling it.
 */
class SnowGlobePhysics(
    val aspect: Float,
    /** Corner radius of the bar, in bar heights (0.5 — a pill). */
    val corner: Float = 0.5f,
    val flakes: Int = 1400,
    seed: Int = 7,
) {
    // ---- Liquid ----
    private val nx = 44
    private val ny = 8
    private val cw = aspect / nx
    private val ch = 1f / ny
    private var u = FloatArray(nx * ny)
    private var v = FloatArray(nx * ny)
    private var u2 = FloatArray(nx * ny)
    private var v2 = FloatArray(nx * ny)
    private val pressure = FloatArray(nx * ny)
    private val pressure2 = FloatArray(nx * ny)
    private val divergence = FloatArray(nx * ny)

    // ---- Flakes ----
    val x = FloatArray(flakes)
    val y = FloatArray(flakes)
    private val vx = FloatArray(flakes)
    private val vy = FloatArray(flakes)
    /** Size of a flake, 0..1. */
    val size = FloatArray(flakes)
    private val phase = FloatArray(flakes)
    /** Lying in a drift (else moving). */
    val lying = BooleanArray(flakes)
    /** Which drift a lying flake is in: [BOTTOM] or [TOP]. */
    val side = IntArray(flakes)
    /** Tumbling down a slope as a heavy crumb (not carried by the liquid). */
    private val tumbling = BooleanArray(flakes)
    /** The column a tumbling grain set off for (it never comes to rest higher than that). */
    private val tumbleTo = IntArray(flakes)
    /** How much a flake adds to the height of its column when it lies. */
    private val thickness = FloatArray(flakes)
    /**
     * How firmly a grain holds on to the snow around it, 0..1. Snow clings together: a
     * grain breaks off a slope, or off a wall turned over, only past its own angle, so the
     * snow lets go bit by bit as the phone turns further, the loose grains first.
     */
    private val cohesion = FloatArray(flakes)

    // ---- The two drifts: a stack of lying flakes per column ----
    val columns = 64
    val columnWidth = aspect / columns
    /** Height of the drift per column, for each wall. */
    private val depth = arrayOf(FloatArray(columns), FloatArray(columns))
    private val top = arrayOf(IntArray(columns) { -1 }, IntArray(columns) { -1 })
    private val below = IntArray(flakes) { -1 }
    private val liftCarry = arrayOf(FloatArray(columns), FloatArray(columns))
    private val slideCarry = arrayOf(FloatArray(columns), FloatArray(columns))
    private val avalanche = arrayOf(BooleanArray(columns), BooleanArray(columns))

    /** Flakes not lying now (in the liquid or tumbling). */
    var floating = 0
        private set

    /** Direction things fall, on the screen (from the phone's tilt); unit length. */
    var gravityX = 0f
        private set
    var gravityY = 1f
        private set

    private var noise = seed * 7919

    private fun rnd(): Float {
        noise = noise * 1103515245 + 12345
        return ((noise ushr 8) and 0xFFFF) / 65535f
    }

    init {
        // All the snow over the flat bottom would lie [FULL_DEPTH] deep.
        val mean = FULL_DEPTH * aspect / flakes / columnWidth
        for (i in 0 until flakes) {
            size[i] = rnd()
            phase[i] = rnd() * 6.28f
            thickness[i] = mean * (0.45f + 1.1f * size[i] * size[i]) / 0.82f
            cohesion[i] = rnd()
        }
    }

    // ---------------------------------------------------------------- shape of the glass

    /** The bottom wall at [px] (it rises in the rounded ends). */
    fun bottomAt(px: Float): Float {
        val d = abs(px - aspect / 2f) - (aspect / 2f - corner)
        if (d <= 0f) return 1f
        val k = min(d, corner)
        return 1f - corner + sqrt(max(0f, corner * corner - k * k))
    }

    /** The top wall at [px]. */
    fun topAt(px: Float): Float = 1f - bottomAt(px)

    /** +1 for the bottom drift (it grows up), -1 for the top one (it grows down). */
    private fun sign(s: Int) = if (s == BOTTOM) 1f else -1f

    private fun wallAt(s: Int, px: Float) = if (s == BOTTOM) bottomAt(px) else topAt(px)

    private fun column(px: Float) = (px / columnWidth).toInt().coerceIn(0, columns - 1)
    private fun columnX(c: Int) = (c + 0.5f) * columnWidth

    /** The surface of drift [s] over column [c]. */
    private fun surface(s: Int, c: Int) = wallAt(s, columnX(c)) - sign(s) * depth[s][c]

    /** Depth of drift [s] at [px], smoothed between columns (for drawing). */
    fun depthAt(s: Int, px: Float): Float {
        val f = (px / columnWidth - 0.5f).coerceIn(0f, columns - 1f)
        val i = f.toInt().coerceAtMost(columns - 2)
        val t = f - i
        return depth[s][i] * (1 - t) + depth[s][i + 1] * t
    }

    /** Depth of the bottom drift at [px]. */
    fun depthAt(px: Float): Float = depthAt(BOTTOM, px)

    /** For tests: the depth of a bottom column. */
    internal fun bottomDepth(c: Int) = depth[BOTTOM][c]
    internal fun topDepth(c: Int) = depth[TOP][c]

    /** Signed distance to the rounded wall of the glass: negative inside. */
    private fun wall(px: Float, py: Float, out: FloatArray): Float {
        val qx = abs(px - aspect / 2f) - (aspect / 2f - corner)
        val qy = abs(py - 0.5f) - (0.5f - corner)
        val sx = if (px < aspect / 2f) -1f else 1f
        val sy = if (py < 0.5f) -1f else 1f
        val ox = max(qx, 0f)
        val oy = max(qy, 0f)
        val outside = hypot(ox, oy)
        val d = outside + min(max(qx, qy), 0f) - corner
        if (outside > 0f) {
            out[0] = sx * ox / outside
            out[1] = sy * oy / outside
        } else if (qx > qy) {
            out[0] = sx; out[1] = 0f
        } else {
            out[0] = 0f; out[1] = sy
        }
        return d
    }

    // ---------------------------------------------------------------- set-ups

    private fun clear() {
        for (s in 0..1) {
            depth[s].fill(0f)
            top[s].fill(-1)
            avalanche[s].fill(false)
        }
        floating = flakes
        for (i in 0 until flakes) {
            lying[i] = false
            tumbling[i] = false
            vx[i] = 0f
            vy[i] = 0f
        }
    }

    /** The globe just shaken: every flake somewhere in the liquid, nothing lying. */
    fun scatter() {
        clear()
        for (i in 0 until flakes) {
            x[i] = corner * 0.4f + rnd() * (aspect - corner * 0.8f)
            y[i] = 0.06f + rnd() * 0.82f
        }
    }

    /** Everything lying on the bottom, as when the globe has stood still for a while. */
    fun settleAll() {
        clear()
        // Over the flat part of the bottom, each flake into the lowest column there, so the
        // drift lies level from the start.
        val first = column(corner) + 1
        val last = column(aspect - corner) - 1
        for (i in 0 until flakes) {
            var c = first
            for (k in first..last) if (depth[BOTTOM][k] < depth[BOTTOM][c]) c = k
            x[i] = columnX(c) + (rnd() - 0.5f) * columnWidth * 0.9f
            lie(i, BOTTOM, c)
        }
        // Let the edges of the drift slip into the rounded ends now, out of sight, so
        // nothing moves when the globe is first shown.
        repeat(1200) { if (!step(1f / 60f)) return }
    }

    /** [i] lies down on drift [s] over column [c], where it is (only its height is fitted). */
    private fun lie(i: Int, s: Int, at: Int) {
        // A column full up to the other wall takes no more: the next one towards the
        // middle does.
        var c = at
        while (full(s, c, thickness[i]) && c != columns / 2) c += if (c < columns / 2) 1 else -1
        if (!lying[i]) floating--
        lying[i] = true
        tumbling[i] = false
        side[i] = s
        vx[i] = 0f
        vy[i] = 0f
        y[i] = surface(s, c) - sign(s) * thickness[i] * 0.5f
        depth[s][c] += thickness[i]
        below[i] = top[s][c]
        top[s][c] = i
    }

    private fun full(s: Int, c: Int, add: Float): Boolean {
        val other = if (s == BOTTOM) TOP else BOTTOM
        val gap = (surface(s, c) - surface(other, c)) * sign(s)
        return gap - add < 0.08f
    }

    /** The flake on top of drift [s] over [c] gets up, with velocity ([ux], [uy]). */
    private fun rise(s: Int, c: Int, ux: Float, uy: Float): Int {
        val i = top[s][c]
        if (i < 0) return -1
        top[s][c] = below[i]
        below[i] = -1
        depth[s][c] = max(0f, depth[s][c] - thickness[i])
        lying[i] = false
        floating++
        vx[i] = ux
        vy[i] = uy
        return i
    }

    // ---------------------------------------------------------------- one step

    /**
     * Advances by [dt]. [dropletX], [dropletHalf] and [dropletV] describe the droplet
     * (centre, half width, speed along the bar); [tiltX], [tiltY] the direction of gravity
     * on the screen; [shakeX], [shakeY] the phone's own acceleration on the screen (m/s²).
     * Returns whether anything still moves.
     */
    fun step(
        dt: Float,
        dropletX: Float = -1f,
        dropletHalf: Float = 0f,
        dropletV: Float = 0f,
        tiltX: Float = 0f,
        tiltY: Float = 1f,
        shakeX: Float = 0f,
        shakeY: Float = 0f,
    ): Boolean {
        val h = dt.coerceIn(0f, 1f / 30f)
        // Gravity: a slight tilt counts about twice, so it shows; the boost fades out
        // towards a quarter turn, so a phone turned further over is turned as far as it is.
        // Lying flat (no direction on the screen) the snow keeps to the bottom.
        var gX = 0f
        var gY = 1f
        if (hypot(tiltX, tiltY) > 0.15f) {
            val angle = kotlin.math.atan2(tiltX, tiltY)
            val turn = abs(angle)
            val boosted = if (turn < HALF_PI) angle * (1f + TILT_BOOST * (1f - turn / HALF_PI)) else angle
            gX = sin(boosted)
            gY = kotlin.math.cos(boosted)
        }
        // Held fully sideways the bar is a short tube on end: keep a little of gravity
        // across it, so the snow settles against one long wall instead of hanging at the end.
        if (abs(gY) < MIN_ACROSS) {
            gY = if (gY < 0f) -MIN_ACROSS else MIN_ACROSS
            gX = (if (gX < 0f) -1f else 1f) * sqrt(1f - MIN_ACROSS * MIN_ACROSS)
        }
        gravityX = gX
        gravityY = gY

        val shake = hypot(shakeX, shakeY)
        forces(h, dropletX, dropletHalf, dropletV, shake)
        advect(h)
        project()
        val damp = exp(-0.8f * h)
        var fastest = 0f
        for (k in u.indices) {
            u[k] *= damp
            v[k] *= damp
            fastest = max(fastest, abs(u[k]) + abs(v[k]))
        }
        var changed = false
        for (s in 0..1) {
            if (letGo(s, h)) changed = true
            if (avalanches(s, h)) changed = true
            if (lift(s, h, shake)) changed = true
        }
        // A grain that came to rest just now may have made a slope too steep: not calm yet.
        val landed = floating
        move(h, shakeX, shakeY)
        if (floating != landed) changed = true
        return floating > 0 || fastest > 0.004f || changed || shake > 1.5f
    }

    /** The drift that gravity presses down now. */
    private fun downSide() = if (gravityY >= 0f) BOTTOM else TOP

    private fun idx(i: Int, j: Int) = j * nx + i

    private fun forces(h: Float, dx: Float, half: Float, dv: Float, shake: Float) {
        if (dx >= 0f && abs(dv) > 1e-3f) {
            // The droplet floats in the middle: it drags the liquid there along with it,
            // and the flow reaches the snow only through the liquid itself.
            val k = min(1f, 7f * h)
            for (j in 0 until ny) {
                val cy = (j + 0.5f) * ch
                if (cy < 0.2f || cy > 0.8f) continue
                for (i in 0 until nx) {
                    val e = abs((i + 0.5f) * cw - dx) - half
                    if (e > cw) continue
                    val w = if (e <= 0f) 1f else 1f - e / cw
                    val n = idx(i, j)
                    u[n] += (dv - u[n]) * k * w
                }
            }
        }
        if (shake > 2f) {
            // A shake whips the liquid into whirls: a blizzard in the glass, wilder for a
            // harder shake; it calms down over a couple of seconds after the shake.
            val whirls = min(12, (shake / 2f).toInt() + 2)
            repeat(whirls) {
                val ci = (rnd() * nx).toInt().coerceIn(0, nx - 1)
                val cj = (rnd() * ny).toInt().coerceIn(0, ny - 1)
                val spin = (if (rnd() < 0.5f) -1f else 1f) * min(shake, 30f) * 2.4f * h
                for (j in max(0, cj - 2)..min(ny - 1, cj + 2)) for (i in max(0, ci - 3)..min(nx - 1, ci + 3)) {
                    val rx = (i - ci) * cw
                    val ry = (j - cj) * ch
                    val f = exp(-(rx * rx + ry * ry) / 0.06f)
                    val n = idx(i, j)
                    u[n] += -ry * spin * f * 6f
                    v[n] += rx * spin * f * 6f
                }
            }
        }
    }

    private fun sample(f: FloatArray, px: Float, py: Float): Float {
        val fx = (px / cw - 0.5f).coerceIn(0f, nx - 1.001f)
        val fy = (py / ch - 0.5f).coerceIn(0f, ny - 1.001f)
        val i = fx.toInt()
        val j = fy.toInt()
        val tx = fx - i
        val ty = fy - j
        val a = f[idx(i, j)] * (1 - tx) + f[idx(i + 1, j)] * tx
        val b = f[idx(i, j + 1)] * (1 - tx) + f[idx(i + 1, j + 1)] * tx
        return a * (1 - ty) + b * ty
    }

    private fun advect(h: Float) {
        for (j in 0 until ny) for (i in 0 until nx) {
            val n = idx(i, j)
            val px = (i + 0.5f) * cw - u[n] * h
            val py = (j + 0.5f) * ch - v[n] * h
            u2[n] = sample(u, px, py)
            v2[n] = sample(v, px, py)
        }
        val tu = u; u = u2; u2 = tu
        val tv = v; v = v2; v2 = tv
    }

    /** Keeps the liquid from piling up or thinning out (it fills the glass). */
    private fun project() {
        for (j in 0 until ny) for (i in 0 until nx) {
            val ur = if (i < nx - 1) u[idx(i + 1, j)] else 0f
            val ul = if (i > 0) u[idx(i - 1, j)] else 0f
            val vd = if (j < ny - 1) v[idx(i, j + 1)] else 0f
            val vu = if (j > 0) v[idx(i, j - 1)] else 0f
            divergence[idx(i, j)] = (ur - ul) / (2 * cw) + (vd - vu) / (2 * ch)
        }
        pressure.fill(0f)
        val ax = 1f / (cw * cw)
        val ay = 1f / (ch * ch)
        repeat(24) {
            for (j in 0 until ny) for (i in 0 until nx) {
                var sum = 0f
                var w = 0f
                if (i > 0) { sum += pressure[idx(i - 1, j)] * ax; w += ax }
                if (i < nx - 1) { sum += pressure[idx(i + 1, j)] * ax; w += ax }
                if (j > 0) { sum += pressure[idx(i, j - 1)] * ay; w += ay }
                if (j < ny - 1) { sum += pressure[idx(i, j + 1)] * ay; w += ay }
                pressure2[idx(i, j)] = (sum - divergence[idx(i, j)]) / w
            }
            System.arraycopy(pressure2, 0, pressure, 0, pressure.size)
        }
        for (j in 0 until ny) for (i in 0 until nx) {
            val n = idx(i, j)
            val pr = if (i < nx - 1) pressure[idx(i + 1, j)] else pressure[n]
            val pl = if (i > 0) pressure[idx(i - 1, j)] else pressure[n]
            val pd = if (j < ny - 1) pressure[idx(i, j + 1)] else pressure[n]
            val pu = if (j > 0) pressure[idx(i, j - 1)] else pressure[n]
            u[n] -= (pr - pl) / (2 * cw)
            v[n] -= (pd - pu) / (2 * ch)
            if (i == 0 || i == nx - 1) u[n] *= 0.3f
            if (j == 0 || j == ny - 1) v[n] *= 0.3f
        }
    }

    /**
     * How far gravity must pull away from its wall for grain [i], on top of drift [s]
     * over [c], to let go. Snow holds on best next to the glass: the outer layers come off
     * at a slight overhang, the last ones only upside down; a little uneven.
     */
    private fun letGoAt(i: Int, s: Int, c: Int): Float {
        val under = ((depth[s][c] - thickness[i]) / FULL_DEPTH).coerceIn(0f, 1f)
        return LET_GO_MIN + LET_GO_SPAN * (1f - under) + (cohesion[i] - 0.5f) * 0.15f
    }

    /** How steep a slope grain [i] holds on to before it breaks off. */
    private fun holdAt(i: Int) = HOLD_SLOPE + HOLD_SPAN * cohesion[i]

    /**
     * The drift on a wall that is no longer down lets go: its grains come off one after
     * another, the ones next to the liquid first, and sink to the other side. Returns
     * whether any came off.
     */
    private fun letGo(s: Int, h: Float): Boolean {
        // How hard gravity pulls the snow away from this wall.
        val away = -sign(s) * gravityY
        if (away < LET_GO_MIN) return false
        var any = false
        for (c in 0 until columns) {
            val i0 = top[s][c]
            if (i0 < 0 || away < letGoAt(i0, s, c)) { slideCarry[s][c] = 0f; continue }
            // The further past its grip, the faster it peels off; well past it, in lumps.
            slideCarry[s][c] += (8f + 120f * (away - letGoAt(i0, s, c))) * h * (0.3f + 1.4f * rnd())
            any = true // a grain is about to let go here
            while (slideCarry[s][c] >= 1f && top[s][c] >= 0 && away >= letGoAt(top[s][c], s, c)) {
                slideCarry[s][c] -= 1f
                val lump = if (away > 0.75f && rnd() < 0.6f) 2 + (rnd() * 3f).toInt() else 1
                val ux = (rnd() - 0.5f) * 0.05f
                val uy = -sign(s) * (0.04f + 0.04f * rnd())
                var k = 0
                while (k < lump && top[s][c] >= 0 && away >= letGoAt(top[s][c], s, c)) {
                    // A lump falls together: the same push for each grain in it.
                    if (rise(s, c, ux, uy) >= 0) any = true
                    k++
                }
            }
        }
        return any
    }

    /**
     * The drift that is down behaves as a loose, crumbly heap. A slope (measured against
     * gravity) holds until it is steeper than [HOLD_SLOPE]; then it gives way and an
     * avalanche runs: the grains on top of the higher column break off one after another,
     * at an uneven pace, and tumble down, until the slope is back at [REST_SLOPE]. Between
     * the two angles nothing moves, so the snow does not creep but slips in small slides.
     */
    private fun avalanches(s: Int, h: Float): Boolean {
        if (s != downSide()) { avalanche[s].fill(false); return false }
        val g = sign(s) * gravityY
        var any = false
        for (c in 0 until columns - 1) {
            val xa = columnX(c)
            val xb = columnX(c + 1)
            // Height against gravity: the potential of the drift's surface at a column.
            val pa = -(gravityX * xa + gravityY * surface(s, c))
            val pb = -(gravityX * xb + gravityY * surface(s, c + 1))
            val diff = pa - pb
            val from = if (diff > 0f) c else c + 1
            if (top[s][from] < 0) { avalanche[s][c] = false; continue }
            // A grain on top shifts the balance by its thickness: counted in, so no grain
            // goes to and fro.
            val t = thickness[top[s][from]] * g
            val raw = abs(diff) / (columnWidth * g)
            val slope = (abs(diff) - t).coerceAtLeast(0f) / (columnWidth * g)
            // The top grain breaks off only past its own angle; a firm one holds the slope.
            if (!avalanche[s][c] && raw > holdAt(top[s][from])) avalanche[s][c] = true
            if (avalanche[s][c] && slope < REST_SLOPE) avalanche[s][c] = false
            if (!avalanche[s][c]) continue
            any = true // a slide is under way here
            // Uneven: grains break off in little bursts, faster for a steeper slope.
            slideCarry[s][c] += (14f + 60f * (slope - REST_SLOPE)) * h * (0.3f + 1.4f * rnd())
            while (slideCarry[s][c] >= 1f && top[s][from] >= 0) {
                slideCarry[s][c] -= 1f
                // A firm grain on top stops the slide here.
                if (raw < holdAt(top[s][from]) && slope < REST_SLOPE + 0.1f) { avalanche[s][c] = false; break }
                // Off it goes downhill with a little hop away from the wall.
                val down = if (from == c) 1f else -1f
                val speed = 0.3f + 0.25f * rnd()
                val hop = 0.08f + 0.12f * rnd()
                val i = rise(s, from, down * speed - hop * gravityX * sign(s), -hop * gravityY)
                if (i >= 0) {
                    x[i] += down * columnWidth * 0.4f
                    tumbling[i] = true
                    tumbleTo[i] = if (from == c) c + 1 else c
                    any = true
                }
            }
        }
        return any
    }

    /** Liquid running fast over a drift, or a hard shake, lifts the grains on top. */
    private fun lift(s: Int, h: Float, shake: Float): Boolean {
        var any = false
        for (c in 0 until columns) {
            if (top[s][c] < 0) { liftCarry[s][c] = 0f; continue }
            val cx = columnX(c)
            val sy = surface(s, c) - sign(s) * 0.04f
            val fu = sample(u, cx, sy)
            val fv = sample(v, cx, sy)
            var rate = max(0f, hypot(fu, fv) - 0.3f) * 30f
            // A shake tears the whole drift up in about half a second.
            if (shake > 2.5f) rate += (shake - 2.5f) * 3f
            if (rate <= 0f) { liftCarry[s][c] = 0f; continue }
            liftCarry[s][c] += rate * h
            while (liftCarry[s][c] >= 1f && top[s][c] >= 0) {
                liftCarry[s][c] -= 1f
                val up = 0.25f + 0.45f * rnd() + shake * 0.07f * (0.5f + rnd())
                val sideways = (rnd() - 0.5f) * (0.2f + shake * 0.08f)
                if (rise(s, c, fu * 0.8f + sideways, fv - sign(s) * up) >= 0) any = true
            }
        }
        return any
    }

    private val normal = FloatArray(2)

    /** Keeps [i] inside the glass, bouncing it off the wall. */
    private fun keepInside(i: Int, bounce: Float) {
        val d = wall(x[i], y[i], normal)
        val margin = 0.012f
        if (d > -margin) {
            x[i] -= (d + margin) * normal[0]
            y[i] -= (d + margin) * normal[1]
            val vn = vx[i] * normal[0] + vy[i] * normal[1]
            if (vn > 0f) {
                vx[i] -= bounce * vn * normal[0]
                vy[i] -= bounce * vn * normal[1]
            }
        }
    }

    /** Whether [i], moving, has come down onto the drift that is down. */
    private fun onDrift(i: Int, s: Int, c: Int): Boolean {
        val reach = (y[i] - surface(s, c)) * sign(s) + thickness[i] * 0.5f
        val towards = (vx[i] * gravityX + vy[i] * gravityY)
        return reach >= -0.012f && towards >= -0.02f
    }

    private fun move(h: Float, shakeX: Float, shakeY: Float) {
        val down = downSide()
        for (i in 0 until flakes) {
            if (lying[i]) continue
            if (tumbling[i]) {
                tumble(i, h, down)
                continue
            }
            val s = size[i]
            val fu = sample(u, x[i], y[i])
            val fv = sample(v, x[i], y[i])
            // Slow sinking, bigger flakes a little faster; they follow the liquid softly.
            val sink = 0.035f + 0.08f * s
            val tau = 0.14f + 0.18f * s
            phase[i] += h * (3.2f + 2.4f * s)
            val sway = sin(phase[i]) * sink * 0.8f
            val tx = fu + sink * gravityX - sway * gravityY
            val ty = fv + sink * gravityY + sway * gravityX
            val k = min(1f, h / tau)
            vx[i] += (tx - vx[i]) * k
            vy[i] += (ty - vy[i]) * k
            // Heavier than the liquid, they lag behind the glass when it is shaken, and the
            // shaken liquid tosses them about.
            vx[i] += -shakeX * 0.12f * h * (0.6f + s)
            vy[i] += -shakeY * 0.12f * h * (0.6f + s)
            val gust = hypot(shakeX, shakeY)
            if (gust > 2f) {
                vx[i] += (rnd() - 0.5f) * gust * 0.18f * h
                vy[i] += (rnd() - 0.5f) * gust * 0.18f * h
            }
            // A little flutter of the small ones.
            vx[i] += (rnd() - 0.5f) * 0.25f * h * (1.2f - s)
            vy[i] += (rnd() - 0.5f) * 0.25f * h * (1.2f - s)
            x[i] += vx[i] * h
            y[i] += vy[i] * h
            keepInside(i, 1.25f)
            val c = column(x[i])
            if (onDrift(i, down, c)) lie(i, down, c)
        }
    }

    /** One tumbling grain: a heavy crumb hopping down the slope until it settles. */
    private fun tumble(i: Int, h: Float, down: Int) {
        vx[i] += gravityX * TUMBLE_GRAVITY * h
        vy[i] += gravityY * TUMBLE_GRAVITY * h
        val drag = exp(-1.2f * h)
        vx[i] *= drag
        vy[i] *= drag
        x[i] += vx[i] * h
        y[i] += vy[i] * h
        keepInside(i, 1.4f)
        val c = column(x[i])
        if (!onDrift(i, down, c)) return
        // On the snow again: still fast and on a steep bit — it bounces on down;
        // otherwise it stops there.
        val along = vx[i] * gravityY - vy[i] * gravityX
        val next = (c + if (along > 0f) 1 else -1).coerceIn(0, columns - 1)
        val pc = -(gravityX * columnX(c) + gravityY * surface(down, c))
        val pn = -(gravityX * columnX(next) + gravityY * surface(down, next))
        val g = abs(gravityY).coerceAtLeast(0.15f)
        if (abs(along) > 0.18f && pc - pn > REST_SLOPE * columnWidth * g) {
            y[i] = surface(down, c) - sign(down) * thickness[i] * 0.5f
            val bounce = abs(along) * 0.35f
            vx[i] = vx[i] * 0.62f - bounce * gravityX
            vy[i] = vy[i] * 0.62f - bounce * gravityY
        } else {
            // Thrown back up the slope (off the end wall, say)? It rests no higher than
            // where it set off for, or it would only start the slide again.
            val target = tumbleTo[i].coerceIn(0, columns - 1)
            val pt = -(gravityX * columnX(target) + gravityY * surface(down, target))
            if (pc > pt + REST_SLOPE * columnWidth * g) {
                x[i] = columnX(target) + (rnd() - 0.5f) * columnWidth * 0.8f
                lie(i, down, target)
            } else {
                lie(i, down, c)
            }
        }
    }

    /** For tests: each flake is either lying in exactly one stack or moving. */
    internal fun consistent(): Boolean {
        var stacked = 0
        for (s in 0..1) for (c in 0 until columns) {
            var i = top[s][c]
            var sum = 0f
            while (i >= 0) {
                if (!lying[i] || side[i] != s) return false
                stacked++
                sum += thickness[i]
                i = below[i]
            }
            if (abs(sum - depth[s][c]) > 1e-3f) return false
        }
        return stacked + floating == flakes && (0 until flakes).count { !lying[it] } == floating
    }

    /** For test messages. */
    internal fun describe(): String =
        "floating $floating notLying ${(0 until flakes).count { !lying[it] }} bottom ${depth[BOTTOM].sum()} top ${depth[TOP].sum()}"

    /** Fluid speed under a point, for tests. */
    internal fun liquidAt(px: Float, py: Float): Float = hypot(sample(u, px, py), sample(v, px, py))

    companion object {
        const val BOTTOM = 0
        const val TOP = 1
        /** Depth of all the snow when it lies evenly over the flat bottom, bar heights. */
        const val FULL_DEPTH = 0.22f
        /** A slope holds up to this (about 24°) for the loosest grain, ... */
        private const val HOLD_SLOPE = 0.45f
        /** ...up to this much steeper (to about 40°) for the firmest. */
        private const val HOLD_SPAN = 0.4f
        /** Snow on a wall turned away lets go past this pull away from it (about 12°)... */
        private const val LET_GO_MIN = 0.2f
        /** ...and the firmest only past this much more (about 64°; all of it upside down). */
        private const val LET_GO_SPAN = 0.7f
        /** ...and a slide stops once it is down to this (about 19°). */
        private const val REST_SLOPE = 0.34f
        /** Gravity on a tumbling grain, bar heights a second². */
        private const val TUMBLE_GRAVITY = 2.6f
        /** Least part of gravity across the bar (see [step]). */
        private const val MIN_ACROSS = 0.3f
        /** How much more a slight tilt counts than it is (fading out at a quarter turn). */
        private const val TILT_BOOST = 1.1f
        private const val HALF_PI = (Math.PI / 2).toFloat()
    }
}

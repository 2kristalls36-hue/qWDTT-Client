package com.wdtt.client.ui.festive

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A snow globe the shape of the tab bar: liquid, and snow flakes that sink through it and
 * pile up on the bottom. Lengths are in bar heights (the bar is [aspect] long and 1 high,
 * y down); times in seconds.
 *
 * Every flake lives all the time — none is ever removed or made — so nothing vanishes or
 * appears from nowhere:
 * - In the liquid a flake is carried by the flow and sinks slowly, at the speed of its
 *   size, swaying as it falls; a shake throws it about (heavier than the liquid, it lags).
 * - Touching the snow it stops where it touched and lies on top of that column of the
 *   drift (the columns are stacks, the newest flake on top).
 * - The flake on top of a column is lifted again when the liquid runs fast enough over it
 *   or the phone is shaken, and rolls off to the next column when the drift is steeper
 *   there than its angle of repose (measured against gravity, so a tilt makes the snow
 *   creep to the lower side).
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
    /** Lying in the drift (else in the liquid). */
    val lying = BooleanArray(flakes)
    /** How much a flake adds to the height of its column when it lies. */
    private val thickness = FloatArray(flakes)
    /** A flake rolling down the drift: the column it rolls to, else -1. */
    private val rollTo = IntArray(flakes) { -1 }

    // ---- The drift: a stack of lying flakes per column ----
    val columns = 64
    val columnWidth = aspect / columns
    /** Height of the drift per column. */
    val depth = FloatArray(columns)
    private val top = IntArray(columns) { -1 }
    private val below = IntArray(flakes) { -1 }
    private val liftCarry = FloatArray(columns)
    private val slideCarry = FloatArray(columns)

    /** Flakes in the liquid now. */
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
        }
    }

    // ---------------------------------------------------------------- shape of the glass

    /** Lowest point of the glass at [px] (the bottom rises in the rounded ends). */
    fun bottomAt(px: Float): Float {
        val d = abs(px - aspect / 2f) - (aspect / 2f - corner)
        if (d <= 0f) return 1f
        val k = min(d, corner)
        return 1f - corner + sqrt(max(0f, corner * corner - k * k))
    }

    private fun column(px: Float) = (px / columnWidth).toInt().coerceIn(0, columns - 1)
    private fun columnX(c: Int) = (c + 0.5f) * columnWidth

    /** Top of the drift at [px], smoothed between columns (for drawing). */
    fun depthAt(px: Float): Float {
        val f = (px / columnWidth - 0.5f).coerceIn(0f, columns - 1f)
        val i = f.toInt().coerceAtMost(columns - 2)
        val t = f - i
        return depth[i] * (1 - t) + depth[i + 1] * t
    }

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

    /** The globe just shaken: every flake somewhere in the liquid, nothing lying. */
    fun scatter() {
        depth.fill(0f)
        top.fill(-1)
        for (i in 0 until flakes) {
            lying[i] = false
            x[i] = corner * 0.4f + rnd() * (aspect - corner * 0.8f)
            y[i] = 0.06f + rnd() * 0.82f
            vx[i] = 0f
            vy[i] = 0f
        }
        floating = flakes
    }

    /** Everything lying on the bottom, evenly (for no animation). */
    fun settleAll() {
        depth.fill(0f)
        top.fill(-1)
        floating = flakes
        for (i in 0 until flakes) {
            lying[i] = false
            rollTo[i] = -1
            // Spread over the flat part of the bottom, then let the drift even out.
            val px = corner + (i + 0.5f) / flakes * (aspect - 2f * corner)
            x[i] = px
            lie(i, column(px))
        }
    }

    /** [i] lies down on top of column [c], where it is (only its height is fitted). */
    private fun lie(i: Int, c: Int) {
        if (!lying[i]) floating--
        lying[i] = true
        rollTo[i] = -1
        vx[i] = 0f
        vy[i] = 0f
        y[i] = bottomAt(columnX(c)) - depth[c] - thickness[i] * 0.5f
        depth[c] += thickness[i]
        below[i] = top[c]
        top[c] = i
    }

    /** The flake on top of [c] gets up into the liquid with velocity ([ux], [uy]). */
    private fun rise(c: Int, ux: Float, uy: Float): Boolean {
        val i = top[c]
        if (i < 0) return false
        top[c] = below[i]
        below[i] = -1
        depth[c] = max(0f, depth[c] - thickness[i])
        lying[i] = false
        floating++
        vx[i] = ux
        vy[i] = uy
        return true
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
        // Gravity: a tilt counts a little more than it is, so a slight one shows; lying
        // flat the phone still lets the snow sink down the screen.
        var gX = tiltX * TILT_GAIN
        var gY = tiltY
        val gl = hypot(gX, gY)
        if (gl > 0.05f) { gX /= gl; gY /= gl } else { gX = 0f; gY = 1f }
        if (gY < 0.3f) { gY = 0.3f; gX = gX.coerceIn(-0.95f, 0.95f) }
        val gn = hypot(gX, gY)
        gravityX = gX / gn
        gravityY = gY / gn

        val shake = hypot(shakeX, shakeY)
        forces(h, dropletX, dropletHalf, dropletV, shake)
        advect(h)
        project()
        // The liquid calms slowly, so a whirl keeps going for a while.
        val damp = exp(-0.8f * h)
        var fastest = 0f
        for (k in u.indices) {
            u[k] *= damp
            v[k] *= damp
            fastest = max(fastest, abs(u[k]) + abs(v[k]))
        }
        val rolled = roll(h) || (0 until flakes).any { rollTo[it] >= 0 }
        val lifted = lift(h, shake)
        move(h, shakeX, shakeY)
        return floating > 0 || fastest > 0.004f || rolled || lifted || shake > 1.5f
    }

    private fun idx(i: Int, j: Int) = j * nx + i

    private fun forces(h: Float, dx: Float, half: Float, dv: Float, shake: Float) {
        if (dx >= 0f && abs(dv) > 1e-3f) {
            // The droplet floats in the middle: it drags the liquid there along with it,
            // and the flow reaches the snow below only through the liquid itself.
            val k = min(1f, 7f * h)
            for (j in 0 until ny) {
                val cy = (j + 0.5f) * ch
                if (cy < 0.12f || cy > 0.8f) continue
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
     * Where the drift is steeper than its angle of repose (against gravity), the flake on
     * top of the higher column rolls off towards the lower one: it gets up with a small
     * push that way and comes down there by itself. Returns whether any rolled.
     */
    private fun roll(h: Float): Boolean {
        var any = false
        val allowed = REPOSE * columnWidth * gravityY
        for (c in 0 until columns - 1) {
            val xa = columnX(c)
            val xb = columnX(c + 1)
            // Height against gravity: the potential of the drift's surface at a column.
            val pa = -(gravityX * xa + gravityY * (bottomAt(xa) - depth[c]))
            val pb = -(gravityX * xb + gravityY * (bottomAt(xb) - depth[c + 1]))
            val diff = pa - pb
            val (from, to) = when {
                diff > 0f -> c to c + 1
                else -> c + 1 to c
            }
            // Moving the top flake lowers one side and raises the other by its thickness:
            // it rolls only if the slope stays at least the other way's repose after it,
            // so no flake keeps rolling to and fro.
            val t = if (top[from] >= 0) thickness[top[from]] * gravityY else 0f
            if (top[from] < 0 || abs(diff) <= allowed + t) { slideCarry[c] = 0f; continue }
            // Faster for a steeper slope; a few flakes a second at the least.
            slideCarry[c] += (10f + 260f * (abs(diff) - allowed - t)) * SLIDE_RATE * h
            while (slideCarry[c] >= 1f && top[from] >= 0) {
                slideCarry[c] -= 1f
                val i = top[from]
                if (rise(from, 0f, 0f)) {
                    rollTo[i] = to
                    any = true
                }
            }
        }
        return any
    }

    /** Liquid running fast over the drift, or a hard shake, lifts the flakes on top. */
    private fun lift(h: Float, shake: Float): Boolean {
        var any = false
        for (c in 0 until columns) {
            if (top[c] < 0) { liftCarry[c] = 0f; continue }
            val cx = columnX(c)
            val sy = bottomAt(cx) - depth[c] - 0.04f
            val fu = sample(u, cx, sy)
            val fv = sample(v, cx, sy)
            var rate = max(0f, hypot(fu, fv) - 0.3f) * 30f
            // A shake tears the whole drift up in about half a second.
            if (shake > 2.5f) rate += (shake - 2.5f) * 3f
            if (rate <= 0f) { liftCarry[c] = 0f; continue }
            liftCarry[c] += rate * h
            while (liftCarry[c] >= 1f && top[c] >= 0) {
                liftCarry[c] -= 1f
                val up = 0.25f + 0.45f * rnd() + shake * 0.07f * (0.5f + rnd())
                val side = (rnd() - 0.5f) * (0.2f + shake * 0.08f)
                if (rise(c, fu * 0.8f + side, fv - up * gravityY)) any = true
            }
        }
        return any
    }

    private val normal = FloatArray(2)

    private fun move(h: Float, shakeX: Float, shakeY: Float) {
        for (i in 0 until flakes) {
            if (lying[i]) continue
            if (rollTo[i] >= 0) {
                // Rolling: it slides over the drift to the next column and lies there.
                val c = rollTo[i]
                val tx = columnX(c) + (hash01(i, 3) - 0.5f) * columnWidth * 0.8f
                val step = ROLL_SPEED * h
                x[i] = if (abs(tx - x[i]) <= step) tx else x[i] + step * (if (tx > x[i]) 1f else -1f)
                val col = column(x[i])
                y[i] = bottomAt(columnX(col)) - depth[col] - thickness[i] * 0.5f
                if (x[i] == tx) lie(i, c)
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
            // The wall of the glass.
            val d = wall(x[i], y[i], normal)
            val margin = 0.012f
            if (d > -margin) {
                x[i] -= (d + margin) * normal[0]
                y[i] -= (d + margin) * normal[1]
                val vn = vx[i] * normal[0] + vy[i] * normal[1]
                if (vn > 0f) {
                    vx[i] -= 1.25f * vn * normal[0]
                    vy[i] -= 1.25f * vn * normal[1]
                }
            }
            // Touching the drift (or the bare bottom), moving down: it lies there.
            val c = column(x[i])
            val surface = bottomAt(columnX(c)) - depth[c]
            if (y[i] + thickness[i] * 0.5f >= surface - 0.012f && gravityY * vy[i] + gravityX * vx[i] >= -0.02f) {
                lie(i, c)
            }
        }
    }

    /** For tests: each flake is either lying in exactly one column or in the liquid. */
    internal fun consistent(): Boolean {
        var stacked = 0
        for (c in 0 until columns) {
            var i = top[c]
            var sum = 0f
            while (i >= 0) {
                if (!lying[i]) return false
                stacked++
                sum += thickness[i]
                i = below[i]
            }
            if (abs(sum - depth[c]) > 1e-3f) return false
        }
        return stacked + floating == flakes && (0 until flakes).count { !lying[it] } == floating
    }

    /** For test messages. */
    internal fun describe(): String {
        var stacked = 0
        for (c in 0 until columns) { var i = top[c]; while (i >= 0) { stacked++; i = below[i] } }
        return "stacked $stacked floating $floating notLying ${(0 until flakes).count { !lying[it] }} maxDepth ${depth.max()}"
    }

    /** Fluid speed under a point, for tests. */
    internal fun liquidAt(px: Float, py: Float): Float = hypot(sample(u, px, py), sample(v, px, py))

    companion object {
        /** Depth of all the snow when it lies evenly over the flat bottom, bar heights. */
        const val FULL_DEPTH = 0.22f
        /** Fluffy snow under liquid lies almost flat: a low angle of repose (about 9°). */
        private const val REPOSE = 0.16f
        private const val SLIDE_RATE = 1f
        /** How fast a flake rolls down the drift, bar heights a second. */
        private const val ROLL_SPEED = 0.5f
        /** How much more a tilt counts than it is. */
        private const val TILT_GAIN = 2.2f
    }
}

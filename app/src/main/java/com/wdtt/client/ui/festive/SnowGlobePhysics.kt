package com.wdtt.client.ui.festive

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A snow globe the shape of the tab bar: liquid, snow flakes in it and snow lying on the
 * bottom. Lengths are in bar heights (the bar is [aspect] long and 1 high, y down);
 * times in seconds.
 *
 * - The liquid is a coarse grid (stable fluids: forces, advection, pressure projection,
 *   damping): a moving droplet drags it along and it swirls round the droplet's ends.
 * - Flakes are carried by the liquid and sink through it, each at the speed of its size,
 *   swaying as they fall; a shake of the phone throws them about (they lag behind the
 *   glass, being heavier than the liquid).
 * - Flakes that touch the snow join it. The lying snow is a height per column; it slides
 *   where it is steeper than its angle of repose (along the phone's tilt), and the moving
 *   liquid lifts it again where it flows fast enough over it.
 * - When nothing moves, [step] says so and the caller stops calling it.
 */
class SnowGlobePhysics(
    val aspect: Float,
    /** Corner radius of the bar, in bar heights (0.5 — a pill). */
    val corner: Float = 0.5f,
    val maxFlakes: Int = 420,
    seed: Int = 7,
) {
    // ---- Liquid ----
    val nx = 44
    val ny = 8
    private val cw = aspect / nx
    private val ch = 1f / ny
    private var u = FloatArray(nx * ny)
    private var v = FloatArray(nx * ny)
    private var u2 = FloatArray(nx * ny)
    private var v2 = FloatArray(nx * ny)
    private val pressure = FloatArray(nx * ny)
    private val pressure2 = FloatArray(nx * ny)
    private val divergence = FloatArray(nx * ny)

    // ---- Lying snow ----
    val columns = 120
    val columnWidth = aspect / columns
    /** Depth of the lying snow per column. */
    val depth = FloatArray(columns)
    private val erodeCarry = FloatArray(columns)

    // ---- Flakes ----
    val x = FloatArray(maxFlakes)
    val y = FloatArray(maxFlakes)
    private val vx = FloatArray(maxFlakes)
    private val vy = FloatArray(maxFlakes)
    /** Size of a flake, 0..1. */
    val size = FloatArray(maxFlakes)
    private val phase = FloatArray(maxFlakes)
    /** Flakes in the liquid now: indices 0 until [count]. */
    var count = 0
        private set

    /** All the snow, as the area it covers when it lies (bar heights²). */
    val totalSnow = FULL_DEPTH * aspect
    private val meanFlake = totalSnow / maxFlakes

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

    private fun flakeVolume(s: Float) = meanFlake * (0.45f + 1.1f * s * s) / 0.82f

    // ---------------------------------------------------------------- shape of the glass

    /** Lowest point of the glass at [px] (the bottom rises in the rounded ends). */
    fun bottomAt(px: Float): Float {
        val d = abs(px - aspect / 2f) - (aspect / 2f - corner)
        if (d <= 0f) return 1f
        val k = min(d, corner)
        return 1f - corner + sqrt(max(0f, corner * corner - k * k))
    }

    /** Top of the lying snow at [px]. */
    fun surfaceAt(px: Float): Float {
        val f = (px / columnWidth - 0.5f).coerceIn(0f, columns - 1f)
        val i = f.toInt().coerceAtMost(columns - 2)
        val t = f - i
        val h = depth[i] * (1 - t) + depth[i + 1] * t
        return bottomAt(px) - h
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
        count = 0
        while (count < maxFlakes) {
            val s = rnd()
            val px = corner * 0.5f + rnd() * (aspect - corner)
            val py = 0.08f + rnd() * 0.8f
            add(px, py, 0f, 0f, s)
        }
    }

    /** Everything lying on the bottom (for no animation). */
    fun settleAll() {
        count = 0
        val each = totalSnow / (aspect - corner)
        for (c in 0 until columns) {
            val cx = (c + 0.5f) * columnWidth
            depth[c] = if (cx > corner * 0.5f && cx < aspect - corner * 0.5f) each else 0f
        }
        repeat(400) { slide(1f / 30f) }
    }

    private fun add(px: Float, py: Float, pvx: Float, pvy: Float, s: Float): Boolean {
        if (count >= maxFlakes) return false
        x[count] = px; y[count] = py; vx[count] = pvx; vy[count] = pvy
        size[count] = s; phase[count] = rnd() * 6.28f
        count++
        return true
    }

    private fun remove(i: Int) {
        val last = count - 1
        x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
        size[i] = size[last]; phase[i] = phase[last]
        count--
    }

    // ---------------------------------------------------------------- one step

    /**
     * Advances by [dt]. [dropletX], [dropletHalf] and [dropletV] describe the droplet
     * (centre, half width, speed along the bar); [tiltX], [tiltY] the direction of
     * gravity on the screen; [shakeX], [shakeY] the phone's own acceleration on the screen
     * (m/s²). Returns whether anything still moves.
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
        // Gravity: lying flat the phone still lets the snow fall down the screen.
        val gl = hypot(tiltX, tiltY)
        var gX = if (gl > 0.05f) tiltX / gl else 0f
        var gY = if (gl > 0.05f) tiltY / gl else 1f
        if (gY < 0.35f) { gY = 0.35f; gX = gX.coerceIn(-0.94f, 0.94f) }
        val gn = hypot(gX, gY)
        gravityX = gX / gn
        gravityY = gY / gn

        val shake = hypot(shakeX, shakeY)
        forces(h, dropletX, dropletHalf, dropletV, shake)
        advect(h)
        project()
        val damp = exp(-1.15f * h)
        var fastest = 0f
        for (k in u.indices) {
            u[k] *= damp
            v[k] *= damp
            fastest = max(fastest, abs(u[k]) + abs(v[k]))
        }
        val moved = slide(h)
        erode(h, shake)
        flakes(h, shakeX, shakeY)
        return count > 0 || fastest > 0.004f || moved > 1e-5f || shake > 1.5f
    }

    private fun idx(i: Int, j: Int) = j * nx + i

    private fun forces(h: Float, dx: Float, half: Float, dv: Float, shake: Float) {
        if (dx >= 0f && (abs(dv) > 1e-3f)) {
            // The droplet drags the liquid it covers along with it.
            val k = min(1f, 7f * h)
            for (j in 0 until ny) {
                val cy = (j + 0.5f) * ch
                // The droplet floats in the middle: it drags the liquid there, and the flow
                // reaches the snow below only through the liquid itself.
                if (cy < 0.12f || cy > 0.8f) continue
                for (i in 0 until nx) {
                    val cx = (i + 0.5f) * cw
                    val e = abs(cx - dx) - half
                    if (e > cw) continue
                    val w = if (e <= 0f) 1f else 1f - e / cw
                    val n = idx(i, j)
                    u[n] += (dv - u[n]) * k * w
                }
            }
        }
        if (shake > 2f) {
            // A shake stirs the liquid: small whirls, stronger for a harder shake.
            val whirls = min(6, (shake / 3f).toInt() + 1)
            repeat(whirls) {
                val ci = (rnd() * nx).toInt().coerceIn(0, nx - 1)
                val cj = (rnd() * ny).toInt().coerceIn(0, ny - 1)
                val spin = (if (rnd() < 0.5f) -1f else 1f) * min(shake, 25f) * 0.035f * h * 30f
                for (j in max(0, cj - 2)..min(ny - 1, cj + 2)) for (i in max(0, ci - 3)..min(nx - 1, ci + 3)) {
                    val rx = (i - ci) * cw
                    val ry = (j - cj) * ch
                    val f = exp(-(rx * rx + ry * ry) / (0.06f))
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
     * The lying snow slides where it is steeper than its angle of repose, measured
     * against gravity, so a tilted phone makes it creep to the lower side. Returns how
     * much moved.
     */
    private fun slide(h: Float): Float {
        var moved = 0f
        // Fluffy snow under liquid lies almost flat: a low angle of repose (about 7°).
        val repose = 0.12f * columnWidth
        val rate = min(1f, 9f * h)
        repeat(2) { pass ->
            val range = if (pass == 0) 0 until columns - 1 else (columns - 2) downTo 0
            for (c in range) {
                val a = c
                val b = c + 1
                val xa = (a + 0.5f) * columnWidth
                val xb = (b + 0.5f) * columnWidth
                // Height against gravity: the potential of the snow surface at a column.
                val pa = -(gravityX * xa + gravityY * (bottomAt(xa) - depth[a]))
                val pb = -(gravityX * xb + gravityY * (bottomAt(xb) - depth[b]))
                val diff = pa - pb
                val allowed = repose * gravityY
                if (diff > allowed && depth[a] > 0f) {
                    val m = min(depth[a], (diff - allowed) / (2f * gravityY) * rate)
                    depth[a] -= m; depth[b] += m; moved += m
                } else if (-diff > allowed && depth[b] > 0f) {
                    val m = min(depth[b], (-diff - allowed) / (2f * gravityY) * rate)
                    depth[b] -= m; depth[a] += m; moved += m
                }
            }
        }
        return moved
    }

    /** Liquid flowing fast over the snow, or a hard shake, lifts flakes off it. */
    private fun erode(h: Float, shake: Float) {
        var spawned = 0
        for (c in 0 until columns) {
            val cx = (c + 0.5f) * columnWidth
            val sy = bottomAt(cx) - depth[c] - 0.04f
            val fu = sample(u, cx, sy)
            val fv = sample(v, cx, sy)
            val speed = hypot(fu, fv)
            var rate = max(0f, speed - 0.3f) * 0.38f
            if (shake > 3f) rate += (shake - 3f) * 0.012f
            if (rate <= 0f || depth[c] <= 1e-6f) {
                // Nothing lifts here now: what was taken but not yet a flake lies down again.
                depth[c] += erodeCarry[c] / columnWidth
                erodeCarry[c] = 0f
                continue
            }
            val take = min(depth[c], rate * h)
            depth[c] -= take
            erodeCarry[c] += take * columnWidth
            while (erodeCarry[c] > 0f && spawned < 60) {
                val s = rnd()
                val vol = flakeVolume(s)
                if (erodeCarry[c] < vol * 0.5f) break
                val px = cx + (rnd() - 0.5f) * columnWidth * 2f
                val lift = 0.25f + 0.5f * rnd() + shake * 0.02f
                if (!add(px, sy, fu * 0.8f + (rnd() - 0.5f) * 0.2f, fv - lift * gravityY, s)) {
                    // No room for more flakes: the rest stays where it lay.
                    depth[c] += erodeCarry[c] / columnWidth
                    erodeCarry[c] = 0f
                    break
                }
                erodeCarry[c] -= vol
                spawned++
            }
        }
    }

    private val normal = FloatArray(2)

    private fun flakes(h: Float, shakeX: Float, shakeY: Float) {
        var i = 0
        while (i < count) {
            val s = size[i]
            val fu = sample(u, x[i], y[i])
            val fv = sample(v, x[i], y[i])
            // Big flakes sink faster and follow the liquid less closely.
            val settle = 0.075f + 0.2f * s
            val tau = 0.07f + 0.13f * s
            phase[i] += h * (7f + 5f * s)
            val sway = sin(phase[i]) * settle * 0.55f
            val tx = fu + settle * gravityX - sway * gravityY
            val ty = fv + settle * gravityY + sway * gravityX
            val k = min(1f, h / tau)
            vx[i] += (tx - vx[i]) * k
            vy[i] += (ty - vy[i]) * k
            // Heavier than the liquid, they lag behind the glass when it is shaken.
            vx[i] += -shakeX * 0.05f * h * (0.6f + s)
            vy[i] += -shakeY * 0.05f * h * (0.6f + s)
            // A little flutter of the small ones.
            vx[i] += (rnd() - 0.5f) * 0.35f * h * (1.2f - s)
            vy[i] += (rnd() - 0.5f) * 0.35f * h * (1.2f - s)
            x[i] += vx[i] * h
            y[i] += vy[i] * h
            // Touching the snow (or the bare bottom): it joins the snow lying there.
            if (y[i] >= surfaceAt(x[i]) - 0.016f && gravityY * vy[i] + gravityX * vx[i] >= -0.05f) {
                deposit(x[i], flakeVolume(s))
                remove(i)
                continue
            }
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
            i++
        }
    }

    private fun deposit(px: Float, vol: Float) {
        // Spread over five columns, as a flake settles into the fluff.
        val c = (px / columnWidth).toInt().coerceIn(0, columns - 1)
        val hgt = vol / columnWidth
        for ((o, w) in DEPOSIT) depth[(c + o).coerceIn(0, columns - 1)] += hgt * w
    }

    /** Snow in the liquid plus snow lying (for tests: nothing is lost or made). */
    fun snowNow(): Float {
        var s = 0f
        for (c in 0 until columns) s += depth[c] * columnWidth
        for (c in 0 until columns) s += erodeCarry[c]
        for (i in 0 until count) s += flakeVolume(size[i])
        return s
    }

    /** Fluid speed under a point, for tests. */
    internal fun liquidAt(px: Float, py: Float): Float = hypot(sample(u, px, py), sample(v, px, py))

    /** Height of the lying snow at a point, for drawing: smoothed between columns. */
    fun depthAt(px: Float): Float {
        val f = (px / columnWidth - 0.5f).coerceIn(0f, columns - 1f)
        val i = f.toInt().coerceAtMost(columns - 2)
        val t = f - i
        return depth[i] * (1 - t) + depth[i + 1] * t
    }

    companion object {
        private val DEPOSIT = listOf(-2 to 0.1f, -1 to 0.2f, 0 to 0.4f, 1 to 0.2f, 2 to 0.1f)
        /** Depth of all the snow when it lies evenly over the flat bottom, bar heights. */
        const val FULL_DEPTH = 0.13f
    }

    @Suppress("unused")
    private fun unusedFloor(v: Float) = floor(v)
}

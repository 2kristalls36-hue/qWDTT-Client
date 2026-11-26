package com.wdtt.client.ui.festive

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A snow globe the shape of the tab bar: liquid with snow hanging in it, and an even drift
 * of snow lying along the bottom. Lengths are in bar heights (the bar is [aspect] long and
 * 1 high, y down); times in seconds.
 *
 * - The drift: little round grains packed level along the bottom, [CRUST_DEPTH] deep (a
 *   level bowl in the rounded ends). It lies there for good: nothing stirs it.
 * - The rest of the snow hangs in the liquid (as heavy as the liquid, near enough): it fills
 *   the glass over the drift evenly, each flake keeping a little apart from the others, and
 *   drifts very slowly with the faint currents. About one in three of the hanging flakes is
 *   a crystal that tips and turns slowly.
 * - The droplet drags the liquid as it moves and the snow swirls after it; a quick move of
 *   the phone sloshes the liquid against the glass and whirls the snow up, until it calms
 *   down. Wherever the snow has bunched up it is pushed out towards where it is thin, so
 *   within seconds it fills the glass evenly again. Then only the slow drift is left, and
 *   [lively] says so.
 * - The liquid is a coarse grid (stable fluids: forces, advection, pressure projection,
 *   damping).
 * Every flake lives all the time: none is ever removed or made.
 */
class SnowGlobePhysics(
    val aspect: Float,
    /** Corner radius of the bar, in bar heights (0.5 — a pill). */
    val corner: Float = 0.5f,
    /** Where the top of the drift is, in bar heights from the top (what rides on it). */
    level: Float = 1f - CRUST_DEPTH,
    /** How many flakes hang in the liquid. */
    hanging: Int = 460,
    seed: Int = 7,
) {
    /** Where the top of the drift is (it is level). */
    val driftTop = level.coerceIn(0.4f, 1f - 2.5f * GRAIN)
    /** Radius of a grain of the drift's top, bar heights. */
    val grain = GRAIN
    /** The places of the grains along the top of the drift, x and y in turn. */
    private val bed = layBed(aspect, corner, driftTop)
    /** How many grains make the top of the drift (the first flakes). */
    val crustCount = bed.size / 2
    val flakes = crustCount + hanging

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
    /** Weight of a flake, one of [WEIGHTS]: the light ones follow the liquid closely. */
    val weight = FloatArray(flakes)
    private val phase = FloatArray(flakes)
    /** Hanging in the liquid (all but the drift). */
    val suspended = BooleanArray(flakes)
    /** In the drift's top (the first [crustCount] flakes). */
    val lying = BooleanArray(flakes)
    /** Radius of each grain of the drift. */
    val radius = FloatArray(flakes)
    /** A grain on the very top of the drift (drawn brighter). */
    private val top = BooleanArray(flakes)

    /** The push a hanging flake gets from its neighbours and from where the snow is thick. */
    private val spreadX = FloatArray(flakes)
    private val spreadY = FloatArray(flakes)
    /** Spacing of the hanging snow, and a grid for finding the hanging neighbours. */
    private val spacing: Float
    private val scols: Int
    private val srows: Int
    private val sStart: IntArray
    private val sItems = IntArray(flakes)
    private val sOf = IntArray(flakes)
    /** A coarse grid of how thick the hanging snow is, against how thick it is when even. */
    private val dcols: Int
    private val drows: Int
    private val dw: Float
    private val dh: Float
    private val thick: FloatArray
    private val room: FloatArray
    private val slopeX: FloatArray
    private val slopeY: FloatArray

    /**
     * Something stirs: the liquid runs, the phone is shaken, the snow spreads out again.
     * Otherwise only the hanging snow drifts, so slowly that half the frames will do.
     */
    var lively = true
        private set

    /**
     * A slow wandering of each flake in the liquid (the faint currents that never quite
     * die in a globe), so the snow drifts on as an even cloud instead of standing still.
     */
    private val driftX = FloatArray(flakes)
    private val driftY = FloatArray(flakes)
    /**
     * A snowflake proper (a flat six-armed crystal), not a crumb: it sinks like a little
     * disc, gliding to one side and the other as it tips over, and turns slowly.
     */
    val crystal = BooleanArray(flakes)
    /** How far a crystal is tipped from lying flat across its fall, radians. */
    val tip = FloatArray(flakes)
    private val tipRate = FloatArray(flakes)
    /** How far a crystal has turned in the plane of the screen, radians. */
    val spin = FloatArray(flakes)
    private val spinRate = FloatArray(flakes)

    private var noise = seed * 7919

    private fun rnd(): Float {
        noise = noise * 1103515245 + 12345
        return ((noise ushr 8) and 0xFFFF) / 65535f
    }

    private val glassNormal = FloatArray(2)

    private fun len(a: Float, b: Float) = sqrt(a * a + b * b)

    init {
        // The hanging snow spreads evenly through the glass over the drift.
        var inside = 0
        for (m in 0 until 60) for (n in 0 until 300) {
            val px = (n + 0.5f) / 300f * aspect
            val py = (m + 0.5f) / 60f * driftTop
            if (wall(px, py, glassNormal) < 0f) inside++
        }
        val area = inside / (60f * 300f) * aspect * driftTop
        spacing = sqrt(area / max(1, flakes - crustCount)) * SPREAD_REACH
        scols = max(1, (aspect / spacing).toInt() + 1)
        srows = max(1, (1f / spacing).toInt() + 1)
        sStart = IntArray(scols * srows + 1)
        drows = 4
        dh = driftTop / drows
        dcols = max(1, (aspect / dh).toInt())
        dw = aspect / dcols
        thick = FloatArray(dcols * drows)
        room = FloatArray(dcols * drows)
        slopeX = FloatArray(dcols * drows)
        slopeY = FloatArray(dcols * drows)
        // How much of each cell of the coarse grid is room for the hanging snow.
        for (cj in 0 until drows) for (ci in 0 until dcols) {
            var inside = 0
            for (m in 0 until 8) for (n in 0 until 8) {
                val px = (ci + (n + 0.5f) / 8f) * dw
                val py = (cj + (m + 0.5f) / 8f) * dh
                if (wall(px, py, glassNormal) < 0f) inside++
            }
            room[cj * dcols + ci] = inside / 64f
        }
        for (i in 0 until flakes) {
            size[i] = rnd()
            phase[i] = rnd() * 6.28f
            radius[i] = grain * (0.8f + 0.4f * rnd())
            // The class: mostly the lighter ones; bigger flakes lean to the heavier.
            val r = (rnd() * 0.75f + size[i] * 0.25f)
            weight[i] = WEIGHTS[(r * r * WEIGHTS.size).toInt().coerceIn(0, WEIGHTS.size - 1)]
            suspended[i] = i >= crustCount
            lying[i] = i < crustCount
            // About one in three of the hanging ones is a crystal: on the bigger side.
            if (suspended[i] && rnd() < CRYSTALS) {
                crystal[i] = true
                weight[i] = WEIGHTS[if (rnd() < 0.6f) 0 else 1]
                size[i] = 0.45f + 0.55f * size[i]
                tip[i] = (rnd() - 0.5f) * 3f
                tipRate[i] = (rnd() - 0.5f) * 2.4f
                spin[i] = rnd() * 6.28f
                spinRate[i] = (rnd() - 0.5f) * 2.2f
            }
        }
        layDrift()
        hangEvenly()
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

    /** Signed distance to the rounded wall of the glass (negative inside); [out]: outward. */
    private fun wall(px: Float, py: Float, out: FloatArray): Float {
        val qx = abs(px - aspect / 2f) - (aspect / 2f - corner)
        val qy = abs(py - 0.5f) - (0.5f - corner)
        val sx = if (px < aspect / 2f) -1f else 1f
        val sy = if (py < 0.5f) -1f else 1f
        val ox = max(qx, 0f)
        val oy = max(qy, 0f)
        val outside = len(ox, oy)
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

    /** Whether grain [i] is on the top of the drift (for drawing). */
    fun exposed(i: Int): Boolean = lying[i] && top[i]

    // ---------------------------------------------------------------- set-ups

    /**
     * The top of the drift: two rows of grains as grains pack, level, out to the glass (in
     * the rounded ends, a level bowl); the body of the drift under them is drawn solid.
     */
    private fun layDrift() {
        for (k in 0 until crustCount) {
            x[k] = bed[2 * k] + (rnd() - 0.5f) * grain * 0.15f
            y[k] = bed[2 * k + 1] + (rnd() - 0.5f) * grain * 0.1f
        }
        // The grains with none over them are on the top.
        for (i in 0 until crustCount) {
            var covered = false
            for (j in 0 until crustCount) {
                if (j == i) continue
                val dy = y[i] - y[j]
                if (dy > grain && dy < 2.6f * grain && abs(x[j] - x[i]) < 1.6f * grain) {
                    covered = true
                    break
                }
            }
            top[i] = !covered
        }
    }

    /**
     * The hanging snow spread evenly through the glass over the drift (on a loose grid, each
     * flake a little off its place), still.
     */
    private fun hangEvenly() {
        val a = spacing / SPREAD_REACH
        val rowStep = a * 0.866f
        val px = ArrayList<Float>()
        val py = ArrayList<Float>()
        var row = 0
        var yy = a * 0.5f
        while (yy < driftTop - a * 0.3f) {
            var xx = (if (row % 2 == 0) 0.5f else 1f) * a
            while (xx < aspect) {
                if (wall(xx, yy, glassNormal) < -0.02f) {
                    px += xx
                    py += yy
                }
                xx += a
            }
            yy += rowStep
            row++
        }
        val n = px.size
        val slots = IntArray(n) { it }
        for (k in n - 1 downTo 1) {
            val j = (rnd() * (k + 1)).toInt().coerceAtMost(k)
            val t = slots[k]; slots[k] = slots[j]; slots[j] = t
        }
        var k = 0
        for (i in crustCount until flakes) {
            val slot = slots[k % max(1, n)]
            k++
            x[i] = px[slot] + (rnd() - 0.5f) * a * 0.6f
            y[i] = py[slot] + (rnd() - 0.5f) * a * 0.6f
            vx[i] = 0f
            vy[i] = 0f
            keepInside(i, 0f)
        }
    }

    /** As when the globe has stood still for a while: the snow hanging evenly, all calm. */
    fun settleAll() {
        u.fill(0f)
        v.fill(0f)
        hangEvenly()
        lively = true
    }

    // ---------------------------------------------------------------- one step

    /**
     * Advances by [dt]. [dropletX], [dropletHalf] and [dropletV] describe the droplet
     * (centre, half width, speed along the bar); [shakeX], [shakeY] the phone's own
     * acceleration on the screen (m/s²). Returns whether anything still moves (the hanging
     * snow always drifts).
     */
    fun step(
        dt: Float,
        dropletX: Float = -1f,
        dropletHalf: Float = 0f,
        dropletV: Float = 0f,
        shakeX: Float = 0f,
        shakeY: Float = 0f,
    ): Boolean {
        val h = dt.coerceIn(0f, 1f / 30f)
        val shake = len(shakeX, shakeY)
        forces(h, dropletX, dropletHalf, dropletV, shakeX, shakeY)
        advect(h)
        project()
        val damp = exp(-LIQUID_DAMPING * h)
        var fastest = 0f
        for (k in u.indices) {
            u[k] *= damp
            v[k] *= damp
            fastest = max(fastest, abs(u[k]) + abs(v[k]))
        }
        spread()
        val bunched = even()
        val sub = h / SUBSTEPS
        var quickest = 0f
        repeat(SUBSTEPS) {
            for (i in crustCount until flakes) {
                flake(i, sub, shakeX, shakeY, shake)
                x[i] += vx[i] * sub
                y[i] += vy[i] * sub
                keepInside(i, 1.25f)
            }
        }
        for (i in crustCount until flakes) quickest = max(quickest, len(vx[i], vy[i]))
        lively = fastest > 0.02f || shake > 1.5f || bunched > BUNCHED || quickest > 0.08f
        return true
    }

    private fun idx(i: Int, j: Int) = j * nx + i

    private fun forces(h: Float, dx: Float, half: Float, dv: Float, shakeX: Float, shakeY: Float) {
        val shake = len(shakeX, shakeY)
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
            // A shake moves the glass and the liquid lags behind: the whole of it sloshes
            // against the glass and rolls over at the ends into big slow eddies (the
            // pressure step turns the slosh into them); a few smaller whirls on top. It
            // calms down over a couple of seconds after the shake.
            val k = SLOSH * h
            for (n in u.indices) {
                u[n] -= shakeX * k
                v[n] -= shakeY * k
            }
            val whirls = min(4, (shake / 5f).toInt() + 1)
            repeat(whirls) {
                val ci = (rnd() * nx).toInt().coerceIn(0, nx - 1)
                val cj = (rnd() * ny).toInt().coerceIn(0, ny - 1)
                val spin = (if (rnd() < 0.5f) -1f else 1f) * min(shake, 30f) * 3f * h
                val r2 = 0.08f + 0.15f * rnd()
                for (j in 0 until ny) for (i in max(0, ci - 6)..min(nx - 1, ci + 6)) {
                    val rx = (i - ci) * cw
                    val ry = (j - cj) * ch
                    val f = exp(-(rx * rx + ry * ry) / r2)
                    val n = idx(i, j)
                    u[n] += -ry * spin * f * 4f
                    v[n] += rx * spin * f * 4f
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

    // ---------------------------------------------------------------- the hanging snow

    /**
     * The hanging snow keeps its spacing: each hanging flake is pushed gently away from the
     * hanging ones nearer than [spacing], and from the glass and the drift, so it fills the
     * glass with no gaps.
     */
    private fun spread() {
        sStart.fill(0)
        for (i in 0 until flakes) {
            spreadX[i] = 0f
            spreadY[i] = 0f
            if (!suspended[i]) {
                sOf[i] = -1
                continue
            }
            val ci = (x[i] / spacing).toInt().coerceIn(0, scols - 1)
            val cj = (y[i] / spacing).toInt().coerceIn(0, srows - 1)
            sOf[i] = cj * scols + ci
            sStart[sOf[i] + 1]++
        }
        for (b in 1..scols * srows) sStart[b] += sStart[b - 1]
        for (i in 0 until flakes) if (sOf[i] >= 0) sItems[sStart[sOf[i]]++] = i
        for (b in scols * srows downTo 1) sStart[b] = sStart[b - 1]
        sStart[0] = 0
        val reach2 = spacing * spacing
        for (i in 0 until flakes) {
            val c = sOf[i]
            if (c < 0) continue
            val ci = c % scols
            val cj = c / scols
            var fx = 0f
            var fy = 0f
            for (dj in -1..1) for (di in -1..1) {
                val ii = ci + di
                val jj = cj + dj
                if (ii < 0 || jj < 0 || ii >= scols || jj >= srows) continue
                val b = jj * scols + ii
                for (k in sStart[b] until sStart[b + 1]) {
                    val j = sItems[k]
                    if (j == i) continue
                    val dx = x[i] - x[j]
                    val dy = y[i] - y[j]
                    val d2 = dx * dx + dy * dy
                    if (d2 >= reach2 || d2 < 1e-10f) continue
                    val d = sqrt(d2)
                    val f = 1f - d / spacing
                    fx += dx / d * f * f
                    fy += dy / d * f * f
                }
            }
            // And off the glass and the drift, the same way.
            val half = spacing * 0.5f
            val wd = -wall(x[i], y[i], glassNormal)
            if (wd < half) {
                val f = 1f - wd / half
                fx -= glassNormal[0] * f * f * 2f
                fy -= glassNormal[1] * f * f * 2f
            }
            val dd = driftTop - y[i]
            if (dd < half) {
                val f = 1f - max(0f, dd) / half
                fy -= f * f * 2f
            }
            spreadX[i] = fx * SPREAD
            spreadY[i] = fy * SPREAD
        }
    }

    /**
     * Where the hanging snow has bunched up (after a shake, say) it is pushed out towards
     * where it is thin, across the whole glass: how thick it is is taken over a coarse grid,
     * and each flake is pushed down the slope of that. Returns how far the thickest or the
     * thinnest part is off even.
     */
    private fun even(): Float {
        thick.fill(0f)
        val hanging = flakes - crustCount
        var roomSum = 0f
        for (r in room) roomSum += r
        val perCell = hanging / max(1e-3f, roomSum)
        for (i in crustCount until flakes) {
            // Shared out over the four nearest cells.
            val fx = (x[i] / dw - 0.5f).coerceIn(0f, dcols - 1.001f)
            val fy = (y[i] / dh - 0.5f).coerceIn(0f, drows - 1.001f)
            val ci = fx.toInt()
            val cj = fy.toInt()
            val tx = fx - ci
            val ty = fy - cj
            thick[cj * dcols + ci] += (1 - tx) * (1 - ty)
            thick[cj * dcols + ci + 1] += tx * (1 - ty)
            thick[(cj + 1) * dcols + ci] += (1 - tx) * ty
            thick[(cj + 1) * dcols + ci + 1] += tx * ty
        }
        var off = 0f
        for (k in thick.indices) {
            val r = room[k]
            thick[k] = if (r < 0.25f) Float.NaN else thick[k] / (perCell * r) - 1f
            if (!thick[k].isNaN()) off = max(off, abs(thick[k]))
        }
        // Its slope in each cell (from the neighbours there are).
        for (cj in 0 until drows) for (ci in 0 until dcols) {
            val k = cj * dcols + ci
            if (thick[k].isNaN()) {
                slopeX[k] = 0f
                slopeY[k] = 0f
                continue
            }
            slopeX[k] = across(ci, cj, 1, 0) / dw
            slopeY[k] = across(ci, cj, 0, 1) / dh
        }
        for (i in crustCount until flakes) {
            val fx = (x[i] / dw - 0.5f).coerceIn(0f, dcols - 1.001f)
            val fy = (y[i] / dh - 0.5f).coerceIn(0f, drows - 1.001f)
            val ci = fx.toInt()
            val cj = fy.toInt()
            val tx = fx - ci
            val ty = fy - cj
            val k = cj * dcols + ci
            val sx = (slopeX[k] * (1 - tx) + slopeX[k + 1] * tx) * (1 - ty) + (slopeX[k + dcols] * (1 - tx) + slopeX[k + dcols + 1] * tx) * ty
            val sy = (slopeY[k] * (1 - tx) + slopeY[k + 1] * tx) * (1 - ty) + (slopeY[k + dcols] * (1 - tx) + slopeY[k + dcols + 1] * tx) * ty
            spreadX[i] -= EVEN * sx
            spreadY[i] -= EVEN * sy
        }
        return off
    }

    /** The change of thickness across cell ([ci], [cj]) along ([di], [dj]), per cell. */
    private fun across(ci: Int, cj: Int, di: Int, dj: Int): Float {
        val here = thick[cj * dcols + ci]
        fun at(i: Int, j: Int): Float? {
            if (i < 0 || j < 0 || i >= dcols || j >= drows) return null
            val t = thick[j * dcols + i]
            return if (t.isNaN()) null else t
        }
        val fwd = at(ci + di, cj + dj)
        val back = at(ci - di, cj - dj)
        return when {
            fwd != null && back != null -> (fwd - back) / 2f
            fwd != null -> fwd - here
            back != null -> here - back
            else -> 0f
        }
    }

    /** A flake in the liquid: carried by it, sinking slowly, swaying. */
    private fun flake(i: Int, h: Float, shakeX: Float, shakeY: Float, gust: Float) {
        val s = size[i]
        val fu = sample(u, x[i], y[i])
        val fv = sample(v, x[i], y[i])
        // Hardly sinking at all, by weight; the light ones follow the liquid closely.
        val w = weight[i]
        val sink = HANG_SINK * (0.6f + 0.8f * w)
        val tau = 0.1f + 0.25f * w
        phase[i] += h * (3.2f + 2.4f * s)
        val sway = sin(phase[i]) * sink * 0.8f
        // The faint currents: a random wander with a memory of a couple of seconds.
        val keep = h / DRIFT_TIME
        val kick = DRIFT_SPEED * sqrt(2f * keep) * 3.46f
        driftX[i] += -driftX[i] * keep + (rnd() - 0.5f) * kick
        driftY[i] += -driftY[i] * keep + (rnd() - 0.5f) * kick
        var gx = fu - sway + driftX[i] + spreadX[i]
        var gy = fv + sink + driftY[i] + spreadY[i]
        if (crystal[i]) {
            // A tipped disc slides sideways as it sinks (it sinks most easily edge first),
            // so a crystal glides one way, tips over and glides back, like a falling leaf;
            // and it turns slowly as it goes.
            tipRate[i] += ((rnd() - 0.5f) * 3f - tipRate[i] * 0.4f) * h
            tip[i] += tipRate[i] * h
            spin[i] += spinRate[i] * h
            val glide = sink * GLIDE * sin(2f * tip[i])
            gx = fu - glide + driftX[i] + spreadX[i]
            gy = fv + sink * 0.7f + driftY[i] + spreadY[i]
        }
        val k = min(1f, h / tau)
        vx[i] += (gx - vx[i]) * k
        vy[i] += (gy - vy[i]) * k
        // A little heavier than the liquid, they lag behind the glass when it is shaken, and
        // the shaken liquid tosses them about.
        vx[i] += -shakeX * 0.12f * h * (0.5f + w)
        vy[i] += -shakeY * 0.12f * h * (0.5f + w)
        if (gust > 2f) {
            vx[i] += (rnd() - 0.5f) * gust * 0.18f * h
            vy[i] += (rnd() - 0.5f) * gust * 0.18f * h
        }
        // A little flutter of the small ones.
        vx[i] += (rnd() - 0.5f) * 0.25f * h * (1.2f - s)
        vy[i] += (rnd() - 0.5f) * 0.25f * h * (1.2f - s)
    }

    /** Keeps flake [i] in the liquid inside the glass and over the drift, bouncing it off. */
    private fun keepInside(i: Int, bounce: Float) {
        val d = wall(x[i], y[i], glassNormal)
        val margin = 0.012f
        if (d > -margin) {
            x[i] -= (d + margin) * glassNormal[0]
            y[i] -= (d + margin) * glassNormal[1]
            val vn = vx[i] * glassNormal[0] + vy[i] * glassNormal[1]
            if (vn > 0f) {
                vx[i] -= bounce * vn * glassNormal[0]
                vy[i] -= bounce * vn * glassNormal[1]
            }
        }
        val floor = driftTop - margin
        if (y[i] > floor && bottomAt(x[i]) > driftTop) {
            y[i] = floor
            if (vy[i] > 0f) vy[i] *= -bounce * 0.5f
        }
    }

    // ---------------------------------------------------------------- for tests

    /** Every flake counted once, inside the glass, the hanging ones over the drift. */
    internal fun consistent(): Boolean {
        if ((0 until flakes).count { lying[it] } != crustCount) return false
        for (i in 0 until flakes) {
            if (x[i].isNaN() || y[i].isNaN()) return false
            if (wall(x[i], y[i], glassNormal) > 0.01f) return false
            if (suspended[i] && y[i] > driftTop + 0.001f && bottomAt(x[i]) > driftTop) return false
        }
        return true
    }

    /** Height of the drift at [px]. */
    internal fun columnDepth(px: Float): Float {
        var top = bottomAt(px)
        for (i in 0 until crustCount) if (abs(x[i] - px) < grain * 1.5f) top = min(top, y[i] - radius[i])
        return bottomAt(px) - top
    }

    /** Fluid speed under a point, for tests. */
    internal fun liquidAt(px: Float, py: Float): Float = len(sample(u, px, py), sample(v, px, py))

    companion object {
        /** Depth of the drift over the flat bottom unless told otherwise, bar heights. */
        const val CRUST_DEPTH = 0.1f
        /** Radius of the grains along the top of the drift, bar heights. */
        private const val GRAIN = 0.02f

        /** Two rows of grains along the top of a drift at [level], within the glass. */
        private fun layBed(aspect: Float, corner: Float, level: Float): FloatArray {
            val a = 2.02f * GRAIN
            val rowStep = a * 0.866f
            val out = ArrayList<Float>()
            for (row in 0..1) {
                val yy = level + GRAIN + row * rowStep
                var xx = (if (row % 2 == 0) 0.5f else 1f) * a
                while (xx < aspect) {
                    // Inside the glass, a grain clear of it.
                    val qx = abs(xx - aspect / 2f) - (aspect / 2f - corner)
                    val qy = abs(yy - 0.5f) - (0.5f - corner)
                    val d = sqrt(max(qx, 0f) * max(qx, 0f) + max(qy, 0f) * max(qy, 0f)) + min(max(qx, qy), 0f) - corner
                    if (d < -GRAIN * 1.05f) {
                        out += xx
                        out += yy
                    }
                    xx += a
                }
            }
            return out.toFloatArray()
        }
        /** How fast the hanging snow sinks: hardly at all. */
        private const val HANG_SINK = 0.003f
        /** How hard hanging flakes keep apart, bar heights a second... */
        private const val SPREAD = 0.1f
        /** ...within this many times their mean spacing. */
        private const val SPREAD_REACH = 1.15f
        /** How hard bunched-up snow is pushed towards thin snow, bar heights² a second. */
        private const val EVEN = 0.045f
        /** Snow this far off even (in thickness) counts as still spreading out. */
        private const val BUNCHED = 0.6f
        private const val SUBSTEPS = 2
        /** How much of the glass's shaking the liquid lags behind, per m/s². */
        private const val SLOSH = 0.05f
        /** How fast the liquid's own motion dies away, per second. */
        private const val LIQUID_DAMPING = 0.55f
        /** The faint currents a flake wanders with: their speed, bar heights a second... */
        private const val DRIFT_SPEED = 0.015f
        /** ...and how long they keep their way, seconds. */
        private const val DRIFT_TIME = 2f
        /** The share of crystals among the hanging flakes. */
        private const val CRYSTALS = 0.3f
        /** How far a tipped crystal glides sideways for each unit it sinks. */
        private const val GLIDE = 0.9f
        /** The weight classes, light to heavy. */
        private val WEIGHTS = floatArrayOf(0.15f, 0.32f, 0.5f, 0.72f, 1f)
    }
}

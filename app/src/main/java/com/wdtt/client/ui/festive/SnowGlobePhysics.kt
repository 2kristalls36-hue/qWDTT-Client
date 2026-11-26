package com.wdtt.client.ui.festive

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A snow globe the shape of the tab bar: liquid with snow hanging in it, and a thin crust of
 * snow stuck to the glass. Lengths are in bar heights (the bar is [aspect] long and 1 high,
 * y down); times in seconds.
 *
 * Every flake lives all the time — none is ever removed or made — so nothing vanishes or
 * appears from nowhere:
 * - Most of the snow hangs in the liquid for good (it is as heavy as the liquid, near
 *   enough): it fills the whole glass evenly, each flake keeping a little apart from the
 *   others, and drifts very slowly with the faint currents and the way the phone leans:
 *   tilted, the snow flows over to the lower side, a little thicker there, leaving no gaps.
 *   About one in three of the hanging flakes is a crystal that tips and turns slowly.
 * - The droplet drags the liquid as it moves and the snow swirls after it; a quick move of
 *   the phone sloshes the liquid against the glass and whirls the snow up, until it calms
 *   down and hangs again. Then only the slow drift is left, and [lively] says so.
 * - The crust: heavier crumbs stuck to the glass at the bottom, a grain or two deep. They
 *   are little round grains that press on and rub against one another (position-based
 *   dynamics). A resting grain sleeps — stays exactly where it is — and wakes only when it
 *   no longer holds (see [recheck]): the phone turned so far that the snow or the glass
 *   under it is steeper than it holds, what held it gone, another grain running into it.
 *   So the crust clings, slides down the glass only when the phone is turned far, comes off
 *   the glass turned up grain by grain, and settles on the glass again wherever it falls.
 *   Running liquid and a shake lift it into the liquid; it sinks back slowly.
 * - The liquid is a coarse grid (stable fluids: forces, advection, pressure projection,
 *   damping).
 */
class SnowGlobePhysics(
    val aspect: Float,
    /** Corner radius of the bar, in bar heights (0.5 — a pill). */
    val corner: Float = 0.5f,
    val flakes: Int = 900,
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
    /** Where a flake was at the start of the substep. */
    private val x0 = FloatArray(flakes)
    private val y0 = FloatArray(flakes)
    /** Size of a flake, 0..1. */
    val size = FloatArray(flakes)
    /**
     * Weight of a flake, one of [WEIGHTS]: light fluff drifts down slowly and is easily
     * carried, heavier crumbs sink and roll faster. More light ones than heavy.
     */
    val weight = FloatArray(flakes)
    private val phase = FloatArray(flakes)
    /**
     * Hanging in the liquid for good (as heavy as the liquid, near enough): most of the
     * snow. The rest is the crust, heavier crumbs that settle on the glass.
     */
    val suspended = BooleanArray(flakes)
    /** How many flakes make the crust on the glass (the first ones). */
    val crustCount: Int
    /** The push a hanging flake gets from its neighbours, so the snow keeps its spacing. */
    private val spreadX = FloatArray(flakes)
    private val spreadY = FloatArray(flakes)
    /** Spacing of the hanging snow, and a grid for finding the hanging neighbours. */
    private val spacing: Float
    private val scols: Int
    private val srows: Int
    private val sStart: IntArray
    private val sItems = IntArray(flakes)
    private val sOf = IntArray(flakes)

    /**
     * Something stirs: the liquid runs, the phone is shaken, a grain of the crust moves or
     * falls. Otherwise only the hanging snow drifts, so slowly that half the frames will do.
     */
    var lively = true
        private set

    /**
     * A slow wandering of each flake in the liquid (the faint currents that never quite
     * die in a globe): it spreads whatever pattern a shake left, so the snow settles as an
     * even cloud instead of sinking as a frozen shape.
     */
    private val driftX = FloatArray(flakes)
    private val driftY = FloatArray(flakes)
    /**
     * A snowflake proper (a flat six-armed crystal), not a crumb: it falls like a little
     * disc, slower, gliding to one side and the other as it tips over, and turns slowly.
     */
    val crystal = BooleanArray(flakes)
    /** How far a crystal is tipped from lying flat across its fall, radians. */
    val tip = FloatArray(flakes)
    private val tipRate = FloatArray(flakes)
    /** How far a crystal has turned in the plane of the screen, radians. */
    val spin = FloatArray(flakes)
    private val spinRate = FloatArray(flakes)
    /**
     * How firmly a grain holds on to the snow round it, 0..1: its friction, and its grip on
     * the glass turned up. Snow gives way bit by bit as the phone turns, the loose grains
     * first.
     */
    private val cohesion = FloatArray(flakes)
    private val friction = FloatArray(flakes)
    /** Mean radius of a grain in the heap, bar heights. */
    val grain: Float
    /** Radius of each grain in the heap. */
    val radius = FloatArray(flakes)

    /** In the heap (else in the liquid). */
    val lying = BooleanArray(flakes)
    /** A grain at rest, held where it is (see the class comment). */
    private val asleep = BooleanArray(flakes)
    /** Asleep on the glass turned up: it clings until pulled off. */
    private val stuck = BooleanArray(flakes)
    /** Where a moving grain was when its current quiet spell began, and for how long. */
    private val still = FloatArray(flakes)
    private val stillX = FloatArray(flakes)
    private val stillY = FloatArray(flakes)
    /** Where a grain has been of late (smoothed): far from it, it is really on its way. */
    private val trailX = FloatArray(flakes)
    private val trailY = FloatArray(flakes)
    private val air = FloatArray(flakes)
    private val contacts = IntArray(flakes)
    private val supported = BooleanArray(flakes)
    /** Where a grain went to sleep last, and whether its moving off has been passed on. */
    private val anchorX = FloatArray(flakes)
    private val anchorY = FloatArray(flakes)
    private val told = BooleanArray(flakes)
    /** Times a grain has tried to break out of a steep place without getting anywhere. */
    private val tries = IntArray(flakes)
    /** Gravity when a grain came to rest: a slope gives way only once the phone has turned. */
    private val restGX = FloatArray(flakes)
    private val restGY = FloatArray(flakes) { 1f }
    /** On the surface of the heap, and which way the surface faces there. */
    private val surface = BooleanArray(flakes)
    private val outX = FloatArray(flakes)
    private val outY = FloatArray(flakes)
    /** Height against gravity, in grains; the moving flakes in order of it, lowest first. */
    private val height = FloatArray(flakes)
    private val avgX = FloatArray(flakes)
    private val avgY = FloatArray(flakes)
    private val order = IntArray(flakes)
    private val rank = IntArray(flakes)
    private var moving = 0
    private val heightBins = IntArray(HEIGHT_BINS + 1)
    /** The flakes near each moving one (from the lower of each pair), found once a substep. */
    private var pairJ = IntArray(flakes * 8)
    private val pairStart = IntArray(flakes + 1)
    private val binOf = IntArray(flakes)

    // ---- A grid of buckets for finding neighbours ----
    private val bucket: Float
    private val bcols: Int
    private val brows: Int
    private val bucketStart: IntArray
    private val bucketItems = IntArray(flakes)
    private val bucketOf = IntArray(flakes)


    /** Grains that moved off where they lay so far (a still globe moves none). */
    var moves = 0L
        private set

    /** Flakes not lying now (in the liquid). */
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

    private val glassNormal = FloatArray(2)

    /** Settling at start: no grain leaves the heap. */
    private var settling = false
    /** Undoing old overlaps (positions only, see [substep]). */
    private var stabilizing = false
    /** A flake landed or a grain left the heap since the surface was last found. */
    private var heapChanged = false
    private var surfaceClock = 0

    private fun len(a: Float, b: Float) = sqrt(a * a + b * b)

    init {
        crustCount = (flakes * CRUST_SHARE).toInt()
        // The crust, packed as grains pack, lies [CRUST_DEPTH] deep over the flat bottom.
        grain = sqrt(CRUST_DEPTH * aspect * 0.82f / (max(1, crustCount) * Math.PI.toFloat() * 1.0133f))
        // The hanging snow spreads evenly through the glass.
        val room = aspect - (4f - Math.PI.toFloat()) * corner * corner
        spacing = sqrt(room / max(1, flakes - crustCount)) * SPREAD_REACH
        scols = max(1, (aspect / spacing).toInt() + 1)
        srows = max(1, (1f / spacing).toInt() + 1)
        sStart = IntArray(scols * srows + 1)
        bucket = 2.6f * grain
        bcols = max(1, (aspect / bucket).toInt() + 1)
        brows = max(1, (1f / bucket).toInt() + 1)
        bucketStart = IntArray(bcols * brows + 1)
        for (i in 0 until flakes) {
            size[i] = rnd()
            phase[i] = rnd() * 6.28f
            cohesion[i] = rnd()
            friction[i] = ROLL_FRICTION * (0.7f + 0.6f * cohesion[i])
            radius[i] = grain * (0.8f + 0.4f * rnd())
            // The class: mostly the lighter ones; bigger flakes lean to the heavier.
            val r = (rnd() * 0.75f + size[i] * 0.25f)
            weight[i] = WEIGHTS[(r * r * WEIGHTS.size).toInt().coerceIn(0, WEIGHTS.size - 1)]
            suspended[i] = i >= crustCount
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
        floating = flakes
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

    /** Whether lying grain [i] is on the surface of the heap (for drawing). */
    fun exposed(i: Int): Boolean = lying[i] && surface[i]

    // ---------------------------------------------------------------- set-ups

    private fun clear() {
        floating = flakes
        for (i in 0 until flakes) {
            lying[i] = false
            asleep[i] = false
            stuck[i] = false
            surface[i] = false
            still[i] = 0f
            air[i] = 0f
            vx[i] = 0f
            vy[i] = 0f
        }
        gravityX = 0f
        gravityY = 1f
    }

    /**
     * The hanging snow spread evenly through the glass (on a loose grid, each flake a little
     * off its place), above the crust.
     */
    private fun hangEvenly() {
        val a = spacing / SPREAD_REACH
        val rowStep = a * 0.866f
        val px = ArrayList<Float>()
        val py = ArrayList<Float>()
        var row = 0
        var yy = a * 0.5f
        while (yy < 1f - CRUST_DEPTH - a * 0.3f) {
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
            keepInside(i, 0f)
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
        // Places in rows, as grains pack, the lowest first, so the snow lies level, also in
        // the rounded ends (in a round globe, a level bowl of snow); its surface gently
        // rolling, as snow lies.
        val a = 2.02f * grain
        val rowStep = a * 0.866f
        val px = ArrayList<Float>()
        val py = ArrayList<Float>()
        var row = 0
        while (true) {
            val yy = 1f - grain * 1.05f - row * rowStep
            if (yy < grain) break
            var xx = (if (row % 2 == 0) 0.5f else 1f) * a
            while (xx < aspect) {
                if (wall(xx, yy, glassNormal) < -grain * 1.05f) {
                    px += xx
                    py += yy
                }
                xx += a
            }
            row++
        }
        val wave1 = hash01(43, flakes) * 6.28f
        val wave2 = hash01(47, flakes) * 6.28f
        val key = FloatArray(px.size) { k ->
            py[k] + grain * (1.3f * sin(px[k] * 3.7f + wave1) + 0.7f * sin(px[k] * 7.1f + wave2)) * 0.4f + rnd() * grain * 0.3f
        }
        val order = px.indices.sortedByDescending { key[it] }
        val grains = IntArray(crustCount) { it }
        for (k in crustCount - 1 downTo 1) {
            val j = (rnd() * (k + 1)).toInt().coerceAtMost(k)
            val t = grains[k]; grains[k] = grains[j]; grains[j] = t
        }
        for (k in 0 until min(crustCount, order.size)) {
            val i = grains[k]
            x[i] = px[order[k]] + (rnd() - 0.5f) * grain * 0.1f
            y[i] = py[order[k]]
            lying[i] = true
            floating--
        }
        hangEvenly()
        // Push the grains apart where they overlap, without setting them moving; then let
        // them settle into each other, out of sight; then the heap rests.
        settling = true
        for (i in 0 until flakes) height[i] = -y[i] / (2f * grain)
        buildBuckets()
        sortByHeight()
        findPairs(0f)
        repeat(RELAX_PASSES) {
            for (i in 0 until flakes) {
                x0[i] = x[i]
                y0[i] = y[i]
            }
            solve()
        }
        for (i in 0 until flakes) {
            vx[i] = 0f
            vy[i] = 0f
        }
        for (k in 0 until SETTLE_SUBSTEPS) substep(1f / 120f, 0f, 0f)
        for (i in 0 until flakes) {
            vx[i] = 0f
            vy[i] = 0f
            if (!lying[i]) continue
            asleep[i] = true
            still[i] = 0f
            anchorX[i] = x[i]
            anchorY[i] = y[i]
        }
        // Whatever would not hold slips now, out of sight, until all of it holds.
        for (round in 0 until 6) {
            for (i in 0 until flakes) recheck(i)
            if ((0 until flakes).none { lying[it] && !asleep[it] }) break
            for (k in 0 until 60) {
                substep(1f / 120f, 0f, 0f)
                if (k % 2 == 1) rest(1f / 60f)
            }
            for (i in 0 until flakes) if (lying[i] && !asleep[i]) {
                asleep[i] = true
                vx[i] = 0f
                vy[i] = 0f
                anchorX[i] = x[i]
                anchorY[i] = y[i]
            }
        }
        settling = false
        for (i in 0 until flakes) {
            restGX[i] = 0f
            restGY[i] = 1f
        }
        findSurface()
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
        if (len(tiltX, tiltY) > 0.15f) {
            val angle = atan2(tiltX, tiltY)
            val turn = abs(angle)
            val boosted = if (turn < HALF_PI) angle * (1f + TILT_BOOST * (1f - turn / HALF_PI)) else angle
            gX = sin(boosted)
            gY = cos(boosted)
        }
        if (abs(gX - gravityX) + abs(gY - gravityY) > 1e-4f) turned(gX, gY)

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
        var changed = false
        if (lift(h, shake, fastest)) changed = true
        if (peel(h)) changed = true
        spread()
        val sub = h / SUBSTEPS
        repeat(SUBSTEPS) { substep(sub, shakeX, shakeY) }
        buildBuckets()
        calm()
        var awake = 0
        for (i in 0 until flakes) {
            if (!lying[i] || asleep[i]) continue
            awake++
            // The trail follows it over about a third of a second.
            trailX[i] += (x[i] - trailX[i]) * TRAIL
            trailY[i] += (y[i] - trailY[i]) * TRAIL
            // Those resting on a grain that moves off look again.
            if (travelling(i)) wakeAround(i)
        }
        rest(h)
        surfaceClock++
        if (changed || heapChanged || (awake > 0 && surfaceClock % 4 == 0)) findSurface()
        heapChanged = false
        var falling = false
        for (i in 0 until crustCount) if (!lying[i]) { falling = true; break }
        lively = falling || awake > 0 || fastest > 0.02f || changed || shake > 1.5f
        return floating > 0 || awake > 0 || fastest > 0.004f || changed || shake > 1.5f
    }

    /**
     * The phone turned: gravity is now ([gX], [gY]). Every resting grain sees whether it
     * still holds where it lies (see [recheck]).
     */
    private fun turned(gX: Float, gY: Float) {
        gravityX = gX
        gravityY = gY
        for (i in 0 until flakes) {
            if (!lying[i]) continue
            if (asleep[i]) recheck(i) else stuck[i] = false
        }
    }

    /**
     * Resting grain [i] sees whether it still holds where it lies; if not, it wakes and
     * the heap's mechanics move it. It holds unless:
     * - nothing is under it any more, or what it rests on is moving off;
     * - it lies on the surface and the surface there is steeper than its own angle of
     *   hold (loose grains about 40°, firm ones up to 60°: the crust is sticky);
     * - it lies on the glass and the glass is tilted further than the snow holds on it:
     *   then the snow slides down along the glass as a mass.
     * On the glass turned up it clings instead (it comes off by [peel]).
     */
    private fun recheck(i: Int) {
        if (!lying[i] || !asleep[i]) return
        if (underGlass(i)) {
            stuck[i] = true
            return
        }
        stuck[i] = false
        var sx = 0f
        var sy = 0f
        var n = 0
        var held = false
        var heldByGrain = false
        var shaken = false
        val bi = bucketOf[i] % bcols
        val bj = bucketOf[i] / bcols
        for (dj in -1..1) for (di in -1..1) {
            val ci = bi + di
            val cj = bj + dj
            if (ci < 0 || cj < 0 || ci >= bcols || cj >= brows) continue
            val b = cj * bcols + ci
            for (k in bucketStart[b] until bucketStart[b + 1]) {
                val j = bucketItems[k]
                if (j == i || !lying[j]) continue
                val dx = x[j] - x[i]
                val dy = y[j] - y[i]
                val d = len(dx, dy)
                if (d > (radius[i] + radius[j]) * 1.15f || d < 1e-6f) continue
                sx += dx / d
                sy += dy / d
                n++
                val below = dx * gravityX + dy * gravityY
                if (below > 0.42f * d) {
                    held = true
                    heldByGrain = true
                }
                // What it rests or leans on moves off (not one rolling over it).
                if (below > -0.3f * d && travelling(j)) shaken = true
            }
        }
        val gd = wall(x[i], y[i], glassNormal) + radius[i] * 1.15f
        var onGlass = false
        var floor = 1f
        if (gd > 0f) {
            onGlass = true
            sx += glassNormal[0] * 2f
            sy += glassNormal[1] * 2f
            n += 2
            floor = gravityX * glassNormal[0] + gravityY * glassNormal[1]
            if (floor > 0.42f) held = true
        }
        val gives = when {
            !held || shaken -> true
            onGlass && !heldByGrain && floor < cos(glassHoldAt(i)) -> true
            n < 5 || len(sx, sy) > 1.5f -> {
                val l = len(sx, sy)
                // The surface faces away from the snow round it.
                val turned = gravityX * restGX[i] + gravityY * restGY[i] < TURNED_COS
                val steep = turned && l > 1e-4f && (sx * gravityX + sy * gravityY) / l < cos(holdAt(i))
                if (steep) {
                    wake(i)
                    // It breaks out of its place and sets off down the slope.
                    val ox = -sx / l
                    val oy = -sy / l
                    val along = gravityX * ox + gravityY * oy
                    var tx = gravityX - along * ox
                    var ty = gravityY - along * oy
                    val tl = len(tx, ty)
                    if (tl > 1e-4f) {
                        tx /= tl
                        ty /= tl
                        vx[i] = tx * BREAK_OUT
                        vy[i] = ty * BREAK_OUT
                    }
                }
                false
            }
            else -> false
        }
        if (gives) wake(i)
    }

    private var breakX = 0f
    private var breakY = 0f

    /**
     * Whether grain [i] lies on the surface where it is steeper than its angle of hold;
     * if so, [breakX], [breakY] is the way down the slope.
     */
    private fun tooSteep(i: Int): Boolean {
        var sx = 0f
        var sy = 0f
        var n = 0
        val bi = bucketOf[i] % bcols
        val bj = bucketOf[i] / bcols
        for (dj in -1..1) for (di in -1..1) {
            val ci = bi + di
            val cj = bj + dj
            if (ci < 0 || cj < 0 || ci >= bcols || cj >= brows) continue
            val b = cj * bcols + ci
            for (k in bucketStart[b] until bucketStart[b + 1]) {
                val j = bucketItems[k]
                if (j == i || !lying[j]) continue
                val dx = x[j] - x[i]
                val dy = y[j] - y[i]
                val d = len(dx, dy)
                if (d > (radius[i] + radius[j]) * 1.15f || d < 1e-6f) continue
                sx += dx / d
                sy += dy / d
                n++
            }
        }
        if (wall(x[i], y[i], glassNormal) + radius[i] * 1.15f > 0f) {
            sx += glassNormal[0] * 2f
            sy += glassNormal[1] * 2f
            n += 2
        }
        val l = len(sx, sy)
        if (!(n < 5 || l > 1.5f) || l < 1e-4f) return false
        // A grain on the move stops only on a gentler slope than a settled one holds.
        if ((sx * gravityX + sy * gravityY) / l >= cos(restAt(i))) return false
        val ox = -sx / l
        val oy = -sy / l
        val along = gravityX * ox + gravityY * oy
        val tx = gravityX - along * ox
        val ty = gravityY - along * oy
        val tl = len(tx, ty)
        if (tl < 1e-4f) return false
        breakX = tx / tl
        breakY = ty / tl
        return true
    }

    /**
     * Grain [j] is really on its way somewhere (not just trembling in a pressed heap):
     * moving, and well off where it has been of late.
     */
    private fun travelling(j: Int) =
        !asleep[j] && lying[j] && len(vx[j], vy[j]) > MOVING && len(x[j] - trailX[j], y[j] - trailY[j]) > 0.25f * grain

    /** The steepest slope grain [i] holds on, radians: loose about 40°, firm up to 60°. */
    private fun holdAt(i: Int) = HOLD_MIN + HOLD_SPAN * cohesion[i]

    /** The steepest slope a grain on the move comes to rest on, radians: 16° to 28°. */
    private fun restAt(i: Int) = REST_MIN + REST_SPAN * cohesion[i]

    /** The steepest glass the snow holds on at grain [i], radians. */
    private fun glassHoldAt(i: Int) = GLASS_HOLD_MIN + GLASS_HOLD_SPAN * cohesion[i]

    /** Whether the glass nearest to grain [i] is above it: the snow there hangs from it. */
    private fun underGlass(i: Int): Boolean {
        wall(x[i], y[i], glassNormal)
        return gravityX * glassNormal[0] + gravityY * glassNormal[1] < -HANGING
    }

    private fun wake(i: Int) {
        if (!asleep[i] || stuck[i]) return
        asleep[i] = false
        still[i] = 0f
        stillX[i] = x[i]
        stillY[i] = y[i]
        trailX[i] = x[i]
        trailY[i] = y[i]
        told[i] = false
        x0[i] = x[i]
        y0[i] = y[i]
        vx[i] = 0f
        vy[i] = 0f
    }

    /** The resting grains round [i] that it may have held up look again. */
    private fun wakeAround(i: Int) {
        val bi = bucketOf[i] % bcols
        val bj = bucketOf[i] / bcols
        val reach = 2.8f * grain
        for (dj in -1..1) for (di in -1..1) {
            val ci = bi + di
            val cj = bj + dj
            if (ci < 0 || cj < 0 || ci >= bcols || cj >= brows) continue
            val b = cj * bcols + ci
            for (k in bucketStart[b] until bucketStart[b + 1]) {
                val j = bucketItems[k]
                if (j == i || !lying[j] || !asleep[j]) continue
                if (len(x[j] - x[i], y[j] - y[i]) > reach) continue
                // Only those above or beside it: the ones below never leant on it.
                val up = -(gravityX * (x[j] - x[i]) + gravityY * (y[j] - y[i]))
                if (up > -0.6f * grain) recheck(j)
            }
        }
    }

    /** Lying grain [i] gets up into the liquid with velocity ([ux], [uy]). */
    private fun rise(i: Int, ux: Float, uy: Float) {
        lying[i] = false
        asleep[i] = false
        stuck[i] = false
        surface[i] = false
        heapChanged = true
        floating++
        moves++
        vx[i] = ux
        vy[i] = uy
        x0[i] = x[i]
        y0[i] = y[i]
        wakeAround(i)
    }

    // ---------------------------------------------------------------- the liquid

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

    // ---------------------------------------------------------------- the heap

    /**
     * How hard gravity must pull grain [i] away from the glass it hangs from to tear it
     * off. Snow holds on best next to the glass: the outer layers come off at a slight
     * overhang, the last ones only upside down; a little uneven.
     */
    private fun letGoAt(i: Int): Float {
        val gap = -wall(x[i], y[i], glassNormal) - radius[i]
        val deep = (gap / CRUST_DEPTH).coerceIn(0f, 1f)
        return LET_GO_MIN + LET_GO_SPAN * (1f - deep) + (cohesion[i] - 0.5f) * 0.15f
    }

    /**
     * Snow clinging to the glass turned up comes off, grain by grain from the surface:
     * the further past its grip, the sooner; well past it, in lumps. Returns whether any
     * grain is about to come off.
     */
    private fun peel(h: Float): Boolean {
        var any = false
        for (i in 0 until flakes) {
            if (!lying[i] || !stuck[i] || !surface[i]) continue
            // How hard gravity pulls it off the glass it hangs from.
            wall(x[i], y[i], glassNormal)
            val away = -(gravityX * glassNormal[0] + gravityY * glassNormal[1])
            val grip = letGoAt(i)
            if (away <= grip) continue
            any = true
            if (rnd() >= (4f + 40f * (away - grip)) * h) continue
            val ux = gravityX * (0.04f + 0.04f * rnd()) * WEIGHT + (rnd() - 0.5f) * 0.05f
            val uy = gravityY * (0.04f + 0.04f * rnd()) * WEIGHT + (rnd() - 0.5f) * 0.05f
            rise(i, ux, uy)
            if (away < 0.75f || rnd() > 0.6f) continue
            // A lump falls together: the same push for each grain in it.
            var more = 1 + (rnd() * 3f).toInt()
            val bi = bucketOf[i] % bcols
            val bj = bucketOf[i] / bcols
            for (dj in -1..1) for (di in -1..1) {
                val ci = bi + di
                val cj = bj + dj
                if (more == 0 || ci < 0 || cj < 0 || ci >= bcols || cj >= brows) continue
                val b = cj * bcols + ci
                for (k in bucketStart[b] until bucketStart[b + 1]) {
                    val j = bucketItems[k]
                    if (more == 0 || !lying[j] || !stuck[j] || !surface[j]) continue
                    if (len(x[j] - x[i], y[j] - y[i]) > 2.6f * grain) continue
                    // Only grains held no firmer than the pull go with it.
                    if (letGoAt(j) >= away) continue
                    rise(j, ux, uy)
                    more--
                }
            }
        }
        return any
    }

    /** Liquid running fast over the heap, or a hard shake, lifts the grains on top. */
    private fun lift(h: Float, shake: Float, fastest: Float): Boolean {
        if (shake <= 2.5f && fastest < 0.3f) return false
        var any = false
        val shaken = max(0f, shake - 2.5f) * LIFT_SHAKE
        for (i in 0 until flakes) {
            if (!lying[i] || !surface[i]) continue
            val fu = sample(u, x[i], y[i])
            val fv = sample(v, x[i], y[i])
            val rate = max(0f, len(fu, fv) - STIR) * LIFT_FLOW + shaken
            if (rate <= 0f || rnd() >= rate * h) continue
            // Off into the flow; a shake flings them every which way: a fan up to 70° either
            // side of straight off the heap, lighter grains further.
            val light = 1.3f - 0.6f * weight[i]
            val sp = (0.15f + 0.35f * rnd() + shake * 0.06f * (0.3f + rnd())) * light
            val turn = (rnd() - 0.5f) * 2.44f
            val ox = outX[i] * cos(turn) - outY[i] * sin(turn)
            val oy = outX[i] * sin(turn) + outY[i] * cos(turn)
            rise(i, fu * 0.8f + ox * sp, fv + oy * sp)
            any = true
        }
        return any
    }

    /**
     * The hanging snow keeps its spacing: each hanging flake is pushed gently away from the
     * hanging ones nearer than [spacing], and from the glass, so it fills the glass with no
     * gaps; gravity only presses it a little thicker to the lower side.
     */
    private fun spread() {
        sStart.fill(0)
        for (i in 0 until flakes) {
            spreadX[i] = 0f
            spreadY[i] = 0f
            if (!suspended[i] || lying[i]) {
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
            // And off the glass, the same way.
            val wd = -wall(x[i], y[i], glassNormal)
            val half = spacing * 0.5f
            if (wd < half) {
                val f = 1f - wd / half
                fx -= glassNormal[0] * f * f * 2f
                fy -= glassNormal[1] * f * f * 2f
            }
            spreadX[i] = fx * SPREAD
            spreadY[i] = fy * SPREAD
        }
    }

    private fun buildBuckets() {
        bucketStart.fill(0)
        for (i in 0 until flakes) {
            val bi = (x[i] / bucket).toInt().coerceIn(0, bcols - 1)
            val bj = (y[i] / bucket).toInt().coerceIn(0, brows - 1)
            val b = bj * bcols + bi
            bucketOf[i] = b
            bucketStart[b + 1]++
        }
        for (b in 1..bcols * brows) bucketStart[b] += bucketStart[b - 1]
        // Fill (bucketStart moves on while filling; put it back after).
        for (i in 0 until flakes) bucketItems[bucketStart[bucketOf[i]]++] = i
        for (b in bcols * brows downTo 1) bucketStart[b] = bucketStart[b - 1]
        bucketStart[0] = 0
    }

    /**
     * One substep of [h]: the moving flakes move — in the liquid as flakes, in the heap
     * under gravity held back by the liquid — then nothing overlaps (each pair of grains
     * pushed apart, the higher one more, so stacks stand) and grains rub (friction).
     */
    private fun substep(h: Float, shakeX: Float, shakeY: Float) {
        val gust = len(shakeX, shakeY)
        val drag = exp(-h / HEAP_DRAG)
        // First the overlaps left from before are undone, by moving the grains only: that
        // gives them no speed, so a pressed heap does not jolt itself into motion.
        val gs = 2f * grain
        for (i in 0 until flakes) {
            height[i] = -(gravityX * x[i] + gravityY * y[i]) / gs
            x0[i] = x[i]
            y0[i] = y[i]
        }
        buildBuckets()
        sortByHeight()
        findPairs(h)
        stabilizing = true
        repeat(STABILIZE_PASSES) { solve() }
        stabilizing = false
        for (i in 0 until flakes) {
            if (asleep[i]) continue
            x0[i] = x[i]
            y0[i] = y[i]
            if (lying[i]) {
                vx[i] = (vx[i] + gravityX * HEAP_GRAVITY * h) * drag
                vy[i] = (vy[i] + gravityY * HEAP_GRAVITY * h) * drag
                val sp = len(vx[i], vy[i])
                if (sp > MAX_HEAP_SPEED) {
                    vx[i] *= MAX_HEAP_SPEED / sp
                    vy[i] *= MAX_HEAP_SPEED / sp
                }
            } else {
                flake(i, h, shakeX, shakeY, gust)
            }
            x[i] += vx[i] * h
            y[i] += vy[i] * h
            contacts[i] = 0
            supported[i] = false
        }
        repeat(ITERATIONS) { solve() }
        for (i in 0 until flakes) {
            if (asleep[i]) continue
            if (!lying[i]) {
                vx[i] = (x[i] - x0[i]) / h
                vy[i] = (y[i] - y0[i]) / h
                keepInside(i, 1.25f)
                val down = vx[i] * gravityX + vy[i] * gravityY
                if (!suspended[i] && supported[i] && down > -0.02f && len(vx[i], vy[i]) < LAND_SPEED) {
                    // Came to rest on the snow or on the glass below: a grain of the heap.
                    heapChanged = true
                    lying[i] = true
                    floating--
                    air[i] = 0f
                    still[i] = 0f
                    told[i] = true
                    anchorX[i] = x[i]
                    anchorY[i] = y[i]
                }
                continue
            }
            vx[i] = (x[i] - x0[i]) / h
            vy[i] = (y[i] - y0[i]) / h
            // Never left pressed into the glass; put back without a jolt (no speed from it).
            stabilizing = true
            onGlass(i)
            stabilizing = false
            if (contacts[i] == 0 && !settling) {
                // Off the snow altogether (over an edge): it goes on through the liquid.
                air[i] += h
                if (air[i] > AIR_TIME) {
                    lying[i] = false
                    floating++
                    moves++
                }
            } else {
                air[i] = 0f
            }
        }
    }

    /** A flake in the liquid: carried by it, sinking slowly, swaying. */
    private fun flake(i: Int, h: Float, shakeX: Float, shakeY: Float, gust: Float) {
        val s = size[i]
        val fu = sample(u, x[i], y[i])
        val fv = sample(v, x[i], y[i])
        // Slow sinking, by weight; the light ones follow the liquid closely.
        val w = weight[i]
        val sink = if (suspended[i]) HANG_SINK * (0.6f + 0.8f * w) else (0.025f + 0.11f * w) * WEIGHT
        val tau = 0.1f + 0.25f * w
        phase[i] += h * (3.2f + 2.4f * s)
        val sway = sin(phase[i]) * sink * 0.8f
        val tx = fu + sink * gravityX - sway * gravityY
        val ty = fv + sink * gravityY + sway * gravityX
        // The faint currents: a random wander with a memory of a couple of seconds.
        val keep = h / DRIFT_TIME
        val kick = DRIFT_SPEED * sqrt(2f * keep) * 3.46f
        driftX[i] += -driftX[i] * keep + (rnd() - 0.5f) * kick
        driftY[i] += -driftY[i] * keep + (rnd() - 0.5f) * kick
        var gx = tx + driftX[i] + spreadX[i]
        var gy = ty + driftY[i] + spreadY[i]
        if (crystal[i]) {
            // A tipped disc slides sideways as it sinks (it sinks most easily edge first),
            // so a crystal glides one way, tips over and glides back, like a falling leaf;
            // and it turns slowly as it goes.
            tipRate[i] += ((rnd() - 0.5f) * 3f - tipRate[i] * 0.4f) * h
            tip[i] += tipRate[i] * h
            spin[i] += spinRate[i] * h
            val glide = sink * GLIDE * sin(2f * tip[i])
            gx = fu + sink * 0.7f * gravityX - glide * gravityY + driftX[i] + spreadX[i]
            gy = fv + sink * 0.7f * gravityY + glide * gravityX + driftY[i] + spreadY[i]
        }
        val k = min(1f, h / tau)
        vx[i] += (gx - vx[i]) * k
        vy[i] += (gy - vy[i]) * k
        // Heavier than the liquid, they lag behind the glass when it is shaken, and the
        // shaken liquid tosses them about.
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

    /** Keeps flake [i] in the liquid inside the glass, bouncing it off the wall. */
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
            // On the glass that is down: it settles there.
            if (gravityX * glassNormal[0] + gravityY * glassNormal[1] > 0.3f) supported[i] = true
        }
    }

    /**
     * Puts the moving flakes in order of height, lowest first: a pass over the contacts in
     * that order carries each push up through a stack at once, so stacks stand firm.
     */
    private fun sortByHeight() {
        var lo = Float.MAX_VALUE
        for (i in 0 until flakes) if (!asleep[i]) lo = min(lo, height[i])
        heightBins.fill(0)
        moving = 0
        for (i in 0 until flakes) {
            rank[i] = -1
            if (asleep[i]) continue
            val b = ((height[i] - lo) * 2f).toInt().coerceIn(0, HEIGHT_BINS - 1)
            binOf[i] = b
            heightBins[b + 1]++
            moving++
        }
        for (b in 1..HEIGHT_BINS) heightBins[b] += heightBins[b - 1]
        for (i in 0 until flakes) {
            if (asleep[i]) continue
            val k = heightBins[binOf[i]]++
            order[k] = i
            rank[i] = k
        }
    }

    /**
     * Finds, for each moving flake in order, the flakes it may touch within the next [h]
     * (each pair once, from the lower one): the passes over the contacts then go through
     * these lists instead of searching round every flake each time.
     */
    private fun findPairs(h: Float) {
        var p = 0
        val margin = 0.25f * grain
        val pull = HEAP_GRAVITY * h
        for (k in 0 until moving) {
            pairStart[k] = p
            val i = order[k]
            val li = lying[i]
            val vi = len(vx[i], vy[i]) + pull
            val bi = bucketOf[i] % bcols
            val bj = bucketOf[i] / bcols
            for (dj in -1..1) for (di in -1..1) {
                val ci = bi + di
                val cj = bj + dj
                if (ci < 0 || cj < 0 || ci >= bcols || cj >= brows) continue
                val b = cj * bcols + ci
                for (m in bucketStart[b] until bucketStart[b + 1]) {
                    val j = bucketItems[m]
                    if (j == i) continue
                    val sj = asleep[j]
                    // Each pair of moving flakes once (from the lower one).
                    if (!sj && rank[j] in 0 until k) continue
                    // Two flakes in the liquid pass each other by.
                    if (!li && !lying[j]) continue
                    val vj = if (sj) 0f else len(vx[j], vy[j]) + pull
                    val lim = radius[i] + radius[j] + margin + (vi + vj) * h * 1.5f
                    val dx = x[i] - x[j]
                    val dy = y[i] - y[j]
                    if (dx * dx + dy * dy > lim * lim) continue
                    if (p == pairJ.size) pairJ = pairJ.copyOf(p * 2)
                    pairJ[p++] = j
                }
            }
        }
        pairStart[moving] = p
    }

    /** One pass over all the contacts of the moving flakes, the lowest first. */
    private fun solve() {
        for (k in 0 until moving) {
            val i = order[k]
            if (asleep[i]) continue
            val li = lying[i]
            if (li) onGlass(i)
            for (p in pairStart[k] until pairStart[k + 1]) {
                val j = pairJ[p]
                if (!li && !lying[j]) continue
                touch(i, j, asleep[j])
            }
        }
    }

    /** Lying grain [i] against the glass: pushed back in, rubbing on it. */
    private fun onGlass(i: Int) {
        val d = wall(x[i], y[i], glassNormal) + radius[i]
        if (d <= 0f) return
        val nx0 = glassNormal[0]
        val ny0 = glassNormal[1]
        x[i] -= nx0 * d
        y[i] -= ny0 * d
        if (stabilizing) return
        var tx = x[i] - x0[i]
        var ty = y[i] - y0[i]
        val dn = tx * nx0 + ty * ny0
        tx -= dn * nx0
        ty -= dn * ny0
        val lt = len(tx, ty)
        if (lt > 1e-9f) {
            val m = 0.7f * kotlin.math.tan(glassHoldAt(i))
            val c = if (lt < m * d) 1f else min(1f, m * 0.8f * d / lt)
            x[i] -= tx * c
            y[i] -= ty * c
        }
        contacts[i]++
        if (gravityX * nx0 + gravityY * ny0 > 0.2f) supported[i] = true
    }

    /** Flakes [i] (moving) and [j] (asleep if [jAsleep]) pushed apart if they overlap. */
    private fun touch(i: Int, j: Int, jAsleep: Boolean) {
        val dx = x[i] - x[j]
        val dy = y[i] - y[j]
        val rr = radius[i] + radius[j]
        val d2 = dx * dx + dy * dy
        if (d2 >= rr * rr) return
        val d = sqrt(d2).coerceAtLeast(1e-6f)
        val nx0 = dx / d
        val ny0 = dy / d
        val pen = rr - d
        // Pushed apart evenly (all on the moving one against a sleeping one). Only when
        // undoing old overlaps the higher one takes more of the push, so stacks stand
        // firm; that moves no grain's speed, so nothing creeps.
        val si = when {
            jAsleep -> 1f
            stabilizing -> {
                val t = STACKING * (height[i] - height[j])
                0.5f + 0.5f * t / (1f + abs(t))
            }
            else -> 0.5f
        }
        val sj = 1f - si
        x[i] += nx0 * pen * si
        y[i] += ny0 * pen * si
        x[j] -= nx0 * pen * sj
        y[j] -= ny0 * pen * sj
        if (stabilizing) return
        if (lying[i] && lying[j]) {
            // Friction: the grains do not slip on each other while they press hard enough.
            var tx = x[i] - x0[i] - (if (jAsleep) 0f else x[j] - x0[j])
            var ty = y[i] - y0[i] - (if (jAsleep) 0f else y[j] - y0[j])
            val dn = tx * nx0 + ty * ny0
            tx -= dn * nx0
            ty -= dn * ny0
            val lt = len(tx, ty)
            if (lt > 1e-9f) {
                val m = (friction[i] + friction[j]) * 0.5f
                val c = if (lt < m * pen) 1f else min(1f, m * 0.8f * pen / lt)
                val fi = c * si
                val fj = c * sj
                x[i] -= tx * fi
                y[i] -= ty * fi
                x[j] += tx * fj
                y[j] += ty * fj
            }
        }
        contacts[i]++
        if (!jAsleep) contacts[j]++
        // Resting on the other one (it is below, against gravity).
        val under = -(nx0 * gravityX + ny0 * gravityY)
        if (lying[j] && under > 0.2f) supported[i] = true
        if (lying[i] && !jAsleep && -under > 0.2f) supported[j] = true
        // A sleeping grain run into hard wakes up and gives way.
        if (jAsleep && -(vx[i] * nx0 + vy[i] * ny0) > WAKE_SPEED && travelling(i)) wake(j)
    }

    /** Whether grain [i] rests only on the glass or on sleeping grains (and on something). */
    private fun onRestingBase(i: Int): Boolean {
        if (len(vx[i], vy[i]) > MOVING) return false
        var any = false
        if (wall(x[i], y[i], glassNormal) + radius[i] * 1.1f > 0f && gravityX * glassNormal[0] + gravityY * glassNormal[1] > 0.3f) any = true
        val bi = bucketOf[i] % bcols
        val bj = bucketOf[i] / bcols
        for (dj in -1..1) for (di in -1..1) {
            val ci = bi + di
            val cj = bj + dj
            if (ci < 0 || cj < 0 || ci >= bcols || cj >= brows) continue
            val b = cj * bcols + ci
            for (k in bucketStart[b] until bucketStart[b + 1]) {
                val j = bucketItems[k]
                if (j == i || !lying[j]) continue
                val dx = x[j] - x[i]
                val dy = y[j] - y[i]
                val d = len(dx, dy)
                if (d > (radius[i] + radius[j]) * 1.1f) continue
                if (dx * gravityX + dy * gravityY < 0.3f * d) continue
                if (!asleep[j]) return false
                any = true
            }
        }
        return any
    }

    /**
     * Grains pressed together in the heap move together: each one's speed is drawn towards
     * the mean of the moving ones touching it. A sliding mass keeps its pace, a grain
     * rolling over resting snow keeps its own; the jostling inside a pressed heap dies away
     * instead of feeding on itself.
     */
    private fun calm() {
        for (i in 0 until flakes) {
            avgX[i] = vx[i]
            avgY[i] = vy[i]
            if (!lying[i] || asleep[i]) continue
            var sx = 0f
            var sy = 0f
            var n = 0
            val bi = bucketOf[i] % bcols
            val bj = bucketOf[i] / bcols
            for (dj in -1..1) for (di in -1..1) {
                val ci = bi + di
                val cj = bj + dj
                if (ci < 0 || cj < 0 || ci >= bcols || cj >= brows) continue
                val b = cj * bcols + ci
                for (k in bucketStart[b] until bucketStart[b + 1]) {
                    val j = bucketItems[k]
                    if (j == i || !lying[j] || asleep[j]) continue
                    if (len(x[j] - x[i], y[j] - y[i]) > (radius[i] + radius[j]) * 1.1f) continue
                    sx += vx[j]
                    sy += vy[j]
                    n++
                }
            }
            if (n == 0) continue
            avgX[i] = vx[i] + (sx / n - vx[i]) * CALM
            avgY[i] = vy[i] + (sy / n - vy[i]) * CALM
        }
        for (i in 0 until flakes) {
            if (!lying[i] || asleep[i]) continue
            vx[i] = avgX[i]
            vy[i] = avgY[i]
        }
    }

    /** Grains that have stopped go to sleep; a grain that moved off wakes those it held up. */
    private fun rest(h: Float) {
        for (i in 0 until flakes) {
            if (!lying[i] || asleep[i]) continue
            if (!told[i] && len(x[i] - anchorX[i], y[i] - anchorY[i]) > 0.3f * grain) {
                told[i] = true
                wakeAround(i)
            }
            // Asleep once it has stayed within a sliver of one place for a while (in a deep
            // heap the grains tremble a little round where they lie; that is rest too).
            // Resting only on sleeping grains or on the glass, sooner: so rest grows up
            // from the bottom of a heap, and a deep heap comes to rest as a whole.
            still[i] += h
            val base = onRestingBase(i)
            if (still[i] < (if (base) SLEEP_TIME * 0.5f else SLEEP_TIME)) continue
            val drift = if (base) SLEEP_DRIFT * 2f else SLEEP_DRIFT
            if (contacts[i] == 0 || len(x[i] - stillX[i], y[i] - stillY[i]) > drift * grain) {
                still[i] = 0f
                stillX[i] = x[i]
                stillY[i] = y[i]
                continue
            }
            // Caught on a slope steeper than it holds (fallen onto a heap's flank, say): it
            // does not stay there but breaks out and rolls on down.
            if (!settling && tries[i] < MAX_TRIES && tooSteep(i)) {
                tries[i]++
                still[i] = 0f
                stillX[i] = x[i]
                stillY[i] = y[i]
                vx[i] = breakX * BREAK_OUT
                vy[i] = breakY * BREAK_OUT
                continue
            }
            asleep[i] = true
            tries[i] = 0
            vx[i] = 0f
            vy[i] = 0f
            restGX[i] = gravityX
            restGY[i] = gravityY
            if (len(x[i] - anchorX[i], y[i] - anchorY[i]) > 0.5f * grain) moves++
            anchorX[i] = x[i]
            anchorY[i] = y[i]
            if (underGlass(i)) stuck[i] = true
        }
    }

    /** Which lying grains are on the surface, and which way it faces there. */
    private fun findSurface() {
        for (i in 0 until flakes) {
            if (!lying[i]) {
                surface[i] = false
                continue
            }
            var sx = 0f
            var sy = 0f
            var n = 0
            val bi = bucketOf[i] % bcols
            val bj = bucketOf[i] / bcols
            for (dj in -1..1) for (di in -1..1) {
                val ci = bi + di
                val cj = bj + dj
                if (ci < 0 || cj < 0 || ci >= bcols || cj >= brows) continue
                val b = cj * bcols + ci
                for (k in bucketStart[b] until bucketStart[b + 1]) {
                    val j = bucketItems[k]
                    if (j == i || !lying[j]) continue
                    val dx = x[j] - x[i]
                    val dy = y[j] - y[i]
                    val d = len(dx, dy)
                    if (d > (radius[i] + radius[j]) * 1.3f || d < 1e-6f) continue
                    sx += dx / d
                    sy += dy / d
                    n++
                }
            }
            // The glass counts as snow round it.
            val gd = wall(x[i], y[i], glassNormal) + radius[i] * 1.3f
            if (gd > 0f) {
                sx += glassNormal[0] * 2f
                sy += glassNormal[1] * 2f
                n += 2
            }
            val len = len(sx, sy)
            if (len > 1e-4f) {
                outX[i] = -sx / len
                outY[i] = -sy / len
            } else {
                outX[i] = -gravityX
                outY[i] = -gravityY
            }
            surface[i] = n < 5 || len > 1.5f
        }
    }

    // ---------------------------------------------------------------- for tests

    /** Every flake counted once and inside the glass. */
    internal fun consistent(): Boolean {
        if ((0 until flakes).count { !lying[it] } != floating) return false
        for (i in 0 until flakes) {
            if (x[i].isNaN() || y[i].isNaN()) return false
            if (wall(x[i], y[i], glassNormal) > 0.01f) return false
        }
        return true
    }

    /** Mean x of the lying snow. */
    internal fun meanLyingX(): Float {
        var s = 0f
        var n = 0
        for (i in 0 until flakes) if (lying[i]) { s += x[i]; n++ }
        return if (n == 0) 0f else s / n
    }

    /** Grains lying in the top half of the glass. */
    internal fun lyingInTopHalf(): Int = (0 until flakes).count { lying[it] && y[it] < 0.5f }

    /** Grains lying in the bottom half of the glass. */
    internal fun lyingInBottomHalf(): Int = (0 until flakes).count { lying[it] && y[it] >= 0.5f }

    /** Height of the snow lying on the bottom at [px]. */
    internal fun columnDepth(px: Float): Float {
        var top = bottomAt(px)
        for (i in 0 until flakes) if (lying[i] && abs(x[i] - px) < grain * 1.5f) top = min(top, y[i] - radius[i])
        return bottomAt(px) - top
    }

    internal fun awakeNow() = (0 until flakes).count { lying[it] && !asleep[it] }

    /** For test messages. */
    internal fun describe(): String =
        "floating $floating lying ${(0 until flakes).count { lying[it] }} awake ${awakeNow()} stuck ${(0 until flakes).count { stuck[it] }}"

    /** Fluid speed under a point, for tests. */
    internal fun liquidAt(px: Float, py: Float): Float = len(sample(u, px, py), sample(v, px, py))

    companion object {
        /** Depth of the crust where it lies evenly over the flat bottom, bar heights. */
        const val CRUST_DEPTH = 0.06f
        /** The share of the snow that makes the crust; the rest hangs in the liquid. */
        private const val CRUST_SHARE = 0.4f
        /** How fast the hanging snow sinks: hardly at all, it only leans with the tilt. */
        private const val HANG_SINK = 0.003f
        /** How hard hanging flakes keep apart, bar heights a second... */
        private const val SPREAD = 0.06f
        /** ...within this many times their mean spacing. */
        private const val SPREAD_REACH = 1.15f
        /** The steepest surface the crust holds on: the loosest grain (40°)... */
        private val HOLD_MIN = Math.toRadians(40.0).toFloat()
        /** ...and this much more for the firmest (to 60°): snow stuck to the glass is sticky. */
        private val HOLD_SPAN = Math.toRadians(20.0).toFloat()
        /** The steepest slope a grain on the move stops on: the loosest (20°)... */
        private val REST_MIN = Math.toRadians(20.0).toFloat()
        /** ...and this much more for the firmest (to 28°). */
        private val REST_SPAN = Math.toRadians(12.0).toFloat()
        /** The steepest glass the crust holds on (50° to 70°). */
        private val GLASS_HOLD_MIN = Math.toRadians(50.0).toFloat()
        private val GLASS_HOLD_SPAN = Math.toRadians(20.0).toFloat()
        /**
         * How heavy the snow is in the liquid, against what it was first made with: the
         * liquid holds up nearly all of its weight, so it sinks, slides and rolls slowly and
         * is easily stirred up. Everything that comes of its weight scales with this.
         */
        private const val WEIGHT = 1f / 3f
        /** A grain faster than this is moving off (those on it look again). */
        private const val MOVING = 0.05f * WEIGHT
        /** How much of the way to a grain its trail comes each step. */
        private const val TRAIL = 0.05f
        /** How far each step a grain's speed goes towards that of the grains round it. */
        private const val CALM = 0.5f
        /** Friction between grains on the move (they roll, so little), a middling grain. */
        private const val ROLL_FRICTION = 0.12f
        /** Gravity on a grain in the heap, bar heights a second², ... */
        private const val HEAP_GRAVITY = 4f * WEIGHT
        /** ...the liquid holding it back (time constant, seconds)... */
        private const val HEAP_DRAG = 0.25f
        /** ...and its top speed. */
        private const val MAX_HEAP_SPEED = 1.2f * WEIGHT
        private const val HEIGHT_BINS = 1024
        /** How much more the higher of two grains gives way when old overlaps are undone. */
        private const val STACKING = 3f
        private const val STABILIZE_PASSES = 2
        /** The speed a grain breaks out of its place with when the slope gives way. */
        private val BREAK_OUT = 0.25f * sqrt(WEIGHT)
        /** A grain held fast where the slope looks too steep stops trying after this. */
        private const val MAX_TRIES = 3
        /** A slope gives way only once the phone has turned this far since it lay (cosine). */
        private val TURNED_COS = cos(Math.toRadians(5.0)).toFloat()
        private const val SUBSTEPS = 2
        private const val ITERATIONS = 3
        private const val SETTLE_SUBSTEPS = 90
        private const val RELAX_PASSES = 40
        /** A grain that stays within this many grains of one place for [SLEEP_TIME] sleeps. */
        private const val SLEEP_DRIFT = 0.12f
        private const val SLEEP_TIME = 0.3f
        /** A sleeping grain run into faster than this wakes. */
        private const val WAKE_SPEED = 0.2f * WEIGHT
        /** A grain off the snow this long is in the liquid again, seconds. */
        private const val AIR_TIME = 1f
        /** A flake faster than this does not settle yet. */
        private const val LAND_SPEED = 0.35f
        /** The glass counts as above the snow when gravity points this much away from it. */
        private const val HANGING = 0.75f
        /** Snow on a wall turned away lets go past this pull away from it (about 12°)... */
        private const val LET_GO_MIN = 0.2f
        /** ...and the grains next to the glass only past this much more (about 64°). */
        private const val LET_GO_SPAN = 0.7f
        /** Liquid running faster than this over the heap starts lifting grains, bar heights/s. */
        private val STIR = 0.3f * sqrt(WEIGHT)
        /** How fast running liquid lifts a grain on top, per (bar height/s) over [STIR]. */
        private const val LIFT_FLOW = 10f
        /** How fast a shake lifts a grain on top, per m/s² over 2.5. */
        private const val LIFT_SHAKE = 1.4f
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
        /** How much more a slight tilt counts than it is (fading out at a quarter turn). */
        private const val TILT_BOOST = 1.1f
        private const val HALF_PI = (Math.PI / 2).toFloat()
    }
}

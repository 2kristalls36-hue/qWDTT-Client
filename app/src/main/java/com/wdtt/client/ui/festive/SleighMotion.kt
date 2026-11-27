package com.wdtt.client.ui.festive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What Santa's train does, second by second (see [SleighTrain]); [pose] is where everything
 * is now.
 *
 * Standing (no connection) the train is a silhouette, in its colours only under the droplet,
 * and only the figure under the droplet comes alive now and then, quietly: the bullfinch on
 * the lantern looks round, flicks its tail, hops round, pecks, fluffs up; Santa sets his cap
 * straight, stretches, chuckles, looks back at the presents; a reindeer shifts a stiff leg,
 * flicks an ear or the tail, lowers its head; the one without the red nose wags its tail
 * at the droplet coming over it, and the lead one looks down at its own red nose, which
 * glows brighter. It blinks. In between nothing moves, and nothing needs drawing
 * ([busy], [secondsToNext]).
 *
 * When the connection starts the train sets off a second later: Santa gives the word with
 * a flick of the reins, the bullfinch flies off, the reindeer pick up speed into a gallop,
 * snow flying where their hooves strike and from under the runners, trees and the wind going
 * by; as it picks up speed every figure takes on its colours. When it stops they slow down
 * and stand, the colours fade back, and the bullfinch comes back to its place.
 */
class SleighMotion(seed: Int = 1) {
    val pose = TrainPose()

    /** Bumped for a tab whenever what it shows changes (read while drawing). */
    val frames = Array(SleighTrain.count) { mutableIntStateOf(0) }

    /** Seconds since it began. */
    var time = 0f
        private set

    private var travelling = false
    private var onFor = 0f
    private var stoppedFor = 0f
    /** This trip set off from standing: it waits, then Santa gives the word. */
    private var departing = false
    /** How far it is on its way to full speed, 0..1 (the speed is eased from it). */
    private var drive = 0f
    /** Time since Santa gave the word, or negative. */
    private var commanded = -1f
    /** The gallop's phase, in strides: the train's, and each reindeer's own (out of step). */
    private var stride = 0f
    private val strides = floatArrayOf(0f, 0f, 0f, -0.13f)
    private var distance = 0f
    /** The tab under the droplet, or -1. */
    private var lensTab = -1

    private var bird = PERCHED
    private var birdT = 0f
    private var birdFacing = 1f

    // The idle stirs, one slot per actor: the bird, Santa, the reindeer, the lead reindeer
    // (each on the tab of the same number).
    private val kind = IntArray(ACTORS) { -1 }
    private val start = FloatArray(ACTORS)
    private val length = FloatArray(ACTORS)
    private val variant = IntArray(ACTORS)
    private val next = FloatArray(ACTORS)
    private val blinkAt = FloatArray(ACTORS)
    private val blinkFrom = FloatArray(ACTORS) { -10f }

    // Snow thrown up: when each particle was made, how long it lasts, its speed.
    private val dustLife = FloatArray(TrainPose.MAX_DUST)
    private val dustVx = FloatArray(TrainPose.MAX_DUST)
    private val dustVy = FloatArray(TrainPose.MAX_DUST)
    private var dustNext = 0
    private val lastU = FloatArray(8) { -1f }
    private var runnerDust = 0f
    private val hoof = FloatArray(2)

    private val last = Array(TrainPose.FIGURES) { FloatArray(J.COUNT * 5) }
    private var lastBird = FloatArray(5)
    private var lastExtra = FloatArray(5)

    private var noise = seed * 7919 + 13

    private fun rnd(): Float {
        noise = noise * 1103515245 + 12345
        return ((noise ushr 8) and 0xFFFF) / 65535f
    }

    init {
        for (a in 0 until ACTORS) {
            next[a] = 1.5f + rnd() * 3f
            blinkAt[a] = 1f + rnd() * 4f
        }
        compose()
    }

    /** 0 standing, 1 at full speed. */
    val speed: Float get() = ease(drive)

    /** Whether it was last told the connection is on. */
    val travellingNow: Boolean get() = travelling

    /** The tab it was last told the droplet is over, or -1. */
    val lensTabNow: Int get() = lensTab

    /** The bullfinch sits in its place on the trailer. */
    val perched: Boolean get() = bird == PERCHED

    /** The bullfinch has flown off and is not to be seen. */
    val birdAway: Boolean get() = bird == AWAY

    private val idle: Boolean get() = !travelling && drive == 0f && commanded < 0f

    /** Something is moving now: frames are needed. */
    val busy: Boolean
        get() = travelling || drive > 0f || commanded >= 0f || bird == LEAVING || bird == RETURNING ||
            kind.any { it >= 0 } || (0 until ACTORS).any { time - blinkFrom[it] < BLINK } ||
            pose.dustFigure.any { it >= 0 }

    /** When not [busy]: seconds until something is due to start (a stir, a blink, the bird). */
    fun secondsToNext(): Float {
        var s = Float.MAX_VALUE
        for (a in 0 until ACTORS) {
            if (!awake(a)) continue
            s = min(s, next[a] - time)
            s = min(s, blinkAt[a] - time)
        }
        if (bird == AWAY && !travelling) s = min(s, BIRD_BACK - stoppedFor)
        return max(0f, s)
    }

    /** Actor [a] may stir and blink: the train goes, or it is under the droplet. */
    private fun awake(a: Int) = !idle || a == lensTab

    /** The tab the droplet at [lens] (in tabs) is over. */
    fun tabUnder(lens: Float): Int = if (lens.isNaN()) -1 else lens.roundToInt().coerceIn(0, ACTORS - 1)

    /**
     * Moves on by [dt] seconds; [travel] is whether the connection is on (or being made),
     * [lens] where the droplet is, in tabs. Returns whether anything moved.
     */
    fun advance(dt: Float, travel: Boolean, lens: Float = Float.NaN): Boolean {
        // Long pauses (nothing was moving, or the app was away) only move the clock on.
        val wasBusy = busy
        val h = if (wasBusy) dt.coerceIn(0f, 0.05f) else dt.coerceAtLeast(0f)
        time += h
        val tab = tabUnder(lens)
        if (tab != lensTab) {
            lensTab = tab
            if (tab == 2 && kind[2] < 0 && idle) {
                // The reindeer wags its tail at the droplet coming over it.
                kind[2] = DEER_WAG
                start[2] = time
                length[2] = LENGTHS[DEER_WAG]
            } else if (tab >= 0 && kind[tab] < 0) {
                // Just come under the droplet: it stirs before long.
                next[tab] = min(next[tab], time + 0.7f + rnd() * 1.2f)
            }
        }
        if (travel != travelling) {
            travelling = travel
            if (travel) {
                onFor = 0f
                departing = drive <= 0.001f
            } else {
                stoppedFor = 0f
            }
        }
        val step = min(h, 0.05f)
        if (travelling) {
            onFor += h
            if (departing) {
                if (onFor >= DELAY && commanded < 0f && drive == 0f) {
                    commanded = 0f
                    if (bird == PERCHED || bird == RETURNING) takeOff()
                }
                if (onFor >= DELAY + PULL_AFTER) drive = min(1f, drive + step / ACCEL)
            } else {
                drive = min(1f, drive + step / ACCEL)
            }
        } else {
            drive = max(0f, drive - step / DECEL)
            if (drive == 0f) stoppedFor += h
        }
        if (commanded >= 0f) {
            commanded += step
            if (commanded > COMMAND) commanded = -1f
        }
        val sp = speed
        if (sp > 0f) {
            // Each reindeer at its own pace, wandering a little: now in step, now not.
            val base = (1.2f + 1.2f * sp) * step
            stride += base
            strides[2] += base * (1f + 0.035f * sin(time * 0.71f))
            strides[3] += base * (1.045f + 0.035f * sin(time * 0.53f + 2f))
        }
        if (stride > 1000f) stride -= 1000f
        for (f in 2..3) if (strides[f] > 1000f) strides[f] -= 1000f
        distance += sp * CRUISE * step
        moveBird(step)
        stir()
        compose()
        dust(step)
        return mark()
    }

    // ------------------------------------------------------------------ the bullfinch

    private fun takeOff() {
        // Taking off on its way back: from where it is on the same path.
        birdT = if (bird == RETURNING) (1f - birdT / RETURN).coerceIn(0f, 1f) * LEAVE * 0.9f else 0f
        bird = LEAVING
        kind[0] = -1
    }

    private fun moveBird(h: Float) {
        when (bird) {
            LEAVING -> {
                birdT += h
                if (birdT >= LEAVE) bird = AWAY
            }
            AWAY -> if (!travelling && drive == 0f && stoppedFor >= BIRD_BACK) {
                bird = RETURNING
                birdT = 0f
            }
            RETURNING -> {
                birdT += h
                if (birdT >= RETURN) {
                    bird = PERCHED
                    birdFacing = 1f
                    next[0] = time + 2f + rnd() * 3f
                }
            }
        }
    }

    // ------------------------------------------------------------------ idle stirs

    private fun stir() {
        for (a in 0 until ACTORS) {
            if (kind[a] >= 0 && time - start[a] >= length[a]) {
                // A hop ends facing the other way.
                if (a == 0 && kind[a] == BIRD_HOP) birdFacing = -birdFacing
                kind[a] = -1
                // A longer stir is followed by a longer rest: mostly they keep still.
                next[a] = time + 5f + rnd() * 6f + length[a]
            }
            if (time >= blinkAt[a]) {
                if (awake(a)) blinkFrom[a] = time
                blinkAt[a] = time + 3f + rnd() * 4f
            }
            if (kind[a] >= 0 || time < next[a]) continue
            // Only standing, only under the droplet, and the bird only in its place.
            if (!idle || a != lensTab || (a == 0 && bird != PERCHED)) {
                next[a] = time + 1f + rnd() * 2f
                continue
            }
            val r = rnd()
            kind[a] = when (a) {
                0 -> when {
                    r < 0.25f -> BIRD_LOOK
                    r < 0.45f -> BIRD_TAIL
                    r < 0.7f -> BIRD_HOP
                    r < 0.85f -> BIRD_PECK
                    else -> BIRD_FLUFF
                }
                1 -> when {
                    r < 0.35f -> SANTA_CLOCK
                    r < 0.55f -> SANTA_STRETCH
                    r < 0.75f -> SANTA_CHUCKLE
                    else -> SANTA_LOOK
                }
                else -> when {
                    a == 3 && r < 0.35f -> DEER_NOSE
                    a == 2 && r < 0.2f -> DEER_WAG
                    r < 0.6f -> DEER_LEG
                    r < 0.75f -> DEER_EAR
                    r < 0.87f -> DEER_TAIL
                    else -> DEER_HEAD
                }
            }
            variant[a] = (rnd() * 4f).toInt().coerceIn(0, 3)
            start[a] = time
            length[a] = LENGTHS[kind[a]]
        }
    }

    // ------------------------------------------------------------------ the pose

    private fun compose() {
        val p = pose
        for (f in 0 until TrainPose.FIGURES) for (j in 0 until J.COUNT) p.set(f, j)
        val a = speed
        p.speed = a
        p.colour = ease(min(1f, drive * 1.3f))
        p.distance = distance
        p.time = time
        p.reinSlack = 1f - 0.9f * a
        p.reinFlick = 0f
        p.noseGlow = 0f
        p.clock = 0f
        p.reinsLaid = 0f
        val t = time

        // Going: the reindeer gallop, the sleighs bump along, the wind blows things back.
        if (a > 0f) {
            val amp = ease(min(1f, a * 1.3f))
            for (f in 2..3) {
                // Each in its own stride, its stretch wandering a little too.
                gallop(f, strides[f], amp * (1f + 0.05f * sin(t * (if (f == 3) 0.61f else 0.47f) + f)))
            }
            p.set(3, J.SCARF, turn = -12f * a + 7f * a * sin(t * 15f), sx = 1f + 0.2f * a)
            p.set(1, J.ROOT, dy = 0.14f * a * sin(stride * 4f * PI.toFloat() + 1f))
            p.set(1, J.SANTA, turn = 3f * a)
            p.set(1, J.HAT_TIP, turn = -14f * a + 5f * a * sin(t * 13f))
            p.set(1, J.ARM, turn = -6f * a)
            p.set(0, J.ROOT, dy = 0.14f * a * sin(stride * 4f * PI.toFloat() + 2.4f))
            p.set(0, J.LANTERN, turn = 16f * a + 4f * a * sin(t * 6.3f))
        }

        // Santa gives the word: arm up, a flick of the reins; the reindeer lift their heads.
        if (commanded >= 0f) {
            val c = (commanded / COMMAND).coerceIn(0f, 1f)
            val arm = when {
                c < 0.3f -> -38f * ease(c / 0.3f)
                c < 0.45f -> -38f + 46f * ease((c - 0.3f) / 0.15f)
                else -> 8f * (1f - ease((c - 0.45f) / 0.55f))
            }
            p.add(1, J.ARM, turn = arm)
            p.add(1, J.SANTA, turn = 4f * sin(PI.toFloat() * c))
            p.add(1, J.HEAD, turn = 3f * sin(PI.toFloat() * c))
            p.reinFlick = 0.9f * sin(PI.toFloat() * c) * min(1f, c / 0.3f)
            p.reinPhase = commanded * 22f
            for (f in 2..3) {
                p.add(f, J.NECK, turn = -7f * sin(PI.toFloat() * c))
                p.add(f, J.DEER_HEAD, turn = -4f * sin(PI.toFloat() * c))
                p.add(f, J.EAR, turn = 10f * sin(PI.toFloat() * c))
            }
        }

        // Slowing down: Santa leans back and draws the reins in.
        if (!travelling && drive > 0f) {
            val pull = sin(PI.toFloat() * (1f - drive))
            p.add(1, J.SANTA, turn = -4f * pull)
            p.add(1, J.ARM, turn = -14f * pull)
        }

        // The idle stirs.
        for (actor in 0 until ACTORS) {
            val k = kind[actor]
            if (k < 0) continue
            val q = ((t - start[actor]) / length[actor]).coerceIn(0f, 1f)
            when (k) {
                BIRD_LOOK -> p.add(TrainPose.BIRD, J.BIRD_HEAD, turn = (if (variant[actor] % 2 == 0) -20f else 14f) * hold(q, 0.2f, 0.25f))
                BIRD_TAIL -> p.add(TrainPose.BIRD, J.BIRD_TAIL, turn = -28f * (bump(q, 0f, 0.35f) + bump(q, 0.4f, 0.75f)))
                BIRD_HOP -> {
                    // Up off the perch for a second, not high, with quick wing beats; it turns
                    // round at the top and comes down on the same place facing the other way.
                    val crouch = bump(q, 0f, 0.14f)
                    val air = ((q - 0.12f) / 0.74f).coerceIn(0f, 1f)
                    val lift = sin(PI.toFloat() * air)
                    p.add(TrainPose.BIRD, J.ROOT, dx = 0.35f * sin(2f * PI.toFloat() * air), dy = -2.3f * lift + 0.2f * bump(q, 0.86f, 1f))
                    p.set(TrainPose.BIRD, J.BIRD_LEGS, sy = if (air in 0.01f..0.99f) 0.35f else 1f - 0.35f * crouch)
                    if (air in 0.01f..0.99f) {
                        p.set(TrainPose.BIRD, J.BIRD_WING, turn = -35f + 48f * sin(t * 2f * PI.toFloat() * 9f))
                        p.set(TrainPose.BIRD, J.BIRD_TAIL, turn = -8f)
                    }
                }
                BIRD_PECK -> p.add(TrainPose.BIRD, J.BIRD_HEAD, turn = 35f * (bump(q, 0f, 0.4f) + bump(q, 0.45f, 0.85f)))
                BIRD_FLUFF -> {
                    val b = bell(q)
                    p.set(TrainPose.BIRD, J.ROOT, sx = 1f + 0.12f * b, sy = 1f + 0.1f * b)
                    p.add(TrainPose.BIRD, J.BIRD_WING, turn = -12f * b)
                }
                SANTA_CLOCK -> {
                    // The reins laid down on the side of the sleigh; down into the sleigh and
                    // up with a big round clock held by its side, turned to him; a look at it
                    // (five to midnight), two nods; back down with it, and the reins taken up
                    // again. The clock comes out and goes in below the side of the sleigh.
                    var key = 0
                    while (key < CLOCK_AT.size - 2 && q >= CLOCK_AT[key + 1]) key++
                    val e = ease(((q - CLOCK_AT[key]) / (CLOCK_AT[key + 1] - CLOCK_AT[key])).coerceIn(0f, 1f))
                    val arm = CLOCK_TURN[key] + (CLOCK_TURN[key + 1] - CLOCK_TURN[key]) * e
                    val reach = CLOCK_REACH[key] + (CLOCK_REACH[key + 1] - CLOCK_REACH[key]) * e
                    p.add(1, J.ARM, turn = arm)
                    // The sleeve reaches; the mitten keeps its size.
                    p.joints[1][J.ARM * 5 + 3] = reach
                    p.set(1, J.HAND, sx = 1f / reach)
                    // Let go of on the side of the sleigh, taken up there again.
                    // Hung on the side of the sleigh while the hand rests there, taken up there.
                    if (q >= 0.13f && q < 0.88f) p.reinsLaid = 1f
                    if (q in 0.245f..0.775f) {
                        p.clock = 1f
                        // Upright whatever the arm does, tipped a little to him as he reads it.
                        val read = hold(((q - 0.4f) / 0.24f).coerceIn(0f, 1f), 0.25f, 0.25f)
                        p.set(1, J.CLOCK, turn = -arm - 6f * read)
                    }
                    val look = hold(((q - 0.36f) / 0.32f).coerceIn(0f, 1f), 0.15f, 0.2f)
                    p.add(1, J.HEAD, turn = 3f * look + 4f * (bump(q, 0.5f, 0.56f) + bump(q, 0.57f, 0.63f)))
                    p.add(1, J.SANTA, turn = 2f * look)
                    p.set(1, J.SANTA_EYE, sy = 1f - 0.45f * hold(((q - 0.42f) / 0.1f).coerceIn(0f, 1f), 0.3f, 0.3f))
                }
                SANTA_STRETCH -> {
                    val b = bell(q)
                    p.add(1, J.ARM, turn = -30f * b + 3f * b * sin(q * 6f * PI.toFloat()))
                    p.add(1, J.SANTA, turn = -3f * b)
                }
                SANTA_CHUCKLE -> {
                    val b = abs(sin(q * 3f * PI.toFloat())) * bell(q)
                    p.add(1, J.SANTA, dy = -0.2f * b)
                    p.add(1, J.HEAD, turn = 5f * sin(q * 3f * PI.toFloat()) * bell(q))
                    p.set(1, J.SANTA_EYE, sy = 1f - 0.7f * hold(q, 0.15f, 0.15f))
                }
                SANTA_LOOK -> {
                    p.add(1, J.HEAD, turn = -9f * hold(q, 0.25f, 0.25f))
                    p.add(1, J.SANTA, turn = -3f * hold(q, 0.25f, 0.25f))
                }
                DEER_LEG -> {
                    // A stiff leg lifted, bent and put down again.
                    val f = actor
                    val b = bell(q)
                    when (variant[actor]) {
                        0 -> { p.add(f, J.HIND_NEAR, turn = -5f * b); p.add(f, J.HOCK_NEAR, turn = -40f * b); p.add(f, J.FETLOCK_HIND_NEAR, turn = 35f * b) }
                        1 -> { p.add(f, J.FORE_NEAR, turn = -10f * b); p.add(f, J.KNEE_NEAR, turn = 60f * b); p.add(f, J.FETLOCK_FORE_NEAR, turn = 40f * b) }
                        2 -> { p.add(f, J.HIND_FAR, turn = -5f * b); p.add(f, J.HOCK_FAR, turn = -40f * b); p.add(f, J.FETLOCK_HIND_FAR, turn = 35f * b) }
                        else -> { p.add(f, J.FORE_FAR, turn = -10f * b); p.add(f, J.KNEE_FAR, turn = 60f * b); p.add(f, J.FETLOCK_FORE_FAR, turn = 40f * b) }
                    }
                    p.add(f, J.ROOT, dy = -0.12f * b)
                }
                DEER_EAR -> p.add(actor, J.EAR, turn = -22f * (bump(q, 0f, 0.4f) + bump(q, 0.5f, 0.9f)))
                DEER_TAIL -> p.add(actor, J.TAIL, turn = -30f * bell(q))
                DEER_WAG -> {
                    // A happy wag: the tail up, flicking quickly, the rump wiggling a little.
                    val on = hold(q, 0.12f, 0.25f)
                    p.add(actor, J.TAIL, turn = (-20f - 22f * sin(q * 9f * PI.toFloat())) * on)
                    p.add(actor, J.ROOT, turn = 0.6f * sin(q * 9f * PI.toFloat()) * on)
                    p.add(actor, J.EAR, turn = 8f * on)
                }
                DEER_HEAD -> {
                    p.add(actor, J.NECK, turn = 9f * hold(q, 0.25f, 0.3f))
                    p.add(actor, J.DEER_HEAD, turn = 5f * hold(q, 0.3f, 0.3f))
                    p.add(actor, J.PUPIL, dy = 0.06f * hold(q, 0.25f, 0.3f))
                }
                DEER_NOSE -> {
                    // It looks down at its own nose, cross-eyed; the nose glows brighter and
                    // brighter; a happy little shake of the head.
                    val look = hold(q, 0.15f, 0.2f)
                    p.add(actor, J.NECK, turn = 8f * look + 4f * sin((q - 0.8f) * 10f * PI.toFloat()) * bump(q, 0.8f, 1f))
                    p.add(actor, J.DEER_HEAD, turn = 7f * look)
                    p.add(actor, J.PUPIL, dx = 0.14f * look, dy = 0.13f * look)
                    p.noseGlow = look * (0.65f + 0.35f * sin(q * 8f * PI.toFloat()))
                    if (q in 0.72f..0.8f) p.set(actor, J.EYE, sy = 0.15f)
                }
            }
        }

        // Blinks.
        blink(TrainPose.BIRD, J.BIRD_EYE, 0)
        blink(1, J.SANTA_EYE, 1)
        blink(2, J.EYE, 2)
        blink(3, J.EYE, 3)

        // The bullfinch: in its place, or on the wing.
        when (bird) {
            PERCHED -> {
                p.birdX = SleighTrain.PERCH.x
                p.birdY = SleighTrain.PERCH.y
                p.birdScale = 1f
                p.birdAlpha = 1f
                // A hop turns it round halfway.
                p.birdFacing = if (kind[0] == BIRD_HOP && (t - start[0]) / length[0] > 0.5f) -birdFacing else birdFacing
            }
            LEAVING -> {
                val q = birdT / LEAVE
                val crouch = q < 0.12f
                val f = if (crouch) 0f else ease((q - 0.12f) / 0.88f)
                flight(f, flapping = !crouch)
                if (crouch) p.set(TrainPose.BIRD, J.ROOT, sy = 1f - 0.12f * sin(q / 0.12f * PI.toFloat()))
                p.birdFacing = -1f
            }
            AWAY -> p.birdAlpha = 0f
            RETURNING -> {
                val q = birdT / RETURN
                val f = 1f - ease(min(1f, q / 0.92f))
                flight(f, flapping = q < 0.78f)
                if (q >= 0.78f) {
                    // Gliding in, then the wings folded, a little bob on landing.
                    val fold = ((q - 0.78f) / 0.17f).coerceIn(0f, 1f)
                    p.set(TrainPose.BIRD, J.BIRD_WING, turn = -45f * (1f - fold))
                    p.set(TrainPose.BIRD, J.BIRD_LEGS, sy = 0.3f + 0.7f * fold)
                    if (q > 0.92f) p.add(TrainPose.BIRD, J.ROOT, dy = 0.25f * sin((q - 0.92f) / 0.08f * PI.toFloat()))
                }
                p.birdFacing = 1f
            }
        }
    }

    /**
     * Reindeer [f] in the gallop at stride [u0], as much as [amp], as a horse gallops (after
     * Muybridge): four beats, the hooves coming down one by one, a hind, the other hind, a
     * fore, the leading fore; then one flight with the legs gathered under the body (not
     * stretched out). Each hoof, while it is down, stays on the snow and goes back under the
     * body as the ground goes by; lifted, it is carried forward in an arc. The legs bend to
     * reach where their hooves are (two-bone reach: hip or shoulder, knee or hock). The body
     * rocks fore and aft, rising in the flight, sinking onto the forelegs; the neck swings
     * as a counterweight, the head steadier on it.
     */
    private fun gallop(f: Int, u0: Float, amp: Float) {
        val p = pose
        val u = frac(u0)
        val fig = SleighTrain.figures[f]
        val dy = amp * key(BODY_DY, u)
        val pitch = amp * key(BODY_TURN, u)
        p.set(f, J.ROOT, turn = pitch, dy = dy)
        p.set(f, J.NECK, turn = amp * key(NECK, u))
        p.set(f, J.DEER_HEAD, turn = amp * key(HEAD, u))
        p.set(f, J.EAR, turn = amp * (-18f + 6f * sin(2f * PI.toFloat() * (u + 0.3f))))
        p.set(f, J.TAIL, turn = amp * key(TAIL, u))
        val px = fig.pivotX[J.ROOT]
        val py = fig.pivotY[J.ROOT]
        val co = cos(pitch * DEG)
        val si = sin(pitch * DEG)
        for (leg in LEGS) {
            // Where in its own stride this leg is: 0 as its hoof strikes the snow.
            val ph = frac(u - leg.strike)
            val hx = fig.pivotX[leg.hip]
            val hy = fig.pivotY[leg.hip]
            val kx = fig.pivotX[leg.knee]
            val ky = fig.pivotY[leg.knee]
            val rest = SleighTrain.hoofRest(leg.fore, leg.far)
            val a = hypot(kx - hx, ky - hy)
            val b = hypot(rest.x - kx, rest.y - ky)
            val upper0 = atan2(ky - hy, kx - hx)
            val lower0 = atan2(rest.y - ky, rest.x - kx)
            // Where the hoof is to be on the screen: down, on the snow, going back with it;
            // lifted, carried forward in an arc. Then the same place in the body's own frame
            // (the body rises, sinks and tips), so a hoof on the snow stays put on it.
            val reach = STRIDE_REACH * amp
            val wx: Float
            val wy: Float
            var fold = 0f
            if (ph < STANCE) {
                val t = ph / STANCE
                wx = rest.x + reach * (1f - 2f * t)
                wy = SleighTrain.GROUND
            } else {
                val t = (ph - STANCE) / (1f - STANCE)
                wx = rest.x - reach + 2f * reach * ease(t)
                wy = SleighTrain.GROUND - (if (leg.fore) FORE_LIFT_H else HIND_LIFT_H) * amp * sin(PI.toFloat() * t)
                fold = sin(PI.toFloat() * t)
            }
            val qx = wx - px
            val qy = wy - py - dy
            val tx = px + qx * co + qy * si
            val ty = py - qx * si + qy * co
            // Two-bone reach from the hip to the hoof; the knee forward, the hock back.
            val dx = tx - hx
            val dyy = ty - hy
            val d = hypot(dx, dyy).coerceIn(abs(a - b) + 0.01f, a + b - 0.001f)
            val base = atan2(dyy, dx)
            val bend = acos(((a * a + d * d - b * b) / (2f * a * d)).coerceIn(-1f, 1f))
            val upper = if (leg.fore) base - bend else base + bend
            val kneeX = hx + a * cos(upper)
            val kneeY = hy + a * sin(upper)
            val lower = atan2(ty - kneeY, tx - kneeX)
            val hipTurn = wrap(upper - upper0)
            val kneeTurn = wrap(lower - lower0) - hipTurn
            p.set(f, leg.hip, turn = hipTurn / DEG)
            p.set(f, leg.knee, turn = kneeTurn / DEG)
            // The hoof kept level on the snow; lifted, it flips back.
            val level = -wrap(lower - lower0) / DEG * (1f - fold)
            p.set(f, leg.fetlock, turn = level + (if (leg.fore) FORE_FOLD else HIND_FOLD) * amp * fold)
        }
    }

    /** The bullfinch on its way: [f] 0 in its place, 1 far off over the top left. */
    private fun flight(f: Float, flapping: Boolean) {
        val p = pose
        p.birdX = SleighTrain.PERCH.x - 30f * f
        p.birdY = SleighTrain.PERCH.y - 15f * (1f - (1f - f) * (1f - f))
        p.birdScale = 1f - 0.55f * f
        p.birdAlpha = if (f < 0.55f) 1f else (1f - (f - 0.55f) / 0.45f).coerceIn(0f, 1f)
        if (flapping) p.set(TrainPose.BIRD, J.BIRD_WING, turn = -35f + 48f * sin(time * 2f * PI.toFloat() * 9f))
        p.set(TrainPose.BIRD, J.BIRD_LEGS, sy = 0.3f)
        p.set(TrainPose.BIRD, J.BIRD_TAIL, turn = -8f)
    }

    private fun blink(figure: Int, eye: Int, actor: Int) {
        val q = (time - blinkFrom[actor]) / BLINK
        if (q !in 0f..1f) return
        val k = eye * 5 + 4
        pose.joints[figure][k] = min(pose.joints[figure][k], 1f - 0.9f * sin(PI.toFloat() * q))
    }

    // ------------------------------------------------------------------ snow thrown up

    private fun dust(h: Float) {
        val p = pose
        // The old snow drifts back, settles and fades.
        for (k in 0 until TrainPose.MAX_DUST) {
            if (p.dustFigure[k] < 0) continue
            p.dustAge[k] += h / dustLife[k]
            if (p.dustAge[k] >= 1f) {
                p.dustFigure[k] = -1
                continue
            }
            p.dustX[k] += dustVx[k] * h
            p.dustY[k] = min(SleighTrain.GROUND, p.dustY[k] + dustVy[k] * h)
            dustVx[k] *= exp(-1.8f * h)
            dustVy[k] += 3f * h
        }
        val a = speed
        if (a <= 0.02f) {
            lastU.fill(-1f)
            return
        }
        // Each hoof as it strikes the ground and as it pushes off.
        var k = 0
        for (f in 2..3) {
            for (leg in LEGS) {
                val u = frac(strides[f] - leg.strike)
                val was = lastU[k]
                lastU[k] = u
                k++
                if (was < 0f) continue
                val n = (a * 2.5f).roundToInt()
                if (n <= 0) continue
                if (crossed(was, u, 0f)) puff(f, leg.fetlock, leg.fore, leg.far, n, kick = false)
                if (crossed(was, u, STANCE)) puff(f, leg.fetlock, leg.fore, leg.far, n, kick = true)
            }
        }
        // Under the runners of the two sleighs, steadily.
        runnerDust += h * 16f * a
        while (runnerDust >= 1f) {
            runnerDust -= 1f
            for (f in 0..1) spawn(f, 1.4f + rnd() * 1.2f, SleighTrain.GROUND - 0.2f, -(3f + 5f * rnd()) * (0.5f + 0.5f * a), -(0.6f + 1.4f * rnd()), 0.3f + 0.25f * rnd(), 0.4f + 0.3f * rnd())
        }
    }

    private fun puff(f: Int, fetlock: Int, fore: Boolean, far: Boolean, n: Int, kick: Boolean) {
        hoofAt(SleighTrain.figures[f], pose.joints[f], fetlock, fore, far, hoof)
        // Only a hoof that is down on the snow throws it.
        if (hoof[1] < SleighTrain.GROUND - 1.2f) return
        repeat(n) {
            val back = if (kick) 5f + 6f * rnd() else 2f + 4f * rnd()
            spawn(f, hoof[0] + (rnd() - 0.5f) * 0.8f, min(hoof[1], SleighTrain.GROUND) - 0.15f, -back, -(1f + 2f * rnd()) * (if (kick) 1.3f else 0.8f), 0.28f + 0.22f * rnd(), 0.35f + 0.3f * rnd())
        }
    }

    private fun spawn(f: Int, x: Float, y: Float, vx: Float, vy: Float, size: Float, life: Float) {
        val k = dustNext
        dustNext = (dustNext + 1) % TrainPose.MAX_DUST
        pose.dustFigure[k] = f
        pose.dustX[k] = x
        pose.dustY[k] = y
        pose.dustSize[k] = size
        pose.dustAge[k] = 0f
        dustVx[k] = vx
        dustVy[k] = vy
        dustLife[k] = life
    }

    /** Which tabs changed since the last frame; their frames are bumped. */
    private fun mark(): Boolean {
        val p = pose
        val dirty = BooleanArray(SleighTrain.count)
        for (f in 0 until SleighTrain.count) {
            val now = p.joints[f]
            val was = last[f]
            var changed = false
            var rootChanged = false
            for (k in now.indices) if (now[k] != was[k]) {
                changed = true
                if (k < 5) rootChanged = true
            }
            if (changed) {
                dirty[f] = true
                // The ropes to the neighbours hang on its root.
                if (rootChanged) {
                    if (f > 0) dirty[f - 1] = true
                    if (f < SleighTrain.count - 1) dirty[f + 1] = true
                }
                now.copyInto(was)
            }
        }
        val b = floatArrayOf(p.birdX, p.birdY, p.birdScale, p.birdFacing, p.birdAlpha)
        val birdJoints = p.joints[TrainPose.BIRD]
        val birdChanged = !b.contentEquals(lastBird) || !birdJoints.contentEquals(last[TrainPose.BIRD])
        if (birdChanged) {
            if (bird == PERCHED) dirty[0] = true else dirty.fill(true)
            lastBird = b
            birdJoints.copyInto(last[TrainPose.BIRD])
        }
        val e = floatArrayOf(p.reinSlack, p.reinFlick, p.noseGlow, p.speed, p.colour)
        if (!e.contentEquals(lastExtra)) {
            if (e[0] != lastExtra[0] || e[1] != lastExtra[1]) { dirty[1] = true; dirty[2] = true }
            if (e[2] != lastExtra[2]) dirty[3] = true
            if (e[4] != lastExtra[4]) dirty.fill(true)
            lastExtra = e
        }
        if (p.speed > 0f || p.dustFigure.any { it >= 0 }) dirty.fill(true)
        var any = false
        for (i in dirty.indices) if (dirty[i]) {
            frames[i].intValue++
            any = true
        }
        return any
    }

    private companion object {
        const val ACTORS = 4
        const val PERCHED = 0
        const val LEAVING = 1
        const val AWAY = 2
        const val RETURNING = 3

        /** From the connection starting to Santa's word, seconds. */
        const val DELAY = 1f
        /** How long his word takes; the reindeer start pulling this long into it. */
        const val COMMAND = 0.9f
        const val PULL_AFTER = 0.45f
        /** Seconds to full speed, and to a stop. */
        const val ACCEL = 3.5f
        const val DECEL = 2.6f
        /** Full speed, tab widths a second (the trees go by at this). */
        const val CRUISE = 1.7f
        /** The bullfinch's flight off and back, and how long after the stop it comes. */
        const val LEAVE = 1.6f
        const val RETURN = 2.6f
        const val BIRD_BACK = 1.4f
        const val BLINK = 0.16f

        /**
         * The gallop, a horse's (four beats, one flight with the legs gathered): when in a
         * stride each hoof strikes the snow, a hind, the other hind, a fore, the leading fore;
         * how long it stays down; how far it goes back under the body meanwhile and how high
         * it is lifted (grid units); how far the fetlock flips back when lifted (degrees).
         */
        private class Leg(val hip: Int, val knee: Int, val fetlock: Int, val fore: Boolean, val far: Boolean, val strike: Float)
        private val LEGS = listOf(
            Leg(J.HIND_NEAR, J.HOCK_NEAR, J.FETLOCK_HIND_NEAR, fore = false, far = false, strike = 0f),
            Leg(J.HIND_FAR, J.HOCK_FAR, J.FETLOCK_HIND_FAR, fore = false, far = true, strike = 0.1f),
            Leg(J.FORE_NEAR, J.KNEE_NEAR, J.FETLOCK_FORE_NEAR, fore = true, far = false, strike = 0.3f),
            Leg(J.FORE_FAR, J.KNEE_FAR, J.FETLOCK_FORE_FAR, fore = true, far = true, strike = 0.42f),
        )
        const val STANCE = 0.34f
        const val STRIDE_REACH = 4.2f
        const val FORE_LIFT_H = 3.6f
        const val HIND_LIFT_H = 2.6f
        const val FORE_FOLD = 65f
        const val HIND_FOLD = 40f
        /**
         * The body over a stride, keyed at its eighths from the first hind strike: its sinking
         * (down is more: it sinks as the legs take it, most on the forelegs, and rises in the
         * flight), its tip (nose down is more: up over the hind legs, down over the forelegs),
         * the neck swinging against it, the head steadier, the tail.
         */
        val BODY_DY = floatArrayOf(0.35f, 0.55f, 0.55f, 0.75f, 0.55f, -0.2f, -0.65f, -0.3f)
        val BODY_TURN = floatArrayOf(-2f, -3f, -1.5f, 1.5f, 3f, 2f, 0f, -1f)
        val NECK = floatArrayOf(-3f, -4f, -2f, 3f, 6f, 4f, -1f, -3f)
        val HEAD = floatArrayOf(2f, 2.5f, 1f, -2f, -3.5f, -2f, 1f, 1.5f)
        val TAIL = floatArrayOf(-24f, -20f, -18f, -22f, -28f, -32f, -30f, -26f)
        const val DEG = (PI / 180.0).toFloat()

        fun wrap(r: Float): Float {
            var x = r
            while (x > PI.toFloat()) x -= 2f * PI.toFloat()
            while (x < -PI.toFloat()) x += 2f * PI.toFloat()
            return x
        }

        const val BIRD_LOOK = 0
        const val BIRD_TAIL = 1
        const val BIRD_HOP = 2
        const val BIRD_PECK = 3
        const val BIRD_FLUFF = 4
        const val SANTA_CLOCK = 5
        /**
         * Santa's clock, keyed: when (share of the stir), the arm's turn and how far the
         * sleeve reaches; the hand stops at each key. Over the side of the sleigh, down onto
         * it (the reins hung there), a moment, down into the sleigh, up with the clock, held,
         * down again, onto the side (the reins taken up), a moment, back.
         */
        val CLOCK_AT = floatArrayOf(0f, 0.07f, 0.12f, 0.15f, 0.25f, 0.4f, 0.64f, 0.78f, 0.86f, 0.9f, 1f)
        val CLOCK_TURN = floatArrayOf(0f, 9f, 15f, 15f, 52f, -45f, -45f, 52f, 15f, 15f, 0f)
        val CLOCK_REACH = floatArrayOf(1f, 1.12f, 1.23f, 1.23f, 1.48f, 1.14f, 1.14f, 1.48f, 1.23f, 1.23f, 1f)
        const val SANTA_STRETCH = 6
        const val SANTA_CHUCKLE = 7
        const val SANTA_LOOK = 8
        const val DEER_LEG = 9
        const val DEER_EAR = 10
        const val DEER_TAIL = 11
        const val DEER_HEAD = 12
        const val DEER_NOSE = 13
        const val DEER_WAG = 14
        val LENGTHS = floatArrayOf(1.6f, 0.8f, 1f, 0.9f, 0.9f, 5f, 1.3f, 1.1f, 1.8f, 1.1f, 0.6f, 0.6f, 1.6f, 2.8f, 1.5f)

        fun frac(x: Float) = x - floor(x)

        /** Whether going from [was] to [now] (stride fractions, forwards) passed [at]. */
        fun crossed(was: Float, now: Float, at: Float): Boolean =
            if (now >= was) at > was && at <= now else at > was || at <= now

        /** A smooth closed curve through [k] (equally spaced over a stride) at [u]. */
        fun key(k: FloatArray, u: Float): Float {
            val n = k.size
            val x = frac(u) * n
            val i = floor(x).toInt()
            val t = x - i
            val p0 = k[(i - 1 + n) % n]
            val p1 = k[i % n]
            val p2 = k[(i + 1) % n]
            val p3 = k[(i + 2) % n]
            return 0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t * t + (-p0 + 3f * p1 - 3f * p2 + p3) * t * t * t)
        }

        fun ease(x: Float): Float {
            val c = x.coerceIn(0f, 1f)
            return c * c * (3f - 2f * c)
        }

        /** 0 → 1 → 0 over the whole stir, smoothly. */
        fun bell(q: Float): Float {
            val s = sin(PI.toFloat() * q.coerceIn(0f, 1f))
            return s * s
        }

        /** Up over [rise], held, down over the last [fall]. */
        fun hold(q: Float, rise: Float, fall: Float): Float = when {
            q < rise -> ease(q / rise)
            q > 1f - fall -> ease((1f - q) / fall)
            else -> 1f
        }

        /** A bell from [a] to [b], 0 elsewhere. */
        fun bump(q: Float, a: Float, b: Float): Float = if (q <= a || q >= b) 0f else bell((q - a) / (b - a))
    }
}

/**
 * Santa's train for the tab bar, moving on with the frames while anything moves and asleep
 * otherwise (until a stir is due, the connection changes or the droplet goes to another
 * tab); [lens] is where the droplet is, in tabs. With animations off it stands.
 */
@Composable
fun rememberSleighMotion(festive: FestiveState, lens: () -> Float): SleighMotion {
    val motion = remember { SleighMotion() }
    val moving = festive.moving
    val travel = festive.travelling
    LaunchedEffect(motion, moving) {
        if (!moving) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            if (motion.busy || travel() != motion.travellingNow || motion.tabUnder(lens()) != motion.lensTabNow) {
                val now = withFrameNanos { it }
                // About 60 frames a second at most: on a 120 Hz screen every other one.
                if (now - last < MIN_FRAME_NS) continue
                motion.advance((now - last) / 1e9f, travel(), lens())
                last = now
            } else {
                // Nothing moves: sleep until something is due, the connection changes or the
                // droplet goes to another tab.
                val wait = (motion.secondsToNext() * 1000f).toLong().coerceAtLeast(16L)
                val before = System.nanoTime()
                withTimeoutOrNull(wait) {
                    snapshotFlow { travel() to motion.tabUnder(lens()) }
                        .first { it != (motion.travellingNow to motion.lensTabNow) }
                }
                motion.advance((System.nanoTime() - before) / 1e9f, travel(), lens())
                last = withFrameNanos { it }
            }
        }
    }
    return motion
}

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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * What Santa's train does, second by second (see [SleighTrain]); [pose] is where everything
 * is now.
 *
 * Standing (no connection), the figures stir now and then, quietly, one at a time: the
 * bullfinch on the trailer looks round, flicks its tail, hops round, pecks, fluffs up;
 * Santa sets his cap straight, stretches, chuckles, looks back at the presents; the reindeer
 * shift a stiff leg, flick an ear or the tail, lower the head, and the lead one looks down
 * at its own red nose, which glows brighter. Everyone blinks. Between these nothing moves,
 * and nothing needs drawing ([busy], [secondsToNext]).
 *
 * When the connection starts the train sets off a second later: Santa gives the word with
 * a flick of the reins, the bullfinch flies off, the reindeer start and pick up speed until
 * they gallop, with trees and the wind going by. When it stops they slow down and stand,
 * and the bullfinch comes back to its place.
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
    /** The gallop's phase, radians. */
    private var stride = 0f
    private var distance = 0f

    private var bird = PERCHED
    private var birdT = 0f
    private var birdFacing = 1f

    // The idle stirs, one slot per actor: the bird, Santa, the reindeer, the lead reindeer.
    private val kind = IntArray(ACTORS) { -1 }
    private val start = FloatArray(ACTORS)
    private val length = FloatArray(ACTORS)
    private val variant = IntArray(ACTORS)
    private val next = FloatArray(ACTORS)
    private val blinkAt = FloatArray(ACTORS)
    private val blinkFrom = FloatArray(ACTORS) { -10f }

    private val last = Array(TrainPose.FIGURES) { FloatArray(J.COUNT * 5) }
    private var lastBird = FloatArray(5)
    private var lastExtra = FloatArray(4)

    private var noise = seed * 7919 + 13

    private fun rnd(): Float {
        noise = noise * 1103515245 + 12345
        return ((noise ushr 8) and 0xFFFF) / 65535f
    }

    init {
        for (a in 0 until ACTORS) {
            next[a] = 2f + a * 1.9f + rnd() * 3f
            blinkAt[a] = 1f + rnd() * 4f
        }
        compose()
    }

    /** 0 standing, 1 at full speed. */
    val speed: Float get() = ease(drive)

    /** Whether it was last told the connection is on. */
    val travellingNow: Boolean get() = travelling

    /** The bullfinch sits in its place on the trailer. */
    val perched: Boolean get() = bird == PERCHED

    /** The bullfinch has flown off and is not to be seen. */
    val birdAway: Boolean get() = bird == AWAY

    /** Something is moving now: frames are needed. */
    val busy: Boolean
        get() = travelling || drive > 0f || commanded >= 0f || bird == LEAVING || bird == RETURNING ||
            kind.any { it >= 0 } || (0 until ACTORS).any { time - blinkFrom[it] < BLINK }

    /** When not [busy]: seconds until something is due to start (a stir, a blink, the bird). */
    fun secondsToNext(): Float {
        var s = Float.MAX_VALUE
        for (a in 0 until ACTORS) {
            s = min(s, next[a] - time)
            s = min(s, blinkAt[a] - time)
        }
        if (bird == AWAY && !travelling) s = min(s, BIRD_BACK - stoppedFor)
        return max(0f, s)
    }

    /**
     * Moves on by [dt] seconds; [travel] is whether the connection is on (or being made).
     * Returns whether anything moved.
     */
    fun advance(dt: Float, travel: Boolean): Boolean {
        // Long pauses (nothing was moving, or the app was away) only move the clock on.
        val wasBusy = busy
        val h = if (wasBusy) dt.coerceIn(0f, 0.05f) else dt.coerceAtLeast(0f)
        time += h
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
        stride += 2f * PI.toFloat() * (0.9f + 2.3f * sp) * step
        if (stride > 1000f) stride -= 2f * PI.toFloat() * 150f
        distance += sp * CRUISE * step
        moveBird(step)
        stir()
        compose()
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
        val idle = !travelling && drive == 0f && commanded < 0f
        for (a in 0 until ACTORS) {
            if (kind[a] >= 0 && time - start[a] >= length[a]) {
                // A hop ends facing the other way.
                if (a == 0 && kind[a] == BIRD_HOP) birdFacing = -birdFacing
                kind[a] = -1
                next[a] = time + 12f + rnd() * 14f
            }
            if (time >= blinkAt[a]) {
                blinkFrom[a] = time
                blinkAt[a] = time + 4f + rnd() * 6f
            }
            if (kind[a] >= 0 || time < next[a]) continue
            if (!idle || (a == 0 && bird != PERCHED)) {
                next[a] = time + 1f + rnd() * 2f
                continue
            }
            // One at a time: if another one is stirring, a little later.
            if ((0 until ACTORS).any { it != a && kind[it] >= 0 }) {
                next[a] = time + 0.6f + rnd() * 1.2f
                continue
            }
            val r = rnd()
            kind[a] = when (a) {
                0 -> when {
                    r < 0.3f -> BIRD_LOOK
                    r < 0.55f -> BIRD_TAIL
                    r < 0.7f -> BIRD_HOP
                    r < 0.85f -> BIRD_PECK
                    else -> BIRD_FLUFF
                }
                1 -> when {
                    r < 0.35f -> SANTA_HAT
                    r < 0.55f -> SANTA_STRETCH
                    r < 0.75f -> SANTA_CHUCKLE
                    else -> SANTA_LOOK
                }
                else -> when {
                    a == 3 && r < 0.35f -> DEER_NOSE
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
        p.distance = distance
        p.time = time
        p.reinSlack = 1f - 0.9f * a
        p.reinFlick = 0f
        p.noseGlow = 0f
        val t = time
        val s = stride

        // Going: the reindeer gallop, the sleighs bump along, the wind blows things back.
        if (a > 0f) {
            for (f in 2..3) {
                gallopLeg(f, J.HIND_NEAR, J.HOCK_NEAR, fore = false, o = 0f, a = a)
                gallopLeg(f, J.HIND_FAR, J.HOCK_FAR, fore = false, o = 0.55f, a = a)
                gallopLeg(f, J.FORE_NEAR, J.KNEE_NEAR, fore = true, o = PI.toFloat(), a = a)
                gallopLeg(f, J.FORE_FAR, J.KNEE_FAR, fore = true, o = PI.toFloat() + 0.55f, a = a)
                val lag = if (f == 3) 0.35f else 0f
                p.set(f, J.ROOT, turn = 2.5f * a * sin(s + 0.7f + lag), dy = -0.4f * a * (0.5f + 0.5f * sin(2f * s + lag)))
                p.set(f, J.NECK, turn = 3.5f * a * sin(s + 1.4f + lag) - 2f * a)
                p.set(f, J.EAR, turn = -16f * a)
                p.set(f, J.TAIL, turn = -22f * a + 6f * a * sin(2f * s))
            }
            p.set(3, J.SCARF, turn = -12f * a + 7f * a * sin(t * 15f), sx = 1f + 0.2f * a)
            p.set(1, J.ROOT, dy = 0.14f * a * sin(2f * s + 1f))
            p.set(1, J.SANTA, turn = 3f * a)
            p.set(1, J.HAT_TIP, turn = -14f * a + 5f * a * sin(t * 13f))
            p.set(1, J.ARM, turn = -6f * a)
            p.set(0, J.ROOT, dy = 0.14f * a * sin(2f * s + 2.4f))
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
                    p.add(TrainPose.BIRD, J.ROOT, dy = -1.2f * sin(PI.toFloat() * q))
                    p.set(TrainPose.BIRD, J.BIRD_LEGS, sy = 1f - 0.5f * sin(PI.toFloat() * q))
                }
                BIRD_PECK -> p.add(TrainPose.BIRD, J.BIRD_HEAD, turn = 35f * (bump(q, 0f, 0.4f) + bump(q, 0.45f, 0.85f)))
                BIRD_FLUFF -> {
                    val b = bell(q)
                    p.set(TrainPose.BIRD, J.ROOT, sx = 1f + 0.12f * b, sy = 1f + 0.1f * b)
                    p.add(TrainPose.BIRD, J.BIRD_WING, turn = -12f * b)
                }
                SANTA_HAT -> {
                    // The arm up to the cap, the cap set straight, the arm down.
                    p.add(1, J.ARM, turn = -95f * hold(q, 0.25f, 0.3f))
                    val w = bump(q, 0.28f, 0.68f)
                    p.add(1, J.HAT, turn = -7f * sin((q - 0.28f) / 0.4f * 2f * PI.toFloat()) * w, dy = -0.25f * w)
                    p.add(1, J.HAT_TIP, turn = 9f * sin((q - 0.28f) / 0.4f * 3f * PI.toFloat()) * w)
                    p.add(1, J.HEAD, turn = -4f * hold(q, 0.25f, 0.3f))
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
                    val f = actor
                    val b = bell(q)
                    when (variant[actor]) {
                        0 -> { p.add(f, J.HIND_NEAR, turn = 6f * b); p.add(f, J.HOCK_NEAR, turn = -40f * b) }
                        1 -> { p.add(f, J.FORE_NEAR, turn = -8f * b); p.add(f, J.KNEE_NEAR, turn = 52f * b) }
                        2 -> { p.add(f, J.HIND_FAR, turn = 6f * b); p.add(f, J.HOCK_FAR, turn = -40f * b) }
                        else -> { p.add(f, J.FORE_FAR, turn = -8f * b); p.add(f, J.KNEE_FAR, turn = 52f * b) }
                    }
                }
                DEER_EAR -> p.add(actor, J.EAR, turn = -22f * (bump(q, 0f, 0.4f) + bump(q, 0.5f, 0.9f)))
                DEER_TAIL -> p.add(actor, J.TAIL, turn = -30f * bell(q))
                DEER_HEAD -> {
                    p.add(actor, J.NECK, turn = 9f * hold(q, 0.25f, 0.3f))
                    p.add(actor, J.PUPIL, dy = 0.06f * hold(q, 0.25f, 0.3f))
                }
                DEER_NOSE -> {
                    // It looks down at its own nose, cross-eyed; the nose glows brighter and
                    // brighter; a happy little shake of the head.
                    val look = hold(q, 0.15f, 0.2f)
                    p.add(actor, J.NECK, turn = 11f * look + 4f * sin((q - 0.8f) * 10f * PI.toFloat()) * bump(q, 0.8f, 1f))
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

    private fun gallopLeg(f: Int, hip: Int, knee: Int, fore: Boolean, o: Float, a: Float) {
        val sw = sin(stride + o)
        val lift = max(0f, sin(stride + o + 1.4f))
        if (fore) {
            pose.set(f, hip, turn = -26f * a * sw)
            pose.set(f, knee, turn = 70f * a * lift)
        } else {
            pose.set(f, hip, turn = 22f * a * sw)
            pose.set(f, knee, turn = -50f * a * lift)
        }
    }

    private fun blink(figure: Int, eye: Int, actor: Int) {
        val q = (time - blinkFrom[actor]) / BLINK
        if (q !in 0f..1f) return
        val k = eye * 5 + 4
        pose.joints[figure][k] = min(pose.joints[figure][k], 1f - 0.9f * sin(PI.toFloat() * q))
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
        var birdChanged = !b.contentEquals(lastBird) || !birdJoints.contentEquals(last[TrainPose.BIRD])
        if (birdChanged) {
            if (bird == PERCHED) dirty[0] = true else dirty.fill(true)
            lastBird = b
            birdJoints.copyInto(last[TrainPose.BIRD])
        }
        val e = floatArrayOf(p.reinSlack, p.reinFlick, p.noseGlow, p.speed)
        if (!e.contentEquals(lastExtra)) {
            if (e[0] != lastExtra[0] || e[1] != lastExtra[1]) { dirty[1] = true; dirty[2] = true }
            if (e[2] != lastExtra[2]) dirty[3] = true
            lastExtra = e
        }
        if (p.speed > 0f) dirty.fill(true)
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

        const val BIRD_LOOK = 0
        const val BIRD_TAIL = 1
        const val BIRD_HOP = 2
        const val BIRD_PECK = 3
        const val BIRD_FLUFF = 4
        const val SANTA_HAT = 5
        const val SANTA_STRETCH = 6
        const val SANTA_CHUCKLE = 7
        const val SANTA_LOOK = 8
        const val DEER_LEG = 9
        const val DEER_EAR = 10
        const val DEER_TAIL = 11
        const val DEER_HEAD = 12
        const val DEER_NOSE = 13
        val LENGTHS = floatArrayOf(1.6f, 0.8f, 0.5f, 0.9f, 0.9f, 2.2f, 1.3f, 1.1f, 1.8f, 1f, 0.6f, 0.6f, 1.6f, 2.8f)

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
 * otherwise (until a stir is due or the connection changes). With animations off it stands.
 */
@Composable
fun rememberSleighMotion(festive: FestiveState): SleighMotion {
    val motion = remember { SleighMotion() }
    val moving = festive.moving
    val travel = festive.travelling
    LaunchedEffect(motion, moving) {
        if (!moving) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            if (motion.busy || travel() != motion.travellingNow) {
                val now = withFrameNanos { it }
                motion.advance((now - last) / 1e9f, travel())
                last = now
            } else {
                // Nothing moves: sleep until something is due, or the connection changes.
                val wait = (motion.secondsToNext() * 1000f).toLong().coerceAtLeast(16L)
                val before = System.nanoTime()
                withTimeoutOrNull(wait) { snapshotFlow { travel() }.first { it != motion.travellingNow } }
                motion.advance((System.nanoTime() - before) / 1e9f, travel())
                last = withFrameNanos { it }
            }
        }
    }
    return motion
}

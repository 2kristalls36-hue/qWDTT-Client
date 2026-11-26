package com.wdtt.client.ui.festive

import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A round snow globe to train the snow on, held as a person holds a phone: tilted back,
 * trembling and wandering by a degree or two, now and then turned, shaken or put down.
 * The accelerometer readings pass through [MotionFilter] as in the app. Measures what
 * the eye notices — lying grains twitching, grains moving while the globe is held still,
 * also held on its side — and renders films (build/outputs/roundglobe, build/outputs/barglobe).
 */
class RoundGlobeTraining {
    /** A hand holding the phone: the angle it means to hold, plus tremor and wander. */
    private class Hand(seed: Long) {
        private val rnd = java.util.Random(seed)
        /** Degrees, the turn the hand means; the phone follows it over about half a second. */
        var aim = 0.0
        private var held = 0.0
        var lean = 35.0 // degrees, tilted back from upright
        var shakeX = 0.0
        var shakeY = 0.0
        private var wander = 0.0
        private var t = 0.0

        /** Raw accelerometer after [dt] seconds: (ax, ay, az) in device axes. */
        fun read(dt: Double): FloatArray {
            t += dt
            held += (aim - held) * (1 - exp(-dt / 0.18))
            // The wander: a slow random drift of about 1.5°.
            wander += (-wander / 1.5) * dt + rnd.nextGaussian() * 1.5 * sqrt(2 * dt / 1.5)
            // The tremor: a fraction of a degree, 8 to 12 times a second.
            val tremor = 0.25 * sin(2 * PI * 9.3 * t) + 0.15 * sin(2 * PI * 11.7 * t + 1.0)
            val a = Math.toRadians(held + wander + tremor)
            val b = Math.toRadians(lean)
            val g = 9.81
            // Hand tremor in acceleration: about 0.2 m/s².
            val nx = rnd.nextGaussian() * 0.2 + shakeX
            val ny = rnd.nextGaussian() * 0.2 + shakeY
            return floatArrayOf(
                (-g * sin(a) * cos(b) + nx).toFloat(),
                (g * cos(a) * cos(b) + ny).toFloat(),
                (g * sin(b) + rnd.nextGaussian() * 0.2).toFloat(),
            )
        }
    }

    private class Run(val sim: SnowGlobePhysics, seed: Long) {
        val hand = Hand(seed)
        val filter = MotionFilter()
        private var sensorClock = 0.0
        val dt = 1f / 60f
        /** The lying grains as drawn, last frame, for measuring their twitching. */
        private var last: FloatArray? = null
        private var lastLying: BooleanArray? = null
        var twitch = 0f
        var twitchSum = 0.0
        var frames = 0

        fun step(droplet: Float = -1f, half: Float = 0f, dv: Float = 0f): Boolean {
            // Sensor at 50 Hz, display at 60 Hz.
            sensorClock += dt
            while (sensorClock >= 0.02) {
                sensorClock -= 0.02
                val r = hand.read(0.02)
                filter.feed(r[0], r[1], r[2], 0.02f)
            }
            val active = sim.step(dt, droplet, half, dv, filter.tiltX, filter.tiltY, filter.shakeX, filter.shakeY)
            measure()
            return active
        }

        private fun measure() {
            // What the eye sees of the heap: where each lying grain is drawn.
            val now = FloatArray(sim.flakes * 2) { if (it % 2 == 0) sim.x[it / 2] else sim.y[it / 2] }
            val lay = BooleanArray(sim.flakes) { sim.lying[it] }
            val prev = last
            val was = lastLying
            if (prev != null && was != null) {
                var most = 0f
                val h = heap
                for (i in 0 until sim.flakes) {
                    if (!lay[i] || !was[i] || (h != null && !h[i])) continue
                    most = max(most, abs(now[2 * i] - prev[2 * i]) + abs(now[2 * i + 1] - prev[2 * i + 1]))
                }
                twitch = max(twitch, most)
                twitchSum += most
                frames++
            }
            last = now
            lastLying = lay
        }

        /** Grains lying when the measuring began: the heap as it was (late landings aside). */
        private var heap: BooleanArray? = null

        fun resetMeasures(heapOnly: Boolean = true) {
            twitch = 0f; twitchSum = 0.0; frames = 0
            heap = if (heapOnly) BooleanArray(sim.flakes) { sim.lying[it] } else null
        }
    }

    @Test fun heldStillTheSnowStaysStill() {
        for (aspect in listOf(1f, 5.5f)) {
            val run = Run(SnowGlobePhysics(aspect), 11)
            run.sim.settleAll()
            repeat(60 * 3) { run.step() } // in the hand for a moment first
            val before = run.sim.moves
            run.resetMeasures()
            repeat(60 * 15) { run.step() }
            val moved = run.sim.moves - before
            println("aspect $aspect: held 15 s — grains moved $moved, worst twitch ${run.twitch}, mean ${run.twitchSum / run.frames}")
            assertTrue("held still, grains moved: $moved", moved == 0L)
            assertTrue("held still, surface twitches: ${run.twitch}", run.twitch < 1e-4f)
        }
    }

    @Test fun settlingSnowGrowsTheDriftSmoothly() {
        val run = Run(SnowGlobePhysics(5.5f), 12)
        run.sim.scatter()
        run.resetMeasures(heapOnly = false)
        repeat(60 * 20) { run.step() }
        // While the snow falls the drift grows, but each frame only a hair.
        println("settling: worst twitch ${run.twitch}, mean ${run.twitchSum / run.frames}")
        // Each grain glides into place; none jumps.
        assertTrue("the drift grows smoothly: ${run.twitch}", run.twitch < 0.05f)
    }

    /** The phone turned on its side and held there, as in the video: the heap settles. */
    @Test fun heldSidewaysTheSnowSettlesAndStaysStill() {
        for (degrees in listOf(-80.0, -95.0, -110.0, 75.0)) {
            val run = Run(SnowGlobePhysics(5.5f), 13)
            run.sim.settleAll()
            run.hand.aim = degrees
            // Light snow slides over within half a minute; what it threw up sinks for a
            // while longer.
            repeat(60 * 90) { run.step() }
            val sinking = run.sim.floating
            val startX = run.sim.x.copyOf()
            val startY = run.sim.y.copyOf()
            run.resetMeasures()
            repeat(60 * 6) { run.step() }
            val landed = sinking - run.sim.floating
            // Heap grains that went anywhere (a grain's breadth or more).
            val went = (0 until run.sim.flakes).count {
                run.sim.lying[it] && Math.hypot((run.sim.x[it] - startX[it]).toDouble(), (run.sim.y[it] - startY[it]).toDouble()) > 2 * run.sim.grain
            } - landed.coerceAtLeast(0)
            println("held at $degrees°: heap grains that went somewhere $went (landed $landed), worst twitch ${run.twitch}, ${run.sim.describe()}")
            // The heap is still; at most the odd flake still sinking lands and knocks a grain
            // on (turned past a quarter, more snow is still on its way down).
            assertTrue("held at $degrees°, heap grains moved: $went", went <= 2 + landed / 2)
            assertTrue("held at $degrees°, a grain jumps: ${run.twitch}", run.twitch < 0.02f)
        }
    }

    @Test fun film() {
        renderFilm(SnowGlobePhysics(1f, flakes = 900), "build/outputs/roundglobe", 300, 300)
        renderFilm(SnowGlobePhysics(5.5f), "build/outputs/barglobe", 120, 660)
    }

    /**
     * Held in the hand: still, then turned to 15° and 35°, back, a droplet sweep (bar
     * only), a shake, upside down, upright, on its side, upright.
     */
    private fun renderFilm(sim: SnowGlobePhysics, path: String, hPx: Int, wPx: Int) {
        val dir = File(path).apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val run = Run(sim, 21)
        sim.settleAll()
        var frame = 0
        var lastX = 0.5f / 4 * sim.aspect
        for (s in 0 until 60 * 84) {
            val t = s * run.dt
            run.hand.aim = when {
                t < 2f -> 0.0
                t < 7f -> 15.0
                t < 14f -> 35.0
                t < 36f -> 0.0
                t < 52f -> 180.0
                t < 58f -> 0.0
                t < 76f -> -95.0
                else -> 0.0
            }
            val shaking = t in 20f..20.6f
            run.hand.shakeX = if (shaking) (if (((t * 10).toInt()) % 2 == 0) 16.0 else -16.0) else 0.0
            run.hand.shakeY = if (shaking) (if (((t * 10).toInt()) % 2 == 0) 10.0 else -10.0) else 0.0
            val bar = sim.aspect > 2f
            val pos = when {
                !bar || t < 16f -> 0f
                t < 16.6f -> (t - 16f) / 0.6f * 3f
                else -> 3f
            }
            val dx = (pos + 0.5f) / 4f * sim.aspect
            val dv = (dx - lastX) / run.dt
            lastX = dx
            if (bar) run.step(dx, 0.51f / 4 * sim.aspect, dv) else run.step()
            if (s % 3 != 0) continue
            val img = BufferedImage(wPx + 40, hPx + 60, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(0x14, 0x1A, 0x24)
            g.fillRect(0, 0, img.width, img.height)
            g.translate(20, 30)
            val k = hPx.toFloat()
            val glass = RoundRectangle2D.Float(0f, 0f, wPx.toFloat(), k, sim.corner * 2 * k, sim.corner * 2 * k)
            g.color = Color(0x1E, 0x26, 0x33)
            g.fill(glass)
            g.clip = glass
            if (bar) {
                val dw = 0.51f / 4 * sim.aspect * k
                g.color = Color(0x2A, 0x34, 0x44)
                g.fill(RoundRectangle2D.Float(dx * k - dw, k * 0.08f, dw * 2, k * 0.84f, k * 0.8f, k * 0.8f))
            }
            g.color = Color(0xE1, 0xE9, 0xF5)
            for (i in 0 until sim.flakes) if (sim.lying[i]) {
                val r = sim.radius[i] * 1.55f * k
                g.fill(Ellipse2D.Float(sim.x[i] * k - r, sim.y[i] * k - r, r * 2, r * 2))
            }
            g.color = Color(0xFB, 0xFD, 0xFF)
            for (i in 0 until sim.flakes) if (sim.exposed(i)) {
                val r = sim.radius[i] * (0.95f + 0.3f * sim.size[i]) * k
                g.fill(Ellipse2D.Float(sim.x[i] * k - r, sim.y[i] * k - r, r * 2, r * 2))
            }
            for (i in 0 until sim.flakes) {
                if (sim.lying[i]) continue
                val r = (0.4f + 0.7f * sim.size[i]) * k / 64f
                val cx = sim.x[i] * k
                val cy = sim.y[i] * k
                if (sim.crystal[i]) {
                    val rad = (1.3f + 1.9f * sim.size[i]) * k / 64f
                    val squash = 0.25f + 0.75f * abs(cos(sim.tip[i].toDouble())).toFloat()
                    val cs = cos(sim.spin[i].toDouble()).toFloat()
                    val sn = sin(sim.spin[i].toDouble()).toFloat()
                    g.color = Color(255, 255, 255, 230)
                    g.stroke = BasicStroke(maxOf(1f, k / 100f))
                    for (a in 0 until 3) {
                        val ang = a * 1.0472
                        val ex = (cos(ang) * rad).toFloat()
                        val ey = (sin(ang) * rad).toFloat() * squash
                        g.draw(java.awt.geom.Line2D.Float(cx - ex * cs + ey * sn, cy - ex * sn - ey * cs, cx + ex * cs - ey * sn, cy + ex * sn + ey * cs))
                    }
                    continue
                }
                g.color = Color(255, 255, 255, 40)
                g.fill(Ellipse2D.Float(cx - r * 2.3f, cy - r * 2.3f, r * 4.6f, r * 4.6f))
                g.color = Color(255, 255, 255, 235)
                g.fill(Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2))
            }
            g.clip = null
            g.color = Color(0x8F, 0xD9, 0xB0, 120)
            g.stroke = BasicStroke(2f)
            g.draw(glass)
            g.color = Color(0xC7, 0xD0, 0xE6)
            val label = when {
                t < 2f -> "held still"
                t < 7f -> "turned 15°"
                t < 14f -> "turned 35°"
                t < 16f -> "held upright"
                t < 20f -> if (bar) "droplet sweep" else "held upright"
                t < 36f -> if (t < 20.6f) "shake" else "blizzard settles"
                t < 52f -> "upside down"
                t < 58f -> "upright"
                t < 76f -> "held on its side"
                else -> "upright"
            }
            g.drawString("t=%.1fs  %s".format(t, label), 0, -10)
            g.dispose()
            ImageIO.write(img, "png", File(dir, "f_%04d.png".format(frame++)))
        }
    }
}

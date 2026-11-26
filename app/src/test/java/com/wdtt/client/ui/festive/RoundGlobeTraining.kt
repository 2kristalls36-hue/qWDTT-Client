package com.wdtt.client.ui.festive

import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
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
 * the eye notices — the drift's surface twitching, grains moving while the globe is held
 * still — and renders films (build/outputs/roundglobe, build/outputs/barglobe).
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
        /** The surface as shown, last frame, for measuring its twitching. */
        private var last: FloatArray? = null
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
            val n = 80
            val now = FloatArray(n) { sim.shownDepthAt(SnowGlobePhysics.BOTTOM, sim.aspect * (it + 0.5f) / n) }
            last?.let { prev ->
                var most = 0f
                for (k in 0 until n) most = max(most, abs(now[k] - prev[k]))
                twitch = max(twitch, most)
                twitchSum += most
                frames++
            }
            last = now
        }

        fun resetMeasures() {
            twitch = 0f; twitchSum = 0.0; frames = 0
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
        run.resetMeasures()
        repeat(60 * 20) { run.step() }
        // While the snow falls the drift grows, but each frame only a hair.
        println("settling: worst twitch ${run.twitch}, mean ${run.twitchSum / run.frames}")
        assertTrue("the drift grows smoothly: ${run.twitch}", run.twitch < 0.004f)
    }

    @Test fun film() {
        renderFilm(SnowGlobePhysics(1f, flakes = 900), "build/outputs/roundglobe", 300, 300)
        renderFilm(SnowGlobePhysics(5.5f), "build/outputs/barglobe", 120, 660)
    }

    /**
     * Held in the hand: still, then turned to 15° and 35°, back, a droplet sweep (bar
     * only), a shake, upside down, upright.
     */
    private fun renderFilm(sim: SnowGlobePhysics, path: String, hPx: Int, wPx: Int) {
        val dir = File(path).apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val run = Run(sim, 21)
        sim.settleAll()
        var frame = 0
        var lastX = 0.5f / 4 * sim.aspect
        for (s in 0 until 60 * 34) {
            val t = s * run.dt
            run.hand.aim = when {
                t < 2f -> 0.0
                t < 5f -> 15.0
                t < 8f -> 35.0
                t < 18f -> 0.0
                t < 26f -> 180.0
                else -> 0.0
            }
            val shaking = t in 13f..13.6f
            run.hand.shakeX = if (shaking) (if (((t * 10).toInt()) % 2 == 0) 16.0 else -16.0) else 0.0
            run.hand.shakeY = if (shaking) (if (((t * 10).toInt()) % 2 == 0) 10.0 else -10.0) else 0.0
            val bar = sim.aspect > 2f
            val pos = when {
                !bar || t < 10f -> 0f
                t < 10.6f -> (t - 10f) / 0.6f * 3f
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
            g.color = Color(0xF2, 0xF6, 0xFC)
            for (side in 0..1) {
                val body = Path2D.Float()
                val n = 160
                val down = side == SnowGlobePhysics.BOTTOM
                fun wall(px: Float) = if (down) sim.bottomAt(px) else 1f - sim.bottomAt(px)
                for (i in 0..n) {
                    val px = sim.aspect * i / n
                    val d = maxOf(0f, sim.shownDepthAt(side, px) - 0.022f)
                    val yy = (wall(px) + if (down) -d else d) * k
                    if (i == 0) { body.moveTo(px * k, wall(px) * k); body.lineTo(px * k, yy) } else body.lineTo(px * k, yy)
                }
                for (i in n downTo 0) {
                    val px = sim.aspect * i / n
                    body.lineTo(px * k, wall(px) * k)
                }
                body.closePath()
                g.fill(body)
            }
            for (i in 0 until sim.flakes) {
                val r = (0.4f + 0.7f * sim.size[i]) * k / 64f
                val cx = sim.x[i] * k
                val cy = sim.shownY(i) * k
                if (sim.lying[i]) {
                    g.color = Color(255, 255, 255, 240)
                    g.fill(Ellipse2D.Float(cx - r * 1.35f, cy - r * 1.35f, r * 2.7f, r * 2.7f))
                } else {
                    g.color = Color(255, 255, 255, 40)
                    g.fill(Ellipse2D.Float(cx - r * 2.3f, cy - r * 2.3f, r * 4.6f, r * 4.6f))
                    g.color = Color(255, 255, 255, 235)
                    g.fill(Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2))
                }
            }
            g.clip = null
            g.color = Color(0x8F, 0xD9, 0xB0, 120)
            g.stroke = BasicStroke(2f)
            g.draw(glass)
            g.color = Color(0xC7, 0xD0, 0xE6)
            val label = when {
                t < 2f -> "held still"
                t < 5f -> "turned 15°"
                t < 8f -> "turned 35°"
                t < 10f -> "held upright"
                t < 13f -> if (bar) "droplet sweep" else "held upright"
                t < 18f -> if (t < 13.6f) "shake" else "blizzard settles"
                t < 26f -> "upside down"
                else -> "upright"
            }
            g.drawString("t=%.1fs  %s".format(t, label), 0, -10)
            g.dispose()
            ImageIO.write(img, "png", File(dir, "f_%04d.png".format(frame++)))
        }
    }
}

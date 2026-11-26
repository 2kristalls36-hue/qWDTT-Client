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
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The snow globe held as a person holds a phone: tilted back, trembling and wandering by a
 * degree or two, now and then shaken, the droplet moved. The accelerometer readings pass
 * through [MotionFilter] as in the app. Renders a film (build/outputs/barglobe).
 */
class SnowGlobeTraining {
    /** A hand holding the phone: tremor and wander. */
    private class Hand(seed: Long) {
        private val rnd = java.util.Random(seed)
        private val lean = 35.0 // degrees, tilted back from upright
        var shakeX = 0.0
        var shakeY = 0.0
        private var wander = 0.0
        private var t = 0.0

        /** Raw accelerometer after [dt] seconds: (ax, ay, az) in device axes. */
        fun read(dt: Double): FloatArray {
            t += dt
            // The wander: a slow random drift of about 1.5°.
            wander += (-wander / 1.5) * dt + rnd.nextGaussian() * 1.5 * sqrt(2 * dt / 1.5)
            // The tremor: a fraction of a degree, 8 to 12 times a second.
            val tremor = 0.25 * sin(2 * PI * 9.3 * t) + 0.15 * sin(2 * PI * 11.7 * t + 1.0)
            val a = Math.toRadians(wander + tremor)
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

        fun step(droplet: Float = -1f, half: Float = 0f, dv: Float = 0f): Boolean {
            // Sensor at 50 Hz, display at 60 Hz.
            sensorClock += dt
            while (sensorClock >= 0.02) {
                sensorClock -= 0.02
                val r = hand.read(0.02)
                filter.feed(r[0], r[1], r[2], 0.02f)
            }
            return sim.step(dt, droplet, half, dv, filter.shakeX, filter.shakeY)
        }
    }

    /** The hand's tremor is no shake: the snow only drifts. */
    @Test fun heldStillTheSnowOnlyDrifts() {
        val run = Run(SnowGlobePhysics(5.5f), 11)
        run.sim.settleAll()
        var lively = 0
        repeat(60 * 15) {
            run.step()
            if (run.sim.lively) lively++
        }
        println("held 15 s: lively $lively frames")
        assertTrue("held still, stirred: $lively", lively < 60)
    }

    @Test fun film() {
        // The drift up to where Santa's train rides on it, as in the app.
        renderFilm(SnowGlobePhysics(5.5f, level = 0.74f), "build/outputs/barglobe", 120, 660)
    }

    /** Held in the hand: still, a droplet sweep, a shake, the snow settling again. */
    private fun renderFilm(sim: SnowGlobePhysics, path: String, hPx: Int, wPx: Int) {
        val dir = File(path).apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val run = Run(sim, 21)
        sim.settleAll()
        var frame = 0
        var lastX = 0.5f / 4 * sim.aspect
        for (s in 0 until 60 * 40) {
            val t = s * run.dt
            val shaking = t in 8f..8.6f
            run.hand.shakeX = if (shaking) (if (((t * 10).toInt()) % 2 == 0) 16.0 else -16.0) else 0.0
            run.hand.shakeY = if (shaking) (if (((t * 10).toInt()) % 2 == 0) 10.0 else -10.0) else 0.0
            val bar = sim.aspect > 2f
            val pos = when {
                !bar || t < 3f -> 0f
                t < 3.6f -> (t - 3f) / 0.6f * 3f
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
            g.fill(java.awt.geom.Rectangle2D.Float(0f, (sim.driftTop + sim.grain) * k, wPx.toFloat(), k))
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
                t < 3f -> "held still"
                t < 8f -> if (bar) "droplet sweep" else "held still"
                t < 8.6f -> "shake"
                else -> "blizzard settles"
            }
            g.drawString("t=%.1fs  %s".format(t, label), 0, -10)
            g.dispose()
            ImageIO.write(img, "png", File(dir, "f_%04d.png".format(frame++)))
        }
    }
}

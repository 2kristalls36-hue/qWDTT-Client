package com.wdtt.client.ui.festive

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

/**
 * Frames of the snow globe through a scripted minute: snowfall, a droplet sweep, a shake
 * and a tilt — for looking at the motion (build/outputs/snowglobe/, joined into a video
 * by hand). Draws roughly as the app does.
 */
class SnowGlobeFilm {
    @Test fun film() {
        val dir = File("build/outputs/snowglobe").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        val dt = 1f / 60f
        val hPx = 120
        val wPx = (hPx * sim.aspect).toInt()
        var frame = 0
        var dropletX = 0.5f / 4 * sim.aspect + 0.0f
        var lastX = dropletX
        val total = 60 * 30
        for (s in 0 until total) {
            val t = s * dt
            // Script: tilt right 1..5 s; droplet sweep 6..6.6 s; shake 9..9.6 s;
            // upside down 15..23 s; upright again after.
            val pos = when {
                t < 8f -> 0f
                t < 8.6f -> (t - 8f) / 0.6f * 3f
                else -> 3f
            }
            dropletX = (pos + 0.5f) / 4f * sim.aspect
            val dv = (dropletX - lastX) / dt
            lastX = dropletX
            val shaking = t in 11f..11.6f
            val sx = if (shaking) (if (((t * 10).toInt()) % 2 == 0) 16f else -16f) else 0f
            val sy = if (shaking) (if (((t * 10).toInt()) % 2 == 0) -10f else 10f) else 0f
            // 15° from 1 s, 30° from 4 s, upright at 7 s; upside down 17..25 s.
            val angle = when {
                t in 1f..4f -> 15.0
                t in 4f..7f -> 30.0
                t in 17f..25f -> 180.0
                else -> 0.0
            }
            val a = Math.toRadians(angle)
            sim.step(
                dt, dropletX, 0.51f / 4 * sim.aspect, dv,
                Math.sin(a).toFloat(), Math.cos(a).toFloat(), sx, sy,
            )
            if (s % 3 != 0) continue
            val img = BufferedImage(wPx + 40, hPx + 60, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(0x14, 0x1A, 0x24)
            g.fillRect(0, 0, img.width, img.height)
            g.translate(20, 30)
            val bar = RoundRectangle2D.Float(0f, 0f, wPx.toFloat(), hPx.toFloat(), hPx.toFloat(), hPx.toFloat())
            g.color = Color(0x1E, 0x26, 0x33)
            g.fill(bar)
            g.clip = bar
            // droplet
            val dxPx = dropletX * hPx
            val dw = 0.51f / 4 * sim.aspect * hPx
            g.color = Color(0x2A, 0x34, 0x44)
            g.fill(RoundRectangle2D.Float(dxPx - dw, hPx * 0.08f, dw * 2, hPx * 0.84f, hPx * 0.8f, hPx * 0.8f))
            // drift body
            val body = Path2D.Float()
            val n = 140
            for (i in 0..n) {
                val px = sim.aspect * i / n
                val d = maxOf(0f, sim.depthAt(px) - 0.022f)
                val yy = (sim.bottomAt(px) - d) * hPx
                if (i == 0) body.moveTo(px * hPx, sim.bottomAt(px) * hPx + 1) else body.lineTo(px * hPx, yy)
                if (i == 0) body.lineTo(px * hPx, yy)
            }
            for (i in n downTo 0) {
                val px = sim.aspect * i / n
                body.lineTo(px * hPx, sim.bottomAt(px) * hPx + 1)
            }
            body.closePath()
            g.color = Color(0xF2, 0xF6, 0xFC)
            g.fill(body)
            val topBody = Path2D.Float()
            for (i in 0..n) {
                val px = sim.aspect * i / n
                val d = maxOf(0f, sim.depthAt(SnowGlobePhysics.TOP, px) - 0.022f)
                val wallY = (1f - sim.bottomAt(px)) * hPx
                if (i == 0) topBody.moveTo(px * hPx, wallY - 1) else topBody.lineTo(px * hPx, wallY + d * hPx)
                if (i == 0) topBody.lineTo(px * hPx, wallY + d * hPx)
            }
            for (i in n downTo 0) {
                val px = sim.aspect * i / n
                topBody.lineTo(px * hPx, (1f - sim.bottomAt(px)) * hPx - 1)
            }
            topBody.closePath()
            g.fill(topBody)
            for (i in 0 until sim.flakes) {
                val r = (0.4f + 0.7f * sim.size[i]) * hPx / 64f * 1.0f
                val cx = sim.x[i] * hPx
                val cy = sim.y[i] * hPx
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
            g.draw(bar)
            g.color = Color(0xC7, 0xD0, 0xE6)
            val label = when {
                t < 1f -> "start: snow lies"
                t < 4f -> "tilt 15° right"
                t < 7f -> "tilt 30° right"
                t < 8f -> "upright"
                t < 11f -> "droplet sweep"
                t < 17f -> if (t < 11.6f) "shake" else "blizzard settles"
                t < 25f -> "upside down"
                else -> "upright again"
            }
            g.drawString("t=%.1fs  %s".format(t, label), 0, -10)
            g.dispose()
            ImageIO.write(img, "png", File(dir, "f_%04d.png".format(frame++)))
        }
    }
}

package com.wdtt.client.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.wdtt.client.ui.festive.SleighMotion
import com.wdtt.client.ui.festive.SleighTrain
import com.wdtt.client.ui.festive.drawSleighTab
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * A film of Santa's train for review (build/outputs/sleighfilm, 20 frames a second): standing
 * and stirring, the connection starting, the run at full speed, the stop, the bird coming
 * back. By night (the dark theme) in colour, by day in colour, by night as in the bar with
 * the droplet on Santa, then on the lead reindeer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SleighFilm {
    @Test fun film() {
        val dir = File("build/outputs/sleighfilm").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val density = Density(2.75f)
        val slot = 82f * density.density
        val tabH = 64f * density.density
        val pad = 8f * density.density
        val w = (slot * 4 + pad * 2).toInt()
        val h = (tabH * 3 + pad * 4).toInt()
        val motion = SleighMotion(seed = 11)
        val tallest = 46f * density.density
        val plain = Color(0xFFC9D1DC)
        val dt = 1f / 60f
        var frame = 0
        for (s in 0 until 60 * 34) {
            val t = s * dt
            // The droplet on Santa, then on the lead reindeer.
            motion.advance(dt, t in 10f..21f, if (t < 5f) 1f else 3f)
            if (s % 3 != 0) continue
            val image = ImageBitmap(w, h)
            val canvas = Canvas(image)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                drawRect(Color(0xFF141A24))
                // By night in colour, by day in colour, by night as in the bar.
                for (row in 0..2) {
                    val y = pad + row * (tabH + pad)
                    drawRoundRect(Color(0xFF1E2633), androidx.compose.ui.geometry.Offset(pad / 2f, y), Size(w - pad, tabH), androidx.compose.ui.geometry.CornerRadius(tabH / 2f))
                    for (i in 0 until 4) {
                        translate(pad + slot * i, y) {
                            // Each tab on a layer of its own, as in the bar.
                            canvas.saveLayer(Rect(-slot, -tabH, slot * 2f, tabH * 2f), Paint())
                            val lens = if (t < 5f) 1f else 3f
                            val from = if (row < 2) -1e5f else slot * (lens - 0.01f - i)
                            val to = if (row < 2) 1e5f else slot * (lens + 1.01f - i)
                            drawSleighTab(i, slot, tabH, tallest, plain, from, to, motion.pose, night = row != 1)
                            canvas.restore()
                        }
                    }
                }
            }
            FileOutputStream(File(dir, "f_%04d.png".format(frame++))).use {
                image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        println("sleigh film: $frame frames, ${SleighTrain.count} tabs")
    }
}

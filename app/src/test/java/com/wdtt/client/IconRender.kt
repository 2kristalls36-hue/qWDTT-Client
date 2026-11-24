package com.wdtt.client

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the launcher icon (full bleed, 512 px, as for the store), the monochrome icon and the tile. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IconRender {
    private fun save(bmp: Bitmap, name: String) {
        val dir = File("build/outputs/icons").apply { mkdirs() }
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun draw(id: Int, size: Int, bg: Int? = null): Bitmap {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (bg != null) c.drawColor(bg)
        ContextCompat.getDrawable(ctx, id)!!.apply { setBounds(0, 0, size, size); draw(c) }
        return bmp
    }

    @Test fun render() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val c = Canvas(store)
        ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_background)!!.apply { setBounds(0, 0, 512, 512); draw(c) }
        ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_foreground)!!.apply { setBounds(0, 0, 512, 512); draw(c) }
        save(store, "playstore.png")
        save(draw(R.drawable.ic_launcher_monochrome, 256, Color.WHITE), "monochrome.png")
        save(draw(R.drawable.ic_tile_lite, 192, Color.DKGRAY), "tile.png")
    }
}

package com.wdtt.client

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.content.Context
import android.widget.RemoteViews
import android.widget.ImageView
import android.widget.TextView

/** Inflates the widget layout with each look applied, for visual review only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h200dp-xxhdpi")
class WidgetScreenshots {
    private fun shot(name: String, night: Boolean, bg: Int, icon: Int, status: String, level: String, levelColor: Int, ping: Int?) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val cfg = android.content.res.Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
        }
        val ctx = base.createConfigurationContext(cfg)
        val parent = FrameLayout(ctx)
        val view = RemoteViews(ctx.packageName, R.layout.tunnel_widget).apply(ctx, parent)
        view.findViewById<ImageView>(R.id.widget_button_bg).setImageResource(bg)
        view.findViewById<ImageView>(R.id.widget_button_icon).setColorFilter(ctx.getColor(icon))
        view.findViewById<TextView>(R.id.widget_title).text = "Дом"
        view.findViewById<TextView>(R.id.widget_status).text = status
        view.findViewById<TextView>(R.id.widget_level).apply { text = level; setTextColor(ctx.getColor(levelColor)) }
        view.findViewById<ImageView>(R.id.widget_ping_dot).apply {
            visibility = if (ping != null) View.VISIBLE else View.GONE
            ping?.let { setColorFilter(it) }
        }
        val w = (300 * ctx.resources.displayMetrics.density).toInt()
        val h = (76 * ctx.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = android.graphics.Bitmap.createBitmap(w + 40, h + 40, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(if (night) 0xFF0F1722.toInt() else 0xFFE8EFF8.toInt())
        canvas.translate(20f, 20f)
        view.draw(canvas)
        java.io.File("build/outputs/roborazzi").mkdirs()
        java.io.FileOutputStream("build/outputs/roborazzi/widget_$name.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun offBalancedLight() = shot("off_balanced_light", false, R.drawable.widget_btn_off_balanced, R.color.widget_balanced, "Не подключено", "Сбалансированный", R.color.widget_balanced, null)
    @Test fun onStableDark() = shot("on_stable_dark", true, R.drawable.widget_btn_on_stable, R.color.widget_on_level, "Подключено · 42 мс", "Стабильный", R.color.widget_stable, 0xFF22C55E.toInt())
    @Test fun onMaxDark() = shot("on_max_dark", true, R.drawable.widget_btn_on_max, R.color.widget_night_text, "Подключено · 156 мс", "Максимальный", R.color.widget_neon_2, 0xFFF59E0B.toInt())
    @Test fun offMaxLight() = shot("off_max_light", false, R.drawable.widget_btn_off_max, R.color.widget_night_faint, "Не подключено", "Максимальный", R.color.widget_neon_2, null)

    /** Launcher icon as round and squircle masks, for visual review only. */
    @Test fun launcherIcon() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val size = 432
        val out = android.graphics.Bitmap.createBitmap(size * 3 + 80, size + 40, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(0xFFDDE3EA.toInt())
        fun layer(id: Int) = ctx.getDrawable(id)!!.apply { setBounds(0, 0, size, size) }
        listOf(0, 1, 2).forEach { i ->
            val icon = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(icon)
            if (i < 2) { layer(R.drawable.ic_launcher_background).draw(c); layer(R.drawable.ic_launcher_foreground).draw(c) }
            else { c.drawColor(0xFFE8DEF8.toInt()); layer(R.drawable.ic_launcher_monochrome).apply { setTint(0xFF4A3B7C.toInt()) }.draw(c) }
            val masked = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val mc = android.graphics.Canvas(masked)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            // Launchers show the middle 72 of 108 units.
            val inset = size * 18f / 108f
            val rect = android.graphics.RectF(inset, inset, size - inset, size - inset)
            val r = if (i == 1) rect.width() * 0.3f else rect.width() / 2f
            mc.drawRoundRect(rect, r, r, p)
            p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            mc.drawBitmap(icon, 0f, 0f, p)
            canvas.drawBitmap(masked, 20f + i * (size + 20f), 20f, null)
        }
        java.io.File("build/outputs/roborazzi").mkdirs()
        java.io.FileOutputStream("build/outputs/roborazzi/launcher_icon.png").use { out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun tileIcon() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val out = android.graphics.Bitmap.createBitmap(420, 240, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawColor(0xFF20242C.toInt())
        val circle = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1565C0.toInt() }
        listOf(Triple(120f, 120f, 160), Triple(330f, 120f, 48)).forEach { (cx, cy, s) ->
            c.drawCircle(cx, cy, s * 0.75f, circle)
            ctx.getDrawable(R.drawable.ic_tile_lite)!!.apply {
                setBounds((cx - s / 2).toInt(), (cy - s / 2).toInt(), (cx + s / 2).toInt(), (cy + s / 2).toInt())
            }.draw(c)
        }
        java.io.File("build/outputs/roborazzi").mkdirs()
        java.io.FileOutputStream("build/outputs/roborazzi/tile_icon.png").use { out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}

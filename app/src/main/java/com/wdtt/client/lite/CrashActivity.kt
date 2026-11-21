package com.wdtt.client.lite

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Plain-view crash report in its own process (":crash"): it does not touch Compose,
 * the tunnel or any app state, so it opens even when the app crashes on every start.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = CrashLog.read(this) ?: "Текст ошибки не сохранён."
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            setBackgroundColor(Color.parseColor("#0F1722"))
        }
        root.addView(TextView(this).apply {
            this.text = "Приложение было закрыто из-за ошибки"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            this.text = "Нажмите «Скопировать» и отправьте текст разработчику."
            setTextColor(Color.parseColor("#B9C9DB"))
            textSize = 14f
            setPadding(0, pad / 2, 0, pad)
        })
        val scroll = ScrollView(this)
        scroll.addView(TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        })
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            this.text = "Скопировать"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Ошибка qWDTT Client", text))
                Toast.makeText(this@CrashActivity, "Текст ошибки скопирован.", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        root.addView(Button(this).apply {
            this.text = "Закрыть"
            setOnClickListener {
                CrashLog.clear(this@CrashActivity)
                finishAndRemoveTask()
            }
        })
        setContentView(root)
    }
}

package com.wdtt.client.ui.home

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.wdtt.client.lite.PowerLevel

/** Neon colors of the maximum level, flowing in this order. */
internal object Neon {
    val Light = listOf(Color(0xFFB026FF), Color(0xFF00E5FF), Color(0xFFFF2BD6), Color(0xFF3D5AFE))
    val Dark = listOf(Color(0xFFC04BFF), Color(0xFF00F0FF), Color(0xFFFF3DE0), Color(0xFF5C7CFF))
    val Night = Color(0xFF0C1020)
    val NightRaised = Color(0xFF151A30)
    val NightText = Color(0xFFF4F7FF)
    val NightMuted = Color(0xFFC7D0E6)
    val NightFaint = Color(0xFF8C97B8)
    val NightTrack = Color(0xFF262D4A)
}

internal data class LevelColors(
    val accent: Color,
    val onAccent: Color,
    val soft: Color,
    val neon: List<Color>,
)

@Composable
internal fun levelColors(level: PowerLevel): LevelColors {
    val dark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.luminanceIsDark()
    val neon = if (dark) Neon.Dark else Neon.Light
    return when (level) {
        PowerLevel.Stable -> if (dark) {
            LevelColors(Color(0xFF4ADE80), Color(0xFF08131F), Color(0xFF173A26), neon)
        } else {
            LevelColors(Color(0xFF1B9E45), Color.White, Color(0xFFD3F2DD), neon)
        }
        PowerLevel.Balanced -> LevelColors(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            MaterialTheme.colorScheme.primaryContainer,
            neon,
        )
        PowerLevel.Maximum -> LevelColors(neon[1], Neon.NightText, Neon.Night, neon)
    }
}

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

/** Ping quality colors, shared by the home chip, the stats tile and the profile sheet. */
internal object PingColors {
    val Good = Color(0xFF22C55E)
    val Mid = Color(0xFFF59E0B)
    val Bad = Color(0xFFEF4444)
    fun of(ms: Long): Color? = when {
        ms <= 0 -> null
        ms < 100 -> Good
        ms < 200 -> Mid
        else -> Bad
    }
}

/**
 * Speed factor for decorative motion. With the system animation scale at 0
 * («Убрать анимацию») the colors still flow, only slower; one-shot effects are skipped.
 */
@Composable
internal fun rememberMotionScale(): Float {
    val context = LocalContext.current
    return remember {
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (scale == 0f) 0.4f else 1f
    }
}

@Composable
internal fun reducedMotion(): Boolean = rememberMotionScale() < 1f

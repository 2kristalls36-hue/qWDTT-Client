package com.wdtt.client.ui.festive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import com.wdtt.client.BuildConfig
import com.wdtt.client.lite.PowerLevel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * New Year decoration: on from 31 December to 2 January (the phone's own date), in every
 * build; outside those days nothing of it is drawn or runs. A preview build
 * (-PfestivePreview=true) shows it on any date.
 */
object FestiveCalendar {
    fun isActive(date: LocalDate): Boolean =
        (date.monthValue == 12 && date.dayOfMonth == 31) || (date.monthValue == 1 && date.dayOfMonth <= 2)

    /**
     * The New Year being met, or just met: on 31 December the next year, on 1 and 2 January
     * this one; on any other day (a preview build) the coming one.
     */
    fun year(date: LocalDate): Int =
        if (date.monthValue == 1 && date.dayOfMonth <= 2) date.year else date.year + 1

    /** The next midnight when the decoration starts (31 December) or ends (3 January). */
    fun nextChange(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        val start = LocalDate.of(if (today.monthValue == 12 && today.dayOfMonth == 31) today.year + 1 else today.year, 12, 31)
            .let { if (!it.isAfter(today)) it.plusYears(1) else it }
        val endYear = if (today.monthValue == 12) today.year + 1 else today.year
        val end = LocalDate.of(endYear, 1, 3).let { if (!it.isAfter(today)) it.plusYears(1) else it }
        return (if (end.isBefore(start)) end else start).atStartOfDay()
    }

    /** Milliseconds from [now] to the next midnight, when the answer may change. */
    fun msToMidnight(now: LocalDateTime): Long {
        val next = now.toLocalDate().plusDays(1).atStartOfDay()
        return java.time.Duration.between(now, next).toMillis().coerceAtLeast(1_000L)
    }
}

@Immutable
class FestiveState(
    val active: Boolean,
    val year: Int,
    val level: PowerLevel,
    /** Animations allowed (the system animation scale is not off). */
    val moving: Boolean,
    /** Seconds since the decoration started; read only while drawing. */
    val time: () -> Float,
    /** The connection is on or being made (Santa's train goes). */
    val travelling: () -> Boolean = { false },
) {
    companion object {
        val Off = FestiveState(false, 0, PowerLevel.Default, false, { 0f })
    }
}

val LocalFestive = staticCompositionLocalOf { FestiveState.Off }

/** Overridden in screenshot tests. */
internal var festiveDateOverride: LocalDate? = null

/**
 * Whether today is a festive day, checked again at midnight while the app is open, and
 * one frame clock for all the decoration (nothing ticks outside the festive days).
 */
@Composable
fun rememberFestive(level: PowerLevel, travelling: State<Boolean>): FestiveState {
    val context = LocalContext.current
    var today by remember { mutableStateOf(festiveDateOverride ?: LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(FestiveCalendar.msToMidnight(LocalDateTime.now()) + 500L)
            today = festiveDateOverride ?: LocalDate.now()
        }
    }
    val active = BuildConfig.FESTIVE_PREVIEW || FestiveCalendar.isActive(today)
    val moving = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) > 0f
    }
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, moving) {
        if (!active || !moving) return@LaunchedEffect
        val start = withFrameNanos { it }
        // About 30 times a second, whatever the screen's rate: the falling snow and the
        // garland look the same, and on a 120 Hz screen they are drawn a quarter as often.
        var last = start
        while (true) withFrameNanos {
            if (it - last >= FESTIVE_FRAME_NS) {
                last = it
                clock.floatValue = (it - start) / 1e9f
            }
        }
    }
    val time: State<Float> = clock
    return remember(active, today, level, moving) {
        FestiveState(active, FestiveCalendar.year(today), level, moving, { time.value }, { travelling.value })
    }
}

/** Candy cane: white and red stripes running down to the right. */
fun candyBrush(stripePx: Float): Brush = Brush.linearGradient(
    0f to CandyRed, 0.5f to CandyRed, 0.5f to Color.White, 1f to Color.White,
    start = Offset.Zero,
    end = Offset(stripePx * 2f, stripePx * 2f),
    tileMode = TileMode.Repeated,
)

val CandyRed = Color(0xFFD7263D)
val CandyDeepRed = Color(0xFF8E1426)

/** Small deterministic noise in 0..1 for seeded shapes. */
internal fun hash01(seed: Int, i: Int): Float {
    var x = seed * 374761393 + i * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777215f
}

/** The festive clock's step: about 30 times a second. */
private const val FESTIVE_FRAME_NS = 30_000_000L

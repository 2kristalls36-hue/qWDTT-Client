package com.wdtt.client.ui.festive

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Snow in a glass globe, in bar units (x and y from 0 to 1). The flakes sink slowly
 * through the liquid and settle on the bottom; a push or a shake lifts them again.
 */
class SnowGlobeSim(val count: Int, seed: Int) {
    val x = FloatArray(count) { hash01(seed, it) }
    val y = FloatArray(count) { hash01(seed + 1, it) * 0.9f }
    private val vx = FloatArray(count)
    private val vy = FloatArray(count)
    val size = FloatArray(count) { 0.6f + 0.4f * hash01(seed + 2, it) }
    private var noise = seed

    private fun rnd(): Float {
        noise++
        return hash01(noise, 977)
    }

    /** Where a flake lies on the bottom: a low heap, bigger flakes a little lower. */
    fun floor(i: Int): Float = 0.94f - 0.05f * hash01(7, i)

    /** The droplet moved by [dx] (bar widths) around [cx]: the liquid drags the flakes near it. */
    fun stir(cx: Float, dx: Float) {
        for (i in 0 until count) {
            val d = abs(x[i] - cx)
            if (d > 0.3f) continue
            val k = 1f - d / 0.3f
            vx[i] += dx * 14f * k
            vy[i] -= abs(dx) * 9f * k * (0.5f + rnd())
        }
    }

    /** A shake of the phone: every flake is thrown up, more for a stronger shake. */
    fun shake(power: Float) {
        for (i in 0 until count) {
            vy[i] -= power * (0.6f + 0.8f * rnd())
            vx[i] += (rnd() - 0.5f) * power * 0.8f
        }
    }

    /** One step of [dt] seconds; [aspect] is height / width. Returns whether anything moves. */
    fun step(dt: Float, aspect: Float): Boolean {
        val drag = exp(-2.4f * dt)
        var moving = false
        for (i in 0 until count) {
            vy[i] += 0.55f * dt
            vx[i] *= drag
            vy[i] *= drag
            x[i] += vx[i] * dt * aspect
            y[i] += vy[i] * dt
            if (x[i] < 0f) { x[i] = 0f; vx[i] = -vx[i] * 0.4f }
            if (x[i] > 1f) { x[i] = 1f; vx[i] = -vx[i] * 0.4f }
            if (y[i] < 0.02f) { y[i] = 0.02f; vy[i] = 0f }
            val f = floor(i)
            if (y[i] >= f) {
                y[i] = f
                vy[i] = 0f
                vx[i] *= 0.6f
            }
            if (abs(vx[i]) > 0.004f || y[i] < f - 0.001f) moving = true
        }
        return moving
    }
}

/**
 * The snow globe inside the glass tab bar: behind the tabs, under the droplet lens. The
 * droplet stirs it as it moves; shaking the phone lifts the snow.
 */
@Composable
fun SnowGlobe(dropletPos: () -> Float, tabs: Int, modifier: Modifier = Modifier, flakes: Int = 50) {
    val festive = LocalFestive.current
    if (!festive.active) return
    val sim = remember(flakes) { SnowGlobeSim(flakes, 5 + flakes) }
    var frame by remember { mutableIntStateOf(0) }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }
    val pos by rememberUpdatedState(dropletPos)
    LaunchedEffect(sim, festive.moving) {
        if (!festive.moving) {
            // No animation: the snow is shown lying on the bottom.
            while (sim.step(1f / 30f, 0.2f)) Unit
            frame++
            return@LaunchedEffect
        }
        launch {
            var last = Float.NaN
            snapshotFlow { pos() }.collect { p ->
                if (!last.isNaN() && abs(p - last) > 1e-4f) {
                    sim.stir((p + 0.5f) / tabs, (p - last) / tabs)
                    wake.trySend(Unit)
                }
                last = p
            }
        }
        wake.trySend(Unit) // the snow settles when the bar first appears
        while (true) {
            wake.receive()
            var t0 = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - t0) / 1e9f).coerceIn(0f, 0.05f)
                t0 = now
                val moving = sim.step(dt, aspectHint)
                frame++
                if (!moving) break
            }
        }
    }
    ShakeListener(enabled = festive.moving) { power ->
        sim.shake(power)
        wake.trySend(Unit)
    }
    Canvas(modifier) {
        if (frame < 0) return@Canvas // read: redraw on every step
        aspectHint = (size.height / size.width).coerceIn(0.05f, 1f)
        val dp = density
        for (i in 0 until sim.count) {
            drawCircle(
                Color.White,
                (1.1f + 1.1f * sim.size[i]) * dp,
                Offset(sim.x[i] * size.width, sim.y[i] * size.height),
                alpha = 0.92f,
            )
        }
    }
}

@Volatile private var aspectHint = 0.2f

/** Calls [onShake] with a strength of about 0.5..1.5 when the phone is shaken, while resumed. */
@Composable
private fun ShakeListener(enabled: Boolean, onShake: (Float) -> Unit) {
    if (!enabled) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val callback by rememberUpdatedState(onShake)
    DisposableEffect(context, lifecycle) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val linear = sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION
        val gravity = FloatArray(3)
        var lastShake = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val a = FloatArray(3)
                for (k in 0..2) {
                    if (linear) a[k] = e.values[k] else {
                        gravity[k] = 0.9f * gravity[k] + 0.1f * e.values[k]
                        a[k] = e.values[k] - gravity[k]
                    }
                }
                val m = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
                val now = System.currentTimeMillis()
                if (m > 7f && now - lastShake > 150L) {
                    lastShake = now
                    callback((m / 14f).coerceIn(0.5f, 1.5f))
                }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        fun on() { if (sensor != null) sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME) }
        fun off() { sensors?.unregisterListener(listener) }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> on()
                Lifecycle.Event.ON_PAUSE -> off()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) on()
        onDispose {
            lifecycle.removeObserver(observer)
            off()
        }
    }
}

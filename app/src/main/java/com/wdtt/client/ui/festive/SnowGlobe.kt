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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private class GlobeHolder {
    var sim: SnowGlobePhysics? = null
}

/**
 * The snow globe inside the glass tab bar, behind the tabs and under the droplet lens
 * (see [SnowGlobePhysics]). The droplet stirs the liquid as it moves, tilting the phone
 * past the snow's grip makes it slide to the lower side, shaking it whirls the snow up.
 * It runs only while something moves.
 */
@Composable
fun SnowGlobe(dropletPos: () -> Float, tabs: Int, corner: Float, modifier: Modifier = Modifier) {
    val festive = LocalFestive.current
    if (!festive.active) return
    val holder = remember { GlobeHolder() }
    var frame by remember { mutableIntStateOf(0) }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }
    val pos by rememberUpdatedState(dropletPos)
    val motion = remember { MotionFilter() }
    if (festive.moving) MotionSensor(motion) { wake.trySend(Unit) }
    LaunchedEffect(festive.moving) {
        while (holder.sim == null) withFrameNanos { }
        val sim = holder.sim!!
        if (!festive.moving) {
            sim.settleAll()
            frame++
            return@LaunchedEffect
        }
        launch { snapshotFlow { pos() }.collect { wake.trySend(Unit) } }
        val half = 0.51f / tabs * sim.aspect
        var lastX = Float.NaN
        while (true) {
            wake.receive()
            var t0 = withFrameNanos { it }
            var calm = 0
            while (calm < 12) {
                val now = withFrameNanos { it }
                val dt = ((now - t0) / 1e9f).coerceIn(0f, 1f / 30f)
                t0 = now
                val dx = (pos() + 0.5f) / tabs * sim.aspect
                val dv = if (lastX.isNaN() || dt <= 0f) 0f else (dx - lastX) / dt
                lastX = dx
                val active = sim.step(dt, dx, half, dv, motion.tiltX, motion.tiltY, motion.shakeX, motion.shakeY)
                frame++
                calm = if (active || abs(dv) > 1e-3f) 0 else calm + 1
            }
            lastX = Float.NaN
        }
    }
    Canvas(
        modifier.onSizeChanged {
            if (holder.sim == null && it.height > 0) {
                // The snow already lies on the bottom when the bar appears.
                holder.sim = SnowGlobePhysics(it.width / it.height.toFloat(), corner.coerceIn(0.05f, 0.5f)).apply { settleAll() }
            }
        },
    ) {
        val sim = holder.sim ?: return@Canvas
        if (frame < 0) return@Canvas // read: redraw on every step
        val k = size.height // pixels per bar height
        val dp = density
        val t = festive.time()
        val glass = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(sim.corner * k)))
        }
        clipPath(glass) {
            // ---- The heap: every lying grain, a little larger than it is, so together they
            // make one body of snow with a fluffy edge. The ones inside a touch bluish, the
            // ones on the surface white on top.
            for (i in 0 until sim.flakes) {
                if (!sim.lying[i]) continue
                drawCircle(SnowShade, sim.radius[i] * 1.55f * k, Offset(sim.x[i] * k, sim.y[i] * k))
            }
            for (i in 0 until sim.flakes) {
                if (!sim.exposed(i)) continue
                drawCircle(SnowWhite, sim.radius[i] * (0.95f + 0.3f * sim.size[i]) * k, Offset(sim.x[i] * k, sim.y[i] * k))
            }

            // ---- Flakes in the liquid: crumbs, and crystals tumbling as they fall.
            for (i in 0 until sim.flakes) {
                if (sim.lying[i]) continue
                val s = sim.size[i]
                val r = (0.4f + 0.7f * s) * dp
                val c = Offset(sim.x[i] * k, sim.y[i] * k)
                if (sim.crystal[i]) {
                    drawCrystal(c, (1.3f + 1.9f * s) * dp, sim.spin[i], sim.tip[i], dp)
                    continue
                }
                drawCircle(Color.White, r * 2.3f, c, alpha = 0.14f)
                drawCircle(Color.White, r, c, alpha = 0.92f)
                if (s > 0.55f) {
                    val a = i * 2.4f
                    drawCircle(Color.White, r * 0.62f, c + Offset(cos(a) * r * 0.75f, sin(a) * r * 0.75f), alpha = 0.75f)
                    drawCircle(Color.White, r * 0.5f, c - Offset(cos(a + 1f) * r * 0.7f, sin(a + 1f) * r * 0.7f), alpha = 0.6f)
                }
            }

            // ---- A few glints on the surface of the heap, slowly coming and going.
            for (i in 0 until sim.flakes step 23) {
                if (!sim.exposed(i)) continue
                val glint = max(0f, sin(t * (0.7f + 0.6f * hash01(59, i)) + hash01(61, i) * 6.28f)).pow(3)
                if (glint < 0.05f) continue
                drawCircle(Color.White, 0.9f * dp, Offset(sim.x[i] * k, sim.y[i] * k), alpha = glint)
            }
        }
    }
}

/**
 * A six-armed crystal of [radius] turned by [spin]; tipped by [tip] it is seen at a slant,
 * flattened across one way (edge on, a thin sliver).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrystal(c: Offset, radius: Float, spin: Float, tip: Float, dp: Float) {
    val squash = 0.25f + 0.75f * abs(cos(tip))
    val cs = cos(spin)
    val sn = sin(spin)
    fun at(ax: Float, ay: Float): Offset {
        val qy = ay * squash
        return Offset(c.x + ax * cs - qy * sn, c.y + ax * sn + qy * cs)
    }
    drawCircle(Color.White, radius * 0.9f, c, alpha = 0.12f)
    val stroke = 0.75f * dp
    for (a in 0 until 3) {
        val ang = a * 1.0472f
        val ex = cos(ang) * radius
        val ey = sin(ang) * radius
        drawLine(Color.White, at(-ex, -ey), at(ex, ey), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round, alpha = 0.92f)
        // A pair of twigs near the end of each arm.
        for (end in 0..1) {
            val side = if (end == 0) 1f else -1f
            val bx = cos(ang) * radius * 0.6f * side
            val by = sin(ang) * radius * 0.6f * side
            for (twig in 0..1) {
                val ta = ang + (if (twig == 0) 0.55f else -0.55f) + (if (end == 1) 3.1416f else 0f)
                drawLine(Color.White, at(bx, by), at(bx + cos(ta) * radius * 0.32f, by + sin(ta) * radius * 0.32f), strokeWidth = stroke * 0.8f, alpha = 0.85f)
            }
        }
    }
    drawCircle(Color.White, stroke * 0.9f, c, alpha = 0.95f)
}

/** The body of the heap: snow in its own shade. */
private val SnowShade = Color(0xFFE1E9F5)

/**
 * Feeds [motion] from the accelerometer while the app is in front (see [MotionFilter]:
 * the hand's tremor and a turn of the phone are not taken for a shake). [onMove] is called
 * for a shake or a change of tilt, to wake the globe.
 */
@Composable
private fun MotionSensor(motion: MotionFilter, onMove: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val wake by rememberUpdatedState(onMove)
    DisposableEffect(context, lifecycle) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val dt = if (last == 0L) 0.02f else ((e.timestamp - last) / 1e9f).coerceIn(0.001f, 0.2f)
                last = e.timestamp
                if (motion.feed(e.values[0], e.values[1], e.values[2], dt)) wake()
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        fun on() { if (sensor != null) sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME) }
        fun off() {
            sensors?.unregisterListener(listener)
            motion.pause()
            last = 0L
        }
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

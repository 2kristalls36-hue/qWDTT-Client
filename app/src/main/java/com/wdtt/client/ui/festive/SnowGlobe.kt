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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The phone's tilt and its own acceleration, on the screen (filled by the sensor). */
internal class DeviceMotion {
    /** Direction of gravity on the screen, about unit length when upright. */
    var tiltX = 0f
    var tiltY = 1f
    /** The phone's own acceleration on the screen, m/s². */
    var shakeX = 0f
    var shakeY = 0f
}

private class GlobeHolder {
    var sim: SnowGlobePhysics? = null
}

/**
 * The snow globe inside the glass tab bar, behind the tabs and under the droplet lens
 * (see [SnowGlobePhysics]). The droplet stirs the liquid as it moves, tilting the phone
 * makes the lying snow creep to the lower side, shaking it whirls the snow up. It runs
 * only while something moves.
 */
@Composable
fun SnowGlobe(dropletPos: () -> Float, tabs: Int, corner: Float, modifier: Modifier = Modifier) {
    val festive = LocalFestive.current
    if (!festive.active) return
    val holder = remember { GlobeHolder() }
    var frame by remember { mutableIntStateOf(0) }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }
    val pos by rememberUpdatedState(dropletPos)
    val motion = remember { DeviceMotion() }
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

        // ---- The bodies of the two drifts (on the bottom, and on the top wall when the
        // phone is upside down), a little under the flakes lying on top of them, so their
        // edges are made of flakes and look fluffy.
        val n = 140
        var any = false
        for (side in 0..1) {
            val down = side == SnowGlobePhysics.BOTTOM
            val wall = { px: Float -> if (down) sim.bottomAt(px) else 1f - sim.bottomAt(px) }
            val body = Path()
            var here = false
            for (i in 0..n) {
                val px = sim.aspect * i / n
                val d = max(0f, sim.depthAt(side, px) - 0.022f)
                if (d > 0.002f) here = true
                val sy = (wall(px) + (if (down) -d else d)) * k
                val edge = wall(px) * k + if (down) 1f else -1f
                if (i == 0) { body.moveTo(px * k, edge); body.lineTo(px * k, sy) } else body.lineTo(px * k, sy)
            }
            if (!here) continue
            any = true
            for (i in n downTo 0) {
                val px = sim.aspect * i / n
                body.lineTo(px * k, wall(px) * k + if (down) 1f else -1f)
            }
            body.close()
            drawPath(
                body,
                Brush.verticalGradient(
                    if (down) listOf(Color.White.copy(alpha = 0.95f), Color(0xFFE2EAF6).copy(alpha = 0.95f))
                    else listOf(Color(0xFFE2EAF6).copy(alpha = 0.95f), Color.White.copy(alpha = 0.95f)),
                    startY = if (down) k * 0.7f else 0f, endY = if (down) k else k * 0.3f,
                ),
            )
        }

        // ---- Every flake, lying or floating: the same flake all the time.
        for (i in 0 until sim.flakes) {
            val s = sim.size[i]
            val r = (0.4f + 0.7f * s) * dp
            val c = Offset(sim.x[i] * k, sim.y[i] * k)
            if (sim.lying[i]) {
                // Only the top layer of the drift shows; the flakes under it are hidden by
                // its body anyway, so they are not drawn.
                val inside = if (sim.side[i] == SnowGlobePhysics.BOTTOM) {
                    sim.y[i] > sim.bottomAt(sim.x[i]) - sim.depthAt(SnowGlobePhysics.BOTTOM, sim.x[i]) + 0.05f
                } else {
                    sim.y[i] < 1f - sim.bottomAt(sim.x[i]) + sim.depthAt(SnowGlobePhysics.TOP, sim.x[i]) - 0.05f
                }
                if (inside) continue
                drawCircle(Color.White, r * 1.35f, c, alpha = 0.95f)
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

        // ---- A few glints on the drift, slowly coming and going.
        if (any) for (g in 0 until 14) {
            val px = (0.04f + 0.92f * hash01(53, g)) * sim.aspect
            val d = sim.depthAt(px)
            if (d < 0.03f) continue
            val glint = max(0f, sin(t * (0.7f + 0.6f * hash01(59, g)) + hash01(61, g) * 6.28f)).pow(3)
            if (glint < 0.05f) continue
            drawCircle(Color.White, 0.9f * dp, Offset(px * k, (sim.bottomAt(px) - d) * k), alpha = glint)
        }
    }
}

/**
 * Feeds [motion] from the accelerometer while the app is in front: gravity (slowly
 * filtered) and the phone's own acceleration. [onMove] is called for a shake or a
 * noticeable change of tilt, to wake the globe.
 */
@Composable
private fun MotionSensor(motion: DeviceMotion, onMove: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val wake by rememberUpdatedState(onMove)
    DisposableEffect(context, lifecycle) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val g = floatArrayOf(0f, 9.81f, 0f)
        var restX = 0f
        var restY = 1f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                for (a in 0..2) g[a] = 0.9f * g[a] + 0.1f * e.values[a]
                val lx = e.values[0] - g[0]
                val ly = e.values[1] - g[1]
                // Device axes to the screen: x the same, y up on the device is down on the screen.
                motion.tiltX = -g[0] / 9.81f
                motion.tiltY = g[1] / 9.81f
                motion.shakeX = lx
                motion.shakeY = -ly
                val tilted = hypot(motion.tiltX - restX, motion.tiltY - restY) > 0.06f
                if (hypot(lx, ly) > 1.5f || tilted) {
                    restX = motion.tiltX
                    restY = motion.tiltY
                    wake()
                }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        fun on() { if (sensor != null) sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME) }
        fun off() {
            sensors?.unregisterListener(listener)
            motion.shakeX = 0f
            motion.shakeY = 0f
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

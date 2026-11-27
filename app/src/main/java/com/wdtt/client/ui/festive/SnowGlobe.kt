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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import kotlin.math.sqrt

internal class GlobeHolder {
    var sim: SnowGlobePhysics? = null
    var warp: GlassWarp? = null
    /** The body of the drift as seen (it never moves). */
    var drift: Path? = null
    /** The bar the globe was made for: made again when it changes. */
    var width = 0f
    var height = 0f
    var corner = 0f

    /**
     * The room and its snow for a bar [w] × [h] with this [corner]: made again whenever the
     * bar changes (its width or rounding with the screen fit, the settings read late), so its
     * walls stay where the glass is. The snow starts lying on the bottom.
     */
    fun fit(w: Float, h: Float, corner: Float, density: Float, ground: ((Float, Float) -> Float)?) {
        if (h <= 0f || (sim != null && w == width && h == height && corner == this.corner)) return
        val glass = GlassWarp(w, h, corner.coerceIn(0.05f, 0.5f) * h, GLASS_DEPTH * density, GLASS_BEZEL * density)
        // The drift's top where it is to be seen: back through the glass into the room.
        val level = ground?.let { g -> roomY(glass, g(w, h)) / glass.roomHeight } ?: (1f - SnowGlobePhysics.CRUST_DEPTH)
        // The snow already lies on the bottom when the bar appears.
        sim = SnowGlobePhysics(glass.roomWidth / glass.roomHeight, glass.roomCorner / glass.roomHeight, level).apply { settleAll() }
        warp = glass
        drift = null
        width = w
        height = h
        this.corner = corner
    }
}

/** The height in the room seen at [displayY] (pixels from the bar's top) in the middle. */
private fun roomY(warp: GlassWarp, displayY: Float): Float {
    val at = FloatArray(4)
    var lo = warp.roomHeight * 0.3f
    var hi = warp.roomHeight
    repeat(40) {
        val mid = (lo + hi) / 2f
        warp.map(warp.roomWidth / 2f, mid, at)
        if (at[1] < displayY) lo = mid else hi = mid
    }
    return (lo + hi) / 2f
}

/**
 * The snow globe inside the glass tab bar, behind the tabs and under the droplet lens
 * (see [SnowGlobePhysics]): snow hanging all through the liquid, drifting very slowly,
 * and a thin crust of it on the glass at the bottom. The droplet stirs the liquid
 * as it moves and the snow swirls after it; a quick move of the phone whirls it all up
 * until it calms down again.
 * It is seen through the bar's curved glass (see [GlassWarp]): near the edges the snow is
 * squeezed into the rim, and the glass shines over it at the very edge. It runs only while
 * something moves.
 */
@Composable
fun SnowGlobe(
    dropletPos: () -> Float,
    tabs: Int,
    corner: Float,
    modifier: Modifier = Modifier,
    /**
     * Where the drift's top should be seen, pixels from the top of the bar, given its width
     * and height (Santa's train rides on it); by default a shallow drift.
     */
    ground: ((Float, Float) -> Float)? = null,
) {
    val festive = LocalFestive.current
    // By night (the dark theme) the drift is moonlit, pale blue, like Santa's train on it.
    val night = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f
    val driftBody = if (night) NightDrift else SnowShade
    val driftTop = if (night) NightDriftTop else SnowWhite
    if (!festive.active) return
    val holder = remember { GlobeHolder() }
    var frame by remember { mutableIntStateOf(0) }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }
    val pos by rememberUpdatedState(dropletPos)
    val motion = remember { MotionFilter() }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    if (festive.moving) MotionSensor(motion) { wake.trySend(Unit) }
    LaunchedEffect(festive.moving) {
        while (holder.sim == null) withFrameNanos { }
        if (!festive.moving) {
            holder.sim!!.settleAll()
            frame++
            return@LaunchedEffect
        }
        launch { snapshotFlow { pos() }.collect { wake.trySend(Unit) } }
        var lastX = Float.NaN
        var skipped = 0f
        var was: SnowGlobePhysics? = null
        while (true) {
            wake.receive()
            var t0 = withFrameNanos { it }
            var calm = 0
            var odd = false
            while (calm < 12) {
                val now = withFrameNanos { it }
                // At most about 60 steps a second: a 120 Hz screen does not double the work.
                if (now - t0 < MIN_FRAME_NS) continue
                val dt = ((now - t0) / 1e9f).coerceIn(0f, 1f / 30f)
                t0 = now
                // The globe of the bar as it is now (made again when the bar changed).
                val sim = holder.sim!!
                val warp = holder.warp!!
                if (sim !== was) {
                    was = sim
                    lastX = Float.NaN
                }
                // The droplet in the room's measure: the room reaches past the drawn bar.
                val half = 0.51f / tabs * warp.width / warp.roomHeight
                val dx = ((pos() + 0.5f) / tabs * warp.width + warp.depth) / warp.roomHeight
                val dv = if (lastX.isNaN() || dt <= 0f) 0f else (dx - lastX) / dt
                lastX = dx
                // Only the hanging snow drifting, so slowly that every other frame will do:
                // half the work for the same look.
                odd = !odd
                if (!sim.lively && abs(dv) < 1e-3f && odd) {
                    skipped += dt
                    continue
                }
                val active = sim.step((dt + skipped).coerceAtMost(1f / 30f), dx, half, dv, motion.shakeX, motion.shakeY)
                skipped = 0f
                frame++
                calm = if (active || abs(dv) > 1e-3f) 0 else calm + 1
            }
            lastX = Float.NaN
        }
    }
    val groundNow by rememberUpdatedState(ground)
    // The glints on the heap come and go over seconds: their own slow clock, so the globe is
    // not drawn again on every frame for them (only when its snow moves, and 8 times a second).
    val glintTime by androidx.compose.runtime.produceState(0f, festive.moving) {
        if (!festive.moving) return@produceState
        while (true) {
            value = android.os.SystemClock.uptimeMillis() % 3_600_000L / 1000f
            kotlinx.coroutines.delay(125)
        }
    }
    Canvas(
        modifier.onSizeChanged { holder.fit(it.width.toFloat(), it.height.toFloat(), corner, density, groundNow) },
    ) {
        // The bar's rounding can change without its size (the screen fit).
        holder.fit(size.width, size.height, corner, density, groundNow)
        val sim = holder.sim ?: return@Canvas
        val warp = holder.warp ?: return@Canvas
        if (frame < 0) return@Canvas // read: redraw on every step
        val k = warp.roomHeight // pixels per bar height of the room
        val dp = density
        val t = glintTime
        val at = FloatArray(4)
        val glass = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(sim.corner * k - warp.depth)))
        }
        clipPath(glass) {
            // The snow on a layer of its own, so the glass can shine over the snow only.
            drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }

            // ---- The drift: a body of snow along the bottom up to its level, seen through the
            // glass; along its top the grains, a little larger than they are, make a fluffy
            // edge, the ones on the very top white. Seen through the rim they flatten
            // against the glass.
            val body = holder.drift ?: Path().also { path ->
                val steps = 64
                for (n in 0..steps) {
                    warp.map(sim.aspect * k * n / steps, (sim.driftTop + sim.grain) * k, at)
                    if (n == 0) path.moveTo(at[0], at[1]) else path.lineTo(at[0], at[1])
                }
                path.lineTo(size.width + k, size.height + k)
                path.lineTo(-k, size.height + k)
                path.close()
                holder.drift = path
            }
            drawPath(body, driftBody)
            for (i in 0 until sim.flakes) {
                if (!sim.lying[i]) continue
                grain(warp, sim.x[i] * k, sim.y[i] * k, sim.radius[i] * 1.55f * k, driftBody, 1f, at)
            }
            for (i in 0 until sim.flakes) {
                if (!sim.exposed(i)) continue
                grain(warp, sim.x[i] * k, sim.y[i] * k, sim.radius[i] * (0.95f + 0.3f * sim.size[i]) * k, driftTop, 1f, at)
            }

            // ---- Flakes in the liquid: crumbs, and crystals tumbling as they fall.
            for (i in 0 until sim.flakes) {
                if (sim.lying[i]) continue
                val s = sim.size[i]
                val q = warp.map(sim.x[i] * k, sim.y[i] * k, at)
                val shrink = 0.5f + 0.5f * q
                val r = (0.4f + 0.7f * s) * dp * shrink
                val c = Offset(at[0], at[1])
                // The hanging snow a little faint, so the tab labels read through it.
                val fade = if (sim.suspended[i]) HANGING_ALPHA else 1f
                if (sim.crystal[i]) {
                    drawCrystal(c, (1.3f + 1.9f * s) * dp * shrink, sim.spin[i], sim.tip[i], dp, fade)
                    continue
                }
                drawCircle(Color.White, r * 2.3f, c, alpha = 0.14f * fade)
                drawCircle(Color.White, r, c, alpha = 0.92f * fade)
                if (s > 0.55f) {
                    val a = i * 2.4f
                    drawCircle(Color.White, r * 0.62f, c + Offset(cos(a) * r * 0.75f, sin(a) * r * 0.75f), alpha = 0.75f * fade)
                    drawCircle(Color.White, r * 0.5f, c - Offset(cos(a + 1f) * r * 0.7f, sin(a + 1f) * r * 0.7f), alpha = 0.6f * fade)
                }
            }

            // ---- A few glints on the surface of the heap, slowly coming and going.
            for (i in 0 until sim.flakes step 23) {
                if (!sim.exposed(i)) continue
                val glint = max(0f, sin(t * (0.7f + 0.6f * hash01(59, i)) + hash01(61, i) * 6.28f)).pow(3)
                if (glint < 0.05f) continue
                warp.map(sim.x[i] * k, sim.y[i] * k, at)
                drawCircle(Color.White, 0.9f * dp, Offset(at[0], at[1]), alpha = glint)
            }

            // ---- The glass in front of the snow: a thin shine right at the edge and a cool
            // shade just inside it, where the snow is seen through the curved rim.
            val edge = CornerRadius(sim.corner * k - warp.depth)
            drawRoundRect(IceBlue, cornerRadius = edge, alpha = 0.35f, style = Stroke(width = 3.5f * dp), blendMode = BlendMode.SrcAtop)
            drawRoundRect(Color.White, cornerRadius = edge, alpha = 0.55f, style = Stroke(width = 1.2f * dp), blendMode = BlendMode.SrcAtop)
            drawIntoCanvas { it.restore() }
        }
    }
}

/**
 * One lying grain of the room at ([px], [py]) as seen through the glass: in the rim it is
 * flattened against the glass as the view is squeezed there.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.grain(
    warp: GlassWarp, px: Float, py: Float, radius: Float, color: Color, alpha: Float, at: FloatArray,
) {
    val q = warp.map(px, py, at)
    if (q > 0.97f) {
        drawCircle(color, radius, Offset(at[0], at[1]), alpha = alpha)
        return
    }
    when {
        abs(at[3]) > 0.85f -> drawOval(color, Offset(at[0] - radius, at[1] - radius * q), Size(radius * 2f, radius * 2f * q), alpha = alpha)
        abs(at[2]) > 0.85f -> drawOval(color, Offset(at[0] - radius * q, at[1] - radius), Size(radius * 2f * q, radius * 2f), alpha = alpha)
        else -> drawCircle(color, radius * sqrt(q), Offset(at[0], at[1]), alpha = alpha)
    }
}

/** How strongly the hanging snow is drawn (the labels read through it). */
private const val HANGING_ALPHA = 0.8f

/** Depth and width of the curved rim of the bar's glass, dp (as its own lens, see LiquidGlass). */
private const val GLASS_DEPTH = 7f
private const val GLASS_BEZEL = 16f

/**
 * A six-armed crystal of [radius] turned by [spin]; tipped by [tip] it is seen at a slant,
 * flattened across one way (edge on, a thin sliver).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrystal(c: Offset, radius: Float, spin: Float, tip: Float, dp: Float, fade: Float) {
    val squash = 0.25f + 0.75f * abs(cos(tip))
    val cs = cos(spin)
    val sn = sin(spin)
    fun at(ax: Float, ay: Float): Offset {
        val qy = ay * squash
        return Offset(c.x + ax * cs - qy * sn, c.y + ax * sn + qy * cs)
    }
    drawCircle(Color.White, radius * 0.9f, c, alpha = 0.12f * fade)
    val stroke = 0.75f * dp
    for (a in 0 until 3) {
        val ang = a * 1.0472f
        val ex = cos(ang) * radius
        val ey = sin(ang) * radius
        drawLine(Color.White, at(-ex, -ey), at(ex, ey), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round, alpha = 0.92f * fade)
        // A pair of twigs near the end of each arm.
        for (end in 0..1) {
            val side = if (end == 0) 1f else -1f
            val bx = cos(ang) * radius * 0.6f * side
            val by = sin(ang) * radius * 0.6f * side
            for (twig in 0..1) {
                val ta = ang + (if (twig == 0) 0.55f else -0.55f) + (if (end == 1) 3.1416f else 0f)
                drawLine(Color.White, at(bx, by), at(bx + cos(ta) * radius * 0.32f, by + sin(ta) * radius * 0.32f), strokeWidth = stroke * 0.8f, alpha = 0.85f * fade)
            }
        }
    }
    drawCircle(Color.White, stroke * 0.9f, c, alpha = 0.95f * fade)
}

/** The body of the drift: snow in its own shade. */
private val SnowShade = Color(0xFFE1E9F5)
/** The drift by moonlight: its body and its top. */
private val NightDrift = Color(0xFFAFC2E4)
private val NightDriftTop = Color(0xFFCFDCF5)

/**
 * Feeds [motion] from the accelerometer while the app is in front (see [MotionFilter]:
 * the hand's tremor and a turn of the phone are not taken for a shake). [onMove] is called
 * for a shake, to wake the globe.
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

/** The shortest step the snow in the globe and Santa's train are moved by: about 60 a second at most. */
internal const val MIN_FRAME_NS = 10_000_000L

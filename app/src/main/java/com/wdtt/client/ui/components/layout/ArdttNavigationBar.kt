package com.wdtt.client.ui.components.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import com.wdtt.client.ui.festive.SleighTrain
import com.wdtt.client.ui.festive.drawSleighTab
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import com.wdtt.client.ui.festive.frost
import com.wdtt.client.ui.festive.snowCap
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.Icons
import com.wdtt.client.ui.components.surface.glassBackdropSource
import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.animateFloatAsState
import com.wdtt.client.ui.motion.magnetTo
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.wdtt.client.ui.theme.isDarkSurface
import com.wdtt.client.ui.components.surface.LiquidGlassPanel
import com.wdtt.client.ui.components.surface.LiquidGlass
import com.wdtt.client.ui.components.surface.LocalGlassBackdrop
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.wdtt.client.ui.components.surface.ArdttFloatingShell
import com.wdtt.client.ui.theme.ArdttElevation
import com.wdtt.client.ui.theme.ArdttSurface
import com.wdtt.client.ui.theme.ArdttMotion
import com.wdtt.client.ui.theme.ArdttNavigationLabelStyle
import com.wdtt.client.ui.theme.ArdttShapes
import com.wdtt.client.ui.theme.ArdttSize
import com.wdtt.client.ui.theme.ArdttSpacing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class ArdttNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0,
    /** Small dot without a number, e.g. a new app version. */
    val dot: Boolean = false,
)

internal object ArdttNavChrome {
    val LabelSize = ArdttNavigationLabelStyle.fontSize
    val LabelLineHeight = ArdttNavigationLabelStyle.lineHeight
}

/** Pale green of a thick glass edge. */
private val GlassEdgeGreen = Color(0xFF8FD9B0)

private object NavBarDefaults {
    val GlassCorner = com.wdtt.client.ui.theme.ArdttRadius.Section
    /** Fitted bar: corners never sharper than this, whatever the screen. */
    val MinCorner = 14.dp
    /** Distance from the bar edge to the droplet edge (vertically, (track − droplet) / 2). */
    val DropletInset = 8.5.dp
    val MinDropletCorner = 10.dp
    val OuterPadding = ArdttSpacing.SmallPlus
    val TrackPadding = ArdttSpacing.Small

    const val BoldEmphasis = 0.55f
    const val OpaqueEmphasis = 0.4f
    const val FadedLabelAlpha = 0.92f
    const val MaxBadgeCount = 99
}

internal data class NavTabPaint(
    val color: Color,
    val bold: Boolean,
    val labelAlpha: Float,
)

internal fun navTabPaint(
    selected: Boolean,
    pending: Boolean,
    pressed: Boolean,
    emphasis: Float,
    selectedColor: Color,
    unselectedColor: Color,
): NavTabPaint {
    val highlighted = selected || pending || pressed
    val visualEmphasis = if (highlighted) 1f else emphasis
    return NavTabPaint(
        color = if (highlighted) {
            selectedColor
        } else {
            lerp(unselectedColor, selectedColor, emphasis)
        },
        bold = visualEmphasis > NavBarDefaults.BoldEmphasis,
        labelAlpha = if (visualEmphasis > NavBarDefaults.OpaqueEmphasis) {
            1f
        } else {
            NavBarDefaults.FadedLabelAlpha
        },
    )
}

internal fun navTabPendingRoute(currentRoute: String, clickedRoute: String): String? =
    clickedRoute.takeIf { it != currentRoute }

/** One-second icon motion. Every pose is back at rest when progress is 0 or 1. */
internal enum class TabIconMotion {
    KeyTurn,
    ServerLights,
    Lift,
    Slide,
    Pulse,
    Heartbeat,
    GearHalfTurn,
    /** The folder opens and closes again, with a light pop. */
    FolderOpen,
    /** The three filter lines narrow one after another, like sifting. */
    Sift,
}

internal data class TabIconPose(
    val rotationX: Float = 0f,
    val rotationZ: Float = 0f,
    val translationXFraction: Float = 0f,
    val translationYFraction: Float = 0f,
    val scale: Float = 1f,
)

/**
 * Full roll around the horizontal axis. Halfway the key is upside down;
 * at the end it is home again. Not a spin in the plane of the icon.
 *
 * Drawn as a vector scale, not a 3D layer: a small glyph rotated in X is
 * clipped by a near camera and looks like dropped frames. The gear stays
 * smooth because it is a flat spin.
 */
internal const val KeyTurnDegrees = 360f

internal fun tabIconMotionFor(route: String): TabIconMotion = when (route) {
    "tunnel" -> TabIconMotion.KeyTurn
    "servers" -> TabIconMotion.ServerLights
    "profiles" -> TabIconMotion.FolderOpen
    "exceptions" -> TabIconMotion.Sift
    "network" -> TabIconMotion.Pulse
    "diagnostics" -> TabIconMotion.Heartbeat
    "settings" -> TabIconMotion.GearHalfTurn
    else -> TabIconMotion.Pulse
}

/**
 * Smooth 0→1→0 weight. Velocity is zero at the start, the midpoint, and the end,
 * so a there-and-back motion does not kick.
 */
internal fun tabIconSettle(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    val wave = sin(Math.PI * t).toFloat()
    return wave * wave
}

/** Two beats inside the same second, also at rest at the ends and the middle. */
internal fun tabIconHeartbeat(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    val wave = sin(2.0 * Math.PI * t).toFloat()
    return wave * wave
}

/** One-way smoothstep, 0→1. Velocity is zero at the start and the end. */
internal fun tabIconTurn(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Orthographic height of the key. 1 upright, 0 edge-on, −1 upside down.
 * Same turn as [KeyTurnDegrees], sampled so neighbouring frames stay close.
 */
internal fun tabIconKeyScaleY(rotationX: Float): Float =
    cos(rotationX * (PI.toFloat() / 180f))

/**
 * Both server lamps, same curve as the diagnostic heartbeat: two soft blinks,
 * fully lit at the start, the middle, and the end.
 */
internal fun tabIconServerLightAlpha(progress: Float): Float =
    1f - tabIconHeartbeat(progress)

internal fun tabIconPose(motion: TabIconMotion, progress: Float): TabIconPose {
    val settle = tabIconSettle(progress)
    return when (motion) {
        TabIconMotion.GearHalfTurn -> TabIconPose(rotationZ = 180f * settle)
        TabIconMotion.KeyTurn -> TabIconPose(
            rotationX = KeyTurnDegrees * tabIconTurn(progress),
        )
        TabIconMotion.ServerLights -> TabIconPose()
        TabIconMotion.Lift -> TabIconPose(
            translationYFraction = -0.16f * settle,
            rotationZ = -8f * settle,
        )
        TabIconMotion.Slide -> TabIconPose(translationXFraction = 0.16f * settle)
        TabIconMotion.FolderOpen -> TabIconPose(scale = 1f + 0.10f * settle)
        TabIconMotion.Sift -> TabIconPose()
        TabIconMotion.Pulse -> TabIconPose(scale = 1f + 0.12f * settle)
        TabIconMotion.Heartbeat -> TabIconPose(scale = 1f + 0.14f * tabIconHeartbeat(progress))
    }
}

/**
 * Vector flip around the horizontal axis. The glyph is redrawn under the
 * scale, so the edge stays a smooth hairline instead of a torn texture.
 */
private fun Modifier.tunnelKeyFlip(rotationX: Float): Modifier = drawWithContent {
    val pivot = center
    val sy = tabIconKeyScaleY(rotationX)
    withTransform({
        scale(scaleX = 1f, scaleY = sy, pivot = pivot)
    }) {
        this@drawWithContent.drawContent()
    }
}

internal fun tabIconPoseAtRest(pose: TabIconPose): Boolean {
    fun wrappedRest(degrees: Float): Boolean {
        val wrapped = abs(degrees % 360f)
        return wrapped < 0.05f || abs(wrapped - 360f) < 0.05f
    }
    return wrappedRest(pose.rotationX) &&
        wrappedRest(pose.rotationZ) &&
        abs(pose.translationXFraction) < 0.01f &&
        abs(pose.translationYFraction) < 0.01f &&
        abs(pose.scale - 1f) < 0.01f
}

/**
 * How the selected tab is coloured, after the power level: plain (stable), the level
 * colour with a rare faint sheen (balanced), or a cut-out through which the neon sheet
 * of the maximum level shows (maximum) — the same colours as its card, no light of its own.
 */
sealed interface NavAccent {
    data object Plain : NavAccent
    data class Sheen(val color: Color) : NavAccent
    data class Neon(val colors: List<Color>) : NavAccent
}

/** Floating pill bottom bar. The current tab is the icon and label, with no fill behind it. */
@Composable
fun ArdttNavigationBar(
    items: List<ArdttNavItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    accent: NavAccent = NavAccent.Plain,
    /** The droplet position in tabs, on every move (drag, travel after a tap). */
    onPosition: ((Float) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shell = ArdttFloatingShell.shellColor()
    val selectedColor = when (accent) {
        NavAccent.Plain -> colors.primary
        is NavAccent.Sheen -> accent.color
        is NavAccent.Neon -> accent.colors[1]
    }
    // One clock for the accent of the selected tab; read only while drawing.
    val accentClock = rememberNavAccentClock(accent)
    val unselectedColor = colors.onSurfaceVariant
    var pendingRoute by remember { mutableStateOf<String?>(null) }
    val selectTab = rememberUpdatedState(onSelect)

    LaunchedEffect(selectedRoute) {
        pendingRoute = null
    }

    // Fitted to the screen: with gesture navigation and known rounded corners the bar
    // comes down to the corners, the same distance from the side and the bottom edge,
    // and its corners run concentric with the screen's (radius = screen radius − gap).
    // Otherwise (buttons at the bottom, unknown corners) it keeps its usual place.
    val screen = LocalScreenShape.current
    val fittedGap = screen.tabBarGap(NavBarDefaults.OuterPadding, ArdttSize.NavTrack / 2)
    val fitted = fittedGap != null
    val gap = fittedGap ?: NavBarDefaults.OuterPadding
    val barRadius = if (fitted) (screen.bottomCorner - gap).coerceIn(NavBarDefaults.MinCorner, ArdttSize.NavTrack / 2)
        else NavBarDefaults.GlassCorner
    val barShape = androidx.compose.foundation.shape.RoundedCornerShape(barRadius)
    // The droplet keeps the same distance to the bar edge all round its corners.
    val dropletRadius = (barRadius - NavBarDefaults.DropletInset).coerceAtLeast(NavBarDefaults.MinDropletCorner)
    val outer = if (fitted) {
        modifier
            .fillMaxWidth()
            .padding(start = gap, end = gap, top = NavBarDefaults.TrackPadding, bottom = gap)
    } else {
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                horizontal = NavBarDefaults.OuterPadding,
                vertical = NavBarDefaults.TrackPadding,
            )
    }
    // Selection droplet: follows the finger when dragged across the bar, then is pulled
    // into the nearest tab by the magnet and that tab opens. A plain tap works as before.
    val count = items.size
    val committedIndex = items.indexOfFirst { it.route == (pendingRoute ?: selectedRoute) }.coerceAtLeast(0)
    val selPos = remember { Animatable(committedIndex.toFloat()) }
    val positionSink = rememberUpdatedState(onPosition)
    LaunchedEffect(selPos) {
        androidx.compose.runtime.snapshotFlow { selPos.value }.collect { positionSink.value?.invoke(it) }
    }
    // Read through state: gesture handlers outlive recompositions.
    val magnetOn by androidx.compose.runtime.rememberUpdatedState(com.wdtt.client.ui.motion.LocalMagnetEnabled.current)
    var dragging by remember { mutableStateOf(false) }
    var hover by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    val range = (count - 1).coerceAtLeast(1).toFloat()
    // Without glass the droplet never travels: it jumps to the chosen tab, and the screens
    // (they follow the droplet position) change at once.
    val instant = !(LocalGlassBackdrop.current != null && LiquidGlass.supported)
    LaunchedEffect(committedIndex, dragging, instant) {
        if (dragging) return@LaunchedEffect
        if (instant) selPos.snapTo(committedIndex.toFloat())
        else selPos.magnetTo(committedIndex.toFloat(), fullRange = range, enabled = magnetOn)
    }
    val currentItems by rememberUpdatedState(items)
    // The drag handler lives across recompositions: read the selected tab through state,
    // or it would compare against the tab that was selected when the bar first appeared.
    val currentSelected by rememberUpdatedState(selectedRoute)
    val committedNow by rememberUpdatedState(committedIndex)
    val glassBar = LocalGlassBackdrop.current != null && LiquidGlass.supported
    val glassNow by rememberUpdatedState(glassBar)
    // New Year, in the glass bar only: the tabs are Santa's train (see SleighTrain).
    val festiveNow = com.wdtt.client.ui.festive.LocalFestive.current
    val sleigh = if (festiveNow.active && glassBar && count == SleighTrain.count) {
        com.wdtt.client.ui.festive.rememberSleighMotion(festiveNow) { selPos.value }
    } else null
    // Where the finger took the droplet. A drag moves it by the finger's travel from there,
    // so taking it off-centre does not make it (and the screens with it) jump to the finger.
    val grab = remember { NavGrab() }
    // Liquid glass: the droplet swells as soon as the bar is touched (touching another tab
    // brings it there) and shrinks when the finger is lifted. One vibration for each.
    var touching by remember { mutableStateOf(false) }
    val grabWatch = Modifier.pointerInput(count) {
        val pad = NavBarDefaults.TrackPadding.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial,
            )
            val slot = (size.width - pad * 2) / count
            val raw = (down.position.x - pad) / slot - 0.5f
            val at = raw.roundToInt().coerceIn(0, count - 1)
            // On the droplet's tab it stays where it is; on another tab it goes there.
            val onDroplet = at == selPos.value.roundToInt()
            val from = if (onDroplet) selPos.value else at.toFloat()
            grab.anchor = from - raw
            if (!glassNow) return@awaitEachGesture
            touching = true
            haptics.perform(com.wdtt.client.ui.motion.Haptic.Grab)
            if (!onDroplet && !dragging) scope.launch { selPos.magnetTo(from, fullRange = range, enabled = magnetOn) }
            try {
                while (true) {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (event.changes.none { it.pressed }) break
                }
            } finally {
                touching = false
                haptics.perform(com.wdtt.client.ui.motion.Haptic.Drop)
                // A tap selects the tab on release. A press that ends without selecting
                // (the finger slid off the bar) puts the droplet back on the current tab.
                scope.launch {
                    androidx.compose.runtime.withFrameNanos { }
                    androidx.compose.runtime.withFrameNanos { }
                    if (!dragging && committedNow != at) {
                        selPos.magnetTo(committedNow.toFloat(), fullRange = range, enabled = magnetOn)
                    }
                }
            }
        }
    }
    val dragToSelect = Modifier.pointerInput(count) {
        val pad = NavBarDefaults.TrackPadding.toPx()
        /** The droplet position for the finger at [x]: shifted by the finger's travel. */
        fun positionAt(x: Float): Float {
            val slot = (size.width - pad * 2) / count
            return ((x - pad) / slot - 0.5f + grab.anchor).coerceIn(0f, (count - 1).toFloat())
        }
        // Vibration: once when the droplet is taken and once when it is let go, none on
        // the tabs it passes. Liquid glass vibrates on touch and release (see grabWatch).
        fun follow(x: Float) {
            if (!glassNow) {
                // Plain bar: the tab under the finger opens at once, nothing slides.
                val slot = (size.width - pad * 2) / count
                val at = kotlin.math.floor((x - pad) / slot).toInt().coerceIn(0, count - 1)
                hover = at
                if (selPos.value != at.toFloat()) scope.launch { selPos.snapTo(at.toFloat()) }
                val route = currentItems[at].route
                if (route != currentSelected && route != pendingRoute) {
                    pendingRoute = route
                    selectTab.value(route)
                }
                return
            }
            val raw = positionAt(x)
            hover = raw.roundToInt()
            val shown = if (magnetOn) tabMagnet(raw) else raw
            scope.launch { selPos.snapTo(shown) }
        }
        fun release() {
            val target = selPos.value.roundToInt().coerceIn(0, count - 1)
            val route = currentItems[target].route
            hover = -1
            if (!glassNow) haptics.perform(com.wdtt.client.ui.motion.Haptic.Drop)
            if (route != currentSelected) {
                navTabPendingRoute(currentSelected, route)?.let { pendingRoute = it }
                selectTab.value(route)
            }
            scope.launch {
                if (glassNow) selPos.magnetTo(target.toFloat(), fullRange = range, enabled = magnetOn)
                else selPos.snapTo(target.toFloat())
                dragging = false
            }
        }
        detectHorizontalDragGestures(
            onDragStart = { o ->
                dragging = true
                if (!glassNow) haptics.perform(com.wdtt.client.ui.motion.Haptic.Grab)
                follow(o.x)
            },
            onDragEnd = { release() },
            onDragCancel = { release() },
        ) { change, _ ->
            change.consume()
            follow(change.position.x)
        }
    }
    val shownIndex = if (dragging && hover >= 0) hover else -1
    // While touched the glass droplet swells.
    val lens by animateFloatAsState(
        if (touching) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 420f),
        label = "tabLens",
    )
    // Where the droplet is, in tab slots: its centre and its half width. The tabs take
    // their colour only inside it, so the colour moves exactly with the droplet.
    val densityNow = androidx.compose.ui.platform.LocalDensity.current
    val tintIndex = if (shownIndex >= 0) shownIndex
        else items.indexOfFirst { it.route == (pendingRoute ?: selectedRoute) }
    val dropletSpan: () -> DropletSpan = {
        if (glassBar) {
            val lh = with(densityNow) { (ArdttSize.NavTrack - 8.dp).toPx() } * (0.86f + 0.14f * lens)
            DropletSpan(selPos.value, (NavDropletWidth + 0.2f * lens) / 2f, (with(densityNow) { ArdttSize.NavTrack.toPx() } - lh) / 2f)
        } else {
            // Without glass the colour does not travel with the droplet: the chosen tab
            // takes it at once, the others lose it at once, as before the glass mode.
            DropletSpan(tintIndex.toFloat(), 0.5f, 0f)
        }
    }

    val tabs: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup()
                .padding(
                    horizontal = NavBarDefaults.TrackPadding,
                    vertical = ArdttSpacing.Tiny,
                ),
        ) {
            items.forEachIndexed { index, item ->
                NavBarTab(
                    index = index,
                    count = count,
                    droplet = dropletSpan,
                    accent = accent,
                    accentClock = accentClock,
                    item = item,
                    selected = if (shownIndex >= 0) index == shownIndex else item.route == selectedRoute,
                    pending = shownIndex < 0 && item.route == pendingRoute,
                    // How much of the droplet is over this tab (weight of the label, halo).
                    emphasis = if (glassBar) (1f - kotlin.math.abs(selPos.value - index)).coerceIn(0f, 1f)
                        else if (index == tintIndex) 1f else 0f,
                    selectedColor = selectedColor,
                    unselectedColor = unselectedColor,
                    sleigh = sleigh,
                    onSelect = {
                        navTabPendingRoute(selectedRoute, item.route)?.let { pendingRoute = it }
                        selectTab.value(item.route)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    val backdrop = LocalGlassBackdrop.current
    if (backdrop != null && LiquidGlass.supported) {
        val dark = isDarkSurface()
        LiquidGlassPanel(
            backdrop = backdrop,
            cornerRadius = barRadius,
            tint = colors.surface.copy(alpha = backdrop.veil(dark)),
            dark = dark,
            blur = backdrop.frost(),
            modifier = outer
                .snowCap(barRadius, seed = 1)
                .shadow(18.dp, barShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .height(ArdttSize.NavTrack)
                .then(grabWatch)
                .then(dragToSelect),
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                // Lens in the lens: the selection is a small frosted glass lens inside the
                // panel. It magnifies what lies under it by about 5%: the page behind the
                // panel, frosted, and the selected tab, sharp. While touched it swells.
                val panelLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
                val frostLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
                val panelGlass = remember(panelLayer) { com.wdtt.client.ui.components.surface.GlassBackdrop(panelLayer) }
                val veil = colors.surface.copy(alpha = backdrop.veil(dark))
                // The inner lens edge is where the light crosses two layers of glass: real
                // soda-lime glass shows a faint green there (iron traces). Kept faint.
                panelGlass.rim = GlassEdgeGreen
                // Panel picture for the lens: the page behind the panel under the veil, with
                // the tabs; the tabs are drawn as usual beneath the lens.
                Box(
                    Modifier
                        .matchParentSize()
                        .frost(seed = 2, strength = 0.8f)
                        .onGloballyPositioned { panelGlass.origin = it.positionInRoot() }
                        .drawWithContent {
                            val offset = panelGlass.origin - backdrop.origin
                            // Two layers for the lens: the page behind the panel, frosted (blur
                            // on its own layer), and the tabs sharp on top. The lens bends both,
                            // so the background is matte while the selected label stays crisp.
                            val r = 5.dp.toPx()
                            frostLayer.renderEffect = androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Clamp)
                            frostLayer.record {
                                translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
                                drawRect(veil)
                            }
                            panelLayer.record {
                                drawLayer(frostLayer)
                                this@drawWithContent.drawContent()
                            }
                            drawContent()
                        },
                ) {
                    // New Year: snow inside the glass, stirred by the droplet and a shake; Santa's
                    // train rides on the drift along its bottom.
                    val groundOf: ((Float, Float) -> Float)? = if (sleigh != null) {
                        val padX = with(densityNow) { NavBarDefaults.TrackPadding.toPx() }
                        val padY = with(densityNow) { ArdttSpacing.Tiny.toPx() }
                        val tallest = with(densityNow) { SleighTrain.height.toPx() }
                        val at: (Float, Float) -> Float = { w, h -> SleighTrain.groundIn(w, h, padX, padY, tallest) }
                        at
                    } else null
                    com.wdtt.client.ui.festive.SnowGlobe({ selPos.value }, count, barRadius / ArdttSize.NavTrack, Modifier.matchParentSize(), groundOf)
                    tabs()
                }
                val trackH = ArdttSize.NavTrack
                Box(
                    Modifier
                        .matchParentSize()
                        .layout { measurable, constraints ->
                            val pad = NavBarDefaults.TrackPadding.toPx()
                            val slot = (constraints.maxWidth - pad * 2) / count
                            val lh = (trackH.toPx() - 8.dp.toPx()) * (0.86f + 0.14f * lens)
                            // Nearly the slot width: the longest label must fit inside the flat middle.
                            val lw = slot * (NavDropletWidth + 0.2f * lens)
                            val placeable = measurable.measure(
                                androidx.compose.ui.unit.Constraints.fixed(lw.roundToInt(), lh.roundToInt())
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                // Never past the bar edge: at the end tabs (and swollen while
                                // dragged) it keeps the gap it has at the top and bottom.
                                val cx = dropletCenter(constraints.maxWidth.toFloat(), pad, slot, selPos.value, lw / 2f, (constraints.maxHeight - lh) / 2f)
                                placeable.place((cx - lw / 2f).roundToInt(), ((constraints.maxHeight - lh) / 2f).roundToInt())
                            }
                        }
                        .shadow((2f + 4f * lens).dp, androidx.compose.foundation.shape.RoundedCornerShape(dropletRadius), ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f)),
                ) {
                    LiquidGlassPanel(
                        backdrop = panelGlass,
                        cornerRadius = dropletRadius,
                        // The frost is in the picture it bends (background only); a light haze.
                        tint = Color.White.copy(alpha = if (dark) 0.08f else 0.20f),
                        dark = dark,
                        // Narrow, shallow rim: the label sits close to the lower edge and must
                        // stay in the flat middle, not curl along the rim.
                        bezel = NavDropletBezel,
                        // Held, the swollen droplet bends the light more at its rim.
                        depth = 2.dp + 3.dp * lens,
                        // A little magnification, like a magnifying lens over the tab; the
                        // labels are kept narrow enough to stay in the flat middle (see NavBarTab).
                        zoom = NavDropletZoom + 0.04f * lens,
                        blur = 0.dp,
                        underlay = lerp(colors.surface, Color.White, if (dark) 0.06f else 0.5f),
                        fringe = 0.45f, dispersion = 0.2f,
                        // Gentler bend across the top and bottom edges: the snow lying under
                        // the droplet is not drawn out into streaks.
                        verticalBend = 0.5f,
                        modifier = Modifier.fillMaxSize(),
                    ) {}
                }
            }
        }
        return
    }

    Surface(
        shape = barShape,
        color = shell,
        contentColor = ArdttSurface.contentColorOn(shell),
        border = ArdttFloatingShell.shellBorder(),
        tonalElevation = ArdttElevation.None,
        // A shadow graphics layer paints this translucent shell opaque for a frame.
        shadowElevation = ArdttElevation.None,
        modifier = outer.snowCap(barRadius, seed = 1),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ArdttSize.NavTrack)
                .frost(seed = 2, strength = 0.8f)
                .then(grabWatch)
                .then(dragToSelect),
        ) {
            SelectionDroplet(count = count, pos = selPos.value, velocity = selPos.velocity, dark = isDarkSurface(), glass = false, corner = dropletRadius)
            tabs()
        }
    }
}

/** 0..1, looping: a slow neon drift, or the sheen cycle (one pass, then a long rest). */
@Composable
private fun rememberNavAccentClock(accent: NavAccent): () -> Float {
    if (accent is NavAccent.Plain) return { 0f }
    val motion = com.wdtt.client.ui.home.rememberMotionScale()
    if (motion <= 0.01f) return { 0.3f }
    val periodMs = if (accent is NavAccent.Neon) NavAccentNeonPeriodMs else NavAccentSheenPeriodMs
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "navAccent")
    val p = t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween((periodMs / motion).toInt(), easing = LinearEasing)),
        label = "navAccentPhase",
    )
    return { p.value }
}

/**
 * Glass droplet: width in tab slots, magnification, the width of its bending rim, and how
 * much closer its rounded ends come at the height of the label (a capsule narrows away
 * from its middle line; the label sits below it).
 */
private const val NavDropletWidth = 1.02f
private const val NavDropletZoom = 0.08f
private val NavDropletBezel = 6.dp
private val NavDropletEndCap = 6.dp

private const val NavAccentNeonPeriodMs = 2600
/** Balanced: one faint pass about every seven seconds. */
private const val NavAccentSheenPeriodMs = 7000
private const val NavAccentSheenPass = 0.2f

/**
 * Colours the glyphs of a tab where the droplet is over it: the accent colour, and per
 * power level the sheen (balanced) or the neon sheet seen through the glyphs (maximum,
 * laid out in bar coordinates so the colours run on from one glyph to the next). Outside
 * the droplet the glyphs keep their plain colour, so the colour travels with the droplet.
 *
 * The same offscreen layer on every tab at all times: the droplet refracts a picture that
 * refers to the tab layers, and a layer added only when a tab became selected was missing
 * from it (the droplet showed an empty spot).
 */
/**
 * This tab's part under the droplet, in the tab's own coordinates: from [first] to [second]
 * along x. Tabs are equal slots, so it comes from the droplet span alone.
 */
private fun dropletRangeInTab(span: DropletSpan, index: Int, count: Int, w: Float, trackPad: Float): Pair<Float, Float> {
    val barCx = dropletCenter(trackPad * 2f + w * count, trackPad, w, span.pos, span.half * w, span.inset)
    val c = barCx - (trackPad + w * index)
    return (c - span.half * w) to (c + span.half * w)
}

/**
 * The legibility border around the glyphs, drawn only outside the droplet. Fading it by how
 * far the droplet had come left it half-strong while the droplet was between two tabs, and
 * the lens showed it around both labels.
 */
@Composable
private fun Modifier.haloOutsideDroplet(
    halo: androidx.compose.ui.graphics.RenderEffect?,
    index: Int,
    count: Int,
    droplet: () -> DropletSpan,
): Modifier {
    if (halo == null) return this
    val plain = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val bordered = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val trackPad = with(androidx.compose.ui.platform.LocalDensity.current) { NavBarDefaults.TrackPadding.toPx() }
    return drawWithContent {
        plain.record { this@drawWithContent.drawContent() }
        bordered.renderEffect = halo
        bordered.record { drawLayer(plain) }
        val (l, r) = dropletRangeInTab(droplet(), index, count, size.width, trackPad)
        val tall = size.height
        if (r <= 0f || l >= size.width) {
            drawLayer(bordered)
            return@drawWithContent
        }
        // Left and right of the droplet: with the border. Under it: the glyphs alone.
        clipRect(left = -size.width, top = -tall, right = l, bottom = size.height + tall) { drawLayer(bordered) }
        clipRect(left = r, top = -tall, right = size.width * 2f, bottom = size.height + tall) { drawLayer(bordered) }
        clipRect(left = l, top = -tall, right = r, bottom = size.height + tall) { drawLayer(plain) }
    }
}

@Composable
private fun Modifier.navTint(
    index: Int,
    count: Int,
    droplet: () -> DropletSpan,
    color: Color,
    accent: NavAccent,
    clock: () -> Float,
    /** False where the tab draws its own colours under the droplet (the New Year train). */
    recolour: Boolean = true,
): Modifier {
    val period = with(androidx.compose.ui.platform.LocalDensity.current) { 220.dp.toPx() }
    val trackPad = with(androidx.compose.ui.platform.LocalDensity.current) { NavBarDefaults.TrackPadding.toPx() }
    return this
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (!recolour) return@drawWithContent
            val w = size.width
            // The droplet in this tab's coordinates, kept inside the bar as the droplet is.
            val (l, r) = dropletRangeInTab(droplet(), index, count, w, trackPad)
            if (r <= 0f || l >= w) return@drawWithContent
            val tall = size.height
            clipRect(left = l, top = -tall, right = r, bottom = size.height + tall) {
                val all = Offset(-w, -tall)
                val area = Size(w * 3f, size.height + tall * 2f)
                drawRect(color, all, area, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                when (accent) {
                    is NavAccent.Neon -> {
                        // Bar coordinates from the tab's place (tabs are equal slots), so the
                        // sheet runs on across the tabs; no position callbacks needed.
                        val shift = clock() * period - (trackPad + w * index)
                        drawRect(
                            Brush.linearGradient(
                                colors = accent.colors + accent.colors.first(),
                                start = Offset(shift, 0f),
                                end = Offset(shift + period, period * 0.35f),
                                tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
                            ),
                            all, area,
                            blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop,
                        )
                    }
                    is NavAccent.Sheen -> {
                        val pass = clock() / NavAccentSheenPass
                        if (pass < 1f) {
                            val band = w * 0.45f
                            val x = -band + pass * (w + band * 2f)
                            val light = lerp(accent.color, Color.White, 0.5f)
                            drawRect(
                                Brush.linearGradient(
                                    0f to Color.Transparent, 0.5f to light.copy(alpha = 0.75f), 1f to Color.Transparent,
                                    start = Offset(x - band / 2f, 0f), end = Offset(x + band / 2f, size.height * 0.3f),
                                ),
                                all, area,
                                blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop,
                            )
                        }
                    }
                    NavAccent.Plain -> Unit
                }
            }
        }
}

/** Where the droplet is: [pos] in tabs, [half] its half width in tab slots, [inset] its top/bottom gap in px. */
/** Where a drag took the droplet: its offset from the finger, in tabs. */
private class NavGrab {
    var anchor = 0f
}

private data class DropletSpan(val pos: Float, val half: Float, val inset: Float)

/**
 * Centre x of the droplet over tab [pos], kept so the droplet never passes the bar edge:
 * at least [inset] (its gap at the top and bottom) from either side.
 */
internal fun dropletCenter(barWidth: Float, pad: Float, slot: Float, pos: Float, halfWidth: Float, inset: Float): Float {
    val cx = pad + slot * (pos + 0.5f)
    val lo = inset + halfWidth
    val hi = barWidth - inset - halfWidth
    return if (hi < lo) barWidth / 2f else cx.coerceIn(lo, hi)
}

/**
 * Magnet between tabs while dragging: near a tab the droplet is drawn in faster the
 * closer it gets and is caught on it; between tabs it follows the finger.
 */
internal fun tabMagnet(v: Float, reach: Float = 0.34f, capture: Float = 0.08f): Float {
    val stop = v.roundToInt().toFloat()
    val d = v - stop
    val x = kotlin.math.abs(d)
    if (x >= reach) return v
    if (x <= capture) return stop
    val u = (x - capture) / (reach - capture)
    return stop + kotlin.math.sign(d) * reach * kotlin.math.sqrt(u)
}

/**
 * Droplet under the selected tab. Moving fast it stretches like a drop of liquid,
 * then settles round. Glass mode: light glass body with a bright rim; plain mode: a soft tint.
 */
@Composable
private fun BoxScope.SelectionDroplet(count: Int, pos: Float, velocity: Float, dark: Boolean, glass: Boolean, corner: androidx.compose.ui.unit.Dp) {
    if (count <= 0) return
    val primary = MaterialTheme.colorScheme.primary
    Canvas(Modifier.matchParentSize()) {
        val pad = NavBarDefaults.TrackPadding.toPx()
        val slot = (size.width - pad * 2) / count
        val stretch = (kotlin.math.abs(velocity) * 0.10f).coerceAtMost(0.45f)
        val w = slot * (0.86f + stretch)
        val h = size.height - 10.dp.toPx() - stretch * 6.dp.toPx()
        val top = (size.height - h) / 2f
        val cx = dropletCenter(size.width, pad, slot, pos, w / 2f, top)
        val left = cx - w / 2f
        val r = CornerRadius(minOf(h / 2f, corner.toPx()))
        if (!glass) {
            drawRoundRect(primary.copy(alpha = if (dark) 0.18f else 0.10f), Offset(left, top), Size(w, h), r)
            return@Canvas
        }
        drawRoundRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = if (dark) 0.16f else 0.55f), Color.White.copy(alpha = if (dark) 0.06f else 0.22f)),
                startY = top, endY = top + h,
            ),
            Offset(left, top), Size(w, h), r,
        )
        drawRoundRect(primary.copy(alpha = if (dark) 0.16f else 0.10f), Offset(left, top), Size(w, h), r)
        drawRoundRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = if (dark) 0.55f else 0.95f), Color.White.copy(alpha = 0.05f), primary.copy(alpha = 0.35f)),
                startY = top, endY = top + h,
            ),
            Offset(left, top), Size(w, h), r,
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }
}

/**
 * The sheet of the profiles icon: a landscape page, nearly as wide as the folder, with
 * lines of text. It comes down from above the open folder and disappears behind its front
 * panel (top edge y = 8 of the 24-unit icon).
 */
@Composable
private fun PaperIntoFolder(progress: Float, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val t = ((progress - 0.06f) / 0.6f).coerceIn(0f, 1f)
        val eased = androidx.compose.animation.core.FastOutSlowInEasing.transform(t)
        val top = (-7f + 16f * eased) * u
        val appear = ((progress - 0.04f) / 0.08f).coerceIn(0f, 1f)
        // Only the part above the front panel shows: below it the sheet is inside the folder.
        clipRect(left = -4f * u, top = -12f * u, right = size.width + 4f * u, bottom = 8f * u) {
            val left = 4.5f * u
            val w = 15f * u
            val h = 7.5f * u
            drawRoundRect(
                tint, Offset(left, top), Size(w, h), CornerRadius(1.2f * u),
                alpha = appear, style = Stroke(width = 1.7f * u),
            )
            // Two lines of text, the second one shorter.
            for (i in 0..1) {
                val y = top + (2.7f + 2.2f * i) * u
                val end = left + w - (if (i == 0) 2.6f else 6f) * u
                drawLine(
                    tint, Offset(left + 2.6f * u, y), Offset(end, y),
                    strokeWidth = 1.3f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, alpha = appear,
                )
            }
        }
    }
}

/**
 * Filter glyph (three centred lines, widest on top) whose lines narrow in turn from
 * top to bottom and spring back: things passing through the filter.
 */
@Composable
private fun SiftIcon(progress: Float, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val widths = floatArrayOf(18f, 12f, 6f)
        val ys = floatArrayOf(7f, 12f, 17f)
        for (i in 0..2) {
            // Each line has its own window inside the motion, staggered by 0.18.
            val local = ((progress - i * 0.18f) / 0.64f).coerceIn(0f, 1f)
            val squeeze = tabIconSettle(local)
            val w = widths[i] * (1f - 0.55f * squeeze) * u
            val cx = size.width / 2f
            drawLine(
                tint,
                Offset(cx - w / 2f, ys[i] * u), Offset(cx + w / 2f, ys[i] * u),
                strokeWidth = 2f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun NavBarTab(
    index: Int,
    count: Int,
    droplet: () -> DropletSpan,
    accent: NavAccent,
    accentClock: () -> Float,
    item: ArdttNavItem,
    selected: Boolean,
    pending: Boolean,
    emphasis: Float,
    selectedColor: Color,
    unselectedColor: Color,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    /** New Year: the tab is a figure of Santa's train, posed by this. */
    sleigh: com.wdtt.client.ui.festive.SleighMotion? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    val iconMotion = tabIconMotionFor(item.route)
    val iconProgress = remember { Animatable(0f) }
    var iconPlay by remember { mutableIntStateOf(0) }
    LaunchedEffect(iconPlay) {
        if (iconPlay == 0) return@LaunchedEffect
        iconProgress.snapTo(0f)
        iconProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = ArdttMotion.TabIcon,
                easing = LinearEasing,
            ),
        )
    }
    val iconPose = tabIconPose(iconMotion, iconProgress.value)
    // New Year: the tabs are Santa's train (see SleighTrain), joined across the gaps, in
    // their own colours under the droplet instead of the accent colour.
    val train = sleigh != null
    // Over clear glass the page shows right behind the tab: a halo follows the outline of
    // the icon and the label, and the faded grey of the other tabs turns full-strength, so
    // they stand apart from the text of the page.
    val glass = LocalGlassBackdrop.current
    val legible = if (glass != null && LiquidGlass.supported) glass.legibility() else 0f
    // The glyphs are drawn in the plain colour; the accent colour is laid over them only
    // inside the droplet (see navTint), so it moves exactly with the droplet.
    val baseColor = lerp(unselectedColor, MaterialTheme.colorScheme.onSurface, legible)
    val paint = navTabPaint(
        selected = false,
        pending = false,
        pressed = false,
        emphasis = emphasis,
        selectedColor = selectedColor,
        unselectedColor = baseColor,
    ).let { it.copy(color = baseColor, labelAlpha = it.labelAlpha + (1f - it.labelAlpha) * legible) }
    val dark = isDarkSurface()
    // Light theme: the page colour. Dark: a quiet grey a little above the page, so the
    // border separates the glyphs without reading as a black outline.
    val haloColor = if (dark) lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onBackground, 0.12f)
        else MaterialTheme.colorScheme.background
    // A thin border, never inside the droplet (see haloOutsideDroplet).
    val haloPx = with(androidx.compose.ui.platform.LocalDensity.current) { 1.8.dp.toPx() }
    val haloStrength = (legible * 20f).roundToInt() / 20f
    val halo = remember(haloStrength, haloColor, haloPx) {
        if (haloStrength > 0.01f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            com.wdtt.client.ui.components.surface.glyphHalo(haloColor, haloPx, haloStrength)
        } else null
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .haloOutsideDroplet(halo, index, count, droplet)
            .navTint(index, count, droplet, selectedColor, accent, clock = accentClock, recolour = !train)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = {
                    // Liquid glass: the touch and the release of the bar already vibrate.
                    if (!selected && !(glass != null && LiquidGlass.supported)) haptics.perform(com.wdtt.client.ui.motion.Haptic.Click)
                    // Liquid glass: the icons stay still (their motion under the lens looked busy).
                    if (!iconProgress.isRunning && !(glass != null && LiquidGlass.supported)) iconPlay++
                    onSelect()
                },
            )
            .semantics { this.selected = selected }
            .drawBehind {
                if (sleigh != null) {
                    // Drawn again whenever this figure moves.
                    sleigh.frames[index].intValue
                    val (l, r) = dropletRangeInTab(droplet(), index, count, size.width, NavBarDefaults.TrackPadding.toPx())
                    drawSleighTab(index, size.width, size.height, SleighTrain.height.toPx(), paint.color, l, r, sleigh.pose)
                }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (train) {
            // Drawn behind (see above), in place of the icon and the label; the label is read out.
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(SleighTrain.height)
                    .semantics { contentDescription = item.label },
            )
            return@Column
        }
        Box(contentAlignment = Alignment.TopEnd) {
            val glyph = Modifier.size(ArdttSize.Icon)
            if (iconMotion == TabIconMotion.Sift) {
                SiftIcon(iconProgress.value, paint.color, glyph)
            } else if (iconMotion == TabIconMotion.FolderOpen) {
                // A sheet of paper is put into the folder: the folder opens, the sheet comes
                // down from above and goes in behind its front, the folder closes.
                val p = iconProgress.value
                val open = p in 0.08f..0.74f
                Box(
                    glyph.graphicsLayer {
                        scaleX = iconPose.scale
                        scaleY = iconPose.scale
                    },
                ) {
                    Icon(
                        imageVector = if (open) Icons.Outlined.FolderOpen else item.icon,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        tint = paint.color,
                    )
                    if (p in 0.04f..0.74f) PaperIntoFolder(p, paint.color, Modifier.matchParentSize())
                }
            } else if (iconMotion == TabIconMotion.ServerLights) {
                ArdttServersTabIcon(
                    lightAlpha = tabIconServerLightAlpha(iconProgress.value),
                    tint = paint.color,
                    modifier = glyph,
                )
            } else {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = if (iconMotion == TabIconMotion.KeyTurn) {
                        glyph.tunnelKeyFlip(iconPose.rotationX)
                    } else {
                        glyph.graphicsLayer {
                            rotationZ = iconPose.rotationZ
                            translationX = iconPose.translationXFraction * size.width
                            translationY = iconPose.translationYFraction * size.height
                            scaleX = iconPose.scale
                            scaleY = iconPose.scale
                        }
                    },
                    tint = paint.color,
                )
            }
            if (item.dot && item.badgeCount == 0) {
                Badge(modifier = Modifier.offset(x = ArdttSpacing.Tiny, y = -ArdttSpacing.Tiny))
            }
            if (item.badgeCount > 0) {
                Badge(
                    modifier = Modifier.offset(
                        x = ArdttSpacing.Medium,
                        y = -ArdttSpacing.Small,
                    ),
                ) {
                    Text("${item.badgeCount.coerceAtMost(NavBarDefaults.MaxBadgeCount)}")
                }
            }
        }
        Spacer(modifier = Modifier.height(ArdttSpacing.Hairline))
        // One line; with a large system font the label shrinks instead of breaking mid-word.
        // Under the glass droplet the label is magnified; it is kept so narrow that even
        // magnified it stays inside the droplet's flat middle and is never bent by the rim.
        val labelFit = if (glass != null && LiquidGlass.supported) {
            Modifier.layout { measurable, constraints ->
                val rim = NavDropletBezel.toPx() + NavDropletEndCap.toPx() + 1.dp.toPx()
                val maxW = ((constraints.maxWidth * NavDropletWidth - 2f * rim) / (1f + NavDropletZoom)).roundToInt()
                    .coerceIn(0, constraints.maxWidth)
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = maxW))
                layout(constraints.maxWidth, placeable.height) {
                    placeable.place((constraints.maxWidth - placeable.width) / 2, 0)
                }
            }
        } else Modifier.fillMaxWidth()
        com.wdtt.client.ui.components.control.FitText(
            text = item.label,
            modifier = labelFit,
            style = ArdttNavigationLabelStyle.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            fontWeight = if (paint.bold) FontWeight.SemiBold else FontWeight.Normal,
            color = paint.color.copy(alpha = paint.labelAlpha),
            minScale = 0.55f,
        )
    }
}

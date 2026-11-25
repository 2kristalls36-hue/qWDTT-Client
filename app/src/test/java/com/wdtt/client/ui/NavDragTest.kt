package com.wdtt.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.foundation.background
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import com.wdtt.client.ui.components.layout.ArdttNavItem
import com.wdtt.client.ui.components.layout.ArdttNavigationBar
import com.wdtt.client.ui.components.layout.tabMagnet
import com.wdtt.client.ui.motion.Haptic
import com.wdtt.client.ui.theme.ArdttTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h300dp-xhdpi")
class NavDragTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dragAcrossTabsSelectsWhereReleased() {
        var route by mutableStateOf("a")
        compose.setContent {
            ArdttTheme {
                Box(Modifier.fillMaxWidth()) {
                    ArdttNavigationBar(
                        items = listOf("a", "b", "c", "d").map { ArdttNavItem(it, "Tab $it", Icons.Outlined.Home) },
                        selectedRoute = route,
                        onSelect = { route = it },
                    )
                }
            }
        }
        val a = compose.onNodeWithText("Tab a").fetchSemanticsNode().boundsInRoot.center
        val c = compose.onNodeWithText("Tab c").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { swipe(Offset(a.x, a.y), Offset(c.x + 10f, c.y), durationMillis = 400) }
        compose.waitForIdle()
        assertEquals("c", route)
    }

    @Test fun dragBackAfterSwitchingTabs() {
        // Regression: tap to the second tab, then drag back to the first one.
        var route by mutableStateOf("a")
        compose.setContent {
            ArdttTheme {
                Box(Modifier.fillMaxWidth()) {
                    ArdttNavigationBar(
                        items = listOf("a", "b", "c", "d").map { ArdttNavItem(it, "Tab $it", Icons.Outlined.Home) },
                        selectedRoute = route,
                        onSelect = { route = it },
                    )
                }
            }
        }
        compose.onNodeWithText("Tab b").performClick()
        compose.waitForIdle()
        assertEquals("b", route)
        val a = compose.onNodeWithText("Tab a").fetchSemanticsNode().boundsInRoot.center
        val b = compose.onNodeWithText("Tab b").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { swipe(Offset(b.x, b.y), Offset(a.x - 10f, a.y), durationMillis = 400) }
        compose.waitForIdle()
        assertEquals("a", route)
    }

    @Test fun dragVibratesOnceWhenTakenAndOnceWhenLetGo() {
        // Without glass: one vibration when the drag starts, one on release; none on the
        // tabs the droplet passes.
        val felt = mutableListOf<com.wdtt.client.ui.motion.Haptic>()
        val haptics = com.wdtt.client.ui.motion.AppHaptics(null, { felt += it }) { true }
        var route by mutableStateOf("a")
        compose.setContent {
            ArdttTheme {
                androidx.compose.runtime.CompositionLocalProvider(com.wdtt.client.ui.motion.LocalAppHaptics provides haptics) {
                    Box(Modifier.fillMaxWidth()) {
                        ArdttNavigationBar(
                            items = listOf("a", "b", "c", "d").map { ArdttNavItem(it, "Tab $it", Icons.Outlined.Home) },
                            selectedRoute = route,
                            onSelect = { route = it },
                        )
                    }
                }
            }
        }
        val a = compose.onNodeWithText("Tab a").fetchSemanticsNode().boundsInRoot.center
        val c = compose.onNodeWithText("Tab c").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(a)
            val steps = 40
            for (i in 1..steps) moveTo(Offset(a.x + (c.x - a.x) * i / steps, a.y))
            up()
        }
        compose.waitForIdle()
        assertEquals("c", route)
        assertEquals(listOf(Haptic.Grab, Haptic.Drop), felt)
    }

    private fun glassBar(felt: MutableList<Haptic>, onPosition: (Float) -> Unit = {}, onRoute: (String) -> Unit) {
        val haptics = com.wdtt.client.ui.motion.AppHaptics(null, { felt += it }) { true }
        // The glass is animated without end: the clock is moved by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ArdttTheme {
                var route by androidx.compose.runtime.remember { mutableStateOf("a") }
                val glass = com.wdtt.client.ui.components.surface.GlassBackdrop(androidx.compose.ui.graphics.rememberGraphicsLayer())
                                androidx.compose.runtime.CompositionLocalProvider(
                    com.wdtt.client.ui.motion.LocalAppHaptics provides haptics,
                    com.wdtt.client.ui.components.surface.LocalGlassBackdrop provides glass,
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        ArdttNavigationBar(
                            items = listOf("a", "b", "c", "d").map { ArdttNavItem(it, "Tab $it", Icons.Outlined.Home) },
                            selectedRoute = route,
                            onSelect = { route = it; onRoute(it) },
                            onPosition = onPosition,
                        )
                    }
                }
            }
        }
    }

    @Test fun glassDropletVibratesOnTouchAndOnRelease() {
        // Liquid glass: one vibration when the droplet swells under the finger and one when
        // it is let go; none on the tabs it passes, no click when it lands.
        val felt = mutableListOf<com.wdtt.client.ui.motion.Haptic>()
        var route = "a"
        glassBar(felt) { route = it }
        val a = compose.onNodeWithText("Tab a").fetchSemanticsNode().boundsInRoot.center
        val c = compose.onNodeWithText("Tab c").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(a)
            val steps = 40
            for (i in 1..steps) moveTo(Offset(a.x + (c.x - a.x) * i / steps, a.y))
            up()
        }
        compose.mainClock.advanceTimeBy(2000)
        assertEquals("c", route)
        assertEquals(listOf(Haptic.Grab, Haptic.Drop), felt)
    }

    @Test fun glassTapVibratesOnTouchAndOnRelease() {
        val felt = mutableListOf<com.wdtt.client.ui.motion.Haptic>()
        var route = "a"
        glassBar(felt) { route = it }
        compose.onNodeWithText("Tab c").performClick()
        compose.mainClock.advanceTimeBy(2000)
        assertEquals("c", route)
        assertEquals(listOf(Haptic.Grab, Haptic.Drop), felt)
    }

    @Test fun glassPressSlidOffTheBarPutsTheDropletBack() {
        // The droplet goes under the finger on touch; a press that ends off the bar does not
        // select the tab, and the droplet returns to the current one.
        val felt = mutableListOf<Haptic>()
        var route = "a"
        var pos = 0f
        glassBar(felt, onPosition = { pos = it }) { route = it }
        val c = compose.onNodeWithText("Tab c").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(c)
            for (i in 1..20) moveTo(Offset(c.x, c.y - 30f * i))
            up()
        }
        compose.mainClock.advanceTimeBy(2000)
        assertEquals("a", route)
        assertEquals(0f, pos, 0.001f)
    }

    @Test fun dropletTakenOffCentreDoesNotJumpToTheFinger() {
        // Regression: the droplet on the first tab taken right of its centre and pulled
        // left jumped right for a moment (and the screens with it), then came back.
        var pos = 0f
        var maxPos = 0f
        compose.setContent {
            ArdttTheme {
                var route by androidx.compose.runtime.remember { mutableStateOf("a") }
                Box(Modifier.fillMaxWidth()) {
                    ArdttNavigationBar(
                        items = listOf("a", "b", "c", "d").map { ArdttNavItem(it, "Tab $it", Icons.Outlined.Home) },
                        selectedRoute = route,
                        onSelect = { route = it },
                        onPosition = { pos = it; if (it > maxPos) maxPos = it },
                    )
                }
            }
        }
        val a = compose.onNodeWithText("Tab a").fetchSemanticsNode().boundsInRoot
        val start = Offset(a.center.x + a.width * 0.35f, a.center.y)
        compose.onRoot().performTouchInput {
            down(start)
            for (i in 1..20) moveTo(Offset(start.x - 12f * i, start.y))
            up()
        }
        compose.waitForIdle()
        assertEquals(0f, pos, 0.001f)
        assertEquals(0f, maxPos, 0.001f)
    }

    @Test fun dropletStaysInsideTheBar() {
        val bar = 400f
        val pad = 8f
        val slot = (bar - pad * 2) / 4
        // Swollen droplet over the last tab: its edge stays the inset away from the bar edge.
        val half = slot * 0.61f
        val cx = com.wdtt.client.ui.components.layout.dropletCenter(bar, pad, slot, 3f, half, 6f)
        assertEquals(bar - 6f, cx + half, 0.01f)
        val cx0 = com.wdtt.client.ui.components.layout.dropletCenter(bar, pad, slot, 0f, half, 6f)
        assertEquals(6f, cx0 - half, 0.01f)
        // In the middle it is not moved.
        assertEquals(pad + slot * 1.5f, com.wdtt.client.ui.components.layout.dropletCenter(bar, pad, slot, 1f, half, 6f), 0.01f)
    }

    @Test fun magnetBetweenTabs() {
        assertEquals(2f, tabMagnet(2.05f), 1e-6f)
        assertEquals(1.5f, tabMagnet(1.5f), 1e-6f)
        val far = tabMagnet(1.62f) - tabMagnet(1.6f)
        val near = tabMagnet(1.88f) - tabMagnet(1.86f)
        assert(near > far)
    }

    private fun lensShot(theme: String, clear: Float = 0.5f) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ArdttTheme(themeMode = theme) {
                val glass = com.wdtt.client.ui.components.surface.GlassBackdrop(androidx.compose.ui.graphics.rememberGraphicsLayer())
                glass.transparency = clear
                androidx.compose.runtime.CompositionLocalProvider(com.wdtt.client.ui.components.surface.LocalGlassBackdrop provides glass) {
                    Box(Modifier.fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                        ArdttNavigationBar(
                            items = listOf(
                                ArdttNavItem("tunnel", "Главная", Icons.Outlined.Home),
                                ArdttNavItem("profiles", "Профили", Icons.Outlined.Home),
                                ArdttNavItem("exceptions", "Приложения", Icons.Outlined.Home),
                                ArdttNavItem("settings", "Настройки", Icons.Outlined.Home),
                            ),
                            selectedRoute = "tunnel",
                            onSelect = {},
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tab_lens_rest_$theme${if (clear > 0.5f) "_clear" else ""}.png")
        val a = compose.onNodeWithText("Главная").fetchSemanticsNode().boundsInRoot.center
        val b = compose.onNodeWithText("Профили").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(Offset(a.x, a.y))
            moveTo(Offset(a.x + 40f, a.y)); moveTo(Offset((a.x + b.x) / 2f, a.y))
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tab_lens_mid_$theme${if (clear > 0.5f) "_clear" else ""}.png")
        compose.onRoot().performTouchInput { moveTo(Offset((a.x + b.x) / 2f + 30f, a.y)); moveTo(Offset(b.x - 10f, a.y)) }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tab_lens_$theme${if (clear > 0.5f) "_clear" else ""}.png")
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(800)
    }

    @Test fun tabLensLight() = lensShot("light")
    @Test fun tabLensDark() = lensShot("dark")
    @Test fun tabLensClearDark() = lensShot("dark", clear = 1f)
    @Test fun tabLensClearLight() = lensShot("light", clear = 1f)
}

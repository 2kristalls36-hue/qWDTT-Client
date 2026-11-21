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
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import com.wdtt.client.ui.components.layout.ArdttNavItem
import com.wdtt.client.ui.components.layout.ArdttNavigationBar
import com.wdtt.client.ui.components.layout.tabMagnet
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

    @Test fun magnetBetweenTabs() {
        assertEquals(2f, tabMagnet(2.05f), 1e-6f)
        assertEquals(1.5f, tabMagnet(1.5f), 1e-6f)
        val far = tabMagnet(1.62f) - tabMagnet(1.6f)
        val near = tabMagnet(1.88f) - tabMagnet(1.86f)
        assert(near > far)
    }

    private fun lensShot(theme: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ArdttTheme(themeMode = theme) {
                val glass = com.wdtt.client.ui.components.surface.GlassBackdrop(androidx.compose.ui.graphics.rememberGraphicsLayer())
                androidx.compose.runtime.CompositionLocalProvider(com.wdtt.client.ui.components.surface.LocalGlassBackdrop provides glass) {
                    Box(Modifier.fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                        ArdttNavigationBar(
                            items = listOf(
                                ArdttNavItem("tunnel", "Главная", Icons.Outlined.Home, glyph = com.wdtt.client.ui.components.layout.LiteTabIcons.Power),
                                ArdttNavItem("profiles", "Профили", Icons.Outlined.Home, glyph = com.wdtt.client.ui.components.layout.LiteTabIcons.Cards),
                                ArdttNavItem("exceptions", "Приложения", Icons.Outlined.Home, glyph = com.wdtt.client.ui.components.layout.LiteTabIcons.Tiles),
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
        val a = compose.onNodeWithText("Главная").fetchSemanticsNode().boundsInRoot.center
        val b = compose.onNodeWithText("Профили").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(Offset(a.x, a.y))
            moveTo(Offset(a.x + 40f, a.y)); moveTo(Offset((a.x + b.x) / 2f + 30f, a.y)); moveTo(Offset(b.x - 10f, a.y))
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tab_lens_$theme.png")
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(800)
    }

    @Test fun tabLensLight() = lensShot("light")
    @Test fun tabLensDark() = lensShot("dark")
}

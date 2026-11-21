package com.wdtt.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
}

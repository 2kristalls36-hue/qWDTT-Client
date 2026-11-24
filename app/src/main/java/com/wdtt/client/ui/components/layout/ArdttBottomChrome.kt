package com.wdtt.client.ui.components.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wdtt.client.ui.theme.ArdttSize
import com.wdtt.client.ui.theme.ArdttSpacing

/**
 * Bottom-edge geometry shared by the floating tab pill and every sticky CTA.
 * Content scrolls edge-to-edge underneath both.
 */
object ArdttBottomChrome {
    /** Matches the floating [ArdttNavigationBar] overlay height. */
    val NavZoneHeight: Dp = ArdttSize.NavZone

    /** Air between a sticky CTA and the tab pill. */
    val StickyGap: Dp = ArdttSpacing.TinyPlus

    val ButtonHeight: Dp = ArdttSize.Button

    /**
     * Space the tab pill occupies, system navigation insets included. On a screen the
     * bar is fitted to (gesture navigation, known corners) it sits lower, over the
     * gesture area, and takes only its own height and the gap below it.
     */
    @Composable
    fun navigationReserve(): Dp {
        val screen = LocalScreenShape.current
        return if (screen.gestureNav && screen.bottomCorner > 0.dp) {
            ArdttSize.NavTrack + ArdttSpacing.Small + ArdttSpacing.SmallPlus
        } else {
            NavZoneHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        }
    }

    /** Bottom offset of a sticky CTA sitting above the tab pill. */
    @Composable
    fun stickyBottomPadding(): Dp = navigationReserve() + StickyGap

    /** Feed padding so the last item can pass under a sticky CTA. */
    @Composable
    fun scrollContentPadding(extra: Dp = ArdttSpacing.Large): Dp =
        stickyBottomPadding() + ButtonHeight + extra
}

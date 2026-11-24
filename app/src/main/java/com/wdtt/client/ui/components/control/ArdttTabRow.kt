package com.wdtt.client.ui.components.control

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One tab: label and icon, with an optional count after the label. */
data class ArdttTab(val label: String, val icon: ImageVector, val count: Int? = null)

/**
 * Tabs of a screen section: the current one is in the accent colour with a line under
 * it that slides over when another tab is chosen; a thin rule runs under the whole row.
 */
@Composable
fun ArdttTabRow(
    tabs: List<ArdttTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val slot = maxWidth / tabs.size
        val indicatorX by animateDpAsState(
            slot * selected,
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            label = "tabIndicator",
        )
        Row(Modifier.fillMaxWidth().height(48.dp).selectableGroup()) {
            tabs.forEachIndexed { i, tab ->
                val on = i == selected
                val tint by animateColorAsState(if (on) colors.primary else colors.onSurfaceVariant, tween(220), label = "tabTint")
                Row(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .selectable(
                            selected = on,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                if (!on) haptics.perform(com.wdtt.client.ui.motion.Haptic.Tick)
                                onSelect(i)
                            },
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = tint,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    // The count rolls up or down when it changes.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = (tab.count ?: 0) > 0,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.6f),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.6f),
                    ) {
                        androidx.compose.animation.AnimatedContent(
                            targetState = tab.count ?: 0,
                            transitionSpec = {
                                val up = targetState > initialState
                                (androidx.compose.animation.slideInVertically { h -> if (up) h else -h } + androidx.compose.animation.fadeIn()) togetherWith
                                    (androidx.compose.animation.slideOutVertically { h -> if (up) -h else h } + androidx.compose.animation.fadeOut())
                            },
                            label = "tabCount",
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .background(tint.copy(alpha = 0.12f), RoundedCornerShape(50))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) { n ->
                            Text(
                                n.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = tint,
                            )
                        }
                    }
                }
            }
        }
        // Rule under the row and the sliding line of the current tab.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outlineVariant.copy(alpha = 0.7f)),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = indicatorX + slot * 0.2f)
                .width(slot * 0.6f)
                .height(3.dp)
                .background(colors.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
        )
    }
}

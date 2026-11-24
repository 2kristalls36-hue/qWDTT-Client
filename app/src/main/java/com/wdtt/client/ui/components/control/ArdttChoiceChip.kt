package com.wdtt.client.ui.components.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.wdtt.client.ui.theme.ArdttAlpha
import com.wdtt.client.ui.theme.ArdttLayout
import com.wdtt.client.ui.theme.ArdttShapes
import com.wdtt.client.ui.theme.ArdttSize
import com.wdtt.client.ui.theme.ArdttSpacing
import com.wdtt.client.ui.theme.ArdttSurface
import com.wdtt.client.ui.theme.isDarkSurface
import com.wdtt.client.ui.theme.selectedControlContainer

/** One option of a segmented control. */
data class ArdttChoice<T>(
    val value: T,
    val label: String,
    /** Filled accent for the selected state; null keeps the neutral primary fill. */
    val accent: Color? = null,
    /** Dim the option without disabling it (missing prerequisite, not forbidden). */
    val dimmed: Boolean = false,
    /** Runs instead of `onSelect` when the option cannot be applied yet. */
    val onBlocked: (() -> Unit)? = null,
    /** A count shown after the label (how many items the option holds); null — none. */
    val count: Int? = null,
)

private object ChoiceChipDefaults {
    /** Selected fill alpha over dark chrome. */
    const val SelectedFillAlphaDark = ArdttAlpha.Outline
    const val SelectedBorderAlphaDark = 0.35f
    const val SelectedBorderAlphaLight = 0.25f
    const val UnselectedBorderAlpha = 0.45f
    const val DimmedBorderAlpha = ArdttAlpha.Outline
    val ContentPadding = PaddingValues(horizontal = ArdttSpacing.Medium)
}

/**
 * Full-width row of mutually exclusive chips.
 *
 * Replaces the four hand-written chip rows (path mode, hide IP, dial path,
 * theme mode) that each repeated the same `Row` + `weight(1f)` scaffolding.
 */
@Composable
fun <T> ArdttChoiceChipRow(
    choices: List<ArdttChoice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    chipHeight: Dp = ArdttSize.Chip,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ArdttLayout.ControlSpacing),
    ) {
        choices.forEach { choice ->
            ArdttChoiceChip(
                label = choice.label,
                selected = choice.value == selected,
                enabled = enabled,
                onClick = {
                    val blocked = choice.onBlocked
                    if (blocked != null) blocked() else onSelect(choice.value)
                },
                modifier = Modifier.weight(1f),
                selectedContainer = choice.accent,
                dimmed = choice.dimmed,
                height = chipHeight,
                count = choice.count,
            )
        }
    }
}

@Composable
fun ArdttChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedContainer: Color? = null,
    dimmed: Boolean = false,
    minWidth: Dp? = null,
    height: Dp = ArdttSize.Chip,
    count: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    val tapped = { if (!selected) haptics.perform(com.wdtt.client.ui.motion.Haptic.Tick); onClick() }
    // Announced as one option of a group («выбрано» / «не выбрано»), not as a
    // plain button whose state the user has to infer from the fill.
    val sizeModifier = modifier
        .semantics {
            role = Role.RadioButton
            this.selected = selected
        }
        .height(height)
        .then(if (minWidth != null) Modifier.widthIn(min = minWidth) else Modifier)

    if (!selected) {
        OutlinedButton(
            onClick = tapped,
            enabled = enabled,
            modifier = sizeModifier,
            shape = ArdttShapes.Chip,
            border = BorderStroke(
                ArdttSize.Border,
                colors.outline.copy(
                    alpha = if (dimmed) {
                        ChoiceChipDefaults.DimmedBorderAlpha
                    } else {
                        ChoiceChipDefaults.UnselectedBorderAlpha
                    },
                ),
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (dimmed) {
                    colors.onSurface.copy(alpha = ArdttAlpha.Disabled)
                } else {
                    colors.onSurface
                },
            ),
            contentPadding = ChoiceChipDefaults.ContentPadding,
        ) {
            ChipLabel(label, count, FontWeight.Medium, Color.Unspecified)
        }
        return
    }

    val dark = isDarkSurface()
    // Accent fills come from the caller (path colors); the label color is
    // derived from the actual fill, never assumed white.
    val accentContent = selectedContainer?.let { ArdttSurface.contentColorOn(it) }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = sizeModifier,
        shape = ArdttShapes.Chip,
        colors = if (selectedContainer != null && accentContent != null) {
            ButtonDefaults.buttonColors(
                containerColor = selectedContainer,
                contentColor = accentContent,
                disabledContainerColor = selectedContainer.copy(alpha = ArdttAlpha.DisabledContainer),
                disabledContentColor = accentContent.copy(alpha = ArdttAlpha.Subtle),
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = selectedControlContainer(
                    darkAlpha = ChoiceChipDefaults.SelectedFillAlphaDark,
                ),
                contentColor = colors.primary,
            )
        },
        border = if (selectedContainer == null) {
            BorderStroke(
                ArdttSize.Border,
                colors.primary.copy(
                    alpha = if (dark) {
                        ChoiceChipDefaults.SelectedBorderAlphaDark
                    } else {
                        ChoiceChipDefaults.SelectedBorderAlphaLight
                    },
                ),
            )
        } else {
            null
        },
        contentPadding = ChoiceChipDefaults.ContentPadding,
    ) {
        ChipLabel(
            label,
            count,
            FontWeight.SemiBold,
            if (dimmed) (accentContent ?: colors.primary).copy(alpha = ArdttAlpha.Subtle) else Color.Unspecified,
        )
    }
}

/** Chip label with an optional count in a small pill after it; the count rolls when it changes. */
@Composable
private fun ChipLabel(label: String, count: Int?, weight: FontWeight, color: Color) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        FitText(label, fontWeight = weight, color = color, modifier = Modifier.weight(1f, fill = false))
        androidx.compose.animation.AnimatedVisibility(
            visible = (count ?: 0) > 0,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally(),
        ) {
            val content = androidx.compose.material3.LocalContentColor.current
            val tint = if (color == Color.Unspecified) content else color
            androidx.compose.animation.AnimatedContent(
                targetState = count ?: 0,
                transitionSpec = {
                    val up = targetState > initialState
                    (androidx.compose.animation.slideInVertically { h -> if (up) h else -h } + androidx.compose.animation.fadeIn())
                        .togetherWith(androidx.compose.animation.slideOutVertically { h -> if (up) -h else h } + androidx.compose.animation.fadeOut())
                },
                label = "chipCount",
                modifier = Modifier
                    .padding(start = 6.dp)
                    .background(tint.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) { n ->
                Text(n.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = tint)
            }
        }
    }
}

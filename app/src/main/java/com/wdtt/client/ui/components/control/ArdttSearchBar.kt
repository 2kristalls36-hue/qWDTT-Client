package com.wdtt.client.ui.components.control

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.wdtt.client.ui.components.layout.ArdttBottomChrome
import com.wdtt.client.ui.components.surface.ArdttFloatingShell
import com.wdtt.client.ui.theme.ArdttAlpha
import com.wdtt.client.ui.theme.ArdttElevation
import com.wdtt.client.ui.theme.ArdttShapes
import com.wdtt.client.ui.theme.ArdttSize
import com.wdtt.client.ui.theme.ArdttSpacing

/**
 * Floating search field that sits above the tab pill (as in ARDTT): translucent shell
 * while idle, solid surface while the keyboard is open.
 */
@Composable
fun ArdttSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardVisible: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "Поиск",
) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(ArdttBottomChrome.ButtonHeight)
            .semantics { contentDescription = "Поиск" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                focusRequester.requestFocus()
                keyboard?.show()
            },
        shape = ArdttShapes.Control,
        color = if (keyboardVisible) colors.surface else ArdttFloatingShell.shellColor(),
        border = ArdttFloatingShell.shellBorder(),
        shadowElevation = ArdttElevation.None,
        tonalElevation = ArdttElevation.None,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = ArdttSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(ArdttSize.Icon), tint = colors.onSurfaceVariant)
            Spacer(Modifier.width(ArdttSpacing.Medium))
            val style = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f).background(Color.Transparent).focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = style.copy(color = colors.onSurfaceVariant.copy(alpha = ArdttAlpha.Subtle)))
                        }
                        inner()
                    }
                },
            )
            if (value.isNotEmpty()) {
                ArdttButton(
                    onClick = { onValueChange("") },
                    variant = ArdttButtonVariant.Icon,
                    icon = Icons.Filled.Close,
                    contentDescription = "Очистить",
                    contentColor = colors.onSurfaceVariant,
                )
            }
        }
    }
}

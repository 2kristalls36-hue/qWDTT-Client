package com.wdtt.client.ui.home

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wdtt.client.BuildConfig
import com.wdtt.client.ui.theme.Brand
import com.wdtt.client.ui.theme.BrandWordmark

/** Where the app's source code, the project it is built on and its licence are. */
internal object AboutLinks {
    const val SOURCE = "https://github.com/2kristalls36-hue/qWDTT-Client"
    const val QWDTT = "https://github.com/SpaceNeuroX/proxy-turn-vk-android"
    const val LICENCE = "https://github.com/2kristalls36-hue/qWDTT-Client/blob/main/LICENSE"
    /** The page of voluntary support (CloudTips); empty — the «Поддержка» section is not shown. */
    const val DONATE = "https://pay.cloudtips.ru/p/7edaf342"
}

/**
 * «О приложении», opened by a long press on the logo of the main screen: a card in the
 * middle of the screen with the logo in white on the neon of the app icon, the version,
 * what the app is for and links to its source code, to qWDTT it is built on and to its
 * licence. It comes in with a short rise and goes with a tap beside it or «Назад».
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    val scheme = MaterialTheme.colorScheme
    val open: (String) -> Unit = { url ->
        haptics.perform(com.wdtt.client.ui.motion.Haptic.Click)
        runCatching { uriHandler.openUri(url) }
            .onFailure { Toast.makeText(context, "Не удалось открыть ссылку.", Toast.LENGTH_SHORT).show() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val rise by animateFloatAsState(
            if (shown) 1f else 0f,
            spring(dampingRatio = 0.82f, stiffness = 420f),
            label = "about",
        )
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = rise.coerceIn(0f, 1f)
                    val s = 0.94f + 0.06f * rise
                    scaleX = s
                    scaleY = s
                    translationY = (1f - rise) * 16.dp.toPx()
                }
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surface)
                // With a large font on a short screen the card scrolls instead of being cut.
                .verticalScroll(rememberScrollState())
                .semantics { paneTitle = "О приложении" },
        ) {
            // The app icon opened out: the white logo on the still neon of its ground.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .background(Brush.linearGradient(Brand.IconNeon)),
                contentAlignment = Alignment.Center,
            ) {
                BrandWordmark(
                    Modifier
                        .size(width = (64f * Brand.LOGO_WIDTH / Brand.LOGO_HEIGHT).dp, height = 64.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(Brand.White, blendMode = BlendMode.SrcIn)
                        }
                        .semantics { contentDescription = "qWDTT Client" },
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Версия ${BuildConfig.VERSION_NAME}",
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    "Приложение для подключения к серверу qWDTT по подписке.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.surfaceVariant),
                ) {
                    AboutLink(Icons.Outlined.Code, "Исходный код", "github.com/2kristalls36-hue/qWDTT-Client") { open(AboutLinks.SOURCE) }
                    AboutDivider()
                    AboutLink(Icons.Outlined.Handshake, "Основано на qWDTT", "github.com/SpaceNeuroX/proxy-turn-vk-android") { open(AboutLinks.QWDTT) }
                    AboutDivider()
                    AboutLink(Icons.Outlined.Gavel, "Лицензия", "GNU GPL v3") { open(AboutLinks.LICENCE) }
                    if (AboutLinks.DONATE.isNotBlank()) {
                        AboutDivider()
                        AboutLink(Icons.Outlined.LocalCafe, SupportText.TITLE, "Угостить автора чашкой кофе") { open(AboutLinks.DONATE) }
                    }
                }
            }
        }
    }
}

/** One link: its sign in a round plate, what it is, where it leads, the «opens outside» mark. */
@Composable
private fun AboutLink(icon: ImageVector, title: String, address: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Открыть ссылку", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(scheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurface)
            Text(
                address,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = scheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(
        Modifier.padding(start = 62.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
    )
}

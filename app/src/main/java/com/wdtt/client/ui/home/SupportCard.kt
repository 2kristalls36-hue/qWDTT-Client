package com.wdtt.client.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.wdtt.client.ui.components.surface.ArdttMessageCard
import com.wdtt.client.ui.theme.ArdttColors
import com.wdtt.client.ui.theme.isDarkSurface

/** The words of the voluntary support, the same in the settings and in «О приложении». */
internal object SupportText {
    const val TITLE = "Поддержка автора"
    const val BODY = "qWDTT Client бесплатный и развивается в свободное время.\n" +
        "Если приложение пригодилось, автора можно угостить чашкой кофе."
    const val ACTION = "Угостить кофе"
}

/**
 * The support card, as in ARDTT: a cup of coffee on a warm cream card by day, on the
 * theme's navy by night; «Угостить кофе» opens the support page in the browser. It is not
 * dismissed and asks nothing elsewhere: it only stands at the end of the settings.
 */
@Composable
internal fun SupportCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkSurface()
    val card = if (dark) lerp(scheme.surface, scheme.primaryContainer, 0.4f) else ArdttColors.SupportSurface
    val accent = if (dark) scheme.primary else ArdttColors.SupportForeground
    val body = if (dark) scheme.onSurfaceVariant else ArdttColors.SupportForeground.copy(alpha = 0.85f)
    val border = if (dark) scheme.primary.copy(alpha = 0.28f) else ArdttColors.SupportAccent.copy(alpha = 0.45f)
    ArdttMessageCard(
        title = SupportText.TITLE,
        body = SupportText.BODY,
        modifier = modifier,
        icon = Icons.Outlined.LocalCafe,
        iconTint = if (dark) scheme.primary else ArdttColors.SupportAccent,
        titleColor = accent,
        bodyColor = body,
        containerColor = card,
        border = BorderStroke(1.dp, border),
        actionText = SupportText.ACTION,
        actionColor = accent,
        onAction = {
            haptics.perform(com.wdtt.client.ui.motion.Haptic.Click)
            runCatching { uriHandler.openUri(AboutLinks.DONATE) }
                .onFailure { android.widget.Toast.makeText(context, "Не удалось открыть ссылку.", android.widget.Toast.LENGTH_SHORT).show() }
        },
    )
}

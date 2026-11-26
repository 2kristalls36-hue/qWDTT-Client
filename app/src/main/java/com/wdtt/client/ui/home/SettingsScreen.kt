package com.wdtt.client.ui.home

import com.wdtt.client.ui.festive.frost
import com.wdtt.client.ui.festive.snowCap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import com.wdtt.client.ui.components.layout.ArdttBottomChrome
import com.wdtt.client.ui.components.layout.ArdttPageHeader
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import com.wdtt.client.lite.AppUpdate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.lite.LiteSettings
import com.wdtt.client.lite.SubscriptionText
import com.wdtt.client.SettingsStore
import com.wdtt.client.ui.components.control.ArdttButton
import com.wdtt.client.ui.components.control.ArdttButtonVariant
import com.wdtt.client.ui.components.control.ArdttChoice
import com.wdtt.client.ui.components.control.ArdttChoiceChip
import com.wdtt.client.ui.components.control.ArdttChoiceChipRow
import kotlinx.coroutines.launch

/**
 * Settings tab: theme, expiry warnings
 * (they apply to every profile), connecting on boot, the connection log and the version.
 */
@Composable
fun SettingsScreen() {
    var logsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    val lite = remember { LiteSettings(context) }
    val settings = remember { SettingsStore(context) }
    val theme by settings.themeMode.collectAsStateWithLifecycle("system")
    val warnEnabled by lite.warnEnabled.collectAsStateWithLifecycle(true)
    val warnDays by lite.warnDays.collectAsStateWithLifecycle(LiteSettings.DEFAULT_WARN_DAYS)
    val onBoot by lite.connectOnBoot.collectAsStateWithLifecycle(false)

    // Blocks settle with their top under the header after a fling (magnet snapping).
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    com.wdtt.client.ui.motion.MagnetSnapTick(listState)
    val snap = com.wdtt.client.ui.motion.rememberMagnetSnapFling(listState)
    com.wdtt.client.ui.components.layout.GlassHeaderPage(
        title = "Настройки",
        scrolled = listState.canScrollBackward,
        backdrop = com.wdtt.client.ui.components.surface.rememberScreenGlass(),
    ) { top ->
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            flingBehavior = snap,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = top + 4.dp, bottom = ArdttBottomChrome.navigationReserve() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                Section("Подключение") {
                    SwitchLine("Подключаться при включении устройства", onBoot) { scope.launch { lite.saveConnectOnBoot(it) } }
                    Hint(
                        if (onBoot) "Приложение подключится к активному профилю после включения устройства."
                        else "Приложение не будет подключаться автоматически."
                    )
                    TrustedWifiSettings(lite)
                }
                }
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    // VK account: with it the app creates call links itself.
                    var vkSignedIn by com.wdtt.client.ui.profiles.rememberVkSignedIn()
                    var vkBusy by remember { mutableStateOf(false) }
                    var confirmSignOut by remember { mutableStateOf(false) }
                    var vkNote by remember { mutableStateOf<String?>(null) }
                    Section("Аккаунт VK") {
                        Hint(if (vkSignedIn) com.wdtt.client.ui.profiles.VkAccountText.SIGNED_IN else com.wdtt.client.ui.profiles.VkAccountText.SIGNED_OUT)
                        vkNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        ArdttButton(
                            text = if (vkSignedIn) com.wdtt.client.ui.profiles.VkAccountText.SIGN_OUT else com.wdtt.client.ui.profiles.VkAccountText.SIGN_IN,
                            variant = if (vkSignedIn) ArdttButtonVariant.Outlined else ArdttButtonVariant.Primary,
                            busy = vkBusy,
                            enabled = !vkBusy,
                            onClick = {
                                if (vkSignedIn) { confirmSignOut = true; return@ArdttButton }
                                scope.launch {
                                    vkBusy = true
                                    val activity = context as? android.app.Activity
                                    val r = com.wdtt.client.vkauth.VkLoginActivity.login(activity ?: context)
                                    vkSignedIn = com.wdtt.client.vkauth.VkSession.hasSessionCookie()
                                    if (r.isFailure || !vkSignedIn) {
                                        // Drop a half-made session so the button stays «Войти».
                                        com.wdtt.client.vkauth.VkSession.clear()
                                        vkSignedIn = false
                                        vkNote = com.wdtt.client.ui.profiles.VkAccountText.LOGIN_CANCELLED
                                        haptics.perform(com.wdtt.client.ui.motion.Haptic.Reject)
                                    } else {
                                        // Right after sign-in a call link is created and saved as the default.
                                        vkNote = com.wdtt.client.ui.profiles.VkAccountText.CREATING
                                        val made = com.wdtt.client.lite.TunnelActions.createDefaultCallLink(context)
                                        vkNote = made.fold({ com.wdtt.client.ui.profiles.VkAccountText.CREATED }, { com.wdtt.client.ui.profiles.VkAccountText.notCreated(it) })
                                        haptics.perform(if (made.isSuccess) com.wdtt.client.ui.motion.Haptic.Confirm else com.wdtt.client.ui.motion.Haptic.Reject)
                                    }
                                    vkBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Default call link: used by profiles without their own link.
                        val defaultLink by settings.globalVkHashes.collectAsStateWithLifecycle("")
                        var linkDraft by remember(defaultLink) { mutableStateOf(defaultLink) }
                        Text(
                            com.wdtt.client.ui.profiles.VkAccountText.DEFAULT_TITLE,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Hint(com.wdtt.client.ui.profiles.VkAccountText.DEFAULT_HINT)
                        com.wdtt.client.ui.components.control.ArdttTextField(
                            value = linkDraft,
                            onValueChange = { linkDraft = it },
                            placeholder = "https://vk.com/call/join/…",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (linkDraft.trim() != defaultLink.trim()) {
                            ArdttButton(
                                text = "Сохранить",
                                onClick = {
                                    scope.launch {
                                        com.wdtt.client.lite.TunnelActions.saveDefaultCallLink(context, linkDraft)
                                        vkNote = null
                                        haptics.perform(com.wdtt.client.ui.motion.Haptic.Confirm)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (vkSignedIn) {
                            var creating by remember { mutableStateOf(false) }
                            ArdttButton(
                                text = if (defaultLink.isBlank()) com.wdtt.client.ui.profiles.VkAccountText.CREATE_LINK else com.wdtt.client.ui.profiles.VkAccountText.CREATE_NEW_LINK,
                                variant = ArdttButtonVariant.Outlined,
                                busy = creating,
                                enabled = !creating && !vkBusy,
                                onClick = {
                                    scope.launch {
                                        creating = true
                                        vkNote = com.wdtt.client.ui.profiles.VkAccountText.CREATING
                                        val made = com.wdtt.client.lite.TunnelActions.createDefaultCallLink(context)
                                        vkNote = made.fold({ com.wdtt.client.ui.profiles.VkAccountText.CREATED }, { com.wdtt.client.ui.profiles.VkAccountText.notCreated(it) })
                                        haptics.perform(if (made.isSuccess) com.wdtt.client.ui.motion.Haptic.Confirm else com.wdtt.client.ui.motion.Haptic.Reject)
                                        creating = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (confirmSignOut) {
                        com.wdtt.client.ui.components.surface.ArdttDialog(
                            title = com.wdtt.client.ui.profiles.VkAccountText.SIGN_OUT_TITLE,
                            onDismissRequest = { confirmSignOut = false },
                            confirmAction = com.wdtt.client.ui.components.surface.ArdttDialogAction("Выйти", destructive = true, onClick = {
                                scope.launch {
                                    com.wdtt.client.vkauth.VkSession.clear()
                                    vkSignedIn = false
                                    confirmSignOut = false
                                }
                            }),
                            dismissAction = com.wdtt.client.ui.components.surface.ArdttDialogAction("Отмена", onClick = { confirmSignOut = false }),
                        ) {
                            Text(com.wdtt.client.ui.profiles.VkAccountText.SIGN_OUT_BODY, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                Section("Подписка") {
                    SwitchLine("Предупреждать об окончании", warnEnabled) { scope.launch { lite.saveWarnEnabled(it) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiteSettings.WARN_DAY_CHOICES.forEach { d ->
                            ArdttChoiceChip(
                                label = SubscriptionText.days(d.toLong()),
                                selected = d == warnDays,
                                enabled = warnEnabled,
                                onClick = { scope.launch { lite.saveWarnDays(d) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Hint(SubscriptionText.warnSentence(warnEnabled, warnDays))
                }
                }
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    val vibration by lite.haptics.collectAsStateWithLifecycle(true)
                    Section("Интерфейс") {
                        Text("Тема", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                        ArdttChoiceChipRow(
                            choices = listOf(
                                ArdttChoice("system", "Системная"),
                                ArdttChoice("light", "Светлая"),
                                ArdttChoice("dark", "Тёмная"),
                            ),
                            selected = theme,
                            onSelect = { scope.launch { settings.saveThemeMode(it) } },
                        )
                        SwitchLine("Вибрация", vibration) { scope.launch { lite.saveHaptics(it) } }
                        Hint(
                            if (vibration) "Приложение подтверждает нажатия, переключения и результаты действий вибрацией."
                            else "Приложение не использует вибрацию."
                        )
                    }
                }
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                Section("Бета-функции") {
                    Hint("Функции в разработке. Их вид и поведение могут измениться.")
                    val glass by lite.liquidGlass.collectAsStateWithLifecycle(false)
                    val glassSupported = com.wdtt.client.ui.components.surface.LiquidGlass.supported
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Жидкое стекло", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                        com.wdtt.client.ui.components.control.LiteSwitch(
                            checked = glass && glassSupported,
                            enabled = glassSupported,
                            onCheckedChange = { on ->
                                haptics.toggle(on)
                                scope.launch {
                                    // Turning the glass on starts from the default transparency.
                                    if (on) lite.saveGlassTransparency(LiteSettings.DEFAULT_GLASS_TRANSPARENCY)
                                    lite.saveLiquidGlass(on)
                                }
                            },
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = glass && glassSupported,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
                    ) {
                        val saved by lite.glassTransparency.collectAsStateWithLifecycle(LiteSettings.DEFAULT_GLASS_TRANSPARENCY)
                        var value by remember { mutableStateOf<Float?>(null) }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Прозрачность", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    "${((value ?: saved) * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Applies live while dragging: the tab bar below changes at once.
                            androidx.compose.material3.Slider(
                                value = value ?: saved,
                                onValueChange = { v -> value = v; scope.launch { lite.saveGlassTransparency(v) } },
                                onValueChangeFinished = { value = null },
                            )
                        }
                    }
                    Hint(
                        when {
                            !glassSupported -> "Эффект доступен на Android 13 и новее."
                            glass -> "Панель навигации отображается в виде стекла."
                            else -> "Панель навигации отображается в стандартном виде."
                        }
                    )
                    val magnet by lite.magnet.collectAsStateWithLifecycle(true)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Магнит", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                        com.wdtt.client.ui.components.control.LiteSwitch(
                            checked = magnet,
                            onCheckedChange = { haptics.toggle(it); scope.launch { lite.saveMagnet(it) } },
                        )
                    }
                    Hint(
                        if (magnet) "Ползунки, вкладки и списки притягиваются к ближайшему положению."
                        else "Ползунки, вкладки и списки останавливаются без притягивания."
                    )
                    val screenFit by lite.screenFit.collectAsStateWithLifecycle(true)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Форма под экран", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                        com.wdtt.client.ui.components.control.LiteSwitch(
                            checked = screenFit,
                            onCheckedChange = { haptics.toggle(it); scope.launch { lite.saveScreenFit(it) } },
                        )
                    }
                    Hint(
                        if (screenFit) "Панель вкладок, карточки и поля повторяют скругление углов экрана."
                        else "Панель вкладок, карточки и поля имеют стандартную форму."
                    )
                }
                }
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                UpdateSection()
                }
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                ArdttButton(
                    text = "Журнал подключения",
                    variant = ArdttButtonVariant.Tonal,
                    onClick = { logsOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                }
            }
        }
    }
    if (logsOpen) LogsSheet(onDismiss = { logsOpen = false })
}

/** Version, check for a newer GitHub release, download with progress, install. */
@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    val installed = remember { AppUpdate.installedVersion(context) }
    var check by remember { mutableStateOf<AppUpdate.Check?>(null) }
    var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsPermission by remember { mutableStateOf(false) }

    fun runCheck() {
        if (checking) return
        checking = true
        error = null
        scope.launch {
            check = AppUpdate.check(context)
            checking = false
        }
    }
    LaunchedEffect(Unit) { runCheck() }

    // Coming back from the permission screen: continue only if it was granted.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && needsPermission && AppUpdate.canInstall(context)) needsPermission = false
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }

    fun startDownload(release: AppUpdate.Release) {
        if (!AppUpdate.canInstall(context)) {
            needsPermission = true
            return
        }
        error = null
        progress = 0f
        scope.launch {
            AppUpdate.download(context, release).collect { d ->
                when (d) {
                    is AppUpdate.Download.Progress -> progress = d.fraction
                    is AppUpdate.Download.Done -> {
                        progress = null
                        haptics.perform(com.wdtt.client.ui.motion.Haptic.Confirm)
                        AppUpdate.install(context, d.file)
                    }
                    is AppUpdate.Download.Error -> {
                        progress = null
                        error = d.message
                        haptics.perform(com.wdtt.client.ui.motion.Haptic.Reject)
                    }
                }
            }
        }
    }

    Section("Обновления") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Установлена версия", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(installed, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }
        val c = check
        val status = when {
            checking -> "Проверка обновлений…"
            c is AppUpdate.Check.Available -> "Доступна версия ${c.release.version}."
            c is AppUpdate.Check.UpToDate -> "Установлена последняя версия."
            c is AppUpdate.Check.Failed -> "Не удалось проверить обновления."
            else -> ""
        }
        if (status.isNotEmpty()) Hint(status)
        if (c is AppUpdate.Check.Available && c.release.notes.isNotBlank()) {
            Text(
                c.release.notes.lines().filter { it.isNotBlank() }.take(6).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        progress?.let {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { it },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
            )
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ToneColors.Critical) }
        if (needsPermission) {
            Hint("Для установки обновления требуется разрешение на установку приложений.")
            ArdttButton(
                text = "Открыть разрешение",
                variant = ArdttButtonVariant.Outlined,
                onClick = { AppUpdate.openInstallPermission(context) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            c is AppUpdate.Check.Available -> ArdttButton(
                text = "Скачать и установить",
                busy = progress != null,
                enabled = progress == null && !needsPermission,
                onClick = { startDownload(c.release) },
                modifier = Modifier.fillMaxWidth(),
            )
            else -> ArdttButton(
                text = "Проверить обновления",
                variant = ArdttButtonVariant.Outlined,
                busy = checking,
                enabled = !checking,
                onClick = { runCheck() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .snowCap(com.wdtt.client.ui.components.layout.fitRadius(20.dp), seed = title.length)
            .clip(com.wdtt.client.ui.components.layout.fitShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .frost(seed = title.length, strength = 0.45f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.outline,
        )
        content()
    }
}

@Composable
internal fun SwitchLine(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        com.wdtt.client.ui.components.control.LiteSwitch(checked = checked, onCheckedChange = { haptics.toggle(it); onChange(it) })
    }
}

@Composable
internal fun Hint(text: String) {
    // The sentence changes with the switch: the old one fades out, the new one fades in.
    AnimatedContent(text, transitionSpec = { fadeIn(tween(300, 120)) togetherWith fadeOut(tween(200)) }, label = "hint") {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

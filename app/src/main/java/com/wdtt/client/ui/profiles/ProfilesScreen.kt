package com.wdtt.client.ui.profiles

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.ConnectionProfile
import com.wdtt.client.MainActivity
import com.wdtt.client.PeerAddress
import com.wdtt.client.ProfilesStore
import com.wdtt.client.SettingsStore
import com.wdtt.client.TunnelManager
import com.wdtt.client.lite.LiteSettings
import com.wdtt.client.lite.ParsedImport
import com.wdtt.client.lite.ProfileImport
import com.wdtt.client.lite.SubscriptionText
import com.wdtt.client.lite.SubscriptionTone
import com.wdtt.client.ui.components.control.ArdttButton
import com.wdtt.client.ui.components.control.ArdttButtonSize
import com.wdtt.client.ui.components.control.ArdttButtonVariant
import com.wdtt.client.ui.components.control.ArdttTextField
import com.wdtt.client.ui.components.layout.ArdttBottomChrome
import com.wdtt.client.ui.components.layout.ArdttPageHeader
import com.wdtt.client.ui.components.surface.ArdttBottomSheet
import com.wdtt.client.ui.components.surface.ArdttDialog
import com.wdtt.client.ui.components.surface.ArdttDialogAction
import com.wdtt.client.ui.home.PingColors
import com.wdtt.client.ui.home.ToneColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ProfilesScreen(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ProfilesStore(context) }
    val profiles by store.profiles.collectAsStateWithLifecycle(emptyList())
    val activeId by settingsStore.currentProfileId.collectAsStateWithLifecycle("")
    val running by TunnelManager.running.collectAsStateWithLifecycle()
    val ping by TunnelManager.pingMs.collectAsStateWithLifecycle()

    var addOpen by remember { mutableStateOf(false) }
    var subscriptionInput by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ParsedImport?>(null) }
    var detailsId by remember { mutableStateOf<String?>(null) }
    val details = profiles.firstOrNull { it.id == detailsId }
    var deleting by remember { mutableStateOf<ConnectionProfile?>(null) }

    fun offer(text: String?, emptyMessage: String) {
        val parsed = text?.let { ProfileImport.parse(it) }
        when {
            parsed != null -> pending = parsed
            text != null && ProfileImport.looksLikeSubscriptionUrl(text) -> subscriptionInput = text.trim()
            else -> Toast.makeText(context, emptyMessage, Toast.LENGTH_SHORT).show()
        }
    }

    // Links and files opened from other apps land here.
    val incoming by MainActivity.pendingFileUri
    LaunchedEffect(incoming) {
        val uri = incoming ?: return@LaunchedEffect
        MainActivity.pendingFileUri.value = null
        offer(ProfileImport.readUri(context, uri), "Файл не содержит профиля.")
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { offer(ProfileImport.readUri(context, uri), "Файл не содержит профиля.") }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    com.wdtt.client.ui.components.layout.GlassHeaderPage(
        title = "Профили",
        scrolled = profiles.isNotEmpty() && listState.canScrollBackward,
        backdrop = com.wdtt.client.ui.components.surface.rememberScreenGlass(),
        actions = {
            ArdttButton(
                icon = Icons.Outlined.Add,
                contentDescription = "Добавить профиль",
                variant = ArdttButtonVariant.Icon,
                onClick = { addOpen = true },
            )
        },
    ) { top ->
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(top = top)) { EmptyProfiles(onAdd = { addOpen = true }) }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 4.dp, bottom = ArdttBottomChrome.navigationReserve() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(profiles, key = { it.id }) { p ->
                    Box(Modifier.animateItem(fadeInSpec = androidx.compose.animation.core.tween(240), fadeOutSpec = androidx.compose.animation.core.tween(160))) {
                        ProfileRow(
                            profile = p,
                            active = p.id == activeId,
                            ping = if (running && p.id == activeId) ping else 0L,
                            onClick = { detailsId = p.id },
                        )
                    }
                }
            }
        }
    }

    if (addOpen) {
        ArdttBottomSheet(onDismissRequest = { addOpen = false }, modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Добавить профиль", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            AddOption(Icons.Outlined.QrCodeScanner, "Сканировать QR-код") {
                addOpen = false
                scanQr(context) { offer(it, "QR-код не содержит профиля.") }
            }
            AddOption(Icons.Outlined.ContentPaste, "Вставить из буфера обмена") {
                addOpen = false
                offer(readClipboard(context), "В буфере обмена нет профиля.")
            }
            AddOption(Icons.Outlined.FolderOpen, "Открыть файл") {
                addOpen = false
                filePicker.launch(arrayOf("*/*"))
            }
            AddOption(Icons.Outlined.Link, "Добавить подписку по ссылке") {
                addOpen = false
                subscriptionInput = ""
            }
        }
    }

    subscriptionInput?.let { initial ->
        var url by remember(initial) { mutableStateOf(initial) }
        var busy by remember { mutableStateOf(false) }
        ArdttDialog(
            title = "Подписка",
            onDismissRequest = { if (!busy) subscriptionInput = null },
            confirmAction = ArdttDialogAction("Добавить", enabled = url.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    val result = store.addSubscription(url.trim())
                    busy = false
                    result.onSuccess {
                        subscriptionInput = null
                        ensureActive(context, settingsStore, store)
                        Toast.makeText(context, "Подписка добавлена.", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "Не удалось загрузить подписку.", Toast.LENGTH_LONG).show()
                    }
                }
            }),
            dismissAction = ArdttDialogAction("Отмена", onClick = { subscriptionInput = null }),
        ) {
            ArdttTextField(value = url, onValueChange = { url = it }, label = "Ссылка на подписку", placeholder = "https://…")
        }
    }

    pending?.let { parsed ->
        ImportPreviewSheet(
            parsed = parsed,
            onDismiss = { pending = null },
            onAdd = { checks ->
                scope.launch {
                    val single = parsed.profiles.singleOrNull()
                    if (single != null) {
                        store.saveProfile(single)
                    } else {
                        // Same ids as in the preview, so the server answers stay attached to them.
                        val groupId = store.resolveGroupIdForImport(parsed.suggestedName?.ifBlank { null } ?: "Импорт")
                        parsed.profiles.forEach { store.saveProfile(it.copy(groupId = groupId)) }
                    }
                    // Keep what the server reported, so the profile shows its subscription right away.
                    val lite = LiteSettings(context)
                    checks.forEach { (id, c) ->
                        if (c is com.wdtt.client.lite.ProfileStatusClient.Check.Ok) lite.saveSubscription(id, c.info)
                    }
                    pending = null
                    ensureActive(context, settingsStore, store)
                    Toast.makeText(context, if (single != null) "Профиль добавлен." else "Профили добавлены.", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    details?.let { p ->
        val isActive = p.id == activeId
        ProfileSheet(
            profile = p,
            isActive = isActive,
            ping = if (running && isActive) ping else 0L,
            onDismiss = { detailsId = null },
            onMakeActive = {
                scope.launch {
                    store.applyProfile(context, p.id)
                    detailsId = null
                }
            },
            onDelete = { detailsId = null; deleting = p },
        )
    }

    deleting?.let { p ->
        val isActiveAndRunning = p.id == activeId && running
        ArdttDialog(
            title = "Удалить профиль?",
            onDismissRequest = { deleting = null },
            confirmAction = ArdttDialogAction("Удалить", destructive = true, enabled = !isActiveAndRunning, onClick = {
                scope.launch {
                    store.deleteProfile(p.id)
                    if (p.id == activeId) settingsStore.saveCurrentProfile("", "")
                    ensureActive(context, settingsStore, store)
                    deleting = null
                }
            }),
            dismissAction = ArdttDialogAction("Отмена", onClick = { deleting = null }),
        ) {
            Text(
                if (isActiveAndRunning) "Профиль используется. Удаление доступно после отключения."
                else "«${p.name.ifBlank { "Профиль" }}» будет удалён с устройства.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** With no active profile, the first one becomes active, so «Подключиться» works right away. */
private suspend fun ensureActive(context: Context, settings: SettingsStore, store: ProfilesStore) {
    val current = settings.currentProfileId.first()
    if (current.isNotBlank() && store.getProfileOnce(current) != null) return
    store.profiles.first().firstOrNull()?.let { store.applyProfile(context, it.id) }
}

private fun readClipboard(context: Context): String? = try {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
} catch (_: Exception) {
    null
}

private fun scanQr(context: Context, onText: (String?) -> Unit) {
    val activity = MainActivity.currentActivity ?: return
    try {
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(activity)
            .startScan()
            .addOnSuccessListener { onText(it.rawValue) }
            .addOnFailureListener { /* cancelled or not available */ }
    } catch (_: Exception) {
        Toast.makeText(context, "Сканер QR-кодов недоступен на этом устройстве.", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun ProfileRow(profile: ConnectionProfile, active: Boolean, ping: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    val lite = remember { LiteSettings(context) }
    val sub by remember(profile.id) { lite.subscription(profile.id) }.collectAsState(null)
    val warnDays by lite.warnDays.collectAsStateWithLifecycle(LiteSettings.DEFAULT_WARN_DAYS)
    val view = SubscriptionText.view(sub, warnDays)
    val scheme = MaterialTheme.colorScheme
    val toneColor = when (view.tone) {
        SubscriptionTone.Warn -> ToneColors.Warn
        SubscriptionTone.Critical, SubscriptionTone.Expired -> ToneColors.Critical
        else -> scheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .then(if (active) Modifier.border(1.5.dp, scheme.primary.copy(alpha = 0.6f), shape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = "Открыть сведения о профиле", onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(if (active) (PingColors.of(ping) ?: scheme.primary) else scheme.outline.copy(alpha = 0.3f), CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                profile.name.ifBlank { "Профиль" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                PeerAddress.host(profile.peer),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (active) Text("Активный", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = scheme.primary)
            if (sub != null) Text(view.short, style = MaterialTheme.typography.labelMedium, color = toneColor)
        }
    }
}

@Composable
private fun AddOption(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyProfiles(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Профилей нет", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Text(
            "Профиль добавляется по ссылке, QR-коду, файлу или подписке.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        ArdttButton(text = "Добавить профиль", onClick = onAdd, size = ArdttButtonSize.Regular)
    }
}

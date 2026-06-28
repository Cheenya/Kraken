package com.disser.kraken.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.message.ChatPreferencesStore
import com.disser.kraken.mesh.MeshServiceSnapshot
import com.disser.kraken.mesh.MeshState
import com.disser.kraken.mesh.MeshTransportSelection
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.ui.components.EmptyState
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.KrakenListRow
import com.disser.kraken.ui.components.KrakenSectionHeader
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.StateBadge
import com.disser.kraken.ui.components.TechnicalDetailsDisclosure
import com.disser.kraken.ui.components.formatEpoch
import com.disser.kraken.ui.icons.KrakenIcons
import com.disser.kraken.ui.theme.KrakenThemePreset
import com.disser.kraken.ui.theme.KrakenThemePresetCatalog
import com.disser.kraken.ui.theme.KrakenDensityMode
import com.disser.kraken.ui.theme.KrakenSurfaceStyle
import com.disser.kraken.ui.theme.tokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    meshSnapshot: MeshServiceSnapshot,
    selectedTransportProfile: String,
    themePreset: KrakenThemePreset,
    quickReaction: String,
    globalChatBackground: String,
    onStartMesh: () -> Unit,
    onStartHotspotCompatibleMesh: () -> Unit,
    onStartWifiDirectTrialMesh: () -> Unit,
    onStopMesh: () -> Unit,
    onSyncMeshNow: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onQuickReactionSelected: (String) -> Unit,
    onGlobalChatBackgroundSelected: (String) -> Unit,
) {
    var editedDisplayName by remember(localIdentity?.identityId) {
        mutableStateOf(localIdentity?.displayName.orEmpty())
    }
    var editingIdentity by remember(localIdentity?.identityId) { mutableStateOf(false) }
    var identityMessage by remember { mutableStateOf<String?>(null) }
    var quickReactionPickerOpen by remember { mutableStateOf(false) }
    var chatBackgroundPickerOpen by remember { mutableStateOf(false) }
    var routeSettingsMode by remember { mutableStateOf(RouteSettingsMode.Auto) }

    ScreenContainer("Настройки", navController, showTitle = true, showBack = false) {
        if (localIdentity != null) {
            KrakenListRow(
                title = localIdentity.displayName,
                subtitle = "${localIdentity.fingerprint.take(4)}…${localIdentity.fingerprint.takeLast(4)} · профиль на этом устройстве",
                leadingIcon = KrakenIcons.Identity,
                badge = "НА УСТРОЙСТВЕ",
            )
            TechnicalDetailsDisclosure("Профиль и имя") {
                InfoCard(
                    "Технические данные",
                    listOf(
                        "Отпечаток: ${localIdentity.fingerprint}",
                        "Создано локально: ${formatEpoch(localIdentity.createdAtEpochMillis)}",
                    )
                )
                if (editingIdentity) {
                    OutlinedTextField(
                        value = editedDisplayName,
                        onValueChange = { editedDisplayName = it },
                        label = { Text("Имя") },
                        supportingText = { Text("Изменение имени не меняет ключ личности.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (editedDisplayName.trim().isEmpty()) {
                                    identityMessage = "Введите имя."
                                } else {
                                    onDisplayNameChanged(editedDisplayName.trim())
                                    editingIdentity = false
                                    identityMessage = "Имя обновлено. Отпечаток не изменился."
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Сохранить")
                        }
                        OutlinedButton(
                            onClick = {
                                editedDisplayName = localIdentity.displayName
                                editingIdentity = false
                                identityMessage = null
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Отмена")
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            editedDisplayName = localIdentity.displayName
                            editingIdentity = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Изменить имя")
                    }
                }
            }
            identityMessage?.let { Text(it) }
        } else {
            EmptyState("Личность не создана", "Создайте личность, чтобы увидеть отпечаток.")
        }
        KrakenListRow(
            title = "Язык",
            subtitle = "Русский по умолчанию",
            leadingIcon = KrakenIcons.Language,
            trailingText = "RU",
        )
        KrakenListRow(
            title = "Внешний вид",
            subtitle = "${themePreset.displayName} · стиль интерфейса",
            leadingIcon = KrakenIcons.Settings,
            trailingText = "›",
            onClick = { navController.navigate(KrakenRoute.ThemePicker.route) },
        )
        KrakenListRow(
            title = "Быстрая реакция",
            subtitle = "Двойной тап по сообщению · пока только локально",
            leadingIcon = KrakenIcons.Chat,
            trailingText = quickReaction,
            onClick = { quickReactionPickerOpen = true },
        )
        KrakenListRow(
            title = "Фон чатов",
            subtitle = "${ChatPreferencesStore.backgroundTitle(globalChatBackground)} · по умолчанию для всех диалогов",
            leadingIcon = KrakenIcons.Chat,
            trailingText = "›",
            onClick = { chatBackgroundPickerOpen = true },
        )
        KrakenSectionHeader("Связь")
        MeshControlCard(
            meshSnapshot = meshSnapshot,
            selectedTransportProfile = selectedTransportProfile,
            onStartMesh = onStartMesh,
            onStartHotspotCompatibleMesh = onStartHotspotCompatibleMesh,
            onStartWifiDirectTrialMesh = onStartWifiDirectTrialMesh,
            onStopMesh = onStopMesh,
            onSyncMeshNow = onSyncMeshNow,
        )
        RouteModeSelector(
            selectedMode = routeSettingsMode,
            onModeSelected = { routeSettingsMode = it },
        )
        if (routeSettingsMode == RouteSettingsMode.Diagnostics) {
            KrakenListRow(
                title = "Диагностика связи",
                subtitle = "Bluetooth/LAN, очередь сообщений и устройства рядом",
                leadingIcon = KrakenIcons.MeshStatus,
                trailingText = "›",
                onClick = { navController.navigate(KrakenRoute.MeshStatus.route) },
            )
        }
        KrakenSectionHeader("Исследование")
        KrakenListRow(
            title = "Проверки и отчёты",
            subtitle = "Отчёты, замеры и проверочные материалы",
            leadingIcon = KrakenIcons.Research,
            trailingText = "›",
            onClick = { navController.navigate(KrakenRoute.Research.route) },
        )
        TechnicalDetailsDisclosure("О контуре проверок") {
            InfoCard(
                "Контур обмена",
                listOf(
                    "Сообщения передаются через локальные прямые маршруты или явно проверенный relay.",
                    "Проверочные экраны показывают состояние локального контура обмена.",
                ),
            )
        }
    }

    if (quickReactionPickerOpen) {
        AlertDialog(
            onDismissRequest = { quickReactionPickerOpen = false },
            title = { Text("Быстрая реакция") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Используется по двойному тапу. В этой версии реакция сохраняется только на вашем устройстве.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChatPreferencesStore.QUICK_REACTION_OPTIONS.forEach { reaction ->
                            OutlinedButton(
                                onClick = {
                                    onQuickReactionSelected(reaction)
                                    quickReactionPickerOpen = false
                                },
                            ) {
                                Text(reaction)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { quickReactionPickerOpen = false }) {
                    Text("Закрыть")
                }
            },
        )
    }

    if (chatBackgroundPickerOpen) {
        ChatBackgroundPickerDialog(
            title = "Фон чатов",
            selectedBackgroundKey = globalChatBackground,
            includeSystemDefault = false,
            onSelect = { backgroundKey ->
                if (backgroundKey != null) {
                    onGlobalChatBackgroundSelected(backgroundKey)
                }
                chatBackgroundPickerOpen = false
            },
            onDismiss = { chatBackgroundPickerOpen = false },
        )
    }
}

@Composable
private fun MeshControlCard(
    meshSnapshot: MeshServiceSnapshot,
    selectedTransportProfile: String,
    onStartMesh: () -> Unit,
    onStartHotspotCompatibleMesh: () -> Unit,
    onStartWifiDirectTrialMesh: () -> Unit,
    onStopMesh: () -> Unit,
    onSyncMeshNow: () -> Unit,
) {
    val meshRunning = meshSnapshot.state !in setOf(MeshState.OFF, MeshState.ERROR)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = meshStateColor(meshSnapshot.state).copy(alpha = 0.18f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = KrakenIcons.MeshStatus,
                        contentDescription = null,
                        tint = meshStateColor(meshSnapshot.state),
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Локальная связь", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${transportProfileLabel(selectedTransportProfile)} · ${meshControlSubtitle(meshSnapshot)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StateBadge(meshStateLabel(meshSnapshot.state))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (meshRunning) {
                    Button(onClick = onSyncMeshNow, modifier = Modifier.weight(1f)) {
                        Text(if (meshSnapshot.queuedPackets > 0) "Отправить" else "Проверить")
                    }
                    OutlinedButton(
                        onClick = {
                            onStopMesh()
                            onStartMesh()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Перезапустить")
                    }
                } else {
                    Button(onClick = onStartMesh, modifier = Modifier.weight(1f)) {
                        Text("Включить связь")
                    }
                    OutlinedButton(onClick = onSyncMeshNow, modifier = Modifier.weight(1f)) {
                        Text("Проверить")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onStartHotspotCompatibleMesh,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("С точкой доступа")
                }
                OutlinedButton(
                    onClick = onStartWifiDirectTrialMesh,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Wi‑Fi Direct")
                }
            }
            if (meshRunning) {
                OutlinedButton(onClick = onStopMesh, modifier = Modifier.fillMaxWidth()) {
                    Text("Остановить связь")
                }
            }
        }
    }
}

private fun meshControlSubtitle(meshSnapshot: MeshServiceSnapshot): String {
    val queue = when {
        meshSnapshot.queuedPackets > 0 -> "очередь: ${meshSnapshot.queuedPackets}"
        meshSnapshot.queue.sentAwaitingAck > 0 -> "ожидает подтверждения: ${meshSnapshot.queue.sentAwaitingAck}"
        else -> "очередь пуста"
    }
    val service = if (meshSnapshot.foregroundServiceEnabled) "служба включена" else "служба выключена"
    return "$service · $queue"
}

private fun transportProfileLabel(profile: String): String =
    when (profile) {
        MeshTransportSelection.PROFILE_WIFI_DIRECT_ONLY -> "только Wi‑Fi Direct"
        MeshTransportSelection.PROFILE_LAN_ONLY -> "только LAN"
        MeshTransportSelection.PROFILE_AUTO -> "авто"
        else -> "совместимо с точкой доступа"
    }

private fun meshStateLabel(state: MeshState): String =
    when (state) {
        MeshState.OFF -> "выкл."
        MeshState.STARTING -> "старт"
        MeshState.SCANNING -> "поиск"
        MeshState.PEER_FOUND -> "рядом"
        MeshState.CONNECTED -> "активна"
        MeshState.DEGRADED -> "огранич."
        MeshState.ERROR -> "ошибка"
    }

@Composable
private fun meshStateColor(state: MeshState): Color =
    when (state) {
        MeshState.CONNECTED,
        MeshState.PEER_FOUND -> MaterialTheme.colorScheme.primary
        MeshState.STARTING,
        MeshState.SCANNING,
        MeshState.DEGRADED -> MaterialTheme.colorScheme.tertiary
        MeshState.ERROR -> MaterialTheme.colorScheme.error
        MeshState.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun RouteModeSelector(
    selectedMode: RouteSettingsMode,
    onModeSelected: (RouteSettingsMode) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = KrakenIcons.MeshStatus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Режим маршрутов", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        selectedMode.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(RouteSettingsMode.Auto, RouteSettingsMode.Diagnostics).forEach { mode ->
                    val selected = selectedMode == mode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onModeSelected(mode) }
                            .semantics { this.selected = selected },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Text(
                            mode.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemePickerScreen(
    navController: NavHostController,
    themePreset: KrakenThemePreset,
    onThemePresetSelected: (KrakenThemePreset) -> Unit,
) {
    ScreenContainer("Внешний вид", navController) {
        ThemePresetSelector(
            selectedPreset = themePreset,
            onThemePresetSelected = onThemePresetSelected,
        )
    }
}

@Composable
private fun ThemePresetSelector(
    selectedPreset: KrakenThemePreset,
    onThemePresetSelected: (KrakenThemePreset) -> Unit,
) {
    InfoCard(
        "Сейчас выбрано",
        listOf("${selectedPreset.displayName}: ${selectedPreset.description}"),
    )
    KrakenSectionHeader("Пользовательские темы")
    KrakenThemePresetCatalog.options(selectedPreset).forEach { option ->
        ThemePresetCard(
            option = option,
            onThemePresetSelected = onThemePresetSelected,
        )
    }
    KrakenSectionHeader("Диагностика")
    InfoCard(
        "Для диагностики",
        listOf(
            "Технические стили нужны для раздела проверок и проверки интерфейса.",
            "Они меняют только визуальные токены, не протоколы и не безопасность.",
        ),
    )
    KrakenThemePresetCatalog.diagnosticOptions(selectedPreset).forEach { option ->
        ThemePresetCard(
            option = option,
            onThemePresetSelected = onThemePresetSelected,
        )
    }
    Spacer(modifier = Modifier.height(40.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePresetCard(
    option: com.disser.kraken.ui.theme.KrakenThemePresetOption,
    onThemePresetSelected: (KrakenThemePreset) -> Unit,
) {
    val preset = option.preset
    val previewTokens = preset.tokens()
    val previewScheme = previewTokens.colorScheme
    val selectedContainer = previewScheme.primaryContainer
    val unselectedContainer = when (previewTokens.surfaceStyle) {
        KrakenSurfaceStyle.SOLID -> previewScheme.surfaceVariant
        KrakenSurfaceStyle.GLASS_LIKE -> previewScheme.surfaceVariant.copy(alpha = 0.74f)
        KrakenSurfaceStyle.CONSOLE -> previewScheme.surface.copy(alpha = 0.94f)
    }
    Card(
        onClick = { onThemePresetSelected(preset) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Стиль ${preset.displayName}. ${preset.description}"
                selected = option.selected
            },
        shape = RoundedCornerShape(previewTokens.cardRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (option.selected) {
                selectedContainer
            } else {
                unselectedContainer
            },
        ),
        border = BorderStroke(
            previewTokens.borderWidth,
            if (option.selected) previewScheme.primary else previewScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
            horizontal = previewTokens.cardContentPaddingHorizontal + 2.dp,
            vertical = previewTokens.cardContentPaddingVertical + 2.dp,
        ),
            horizontalArrangement = Arrangement.spacedBy(previewTokens.listRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(previewTokens.controlRadius),
                color = previewScheme.primary,
                modifier = Modifier.size(previewTokens.avatarSize),
            ) {
                Icon(
                    imageVector = themePresetIcon(preset),
                    contentDescription = null,
                    tint = previewScheme.onPrimary,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(previewTokens.contentSpacing),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        preset.displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (option.selected) previewScheme.onPrimaryContainer else previewScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (option.selected) {
                        StateBadge("Выбран")
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(previewTokens.contentSpacing + 3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        preset.densityMode.russianLabel(),
                        color = if (option.selected) previewScheme.onPrimaryContainer else previewScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "·",
                        color = if (option.selected) previewScheme.onPrimaryContainer else previewScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        preset.surfaceStyle.russianLabel(),
                        color = if (option.selected) previewScheme.onPrimaryContainer else previewScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    preset.description,
                    color = if (option.selected) previewScheme.onPrimaryContainer else previewScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ThemeColorSwatch(previewScheme.background, "Фон")
                    ThemeColorSwatch(previewScheme.surface, "Слой")
                    ThemeColorSwatch(previewScheme.primary, "Акцент")
                    ThemeColorSwatch(previewScheme.secondary, "Второй")
                    ThemeColorSwatch(previewScheme.error, "Риск")
                }
            }
        }
    }
}

private fun themePresetIcon(preset: KrakenThemePreset): ImageVector =
    when (preset) {
        KrakenThemePreset.KRAKEN_DARK -> KrakenIcons.ShieldOutline
        KrakenThemePreset.LIQUID_GLASS -> KrakenIcons.Settings
        KrakenThemePreset.ABYSS -> KrakenIcons.LockOutline
        KrakenThemePreset.SIGNAL_CLEAN -> KrakenIcons.PrivacyLock
        KrakenThemePreset.COMPACT_MESSENGER -> KrakenIcons.Chat
        KrakenThemePreset.AMOLED_BLACK -> KrakenIcons.ShieldOutline
        KrakenThemePreset.RESEARCH_CONSOLE -> KrakenIcons.Research
    }

@Composable
private fun ThemeColorSwatch(color: Color, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = color,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(width = 30.dp, height = 12.dp),
        ) {}
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun KrakenDensityMode.russianLabel(): String =
    when (this) {
        KrakenDensityMode.COMFORTABLE -> "свободно"
        KrakenDensityMode.COMPACT -> "компактно"
        KrakenDensityMode.TECHNICAL -> "технически"
    }

private fun KrakenSurfaceStyle.russianLabel(): String =
    when (this) {
        KrakenSurfaceStyle.SOLID -> "плотная"
        KrakenSurfaceStyle.GLASS_LIKE -> "стекло"
        KrakenSurfaceStyle.CONSOLE -> "консоль"
    }

private enum class RouteSettingsMode(
    val label: String,
    val subtitle: String,
) {
    Auto(
        label = "Обычно",
        subtitle = "Обычный сценарий без ручного выбора транспорта",
    ),
    Diagnostics(
        label = "Диагностика",
        subtitle = "Инструменты диагностики и evidence",
    );
}

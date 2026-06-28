package com.disser.kraken.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import com.disser.kraken.handshake.OfflineHandshakeService
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.message.ChatPreferencesStore
import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageDirection
import com.disser.kraken.message.MessageService
import com.disser.kraken.message.MessageStatus
import com.disser.kraken.message.SavedMessage
import com.disser.kraken.mesh.MeshServiceSnapshot
import com.disser.kraken.mesh.MeshState
import com.disser.kraken.mesh.PeerRouteAggregator
import com.disser.kraken.mesh.PeerRouteFormatter
import com.disser.kraken.mesh.PeerRouteKind
import com.disser.kraken.mesh.PeerRouteSnapshot
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.realm.RealmCommunicationDecision
import com.disser.kraken.realm.RealmCommunicationPolicy
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.ComplaintEvent
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipService
import com.disser.kraken.relationship.RelationshipState
import com.disser.kraken.relationship.UnlinkReason
import com.disser.kraken.ui.components.EmptyState
import com.disser.kraken.ui.components.KrakenCompactCard
import com.disser.kraken.ui.components.PayloadQrCodeCard
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.TechnicalDetailsDisclosure
import com.disser.kraken.ui.components.WarningCard
import com.disser.kraken.ui.components.formatEpoch
import com.disser.kraken.ui.icons.KrakenIcons
import com.disser.kraken.ui.theme.LocalKrakenThemeTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ChatScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    relationships: List<Relationship>,
    messages: List<LocalMessage>,
    savedMessages: List<SavedMessage>,
    realmSnapshot: RealmSnapshot,
    meshSnapshot: MeshServiceSnapshot,
    selectedRelationshipId: String?,
    mutedRelationshipIds: Set<String>,
    quickReaction: String,
    globalChatBackground: String,
    chatBackgroundKey: String,
    onRelationshipSelected: (Relationship) -> Unit,
    onConversationBack: () -> Unit,
    onOpenContactProfile: (Relationship) -> Unit,
    onSendMessage: (Relationship, String, LocalMessage?) -> Unit,
    onRetryMessage: (LocalMessage) -> Unit,
    onDeleteMessage: (LocalMessage) -> Unit,
    onDeleteMessages: (List<LocalMessage>) -> Unit,
    onSaveMessagesToFavorites: (List<LocalMessage>) -> Unit,
    onClearConversation: (Relationship) -> Unit,
    onSetRelationshipMuted: (Relationship, Boolean) -> Unit,
    onChatBackgroundSelected: (Relationship, String?) -> Unit,
) {
    val selectedRelationship = relationships.firstOrNull { it.relationshipId == selectedRelationshipId }
    val conversationRelationship = when {
        selectedRelationship?.state == RelationshipState.ACTIVE -> selectedRelationship
        else -> null
    }

    if (conversationRelationship == null) {
        ChatListRoot(
            navController = navController,
            localIdentity = localIdentity,
            relationships = relationships,
            messages = messages,
            savedMessages = savedMessages,
            realmSnapshot = realmSnapshot,
            meshSnapshot = meshSnapshot,
            mutedRelationshipIds = mutedRelationshipIds,
            onRelationshipSelected = onRelationshipSelected,
        )
    } else {
        ChatConversation(
            relationship = conversationRelationship,
            localIdentity = localIdentity,
            messages = messages,
            realmSnapshot = realmSnapshot,
            meshSnapshot = meshSnapshot,
            muted = conversationRelationship.relationshipId in mutedRelationshipIds,
            quickReaction = quickReaction,
            globalChatBackground = globalChatBackground,
            chatBackgroundKey = chatBackgroundKey,
            onBack = onConversationBack,
            onOpenContactProfile = { onOpenContactProfile(conversationRelationship) },
            onSendMessage = onSendMessage,
            onRetryMessage = onRetryMessage,
            onDeleteMessage = onDeleteMessage,
            onDeleteMessages = onDeleteMessages,
            onSaveMessagesToFavorites = onSaveMessagesToFavorites,
            onClearConversation = onClearConversation,
            onSetMuted = { muted -> onSetRelationshipMuted(conversationRelationship, muted) },
            onChatBackgroundSelected = { backgroundKey -> onChatBackgroundSelected(conversationRelationship, backgroundKey) },
        )
    }
}

@Composable
fun SavedMessagesScreen(
    navController: NavHostController,
    savedMessages: List<SavedMessage>,
) {
    ScreenContainer("Избранное", navController) {
        if (savedMessages.isEmpty()) {
            EmptyState(
                "Избранного пока нет",
                "Выделите сообщение в чате и сохраните его в избранное.",
                actionLabel = "К чатам",
                route = KrakenRoute.Chat,
                navController = navController,
            )
            return@ScreenContainer
        }

        Text(
            "${savedMessages.size} сохранённых сообщений",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            savedMessages.forEach { saved ->
                SavedMessageRow(saved)
            }
        }
    }
}

@Composable
private fun SavedMessageRow(saved: SavedMessage) {
    KrakenCompactCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = KrakenIcons.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        saved.sourceDisplayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        compactChatTimestamp(saved.originalCreatedAtEpochMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    saved.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Сохранено ${compactChatTimestamp(saved.savedAtEpochMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChatListRoot(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    relationships: List<Relationship>,
    messages: List<LocalMessage>,
    savedMessages: List<SavedMessage>,
    realmSnapshot: RealmSnapshot,
    meshSnapshot: MeshServiceSnapshot,
    mutedRelationshipIds: Set<String>,
    onRelationshipSelected: (Relationship) -> Unit,
) {
    ScreenContainer("Чаты", navController, showTitle = false, showBack = false) {
        val active = relationships.filter { it.state == RelationshipState.ACTIVE }
        var chatSearchQuery by remember { mutableStateOf("") }
        val chatRows = active.map { relationship ->
            val allowed = localIdentity?.let {
                RealmCommunicationPolicy.canUseRelationship(it, relationship, realmSnapshot).allowed
            } == true
            ChatThreadPreview(
                relationship = relationship,
                allowed = allowed,
                lastMessage = lastConversationMessage(relationship, messages),
                route = PeerRouteAggregator.routeFor(relationship, meshSnapshot),
            )
        }
        val visibleRows = chatRows.filter { it.matchesSearchQuery(chatSearchQuery) }

        ChatListHeader(
            activeChatsTotal = active.size,
            savedMessagesTotal = savedMessages.size,
            meshSnapshot = meshSnapshot,
        )

        SavedMessagesPinnedRow(
            savedMessagesTotal = savedMessages.size,
            latestSavedMessage = savedMessages.firstOrNull(),
            onClick = { navController.navigate(KrakenRoute.SavedMessages.route) },
        )

        if (active.isEmpty()) {
            EmptyState(
                "Чатов пока нет",
                "Активные диалоги появятся здесь после добавления контакта. Избранное доступно сверху.",
                actionLabel = "К контактам",
                route = KrakenRoute.Contacts,
                navController = navController,
            )
            return@ScreenContainer
        }

        ChatSearchField(
            query = chatSearchQuery,
            onQueryChange = { chatSearchQuery = it },
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )

        if (visibleRows.isEmpty()) {
            ChatSearchEmptyState(chatSearchQuery)
            return@ScreenContainer
        }

        visibleRows.forEachIndexed { index, preview ->
            val relationship = preview.relationship
            ChatThreadRow(
                title = relationship.peerDisplayName ?: "Неизвестный контакт",
                subtitle = chatListSubtitle(preview.lastMessage, preview.allowed),
                leadingText = relationship.peerDisplayName?.take(2),
                leadingIcon = if (relationship.peerDisplayName == null) KrakenIcons.Contacts else null,
                route = preview.route,
                trailingText = preview.lastMessage?.let { compactChatTimestamp(it.createdAtEpochMillis) },
                trailingStatus = if (relationship.relationshipId in mutedRelationshipIds) {
                    "без звука"
                } else {
                    preview.lastMessage?.takeIf { it.direction == MessageDirection.OUTGOING }?.let {
                        visibleMeshDeliveryLabel(it.status)
                    }
                },
                onClick = { onRelationshipSelected(relationship) },
            )
            if (index != visibleRows.lastIndex) {
                ChatThinDivider(Modifier.padding(start = 64.dp))
            }
        }
    }
}

private data class ChatThreadPreview(
    val relationship: Relationship,
    val allowed: Boolean,
    val lastMessage: LocalMessage?,
    val route: PeerRouteSnapshot,
)

@Composable
private fun SavedMessagesPinnedRow(
    savedMessagesTotal: Int,
    latestSavedMessage: SavedMessage?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Избранное. Сохранённых сообщений: $savedMessagesTotal"
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = KrakenIcons.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Избранное",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    latestSavedMessage?.let { "${it.sourceDisplayName}: ${it.body}" }
                        ?: "Сохранённые сообщения появятся здесь",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    savedMessagesTotal.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    "закреплено",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ChatSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(start = 12.dp, end = if (query.isBlank()) 12.dp else 2.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = KrakenIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                "Поиск",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotBlank()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = "Очистить поиск" },
                ) {
                    Icon(
                        imageVector = KrakenIcons.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSearchEmptyState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Ничего не найдено",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            query.trim().take(48),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun ChatThreadPreview.matchesSearchQuery(query: String): Boolean {
    val terms = query.trim()
        .lowercase(Locale.ROOT)
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    if (terms.isEmpty()) return true

    val searchable = listOfNotNull(
        relationship.peerDisplayName,
        relationship.peerFingerprint,
        relationship.peerPublicKey,
        lastMessage?.body,
    ).joinToString(" ").lowercase(Locale.ROOT)
    return terms.all(searchable::contains)
}

@Composable
private fun ChatListHeader(
    activeChatsTotal: Int,
    savedMessagesTotal: Int,
    meshSnapshot: MeshServiceSnapshot,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text("Kraken", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                buildList {
                    add(if (activeChatsTotal == 1) "1 диалог" else "$activeChatsTotal диалогов")
                    if (savedMessagesTotal > 0) add("избранное $savedMessagesTotal")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chatTransportStatusItems(meshSnapshot).forEach { item ->
                    TransportStatusPill(item)
                }
            }
        }
    }
}

private data class ChatTransportStatusItem(
    val label: String,
    val active: Boolean,
)

@Composable
private fun TransportStatusPill(item: ChatTransportStatusItem) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (item.active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            item.label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (item.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (item.active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun chatTransportStatusItems(meshSnapshot: MeshServiceSnapshot): List<ChatTransportStatusItem> {
    val diagnostics = meshSnapshot.transportDiagnostics
    val meshRunning = meshSnapshot.state !in setOf(MeshState.OFF, MeshState.ERROR)
    val bleActive = meshRunning && (
        diagnostics.bleConnectedPeerCount > 0 ||
            diagnostics.bleAdvertisingState.isActiveTransportState() ||
            diagnostics.bleScanningState.isActiveTransportState() ||
            diagnostics.bleGattServerState.isActiveTransportState()
        )
    val lanActive = meshRunning && (
        diagnostics.localPort != null ||
            diagnostics.localAddresses.isNotEmpty() ||
            diagnostics.registrationState.isActiveTransportState()
        )
    val wifiDirectActive = meshRunning && (
        diagnostics.wifiDirectGroupFormed == true ||
            diagnostics.p2pVisibleDeviceCount > 0 ||
            diagnostics.p2pServiceFoundCount > 0 ||
            diagnostics.wifiDirectBoundEndpoints.isNotEmpty()
        )
    return listOf(
        ChatTransportStatusItem("BLE", bleActive),
        ChatTransportStatusItem("LAN", lanActive),
        ChatTransportStatusItem("Wi‑Fi D", wifiDirectActive),
    )
}

private fun String.isActiveTransportState(): Boolean {
    val normalized = lowercase(Locale.ROOT)
    return normalized.contains("active") ||
        normalized.contains("started") ||
        normalized.contains("running") ||
        normalized.contains("registered") ||
        normalized.contains("advertising") ||
        normalized.contains("scanning")
}

@Composable
private fun ChatThreadRow(
    title: String,
    subtitle: String,
    leadingText: String?,
    leadingIcon: ImageVector?,
    route: PeerRouteSnapshot,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    trailingStatus: String? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalKrakenThemeTokens.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(title, subtitle, trailingText, trailingStatus).joinToString(". ")
        },
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 68.dp)
                .padding(horizontal = 2.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatListAvatar(
                leadingText = leadingText,
                leadingIcon = leadingIcon,
                route = route,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (trailingText != null) {
                    Text(
                        trailingText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (trailingStatus != null) {
                    Text(
                        trailingStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatListAvatar(
    leadingText: String?,
    leadingIcon: ImageVector?,
    route: PeerRouteSnapshot,
) {
    val statusColor = when (route.kind) {
        PeerRouteKind.DIRECT_BLE,
        PeerRouteKind.DIRECT_LAN,
        PeerRouteKind.ROUTED_MESH -> Color(0xFF35D07F)
        PeerRouteKind.NONE -> Color(0xFF6F7C83)
    }
    Box(modifier = Modifier.size(52.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.Center),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp),
                    )
                } else {
                    Text(
                        leadingText?.take(2)?.uppercase() ?: "QR",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Surface(
            modifier = Modifier
                .size(14.dp)
                .align(Alignment.BottomEnd),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(9.dp),
                    shape = CircleShape,
                    color = statusColor,
                ) {}
            }
        }
    }
}

@Composable
private fun ChatConversation(
    relationship: Relationship,
    localIdentity: LocalIdentity?,
    messages: List<LocalMessage>,
    realmSnapshot: RealmSnapshot,
    meshSnapshot: MeshServiceSnapshot,
    muted: Boolean,
    quickReaction: String,
    globalChatBackground: String,
    chatBackgroundKey: String,
    onBack: () -> Unit,
    onOpenContactProfile: () -> Unit,
    onSendMessage: (Relationship, String, LocalMessage?) -> Unit,
    onRetryMessage: (LocalMessage) -> Unit,
    onDeleteMessage: (LocalMessage) -> Unit,
    onDeleteMessages: (List<LocalMessage>) -> Unit,
    onSaveMessagesToFavorites: (List<LocalMessage>) -> Unit,
    onClearConversation: (Relationship) -> Unit,
    onSetMuted: (Boolean) -> Unit,
    onChatBackgroundSelected: (String?) -> Unit,
) {
    val tokens = LocalKrakenThemeTokens.current
    val clipboardManager = LocalClipboardManager.current
    var draft by remember(relationship.relationshipId) { mutableStateOf("") }
    val conversationId = MessageService.conversationIdFor(relationship)
    val conversationMessages = MessageService.sortConversationMessages(
        messages.filter { it.conversationId == conversationId },
    )
    val decision = localIdentity?.let {
        RealmCommunicationPolicy.canUseRelationship(it, relationship, realmSnapshot)
    } ?: RealmCommunicationDecision(false)
    val canSend = decision.allowed
    val conversationItems = remember(conversationMessages) {
        buildConversationItems(conversationMessages.takeLast(80)).asReversed()
    }
    val listState = rememberLazyListState()
    val latestMessageKey = conversationMessages.lastOrNull()?.messageId
    var actionCandidate by remember { mutableStateOf<MessageActionCandidate?>(null) }
    val messageBounds = remember(relationship.relationshipId) { mutableStateMapOf<String, Rect>() }
    var quotedMessage by remember(relationship.relationshipId) { mutableStateOf<LocalMessage?>(null) }
    var selectedMessageIds by remember(relationship.relationshipId) { mutableStateOf<Set<String>>(emptySet()) }
    val localReactions = remember(relationship.relationshipId) { mutableStateMapOf<String, String>() }
    var clearConversationRequested by remember { mutableStateOf(false) }
    var backgroundPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId, latestMessageKey) {
        if (latestMessageKey != null) {
            listState.animateScrollToItem(0)
        }
    }

    val selectedMessages = remember(selectedMessageIds, conversationMessages) {
        conversationMessages.filter { it.messageId in selectedMessageIds }
    }
    val selectionMode = selectedMessageIds.isNotEmpty()

    BackHandler {
        if (selectionMode) {
            selectedMessageIds = emptySet()
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        ChatConversationBackground(
            backgroundKey = chatBackgroundKey,
            modifier = Modifier.fillMaxSize(),
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                Column {
                    if (selectionMode) {
                        MessageSelectionTopBar(
                            selectedTotal = selectedMessageIds.size,
                            onClose = { selectedMessageIds = emptySet() },
                        )
                    } else {
                        ConversationTopBar(
                            relationship = relationship,
                            subtitle = meshAwareContactSubtitle(relationship, meshSnapshot),
                            hasMessages = conversationMessages.isNotEmpty(),
                            muted = muted,
                            onBack = onBack,
                            onClick = onOpenContactProfile,
                            onSetMuted = onSetMuted,
                            onClearConversation = { clearConversationRequested = true },
                            onOpenBackgroundPicker = { backgroundPickerOpen = true },
                        )
                    }
                    ChatThinDivider()
                }
            }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = tokens.screenPadding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
            ) {
                if (conversationMessages.isEmpty()) {
                    item {
                        EmptyConversationHint(canSend, RealmCommunicationPolicy.blockReasonLabel(decision.blockReason))
                    }
                } else {
                    itemsIndexed(conversationItems, key = { _, item -> item.key }) { _, item ->
                        when (item) {
                            is ConversationListItem.DateHeader -> DatePill(item.label)
                            is ConversationListItem.MessageRow -> MessageBubble(
                                message = item.message,
                                startsGroup = item.startsGroup,
                                selected = item.message.messageId in selectedMessageIds,
                                selectionMode = selectionMode,
                                localReaction = localReactions[item.message.messageId],
                                onReplyRequested = { quotedMessage = it },
                                onQuickReaction = { message ->
                                    if (localReactions[message.messageId] == quickReaction) {
                                        localReactions.remove(message.messageId)
                                    } else {
                                        localReactions[message.messageId] = quickReaction
                                    }
                                },
                                onActionRequested = { message ->
                                    actionCandidate = MessageActionCandidate(
                                        message = message,
                                        anchorBounds = messageBounds[message.messageId],
                                    )
                                },
                                onToggleSelection = { message ->
                                    selectedMessageIds = if (message.messageId in selectedMessageIds) {
                                        selectedMessageIds - message.messageId
                                    } else {
                                        selectedMessageIds + message.messageId
                                    }
                                    actionCandidate = null
                                },
                                onStartSelection = { message ->
                                    selectedMessageIds = selectedMessageIds + message.messageId
                                    actionCandidate = null
                                },
                                onRetryMessage = onRetryMessage,
                                onBoundsChanged = { message, bounds ->
                                    messageBounds[message.messageId] = bounds
                                },
                            )
                        }
                    }
                }
            }
            ChatThinDivider()
            if (selectionMode) {
                MessageSelectionActionBar(
                    selectedTotal = selectedMessageIds.size,
                    onSave = {
                        onSaveMessagesToFavorites(selectedMessages)
                        selectedMessageIds = emptySet()
                    },
                    onDelete = {
                        onDeleteMessages(selectedMessages)
                        selectedMessageIds = emptySet()
                    },
                    onCancel = { selectedMessageIds = emptySet() },
                )
            } else {
                ChatComposer(
                    draft = draft,
                    canSend = canSend,
                    blockReason = RealmCommunicationPolicy.blockReasonLabel(decision.blockReason),
                    quotedMessage = quotedMessage,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                        .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
                    onDraftChanged = { draft = it },
                    onCancelReply = { quotedMessage = null },
                    onSend = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty() && canSend) {
                            onSendMessage(relationship, trimmed, quotedMessage)
                            draft = ""
                            quotedMessage = null
                        }
                    },
                )
            }
        }
    }

    actionCandidate?.let { candidate ->
        val message = candidate.message
        MessageActionMenuOverlay(
            message = message,
            anchorBounds = candidate.anchorBounds,
            onDismiss = { actionCandidate = null },
            onRetry = {
                onRetryMessage(message)
                actionCandidate = null
            },
            quickReaction = quickReaction,
            onReact = { reaction ->
                if (localReactions[message.messageId] == reaction) {
                    localReactions.remove(message.messageId)
                } else {
                    localReactions[message.messageId] = reaction
                }
                actionCandidate = null
            },
            onReply = {
                quotedMessage = message
                actionCandidate = null
            },
            onCopy = {
                clipboardManager.setText(AnnotatedString(message.body))
                actionCandidate = null
            },
            onSave = {
                onSaveMessagesToFavorites(listOf(message))
                actionCandidate = null
            },
            onSelect = {
                selectedMessageIds = setOf(message.messageId)
                actionCandidate = null
            },
            onDelete = {
                onDeleteMessage(message)
                actionCandidate = null
            },
        )
    }

    if (clearConversationRequested) {
        AlertDialog(
            onDismissRequest = { clearConversationRequested = false },
            title = { Text("Очистить чат?") },
            text = { Text("Сообщения будут удалены только на этом устройстве. Контакт и QR-доверие останутся.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearConversation(relationship)
                        clearConversationRequested = false
                    },
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConversationRequested = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (backgroundPickerOpen) {
        ChatBackgroundPickerDialog(
            title = "Фон этого чата",
            selectedBackgroundKey = if (chatBackgroundKey == globalChatBackground) null else chatBackgroundKey,
            globalBackgroundKey = globalChatBackground,
            includeSystemDefault = true,
            onSelect = { backgroundKey ->
                onChatBackgroundSelected(backgroundKey)
                backgroundPickerOpen = false
            },
            onDismiss = { backgroundPickerOpen = false },
        )
    }
}

@Composable
private fun ChatConversationBackground(
    backgroundKey: String,
    modifier: Modifier = Modifier,
) {
    val normalizedBackground = ChatPreferencesStore.normalizeBackground(backgroundKey)
    val base = MaterialTheme.colorScheme.background
    val line = MaterialTheme.colorScheme.primary.copy(alpha = 0.055f)
    val secondaryLine = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
    val deepSignalDot = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
    val backgroundColor = when (normalizedBackground) {
        "amoled_black" -> Color.Black
        "solid_dark" -> Color(0xFF101418)
        "deep_signal" -> Color(0xFF071B1D)
        else -> base
    }
    Canvas(modifier = modifier.background(backgroundColor)) {
        if (normalizedBackground == "solid_dark" || normalizedBackground == "amoled_black") {
            return@Canvas
        }
        val step = 92.dp.toPx()
        val shortStroke = 34.dp.toPx()
        val longStroke = 58.dp.toPx()
        val stroke = 1.dp.toPx()
        var row = 0
        var y = -step
        while (y < size.height + step) {
            var x = if (row % 2 == 0) -step / 2f else 0f
            var column = 0
            while (x < size.width + step) {
                val tilt = if ((row + column) % 2 == 0) shortStroke else longStroke
                val color = if ((row + column) % 3 == 0) line else secondaryLine
                drawLine(
                    color = color,
                    start = Offset(x, y + step * 0.72f),
                    end = Offset(x + tilt, y + step * 0.46f),
                    strokeWidth = stroke,
                )
                x += step
                column += 1
            }
            y += step
            row += 1
        }
        if (normalizedBackground == "deep_signal") {
            val dotStep = 74.dp.toPx()
            var dotY = dotStep * 0.4f
            var dotRow = 0
            while (dotY < size.height) {
                var dotX = if (dotRow % 2 == 0) dotStep * 0.5f else dotStep
                while (dotX < size.width) {
                    drawCircle(
                        color = deepSignalDot,
                        radius = 1.6.dp.toPx(),
                        center = Offset(dotX, dotY),
                    )
                    dotX += dotStep
                }
                dotY += dotStep
                dotRow += 1
            }
        }
        drawRect(Color.Black.copy(alpha = if (normalizedBackground == "deep_signal") 0.08f else 0.18f))
    }
}

@Composable
private fun ChatThinDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
    )
}

@Composable
private fun ConversationTopBar(
    relationship: Relationship,
    subtitle: String,
    hasMessages: Boolean,
    muted: Boolean,
    onBack: () -> Unit,
    onClick: () -> Unit,
    onSetMuted: (Boolean) -> Unit,
    onClearConversation: () -> Unit,
    onOpenBackgroundPicker: () -> Unit,
) {
    val tokens = LocalKrakenThemeTokens.current
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = tokens.screenPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(KrakenIcons.Back, contentDescription = "К списку чатов")
        }
        ContactAvatar(
            name = relationship.peerDisplayName,
            leadingIcon = null,
            size = 42.dp,
            modifier = Modifier.clickable(onClick = onClick),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                relationship.peerDisplayName ?: "Неизвестный контакт",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.semantics { contentDescription = "Действия чата" },
            ) {
                Icon(KrakenIcons.MoreVert, contentDescription = null)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Профиль контакта") },
                    onClick = {
                        menuExpanded = false
                        onClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (muted) "Включить уведомления" else "Отключить уведомления") },
                    onClick = {
                        menuExpanded = false
                        onSetMuted(!muted)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Фон чата") },
                    onClick = {
                        menuExpanded = false
                        onOpenBackgroundPicker()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Очистить переписку") },
                    enabled = hasMessages,
                    onClick = {
                        menuExpanded = false
                        onClearConversation()
                    },
                )
            }
        }
    }
}

@Composable
private fun ContactHeader(
    relationship: Relationship,
    subtitle: String,
    onClick: () -> Unit,
) {
    KrakenCompactCard(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ContactAvatar(
                name = relationship.peerDisplayName,
                leadingIcon = null,
                size = 42.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    relationship.peerDisplayName ?: "Неизвестный контакт",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(KrakenIcons.Contacts, contentDescription = "Профиль контакта")
        }
    }
}

@Composable
private fun ContactAvatar(
    name: String?,
    leadingIcon: ImageVector?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(size * 0.55f),
                )
            } else {
                Text(
                    name?.take(2)?.uppercase() ?: "QR",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EmptyConversationHint(canSend: Boolean, blockReason: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            if (canSend) {
                "Сообщений пока нет. Напишите первое сообщение."
            } else {
                "Переписка недоступна: $blockReason."
            },
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatComposer(
    draft: String,
    canSend: Boolean,
    blockReason: String,
    quotedMessage: LocalMessage?,
    modifier: Modifier = Modifier,
    onDraftChanged: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
) {
    val canSubmit = canSend && draft.isNotBlank()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 0.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
                quotedMessage?.let { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 2.dp, end = 8.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .heightIn(min = 34.dp)
                                        .padding(vertical = 1.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(999.dp),
                                ) {
                                    Text(" ", modifier = Modifier.padding(horizontal = 2.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (target.direction == MessageDirection.OUTGOING) {
                                            "Ответ на ваше сообщение"
                                        } else {
                                            "Ответ контакту"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        target.body.replace('\n', ' ').take(72),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        TextButton(onClick = onCancelReply) {
                            Text("×")
                        }
                    }
                }
                ClassicEmojiStrip(
                    enabled = canSend,
                    onEmojiSelected = { emoji -> onDraftChanged(draft + emoji) },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChanged,
                        enabled = canSend,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        minLines = 1,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (draft.isEmpty()) {
                                    Text(
                                        if (canSend) "Сообщение" else blockReason,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    IconButton(
                        onClick = onSend,
                        enabled = canSubmit,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = "Отправить сообщение"
                            },
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (canSubmit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    KrakenIcons.Send,
                                    contentDescription = null,
                                    tint = if (canSubmit) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier
                                        .size(23.dp)
                                        .alpha(if (canSubmit) 1f else 0.45f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ClassicMessengerEmojis = listOf(
    "🙂",
    "😁",
    "😉",
    "😎",
    "😢",
    "😡",
    "😘",
    "😍",
    "😜",
    "😇",
    "😴",
    "🌼",
)

@Composable
private fun ClassicEmojiStrip(
    enabled: Boolean,
    onEmojiSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 0.dp, end = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClassicMessengerEmojis.forEach { emoji ->
            Surface(
                modifier = Modifier
                    .size(34.dp)
                    .alpha(if (enabled) 1f else 0.42f)
                    .clickable(enabled = enabled) { onEmojiSelected(emoji) }
                    .semantics {
                        contentDescription = "Добавить смайлик $emoji"
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                tonalElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        emoji,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun ContactProfileScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    relationships: List<Relationship>,
    messages: List<LocalMessage>,
    realmSnapshot: RealmSnapshot,
    selectedRelationshipId: String?,
    mutedRelationshipIds: Set<String>,
    onRelationshipUpdated: (Relationship) -> Unit,
    onComplaintCreated: (ComplaintEvent) -> Unit,
    onCancelPairing: (Relationship) -> Unit,
    onForgetRelationship: (Relationship) -> Unit,
    onClearConversation: (Relationship) -> Unit,
    onSetRelationshipMuted: (Relationship, Boolean) -> Unit,
    onOpenChat: (Relationship) -> Unit,
) {
    val relationship = relationships.firstOrNull { it.relationshipId == selectedRelationshipId }
    val handshakeService = remember { OfflineHandshakeService() }
    var handshakePayload by remember(relationship?.relationshipId) { mutableStateOf<HandshakeQrPayloadView?>(null) }
    var handshakeError by remember(relationship?.relationshipId) { mutableStateOf<String?>(null) }
    var pendingUnlinkReason by remember { mutableStateOf<UnlinkReason?>(null) }
    var actionSheetOpen by remember(relationship?.relationshipId) { mutableStateOf(false) }
    var deleteReasonSheetOpen by remember(relationship?.relationshipId) { mutableStateOf(false) }
    var cancelPairingRequested by remember(relationship?.relationshipId) { mutableStateOf(false) }
    var forgetRelationshipRequested by remember(relationship?.relationshipId) { mutableStateOf(false) }
    var clearConversationRequested by remember(relationship?.relationshipId) { mutableStateOf(false) }

    ScreenContainer("Профиль", navController, showTitle = false) {
        if (relationship == null) {
            EmptyState("Контакт не выбран", "Откройте профиль из списка чатов.")
            return@ScreenContainer
        }
        val conversationId = MessageService.conversationIdFor(relationship)
        val hasMessages = messages.any { it.conversationId == conversationId }
        val muted = relationship.relationshipId in mutedRelationshipIds
        ContactProfileHero(
            relationship = relationship,
            onOpenChat = if (relationship.state == RelationshipState.ACTIVE) {
                { onOpenChat(relationship) }
            } else {
                null
            },
            onOpenActions = { actionSheetOpen = true },
        )
        TrustSummaryCard(relationship)

        TechnicalDetailsDisclosure(if (relationship.state == RelationshipState.ACTIVE) "QR-детали" else "QR-рукопожатие") {
            HandshakeActionPanel(
                navController = navController,
                relationship = relationship,
                localIdentity = localIdentity,
                realmSnapshot = realmSnapshot,
                handshakeService = handshakeService,
                handshakePayload = handshakePayload,
                handshakeError = handshakeError,
                onRelationshipUpdated = onRelationshipUpdated,
                onOpenChat = onOpenChat,
                onPayloadReady = {
                    handshakePayload = it
                    handshakeError = null
                },
                onError = {
                    handshakePayload = null
                    handshakeError = it
                },
                onClearPayload = { handshakePayload = null },
            )
        }

        TechnicalDetailsDisclosure("Технические детали") {
            WarningCard(
                "Граница доверия",
                listOf(
                    "Контакт активируется только QR-рукопожатием.",
                    "Обнаружение транспорта само по себе не создаёт доверие.",
                    "Сообщения проходят через локальный контур обмена для выбранного маршрута.",
                ),
            )
            ProfileLine("relationshipId", relationship.relationshipId)
            ProfileLine("peerPublicKey", relationship.peerPublicKey.take(48))
            ProfileLine("offlineRole", relationship.offlineHandshakeRole?.name ?: "none")
        }

        if (actionSheetOpen) {
            ContactActionsSheet(
                relationship = relationship,
                muted = muted,
                hasMessages = hasMessages,
                onDismiss = { actionSheetOpen = false },
                onOpenChat = {
                    actionSheetOpen = false
                    onOpenChat(relationship)
                },
                onToggleMuted = {
                    actionSheetOpen = false
                    onSetRelationshipMuted(relationship, !muted)
                },
                onClearConversation = {
                    actionSheetOpen = false
                    clearConversationRequested = true
                },
                onCancelPairing = {
                    actionSheetOpen = false
                    cancelPairingRequested = true
                },
                onForgetRelationship = {
                    actionSheetOpen = false
                    forgetRelationshipRequested = true
                },
                onDeleteContact = {
                    actionSheetOpen = false
                    deleteReasonSheetOpen = true
                },
            )
        }
    }

    val targetRelationship = relationship
    if (targetRelationship != null && clearConversationRequested) {
        ClearConversationSheet(
            relationship = targetRelationship,
            onDismiss = { clearConversationRequested = false },
            onConfirm = {
                onClearConversation(targetRelationship)
                clearConversationRequested = false
            },
        )
    }

    if (targetRelationship != null && cancelPairingRequested) {
        ContactCancelPairingSheet(
            relationship = targetRelationship,
            onDismiss = { cancelPairingRequested = false },
            onConfirm = {
                onCancelPairing(targetRelationship)
                cancelPairingRequested = false
                navController.navigate(KrakenRoute.Contacts.route) {
                    popUpTo(KrakenRoute.ContactProfile.route) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }

    if (targetRelationship != null && forgetRelationshipRequested) {
        ContactForgetRelationshipSheet(
            relationship = targetRelationship,
            onDismiss = { forgetRelationshipRequested = false },
            onConfirm = {
                onForgetRelationship(targetRelationship)
                forgetRelationshipRequested = false
                navController.navigate(KrakenRoute.Contacts.route) {
                    popUpTo(KrakenRoute.ContactProfile.route) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }

    if (targetRelationship != null && deleteReasonSheetOpen) {
        ContactDeleteReasonSheet(
            relationship = targetRelationship,
            onDismiss = { deleteReasonSheetOpen = false },
            onReasonSelected = { reason ->
                deleteReasonSheetOpen = false
                pendingUnlinkReason = reason
            },
        )
    }

    pendingUnlinkReason?.let { reason ->
        ContactUnlinkSheet(
            reason = reason,
            onDismiss = { pendingUnlinkReason = null },
            onConfirm = {
                val target = relationship ?: return@ContactUnlinkSheet
                val result = RelationshipService.unlinkRelationship(target, reason)
                onRelationshipUpdated(result.relationship)
                result.complaintEvent?.let(onComplaintCreated)
                pendingUnlinkReason = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactActionsSheet(
    relationship: Relationship,
    muted: Boolean,
    hasMessages: Boolean,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onToggleMuted: () -> Unit,
    onClearConversation: () -> Unit,
    onCancelPairing: () -> Unit,
    onForgetRelationship: () -> Unit,
    onDeleteContact: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                relationship.peerDisplayName ?: "Контакт",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ContactActionSheetButton("Открыть чат", enabled = relationship.state == RelationshipState.ACTIVE, onClick = onOpenChat)
            ContactActionSheetButton(
                if (muted) "Включить уведомления" else "Отключить уведомления",
                onClick = onToggleMuted,
            )
            ContactActionSheetButton("Очистить переписку", enabled = hasMessages, onClick = onClearConversation)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (relationship.state in setOf(RelationshipState.PENDING_IMPORT, RelationshipState.PENDING_HANDSHAKE)) {
                ContactActionSheetButton("Отменить сопряжение", destructive = true, onClick = onCancelPairing)
            } else {
                ContactActionSheetButton("Забыть устройство", destructive = true, onClick = onForgetRelationship)
                ContactActionSheetButton("Разорвать с причиной", destructive = true, onClick = onDeleteContact)
            }
        }
    }
}

@Composable
private fun ContactActionSheetButton(
    label: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            color = if (destructive && enabled) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            textAlign = TextAlign.Start,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearConversationSheet(
    relationship: Relationship,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Очистить переписку?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Сообщения с ${relationship.peerDisplayName ?: "контактом"} будут удалены только на этом устройстве. Сопряжение сохранится.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("Очистить")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Отмена")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactCancelPairingSheet(
    relationship: Relationship,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Отменить сопряжение?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Запись ${relationship.peerDisplayName ?: "контакта"} будет удалена только с этого устройства. Для новой попытки понадобится заново отсканировать QR.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("Отменить сопряжение")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Оставить")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactForgetRelationshipSheet(
    relationship: Relationship,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Забыть устройство?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Локальная связь с ${relationship.peerDisplayName ?: "контактом"} будет удалена. Для новой попытки понадобится заново отсканировать QR.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("Забыть устройство")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Оставить")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDeleteReasonSheet(
    relationship: Relationship,
    onDismiss: () -> Unit,
    onReasonSelected: (UnlinkReason) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Почему удалить контакт?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                relationship.peerDisplayName ?: "Контакт",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UnlinkReason.entries.forEach { reason ->
                ContactActionSheetButton(unlinkReasonLabel(reason), onClick = { onReasonSelected(reason) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactUnlinkSheet(
    reason: UnlinkReason,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Удалить контакт?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Причина: ${unlinkReasonLabel(reason)}. Контакт будет недоступен для переписки без нового QR-подтверждения.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("Удалить контакт")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Отмена")
            }
        }
    }
}

@Composable
private fun ContactProfileHero(
    relationship: Relationship,
    onOpenChat: (() -> Unit)?,
    onOpenActions: () -> Unit,
) {
    KrakenCompactCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ContactAvatar(
                name = relationship.peerDisplayName,
                leadingIcon = null,
                size = 72.dp,
            )
            Text(
                relationship.peerDisplayName ?: "Неизвестный контакт",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                relationshipBadge(relationship.state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            onOpenChat?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) {
                        Text("Открыть чат")
                    }
                    Button(onClick = onOpenActions, modifier = Modifier.weight(1f)) {
                        Text("Действия")
                    }
                }
            } ?: Button(onClick = onOpenActions, modifier = Modifier.fillMaxWidth()) {
                Text("Действия")
            }
        }
    }
}

@Composable
private fun TrustSummaryCard(relationship: Relationship) {
    KrakenCompactCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Доверие", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${trustLabel(relationship.state)} · ${relationship.peerFingerprint.shortContactFingerprint()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            relationship.realmId?.let { realmId ->
                Text(
                    realmId.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ProfileLine("Создан", formatEpoch(relationship.createdAtEpochMillis))
    }
}

@Composable
private fun HandshakeActionPanel(
    navController: NavHostController,
    relationship: Relationship,
    localIdentity: LocalIdentity?,
    realmSnapshot: RealmSnapshot,
    handshakeService: OfflineHandshakeService,
    handshakePayload: HandshakeQrPayloadView?,
    handshakeError: String?,
    onRelationshipUpdated: (Relationship) -> Unit,
    onOpenChat: (Relationship) -> Unit,
    onPayloadReady: (HandshakeQrPayloadView) -> Unit,
    onError: (String) -> Unit,
    onClearPayload: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RelationshipHandshakeActions(
            navController = navController,
            relationship = relationship,
            localIdentity = localIdentity,
            realmSnapshot = realmSnapshot,
            handshakeService = handshakeService,
            onRelationshipUpdated = onRelationshipUpdated,
            onOpenChat = onOpenChat,
            onPayloadReady = onPayloadReady,
            onError = onError,
        )
        handshakeError?.let {
            WarningCard("Действие недоступно", listOf(it))
        }
        handshakePayload?.let { payload ->
            PayloadQrCodeCard(
                title = payload.title,
                payloadJson = payload.payloadJson,
                details = payload.details,
            )
            OutlinedButton(onClick = onClearPayload, modifier = Modifier.fillMaxWidth()) {
                Text("Скрыть QR")
            }
        }
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun lastConversationMessage(
    relationship: Relationship,
    messages: List<LocalMessage>,
): LocalMessage? {
    val conversationId = MessageService.conversationIdFor(relationship)
    return MessageService.sortConversationMessages(
        messages.filter { it.conversationId == conversationId },
    ).lastOrNull()
}

private fun chatListSubtitle(message: LocalMessage?, allowed: Boolean): String {
    if (!allowed) {
        return message?.let { "${conversationSummary(it)} · отправка недоступна" } ?: "Отправка недоступна"
    }
    return message?.let(::conversationSummary) ?: "Нет сообщений"
}

private fun conversationSummary(message: LocalMessage): String {
    if (isTechnicalMessageBody(message.body)) {
        return "Служебное сообщение"
    }
    val author = if (message.direction == MessageDirection.OUTGOING) "Вы: " else ""
    val body = message.body.replace('\n', ' ').take(48).ifBlank { "сообщение" }
    return "$author$body"
}

internal fun isTechnicalMessageBody(body: String): Boolean {
    val trimmed = body.trim()
    if (trimmed.isBlank()) return false
    val normalized = trimmed.lowercase(Locale.ROOT)
    return trimmed.startsWith("{") ||
        trimmed.startsWith("[") ||
        normalized.contains("message_id") ||
        normalized.contains("packet_id") ||
        normalized.contains("payload") ||
        normalized.contains("route_specific") ||
        normalized.contains("route trial") ||
        normalized.contains("benchmark trial") ||
        normalized.contains("inline relay") ||
        normalized.contains("queue_retry") ||
        normalized.contains("debug")
}

internal fun technicalMessagePreview(body: String): String =
    body.replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
        .ifBlank { "Технические данные скрыты" }

private fun compactChatTimestamp(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale("ru", "RU")).format(Date(epochMillis))

private fun meshAwareContactSubtitle(
    relationship: Relationship,
    meshSnapshot: MeshServiceSnapshot,
): String {
    val route = PeerRouteAggregator.routeFor(relationship, meshSnapshot)
    return PeerRouteFormatter.subtitle(route, meshSnapshot.state, meshSnapshot.lastSyncAtEpochMillis)
}

private sealed interface ConversationListItem {
    val key: String

    data class DateHeader(
        override val key: String,
        val label: String,
    ) : ConversationListItem

    data class MessageRow(
        val message: LocalMessage,
        val startsGroup: Boolean,
    ) : ConversationListItem {
        override val key: String = message.messageId
    }
}

private fun buildConversationItems(messages: List<LocalMessage>): List<ConversationListItem> {
    val items = mutableListOf<ConversationListItem>()
    var currentDay: String? = null
    var previousMessage: LocalMessage? = null
    messages.forEach { message ->
        val dayKey = conversationDayKey(message.createdAtEpochMillis)
        if (dayKey != currentDay) {
            items += ConversationListItem.DateHeader(
                key = "date-$dayKey",
                label = conversationDayLabel(message.createdAtEpochMillis),
            )
            currentDay = dayKey
            previousMessage = null
        }
        items += ConversationListItem.MessageRow(
            message = message,
            startsGroup = previousMessage?.let { previous ->
                previous.direction != message.direction ||
                    message.createdAtEpochMillis - previous.createdAtEpochMillis > MESSAGE_GROUP_GAP_MILLIS
            } ?: true,
        )
        previousMessage = message
    }
    return items
}

private const val MESSAGE_GROUP_GAP_MILLIS = 5 * 60 * 1000L

private data class MessageActionCandidate(
    val message: LocalMessage,
    val anchorBounds: Rect?,
)

private fun conversationDayKey(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(epochMillis))

private fun conversationDayLabel(epochMillis: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(target, today) -> "Сегодня"
        sameDay(target, yesterday) -> "Вчера"
        else -> SimpleDateFormat("d MMMM", Locale("ru", "RU")).format(Date(epochMillis))
    }
}

private fun sameDay(left: Calendar, right: Calendar): Boolean =
    left.get(Calendar.YEAR) == right.get(Calendar.YEAR) &&
        left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR)

private fun relationshipBadge(state: RelationshipState): String =
    when (state) {
        RelationshipState.ACTIVE -> "Активный контакт"
        RelationshipState.PENDING_IMPORT -> "Ожидает QR"
        RelationshipState.PENDING_HANDSHAKE -> "Нужно рукопожатие"
        RelationshipState.UNLINK_REQUESTED -> "Завершение контакта"
        RelationshipState.UNLINKED -> "Контакт завершён"
        RelationshipState.BLOCKED_BY_PEER -> "Заблокирован"
        RelationshipState.REJOIN_REQUIRED -> "Нужно новое QR"
    }

private fun trustLabel(state: RelationshipState): String =
    if (state == RelationshipState.ACTIVE) "Проверено QR" else "Не завершено"

private fun String.shortContactFingerprint(): String =
    if (length <= 12) this else "${take(4)}…${takeLast(4)}"

@Composable
private fun DatePill(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageBubble(
    message: LocalMessage,
    startsGroup: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    localReaction: String?,
    onReplyRequested: (LocalMessage) -> Unit,
    onQuickReaction: (LocalMessage) -> Unit,
    onActionRequested: (LocalMessage) -> Unit,
    onToggleSelection: (LocalMessage) -> Unit,
    onStartSelection: (LocalMessage) -> Unit,
    onRetryMessage: (LocalMessage) -> Unit,
    onBoundsChanged: (LocalMessage, Rect) -> Unit,
) {
    val incoming = message.direction == MessageDirection.INCOMING
    val density = LocalDensity.current
    val replyThresholdPx = with(density) { 54.dp.toPx() }
    val maxSwipePx = with(density) { 86.dp.toPx() }
    var dragOffsetPx by remember(message.messageId) { mutableStateOf(0f) }
    val animatedDragOffsetPx by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "messageSwipeOffset",
    )
    val replyHintVisible = if (incoming) {
        dragOffsetPx > replyThresholdPx * 0.45f
    } else {
        dragOffsetPx < -replyThresholdPx * 0.45f
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.Transparent)
            .padding(top = if (startsGroup) 8.dp else 0.dp),
    ) {
        val bubbleMaxWidth = maxWidth * 0.78f
        val alignment = if (incoming) Alignment.CenterStart else Alignment.CenterEnd
        if (replyHintVisible) {
            Surface(
                modifier = Modifier
                    .align(if (incoming) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 10.dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            ) {
                Text(
                    "↩ Ответ",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (selectionMode) {
            MessageSelectionIndicator(
                selected = selected,
                modifier = Modifier
                    .align(if (incoming) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 6.dp),
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = bubbleMaxWidth)
                .align(alignment)
                .onGloballyPositioned { coordinates ->
                    onBoundsChanged(message, coordinates.boundsInRoot())
                }
                .offset { IntOffset(animatedDragOffsetPx.roundToInt(), 0) }
                .pointerInput(message.messageId) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val shouldReply = if (incoming) {
                                dragOffsetPx >= replyThresholdPx
                            } else {
                                dragOffsetPx <= -replyThresholdPx
                            }
                            if (shouldReply) {
                                onReplyRequested(message)
                            }
                            dragOffsetPx = 0f
                        },
                        onDragCancel = { dragOffsetPx = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            val nextOffset = dragOffsetPx + dragAmount
                            dragOffsetPx = if (incoming) {
                                nextOffset.coerceIn(0f, maxSwipePx)
                            } else {
                                nextOffset.coerceIn(-maxSwipePx, 0f)
                            }
                        },
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (selectionMode) {
                            onToggleSelection(message)
                        } else {
                            onActionRequested(message)
                        }
                    },
                    onDoubleClick = {
                        if (selectionMode) {
                            onToggleSelection(message)
                        } else if (message.status == MessageStatus.FAILED) {
                            onActionRequested(message)
                        } else {
                            onQuickReaction(message)
                        }
                    },
                    onLongClick = { onStartSelection(message) },
                ),
            horizontalAlignment = if (incoming) Alignment.Start else Alignment.End,
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomEnd = if (incoming) 18.dp else 4.dp,
                    bottomStart = if (incoming) 4.dp else 18.dp,
                ),
                color = if (incoming) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    MessageReplyPreview(message)
                    MessageBody(message, incoming)
                    MessageMetaRow(
                        message = message,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
            if (!selectionMode && message.direction == MessageDirection.OUTGOING && message.status == MessageStatus.FAILED) {
                FailedMessageInlineRetry(onRetry = { onRetryMessage(message) })
            }
            localReaction?.let { reaction ->
                Surface(
                    modifier = Modifier.padding(top = 2.dp, start = 8.dp, end = 8.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Text(
                        reaction,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBody(message: LocalMessage, incoming: Boolean) {
    if (isTechnicalMessageBody(message.body)) {
        TechnicalMessageBody(message.body, incoming)
    } else {
        Text(
            message.body,
            style = MaterialTheme.typography.bodyMedium,
            color = if (incoming) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun TechnicalMessageBody(body: String, incoming: Boolean) {
    val labelColor = if (incoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
    val previewColor = if (incoming) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (incoming) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.46f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.24f)
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "Служебное сообщение",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                technicalMessagePreview(body),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = previewColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FailedMessageInlineRetry(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.padding(top = 4.dp, end = 4.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onRetry)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = KrakenIcons.Retry,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Повторить",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MessageSelectionIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(24.dp),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = KrakenIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.outlineVariant,
                ) {}
            }
        }
    }
}

@Composable
private fun MessageReplyPreview(message: LocalMessage) {
    val preview = message.replyToBodyPreview?.takeIf { it.isNotBlank() } ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Surface(
            modifier = Modifier.heightIn(min = 34.dp),
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(" ", modifier = Modifier.padding(horizontal = 2.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                "Ответ на ${message.replyToSenderName ?: "сообщение"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MessageMetaRow(
    message: LocalMessage,
    modifier: Modifier = Modifier,
) {
    val outgoing = message.direction == MessageDirection.OUTGOING
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            compactChatTimestamp(message.createdAtEpochMillis),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val statusLabel = visibleMeshDeliveryLabel(message.status)
        if (outgoing && statusLabel != null) {
            Text(
                statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun visibleMeshDeliveryLabel(status: MessageStatus): String? =
    when (status) {
        MessageStatus.DELIVERED_TO_PEER -> "доставлено"
        MessageStatus.SENT_TO_TRANSPORT -> "отправлено"
        MessageStatus.LOCAL_PENDING,
        MessageStatus.READY_FOR_TRANSPORT -> "ждёт маршрут"
        MessageStatus.FAILED -> "ошибка"
    }

@Composable
private fun MessageSelectionTopBar(
    selectedTotal: Int,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(KrakenIcons.Back, contentDescription = "Отменить выбор")
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Выбрано: $selectedTotal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Можно сохранить в избранное или удалить локально", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageSelectionActionBar(
    selectedTotal: Int,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text("В избранное")
            }
            Button(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text("Удалить $selectedTotal")
            }
            TextButton(onClick = onCancel) {
                Text("Отмена")
            }
        }
    }
}

@Composable
private fun MessageActionMenuOverlay(
    message: LocalMessage,
    anchorBounds: Rect?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    quickReaction: String,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val retryable = message.direction == MessageDirection.OUTGOING && message.status == MessageStatus.FAILED
    val density = LocalDensity.current
    val menuWidth = 252.dp
    val estimatedMenuHeight = if (retryable) 276.dp else 240.dp
    val dismissInteractionSource = remember { MutableInteractionSource() }
    val menuInteractionSource = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = dismissInteractionSource,
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        val menuWidthPx = with(density) { menuWidth.toPx() }
        val menuHeightPx = with(density) { estimatedMenuHeight.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }
        val composerReservePx = with(density) { 112.dp.toPx() }
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = (constraints.maxHeight.toFloat() - composerReservePx).coerceAtLeast(menuHeightPx + marginPx * 2)
        val maxOffsetX = (maxWidthPx - menuWidthPx - marginPx).coerceAtLeast(marginPx)
        val maxOffsetY = (maxHeightPx - menuHeightPx - marginPx).coerceAtLeast(marginPx)
        val anchor = anchorBounds
        val offsetX = if (anchor != null) {
            val anchorGapPx = with(density) { 8.dp.toPx() }
            val preferred = if (message.direction == MessageDirection.OUTGOING) {
                anchor.left - menuWidthPx - anchorGapPx
            } else {
                anchor.right + anchorGapPx
            }
            preferred.coerceIn(marginPx, maxOffsetX)
        } else {
            (maxWidthPx - menuWidthPx - with(density) { 22.dp.toPx() }).coerceIn(marginPx, maxOffsetX)
        }
        val offsetY = if (anchor != null) {
            val below = anchor.bottom + with(density) { 8.dp.toPx() }
            val above = anchor.top - menuHeightPx - with(density) { 8.dp.toPx() }
            val preferred = if (below + menuHeightPx <= maxHeightPx - marginPx) below else above
            preferred.coerceIn(marginPx, maxOffsetY)
        } else {
            (maxHeightPx - menuHeightPx - with(density) { 96.dp.toPx() }).coerceIn(marginPx, maxOffsetY)
        }
        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .widthIn(min = 224.dp, max = menuWidth)
                .clickable(
                    interactionSource = menuInteractionSource,
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp)
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("👍", "❤️", "😂", "😮", "😢", "🔥", quickReaction).distinct().take(6).forEach { reaction ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable(onClick = { onReact(reaction) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(reaction, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (retryable) {
                    MessageMenuRow(KrakenIcons.Retry, "Повторить", onRetry)
                }
                MessageMenuRow(KrakenIcons.Reply, "Ответить", onReply)
                MessageMenuRow(KrakenIcons.Copy, "Копировать", onCopy)
                MessageMenuRow(KrakenIcons.Star, "В избранное", onSave)
                MessageMenuRow(KrakenIcons.Check, "Выбрать", onSelect)
                MessageMenuRow(KrakenIcons.Delete, "Удалить", onDelete)
            }
        }
    }
}

@Composable
private fun MessageMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun unlinkReasonLabel(reason: UnlinkReason): String =
    when (reason) {
        UnlinkReason.ENDED_INTERACTION -> "общение завершено"
        UnlinkReason.UNWANTED_MESSAGES -> "нежелательные сообщения"
        UnlinkReason.SPAM -> "спам"
        UnlinkReason.THREAT_PRESSURE_OR_ETHICS_ABUSE -> "давление или угроза"
        UnlinkReason.OTHER -> "другая причина"
    }

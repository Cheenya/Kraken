package com.disser.kraken.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.disser.kraken.mesh.BlePermissions
import com.disser.kraken.mesh.KrakenMessageNotifier
import com.disser.kraken.mesh.MeshState
import com.disser.kraken.mesh.WifiDirectPermissions
import com.disser.kraken.ui.components.KrakenScaffold
import com.disser.kraken.qr.KrakenQrPayloadCodec
import com.disser.kraken.ui.screens.ChannelsScreen
import com.disser.kraken.ui.screens.ChatScreen
import com.disser.kraken.ui.screens.ContactProfileScreen
import com.disser.kraken.ui.screens.ContactsScreen
import com.disser.kraken.ui.screens.CreateIdentityScreen
import com.disser.kraken.ui.screens.HomeScreen
import com.disser.kraken.ui.screens.ImportInviteScreen
import com.disser.kraken.ui.screens.MeshStatusScreen
import com.disser.kraken.ui.screens.MyQrScreen
import com.disser.kraken.ui.screens.PendingApprovalsScreen
import com.disser.kraken.ui.screens.RealmManageScreen
import com.disser.kraken.ui.screens.RealmsScreen
import com.disser.kraken.ui.screens.ResearchScreen
import com.disser.kraken.ui.screens.SettingsScreen
import com.disser.kraken.ui.screens.SavedMessagesScreen
import com.disser.kraken.ui.screens.ThemePickerScreen
import com.disser.kraken.ui.screens.TwoPhoneChecklistScreen
import com.disser.kraken.ui.screens.WelcomeScreen
import com.disser.kraken.ui.screens.experimental.UiLabScreen
import com.disser.kraken.ui.theme.KrakenTheme
import com.disser.kraken.qr.QrScannerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun KrakenApp(
    launchIntent: Intent? = null,
    onLaunchReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val appState = remember { KrakenAppState(context.applicationContext) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val appScope = rememberCoroutineScope()
    val connectivityPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { appState.restartMeshAfterPermissionChange() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { appState.refreshMeshSnapshot() }
    val syncMeshNow: () -> Unit = {
        appScope.launch { appState.syncMeshNow() }
    }
    val openRelationshipId = launchIntent?.getStringExtra(KrakenMessageNotifier.EXTRA_OPEN_RELATIONSHIP_ID)
    val qrDeepLinkText = launchIntent
        ?.dataString
        ?.takeIf { KrakenQrPayloadCodec.isSupportedUri(it) }
    var consumedQrDeepLinkText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        val missingConnectivityPermissions = (
            BlePermissions.requiredRuntimePermissions() + WifiDirectPermissions.requiredRuntimePermissions()
            )
            .distinct()
            .filterNot { permission ->
                context.applicationContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
        if (missingConnectivityPermissions.isNotEmpty()) {
            connectivityPermissionLauncher.launch(missingConnectivityPermissions)
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(appState) {
        while (true) {
            delay(2_000)
            appState.refreshMeshSnapshot()
        }
    }

    LaunchedEffect(
        appState.localIdentity?.identityId,
        appState.meshSnapshot.state,
        appState.meshSnapshot.lastPacketStatus,
    ) {
        if (appState.localIdentity == null) {
            onLaunchReady()
            return@LaunchedEffect
        }
        appState.ensureMeshStarted()
        val launchWasRequested = appState.meshSnapshot.lastPacketStatus == "service-start-requested"
        val meshIsStartingOrRunning = appState.meshSnapshot.state !in setOf(MeshState.OFF, MeshState.ERROR)
        if (launchWasRequested || meshIsStartingOrRunning) {
            onLaunchReady()
        }
    }

    LaunchedEffect(openRelationshipId) {
        if (!openRelationshipId.isNullOrBlank()) {
            appState.selectChatRelationshipId(openRelationshipId)
            navController.navigate(KrakenRoute.Chat.route) {
                launchSingleTop = true
                popUpTo(KrakenRoute.Welcome.route) { inclusive = false }
            }
        }
    }
    LaunchedEffect(qrDeepLinkText) {
        if (!qrDeepLinkText.isNullOrBlank() && qrDeepLinkText != consumedQrDeepLinkText) {
            navController.navigate(KrakenRoute.QrScanner.route) {
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(appState.latestHandshakeCompletion?.relationshipId) {
        val completed = appState.latestHandshakeCompletion ?: return@LaunchedEffect
        if (backStackEntry?.destination?.route != KrakenRoute.Contacts.route) {
            navController.navigate(KrakenRoute.Contacts.route) {
                launchSingleTop = true
            }
        }
        appState.consumeHandshakeCompletion(completed.relationshipId)
    }

    KrakenTheme(preset = appState.themePreset) {
        val selectedActiveChat = appState.relationships.any { relationship ->
            relationship.relationshipId == appState.selectedChatRelationshipId &&
                relationship.state == com.disser.kraken.relationship.RelationshipState.ACTIVE
        }
        val showBottomBar = !(backStackEntry?.destination?.route == KrakenRoute.Chat.route && selectedActiveChat)
        val startDestination = KrakenRoute.Welcome.route
        KrakenScaffold(
            navController = navController,
            showBottomBar = showBottomBar,
            onBottomRouteSelected = { route ->
                if (route == KrakenRoute.Chat) {
                    appState.clearChatSelection()
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(padding),
            ) {
                composable(KrakenRoute.Welcome.route) {
                    WelcomeScreen(
                        navController = navController,
                        hasLocalIdentity = appState.localIdentity != null,
                    )
                }
                composable(KrakenRoute.Home.route) {
                    HomeScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        pendingInvites = appState.pendingInvites,
                        relationships = appState.relationships,
                        complaints = appState.complaints,
                        realmSnapshot = appState.realmSnapshot,
                        meshSnapshot = appState.meshSnapshot,
                        onStartMesh = appState::startMesh,
                        onSyncMeshNow = syncMeshNow,
                    )
                }
                composable(KrakenRoute.CreateIdentity.route) {
                    CreateIdentityScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        onIdentityCreated = appState::createIdentity,
                    )
                }
                composable(KrakenRoute.MyQr.route) {
                    MyQrScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        directInviteRecord = appState.currentDirectInviteRecord(),
                        directInvitePayload = appState.currentDirectInvitePayload(),
                        meshSnapshot = appState.meshSnapshot,
                        onCreateDirectInvite = appState::createDirectInvite,
                        onRevokeInvite = appState::revokeIssuedInvite,
                    )
                }
                composable(KrakenRoute.ImportInvite.route) {
                    ImportInviteScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        pendingInvites = appState.pendingInvites,
                        onInviteJsonImported = appState::importInviteJson,
                    )
                }
                composable(KrakenRoute.QrScanner.route) {
                    QrScannerScreen(
                        navController = navController,
                        onInviteQrScanned = appState::processScannedQrPayload,
                        meshSnapshot = appState.meshSnapshot,
                        onOpenChat = { relationship ->
                            appState.selectChatRelationship(relationship)
                            navController.navigate(KrakenRoute.Chat.route) {
                                popUpTo(KrakenRoute.QrScanner.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        initialScannedText = qrDeepLinkText?.takeIf { it != consumedQrDeepLinkText },
                        onInitialScannedTextConsumed = { consumedQrDeepLinkText = it },
                    )
                }
                composable(KrakenRoute.Contacts.route) {
                    ContactsScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        pendingInvites = appState.pendingInvites,
                        relationships = appState.relationships,
                        realmSnapshot = appState.realmSnapshot,
                        onRelationshipUpdated = appState::updateRelationship,
                        onCancelPairing = appState::cancelRelationshipPairing,
                        onOpenContactProfile = { relationship ->
                            appState.selectChatRelationship(relationship)
                            navController.navigate(KrakenRoute.ContactProfile.route)
                        },
                        onOpenChat = { relationship ->
                            appState.selectChatRelationship(relationship)
                            navController.navigate(KrakenRoute.Chat.route)
                        },
                    )
                }
                composable(KrakenRoute.Realms.route) {
                    RealmsScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        realmSnapshot = appState.realmSnapshot,
                        onCreateRealm = appState::createRealm,
                        onRealmUpdated = appState::updateRealm,
                        onCreateDemoPendingRequest = appState::createDemoPendingRequest,
                        onManageRealm = { realm ->
                            appState.selectRealm(realm.realmId)
                            navController.navigate(KrakenRoute.RealmManage.route)
                        },
                    )
                }
                composable(KrakenRoute.RealmManage.route) {
                    RealmManageScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        realm = appState.realmSnapshot.realms.firstOrNull { it.realmId == appState.selectedRealmId },
                        realmSnapshot = appState.realmSnapshot,
                        onRealmUpdated = appState::updateRealm,
                        onCreateDemoPendingRequest = appState::createDemoPendingRequest,
                        onDeleteLocalRealmRecord = appState::deleteLocalRealmRecord,
                        onPromoteMember = appState::promoteRealmMember,
                        onDemoteMember = appState::demoteRealmMember,
                        onRestrictMember = appState::restrictRealmMember,
                        onRestoreMember = appState::restoreRealmMember,
                        onRemoveMember = appState::removeRealmMember,
                        onCreateRealmInvite = appState::createRealmInvite,
                        issuedInvites = appState.issuedInvites,
                        onRevokeIssuedInvite = appState::revokeIssuedInvite,
                    )
                }
                composable(KrakenRoute.PendingApprovals.route) {
                    PendingApprovalsScreen(
                        navController = navController,
                        pendingRequests = appState.realmSnapshot.pendingRequests,
                        realms = appState.realmSnapshot.realms,
                        certificates = appState.realmSnapshot.membershipCertificates,
                        relationships = appState.relationships,
                        complaints = appState.complaints,
                        localIdentity = appState.localIdentity,
                        onApprovalOutcome = appState::applyApprovalOutcome,
                    )
                }
                composable(KrakenRoute.Chat.route) {
                    ChatScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        relationships = appState.relationships,
                        messages = appState.messages,
                        savedMessages = appState.savedMessages,
                        realmSnapshot = appState.realmSnapshot,
                        meshSnapshot = appState.meshSnapshot,
                        selectedRelationshipId = appState.selectedChatRelationshipId,
                        mutedRelationshipIds = appState.mutedRelationshipIds,
                        quickReaction = appState.quickReaction,
                        globalChatBackground = appState.globalChatBackground,
                        chatBackgroundKey = appState.chatBackgroundFor(appState.selectedChatRelationshipId),
                        onRelationshipSelected = appState::selectChatRelationship,
                        onConversationBack = appState::clearChatSelection,
                        onOpenContactProfile = { relationship ->
                            appState.selectChatRelationship(relationship)
                            navController.navigate(KrakenRoute.ContactProfile.route)
                        },
                        onSendMessage = { relationship, body, replyToMessage ->
                            appState.sendLocalMessage(relationship, body, replyToMessage)
                            syncMeshNow()
                        },
                        onRetryMessage = { message ->
                            appState.retryMessage(message.messageId)
                            syncMeshNow()
                        },
                        onDeleteMessage = { message ->
                            appState.deleteMessage(message.messageId)
                        },
                        onDeleteMessages = { messages ->
                            appState.deleteMessages(messages.map { it.messageId }.toSet())
                        },
                        onSaveMessagesToFavorites = appState::saveMessagesToFavorites,
                        onClearConversation = appState::clearConversation,
                        onSetRelationshipMuted = appState::setRelationshipMuted,
                        onChatBackgroundSelected = appState::selectChatBackgroundOverride,
                    )
                }
                composable(KrakenRoute.SavedMessages.route) {
                    SavedMessagesScreen(
                        navController = navController,
                        savedMessages = appState.savedMessages,
                    )
                }
                composable(KrakenRoute.ContactProfile.route) {
                    ContactProfileScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        relationships = appState.relationships,
                        messages = appState.messages,
                        realmSnapshot = appState.realmSnapshot,
                        selectedRelationshipId = appState.selectedChatRelationshipId,
                        mutedRelationshipIds = appState.mutedRelationshipIds,
                        onRelationshipUpdated = appState::updateRelationship,
                        onComplaintCreated = appState::addComplaint,
                        onCancelPairing = appState::cancelRelationshipPairing,
                        onForgetRelationship = appState::forgetRelationship,
                        onClearConversation = appState::clearConversation,
                        onSetRelationshipMuted = appState::setRelationshipMuted,
                        onOpenChat = { relationship ->
                            appState.selectChatRelationship(relationship)
                            navController.navigate(KrakenRoute.Chat.route) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(KrakenRoute.Channels.route) {
                    ChannelsScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        realmSnapshot = appState.realmSnapshot,
                        channelSnapshot = appState.channelSnapshot,
                        smallGroupSnapshot = appState.smallGroupSnapshot,
                        onCreateDemoChannel = appState::createDemoChannel,
                        onMembershipUpdated = appState::updateChannelMembership,
                        onCreateDemoSmallGroup = appState::createDemoSmallGroup,
                    )
                }
                composable(KrakenRoute.MeshStatus.route) {
                    MeshStatusScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        relationships = appState.relationships,
                        realmSnapshot = appState.realmSnapshot,
                        relayPolicyState = appState.relayPolicyState,
                        meshSnapshot = appState.meshSnapshot,
                        selectedTransportProfile = appState.meshTransportProfile,
                        onStartMesh = appState::startMesh,
                        onStartHotspotCompatibleMesh = appState::startHotspotCompatibleMesh,
                        onStartWifiDirectTrialMesh = appState::startWifiDirectTrialMesh,
                        onStopMesh = appState::stopMesh,
                        onSyncMeshNow = syncMeshNow,
                        onAddManualPeer = { fingerprint, host, port -> appState.addManualLanPeer(fingerprint, host, port) },
                    )
                }
                composable(KrakenRoute.TwoPhoneChecklist.route) {
                    TwoPhoneChecklistScreen(
                        navController = navController,
                        meshSnapshot = appState.meshSnapshot,
                        onRunDebugEvidenceProbe = appState::runDebugRouteSpecificEvidenceProbe,
                    )
                }
                composable(KrakenRoute.Settings.route) {
                    SettingsScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        meshSnapshot = appState.meshSnapshot,
                        selectedTransportProfile = appState.meshTransportProfile,
                        themePreset = appState.themePreset,
                        quickReaction = appState.quickReaction,
                        globalChatBackground = appState.globalChatBackground,
                        onStartMesh = appState::startMesh,
                        onStartHotspotCompatibleMesh = appState::startHotspotCompatibleMesh,
                        onStartWifiDirectTrialMesh = appState::startWifiDirectTrialMesh,
                        onStopMesh = appState::stopMesh,
                        onSyncMeshNow = syncMeshNow,
                        onDisplayNameChanged = appState::updateDisplayName,
                        onQuickReactionSelected = appState::selectQuickReaction,
                        onGlobalChatBackgroundSelected = appState::selectGlobalChatBackground,
                    )
                }
                composable(KrakenRoute.ThemePicker.route) {
                    ThemePickerScreen(
                        navController = navController,
                        themePreset = appState.themePreset,
                        onThemePresetSelected = appState::selectThemePreset,
                    )
                }
                composable(KrakenRoute.Research.route) { ResearchScreen(navController) }
                composable(KrakenRoute.UiLab.route) {
                    UiLabScreen(
                        navController = navController,
                        localIdentity = appState.localIdentity,
                        pendingInvites = appState.pendingInvites,
                        relationships = appState.relationships,
                        complaints = appState.complaints,
                        realmSnapshot = appState.realmSnapshot,
                        onLoadDemoData = appState::loadDemoData,
                        onResetDemoData = appState::resetDemoData,
                    )
                }
            }
        }
    }
}

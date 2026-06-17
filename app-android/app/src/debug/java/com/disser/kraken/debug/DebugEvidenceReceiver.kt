package com.disser.kraken.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.disser.kraken.BuildConfig
import com.disser.kraken.mesh.DebugDirectSendResult
import com.disser.kraken.mesh.MeshDebugTransportRelationshipAlignment
import com.disser.kraken.mesh.MeshEvidenceRouteAttemptExport
import com.disser.kraken.mesh.MeshEvidenceSnapshotExport
import com.disser.kraken.mesh.MeshEvidenceTransportExport
import com.disser.kraken.mesh.MeshEvidenceTransportPathExport
import com.disser.kraken.mesh.MeshEvidenceWifiDirectPermissionExport
import com.disser.kraken.mesh.MeshEvidenceExporter
import com.disser.kraken.mesh.MeshForegroundService
import com.disser.kraken.mesh.MeshRuntime
import com.disser.kraken.mesh.MeshServiceSnapshot
import com.disser.kraken.mesh.WifiDirectPermissionDiagnostics
import com.disser.kraken.mesh.WifiDirectPermissions
import com.disser.kraken.mesh.WifiDirectBoundEndpointDiagnostic
import com.disser.kraken.mesh.WifiDirectConnectAttemptDiagnostic
import com.disser.kraken.mesh.WifiDirectPeerEndpointDiagnostic
import com.disser.kraken.mesh.WifiDirectTxtRecordDiagnostic
import com.disser.kraken.mesh.WifiDirectVisibleDeviceDiagnostic
import com.disser.kraken.ui.components.TransportPathReadiness
import com.disser.kraken.ui.components.TransportReadinessMonitor
import java.io.File
import kotlinx.coroutines.runBlocking

class DebugEvidenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG || intent.action != ACTION_EXPORT_ROUTE_EVIDENCE) return

        val pendingResult = goAsync()
        Thread(
            {
                val appContext = context.applicationContext
                runCatching {
                    val runtime = MeshRuntime.get(appContext)
                    val startMeshRequested = intent.getBooleanExtra(EXTRA_START_MESH_BEFORE_EXPORT, false)
                    val probeRequested = intent.getBooleanExtra(EXTRA_RUN_LOCAL_HOSTILE_PROBE, false)
                    val transportProfile = intent.getStringExtra(EXTRA_TRANSPORT_PROFILE)?.trim().orEmpty()
                    val manualPeerFingerprint = intent.getStringExtra(EXTRA_MANUAL_PEER_FINGERPRINT)?.trim().orEmpty()
                    val manualPeerHost = intent.getStringExtra(EXTRA_MANUAL_PEER_HOST)?.trim().orEmpty()
                    val manualPeerPort = intent.getIntExtra(EXTRA_MANUAL_PEER_PORT, 0)
                    val debugWifiDirectPeerDeviceAddress = intent
                        .getStringExtra(EXTRA_DEBUG_WIFI_DIRECT_PEER_DEVICE_ADDRESS)
                        ?.trim()
                        .orEmpty()
                    val debugWifiDirectPeerDeviceName = intent
                        .getStringExtra(EXTRA_DEBUG_WIFI_DIRECT_PEER_DEVICE_NAME)
                        ?.trim()
                        .orEmpty()
                    val debugWifiDirectPeerPort = intent.getIntExtra(EXTRA_DEBUG_WIFI_DIRECT_PEER_PORT, 0)
                    val debugSendBody = intent.getStringExtra(EXTRA_DEBUG_SEND_BODY)?.trim().orEmpty()
                    val syncAfterDebugSend = intent.getBooleanExtra(EXTRA_SYNC_AFTER_DEBUG_SEND, false)
                    val syncBeforeExport = intent.getBooleanExtra(EXTRA_SYNC_BEFORE_EXPORT, false)
                    val debugSendAttempts = intent.getIntExtra(EXTRA_DEBUG_SEND_ATTEMPTS, DEFAULT_DEBUG_SEND_ATTEMPTS)
                        .coerceIn(1, MAX_DEBUG_SEND_ATTEMPTS)
                    val debugSendRetryDelayMs = intent.getIntExtra(
                        EXTRA_DEBUG_SEND_RETRY_DELAY_MS,
                        DEFAULT_DEBUG_SEND_RETRY_DELAY_MS,
                    ).coerceIn(0, MAX_DEBUG_SEND_RETRY_DELAY_MS)
                    val syncAttempts = intent.getIntExtra(EXTRA_SYNC_ATTEMPTS, DEFAULT_SYNC_ATTEMPTS)
                        .coerceIn(0, MAX_SYNC_ATTEMPTS)
                    val startMeshSettleMs = intent.getIntExtra(EXTRA_START_MESH_SETTLE_MS, DEFAULT_START_MESH_SETTLE_MS)
                        .coerceIn(0, MAX_START_MESH_SETTLE_MS)
                    val reuseRunningMesh = intent.getBooleanExtra(EXTRA_REUSE_RUNNING_MESH, false)
                    val startForegroundWifiDirect = intent.getBooleanExtra(EXTRA_START_FOREGROUND_WIFI_DIRECT, false)
                    val ensureWifiDirectGroupOwner = intent.getBooleanExtra(EXTRA_ENSURE_WIFI_DIRECT_GROUP_OWNER, false)
                    val forceStopMeshBeforeStart = intent.getBooleanExtra(EXTRA_FORCE_STOP_MESH_BEFORE_START, false)
                    val holdAfterExportMs = intent.getIntExtra(EXTRA_HOLD_AFTER_EXPORT_MS, 0)
                        .coerceIn(0, MAX_HOLD_AFTER_EXPORT_MS)
                    val debugSendWaitMs = intent.getIntExtra(EXTRA_DEBUG_SEND_WAIT_MS, 0)
                        .coerceIn(0, MAX_DEBUG_SEND_WAIT_MS)
                    val manualPeerRequested = manualPeerFingerprint.isNotBlank() ||
                        manualPeerHost.isNotBlank() ||
                        manualPeerPort > 0
                    val debugWifiDirectPeerRequested = debugWifiDirectPeerDeviceAddress.isNotBlank() ||
                        debugWifiDirectPeerPort > 0
                    val debugSendRequested = debugSendBody.isNotBlank()
                    val needsMeshStart = startMeshRequested ||
                        (!reuseRunningMesh && (manualPeerRequested || debugWifiDirectPeerRequested || debugSendRequested))
                    if (forceStopMeshBeforeStart && (needsMeshStart || startForegroundWifiDirect)) {
                        runtime.stop()
                    }
                    if (startForegroundWifiDirect) {
                        MeshForegroundService.startDebugWifiDirectOnly(appContext)
                        Thread.sleep(startMeshSettleMs.toLong())
                    }
                    if (needsMeshStart) {
                        when (transportProfile) {
                            TRANSPORT_PROFILE_HOTSPOT_COMPATIBLE -> runtime.startHotspotCompatible()
                            TRANSPORT_PROFILE_WIFI_DIRECT_ONLY -> runtime.startDebugWifiDirectOnly()
                            TRANSPORT_PROFILE_LAN_ONLY -> runtime.startDebugLanOnly()
                            else -> runtime.start()
                        }
                        Thread.sleep(startMeshSettleMs.toLong())
                    }
                    val ensureWifiDirectGroupOwnerResult = if (ensureWifiDirectGroupOwner) {
                        runtime.ensureDebugWifiDirectGroupOwner()
                    } else {
                        null
                    }
                    val manualPeerSnapshot = if (manualPeerRequested) {
                        runtime.addManualLanPeer(
                            fingerprint = manualPeerFingerprint,
                            host = manualPeerHost,
                            port = manualPeerPort,
                        )
                    } else {
                        null
                    }
                    val debugWifiDirectPeerSnapshot = if (debugWifiDirectPeerRequested) {
                        runtime.addDebugWifiDirectPeerForFirstRelationship(
                            deviceAddress = debugWifiDirectPeerDeviceAddress,
                            deviceName = debugWifiDirectPeerDeviceName.ifBlank { null },
                            port = debugWifiDirectPeerPort,
                        )
                    } else {
                        null
                    }
                    var transportRelationshipAlignment = runtime.debugTransportRelationshipAlignment()
                    var debugSendWaitSatisfied: Boolean? = null
                    if (debugSendRequested && debugSendWaitMs > 0) {
                        debugSendWaitSatisfied = waitForDebugSendableTransportPeer(runtime, debugSendWaitMs)
                        transportRelationshipAlignment = runtime.debugTransportRelationshipAlignment()
                    }
                    val debugSendResults = mutableListOf<DebugDirectSendResult>()
                    val directSendResult = if (debugSendRequested) {
                        var lastResult: DebugDirectSendResult? = null
                        for (attemptIndex in 0 until debugSendAttempts) {
                            if (attemptIndex > 0 && debugSendRetryDelayMs > 0) {
                                Thread.sleep(debugSendRetryDelayMs.toLong())
                            }
                            lastResult = runtime.sendDebugDirectTextToFirstSendableRelationship(debugSendBody)
                            lastResult?.let(debugSendResults::add)
                            if (lastResult?.success == true) break
                        }
                        lastResult
                    } else {
                        null
                    }
                    transportRelationshipAlignment = runtime.debugTransportRelationshipAlignment()
                    var lastSyncSummary: String? = null
                    if (syncAfterDebugSend && directSendResult?.success == false) {
                        lastSyncSummary = "skipped_after_failed_direct_send"
                    } else if (syncBeforeExport || (syncAfterDebugSend && directSendResult?.success != true)) {
                        lastSyncSummary = runSyncAttempts(runtime, syncAttempts)
                    }
                    writeEvidenceFiles(
                        context = appContext,
                        runtime = runtime,
                        recordProbeBeforeSnapshot = probeRequested,
                        startMeshRequested = startMeshRequested,
                        probeRequested = probeRequested,
                        transportProfile = transportProfile,
                        manualPeerRequested = manualPeerRequested,
                        manualPeerStatus = manualPeerSnapshot?.lastPacketStatus,
                        debugWifiDirectPeerRequested = debugWifiDirectPeerRequested,
                        debugWifiDirectPeerDeviceAddress = debugWifiDirectPeerDeviceAddress,
                        debugWifiDirectPeerDeviceName = debugWifiDirectPeerDeviceName,
                        debugWifiDirectPeerPort = debugWifiDirectPeerPort,
                        debugWifiDirectPeerStatus = debugWifiDirectPeerSnapshot?.lastPacketStatus,
                        debugSendRequested = debugSendRequested,
                        debugMessageId = directSendResult?.message?.messageId,
                        debugMessageBody = directSendResult?.message?.body,
                        debugPacketId = directSendResult?.packetId,
                        debugSendSuccess = directSendResult?.success,
                        debugSendError = directSendResult?.error?.name,
                        debugSendAttempts = debugSendAttempts,
                        debugSendRetryDelayMs = debugSendRetryDelayMs,
                        debugSendResults = debugSendResults,
                        debugSendWaitMs = debugSendWaitMs,
                        debugSendWaitSatisfied = debugSendWaitSatisfied,
                        startForegroundWifiDirect = startForegroundWifiDirect,
                        ensureWifiDirectGroupOwner = ensureWifiDirectGroupOwner,
                        ensureWifiDirectGroupOwnerResult = ensureWifiDirectGroupOwnerResult,
                        forceStopMeshBeforeStart = forceStopMeshBeforeStart,
                        holdAfterExportMs = holdAfterExportMs,
                        syncAfterDebugSend = syncAfterDebugSend,
                        syncBeforeExport = syncBeforeExport,
                        syncAttempts = syncAttempts,
                        startMeshSettleMs = startMeshSettleMs,
                        reuseRunningMesh = reuseRunningMesh,
                        transportRelationshipAlignment = transportRelationshipAlignment,
                        lastSyncSummary = lastSyncSummary,
                    )
                    if (holdAfterExportMs > 0) {
                        Thread(
                            {
                                runCatching {
                                    Thread.sleep(holdAfterExportMs.toLong())
                                    writeEvidenceFiles(
                                        context = appContext,
                                        runtime = runtime,
                                        recordProbeBeforeSnapshot = false,
                                        startMeshRequested = startMeshRequested,
                                        probeRequested = probeRequested,
                                        transportProfile = transportProfile,
                                        manualPeerRequested = manualPeerRequested,
                                        manualPeerStatus = manualPeerSnapshot?.lastPacketStatus,
                                        debugWifiDirectPeerRequested = debugWifiDirectPeerRequested,
                                        debugWifiDirectPeerDeviceAddress = debugWifiDirectPeerDeviceAddress,
                                        debugWifiDirectPeerDeviceName = debugWifiDirectPeerDeviceName,
                                        debugWifiDirectPeerPort = debugWifiDirectPeerPort,
                                        debugWifiDirectPeerStatus = debugWifiDirectPeerSnapshot?.lastPacketStatus,
                                        debugSendRequested = debugSendRequested,
                                        debugMessageId = directSendResult?.message?.messageId,
                                        debugMessageBody = directSendResult?.message?.body,
                                        debugPacketId = directSendResult?.packetId,
                                        debugSendSuccess = directSendResult?.success,
                                        debugSendError = directSendResult?.error?.name,
                                        debugSendAttempts = debugSendAttempts,
                                        debugSendRetryDelayMs = debugSendRetryDelayMs,
                                        debugSendResults = debugSendResults,
                                        debugSendWaitMs = debugSendWaitMs,
                                        debugSendWaitSatisfied = debugSendWaitSatisfied,
                                        startForegroundWifiDirect = startForegroundWifiDirect,
                                        ensureWifiDirectGroupOwner = ensureWifiDirectGroupOwner,
                                        ensureWifiDirectGroupOwnerResult = ensureWifiDirectGroupOwnerResult,
                                        forceStopMeshBeforeStart = forceStopMeshBeforeStart,
                                        holdAfterExportMs = holdAfterExportMs,
                                        syncAfterDebugSend = syncAfterDebugSend,
                                        syncBeforeExport = syncBeforeExport,
                                        syncAttempts = syncAttempts,
                                        startMeshSettleMs = startMeshSettleMs,
                                        reuseRunningMesh = reuseRunningMesh,
                                        transportRelationshipAlignment = runtime.debugTransportRelationshipAlignment(),
                                        lastSyncSummary = lastSyncSummary,
                                    )
                                }
                            },
                            "kraken-debug-evidence-post-hold-refresh",
                        ).start()
                    }
                }.getOrElse { error ->
                    File(appContext.filesDir, COMMAND_RESULT_FILE).writeText(errorResultJson(error))
                }
                pendingResult.finish()
            },
            "kraken-debug-evidence-export",
        ).start()
    }

    private fun writeEvidenceFiles(
        context: Context,
        runtime: MeshRuntime,
        recordProbeBeforeSnapshot: Boolean,
        startMeshRequested: Boolean,
        probeRequested: Boolean,
        transportProfile: String,
        manualPeerRequested: Boolean,
        manualPeerStatus: String?,
        debugWifiDirectPeerRequested: Boolean,
        debugWifiDirectPeerDeviceAddress: String,
        debugWifiDirectPeerDeviceName: String,
        debugWifiDirectPeerPort: Int,
        debugWifiDirectPeerStatus: String?,
        debugSendRequested: Boolean,
        debugMessageId: String?,
        debugMessageBody: String?,
        debugPacketId: String?,
        debugSendSuccess: Boolean?,
        debugSendError: String?,
        debugSendAttempts: Int,
        debugSendRetryDelayMs: Int,
        debugSendResults: List<DebugDirectSendResult>,
        debugSendWaitMs: Int,
        debugSendWaitSatisfied: Boolean?,
        startForegroundWifiDirect: Boolean,
        ensureWifiDirectGroupOwner: Boolean,
        ensureWifiDirectGroupOwnerResult: String?,
        forceStopMeshBeforeStart: Boolean,
        holdAfterExportMs: Int,
        syncAfterDebugSend: Boolean,
        syncBeforeExport: Boolean,
        syncAttempts: Int,
        startMeshSettleMs: Int,
        reuseRunningMesh: Boolean,
        transportRelationshipAlignment: MeshDebugTransportRelationshipAlignment,
        lastSyncSummary: String?,
    ) {
        val snapshot = if (recordProbeBeforeSnapshot) {
            runtime.recordDebugNegativeEvidence()
        } else {
            runtime.snapshot()
        }
        val transportReadiness = buildTransportReadiness(context, snapshot)
        val export = MeshEvidenceExporter.build(
            snapshot = snapshot,
            generatedAtEpochMillis = System.currentTimeMillis(),
            appBuildType = "debug",
            appVersionName = BuildConfig.KRAKEN_VERSION_NAME,
            gitSha = BuildConfig.GIT_SHA,
            deviceModel = Build.MODEL,
            sourceState = BuildConfig.SOURCE_STATE,
            transportReadiness = transportReadiness,
            messages = runtime.loadMessages(),
            receivedPackets = runtime.loadReceivedPackets(),
        )
        val filesDir = context.filesDir
        File(filesDir, ROUTE_JSON_FILE).writeText(MeshEvidenceExporter.toJson(export))
        File(filesDir, ROUTE_MD_FILE).writeText(MeshEvidenceExporter.toMarkdownSummary(export))
        File(filesDir, COMMAND_RESULT_FILE).writeText(
            commandResultJson(
                startMeshRequested = startMeshRequested,
                probeRequested = probeRequested,
                transportProfile = transportProfile.ifBlank { TRANSPORT_PROFILE_ALL },
                manualPeerRequested = manualPeerRequested,
                manualPeerStatus = manualPeerStatus,
                debugWifiDirectPeerRequested = debugWifiDirectPeerRequested,
                debugWifiDirectPeerDeviceAddress = debugWifiDirectPeerDeviceAddress,
                debugWifiDirectPeerDeviceName = debugWifiDirectPeerDeviceName,
                debugWifiDirectPeerPort = debugWifiDirectPeerPort,
                debugWifiDirectPeerStatus = debugWifiDirectPeerStatus,
                debugSendRequested = debugSendRequested,
                debugMessageId = debugMessageId,
                debugMessageBody = debugMessageBody,
                debugPacketId = debugPacketId,
                debugSendSuccess = debugSendSuccess,
                debugSendError = debugSendError,
                debugSendAttempts = debugSendAttempts,
                debugSendRetryDelayMs = debugSendRetryDelayMs,
                debugSendResults = debugSendResults,
                debugSendWaitMs = debugSendWaitMs,
                debugSendWaitSatisfied = debugSendWaitSatisfied,
                startForegroundWifiDirect = startForegroundWifiDirect,
                ensureWifiDirectGroupOwner = ensureWifiDirectGroupOwner,
                ensureWifiDirectGroupOwnerResult = ensureWifiDirectGroupOwnerResult,
                forceStopMeshBeforeStart = forceStopMeshBeforeStart,
                holdAfterExportMs = holdAfterExportMs,
                syncAfterDebugSend = syncAfterDebugSend,
                syncBeforeExport = syncBeforeExport,
                syncAttempts = syncAttempts,
                startMeshSettleMs = startMeshSettleMs,
                reuseRunningMesh = reuseRunningMesh,
                transportRelationshipAlignment = transportRelationshipAlignment,
                lastSyncSummary = lastSyncSummary,
                export = export,
            ),
        )
    }

    private fun runSyncAttempts(runtime: MeshRuntime, syncAttempts: Int): String? {
        var lastSyncSummary: String? = null
        var syncObservedActivity = false
        repeat(syncAttempts.coerceAtLeast(1)) {
            if (syncObservedActivity) return@repeat
            val result = runBlocking { runtime.syncNow() }
            lastSyncSummary = result.snapshot.lastSyncSummary
            if (result.sentCount > 0 || result.receivedCount > 0 || result.rejectedCount > 0) {
                syncObservedActivity = true
            }
            Thread.sleep(SYNC_ATTEMPT_SETTLE_MS)
        }
        return lastSyncSummary
    }

    private fun waitForDebugSendableTransportPeer(runtime: MeshRuntime, waitMs: Int): Boolean {
        val deadline = System.currentTimeMillis() + waitMs
        do {
            val alignment = runtime.debugTransportRelationshipAlignment()
            if (alignment.sendableRelationshipCount > 0 && alignment.relationshipPeerSeenByTransport) {
                return true
            }
            Thread.sleep(DEBUG_SEND_WAIT_POLL_MS)
        } while (System.currentTimeMillis() < deadline)
        return false
    }

    private fun commandResultJson(
        startMeshRequested: Boolean,
        probeRequested: Boolean,
        transportProfile: String,
        manualPeerRequested: Boolean,
        manualPeerStatus: String?,
        debugWifiDirectPeerRequested: Boolean,
        debugWifiDirectPeerDeviceAddress: String,
        debugWifiDirectPeerDeviceName: String,
        debugWifiDirectPeerPort: Int,
        debugWifiDirectPeerStatus: String?,
        debugSendRequested: Boolean,
        debugMessageId: String?,
        debugMessageBody: String?,
        debugPacketId: String?,
        debugSendSuccess: Boolean?,
        debugSendError: String?,
        debugSendAttempts: Int,
        debugSendRetryDelayMs: Int,
        debugSendResults: List<DebugDirectSendResult>,
        debugSendWaitMs: Int,
        debugSendWaitSatisfied: Boolean?,
        startForegroundWifiDirect: Boolean,
        ensureWifiDirectGroupOwner: Boolean,
        ensureWifiDirectGroupOwnerResult: String?,
        forceStopMeshBeforeStart: Boolean,
        holdAfterExportMs: Int,
        syncAfterDebugSend: Boolean,
        syncBeforeExport: Boolean,
        syncAttempts: Int,
        startMeshSettleMs: Int,
        reuseRunningMesh: Boolean,
        transportRelationshipAlignment: MeshDebugTransportRelationshipAlignment,
        lastSyncSummary: String?,
        export: MeshEvidenceSnapshotExport,
    ): String =
        buildString {
            appendLine("{")
            appendLine("  \"action\": \"$ACTION_EXPORT_ROUTE_EVIDENCE\",")
            appendLine("  \"start_mesh_before_export\": $startMeshRequested,")
            appendLine("  \"run_local_hostile_probe\": $probeRequested,")
            appendLine("  \"transport_profile\": \"$transportProfile\",")
            appendLine("  \"manual_peer_requested\": $manualPeerRequested,")
            appendLine("  \"manual_peer_status\": ${manualPeerStatus?.let { "\"$it\"" } ?: "null"},")
            appendLine("  \"debug_wifi_direct_peer_requested\": $debugWifiDirectPeerRequested,")
            appendLine("  \"debug_wifi_direct_peer_device_address\": ${debugWifiDirectPeerDeviceAddress.ifBlank { null }.jsonStringOrNull()},")
            appendLine("  \"debug_wifi_direct_peer_device_name\": ${debugWifiDirectPeerDeviceName.ifBlank { null }.jsonStringOrNull()},")
            appendLine("  \"debug_wifi_direct_peer_port\": ${debugWifiDirectPeerPort.takeIf { it > 0 } ?: "null"},")
            appendLine("  \"debug_wifi_direct_peer_status\": ${debugWifiDirectPeerStatus.jsonStringOrNull()},")
            appendLine("  \"debug_send_requested\": $debugSendRequested,")
            appendLine("  \"debug_message_id\": ${debugMessageId?.let { "\"$it\"" } ?: "null"},")
            appendLine("  \"debug_message_body\": ${debugMessageBody?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
            appendLine("  \"debug_packet_id\": ${debugPacketId?.let { "\"$it\"" } ?: "null"},")
            appendLine("  \"debug_send_success\": ${debugSendSuccess ?: "null"},")
            appendLine("  \"debug_send_error\": ${debugSendError?.let { "\"$it\"" } ?: "null"},")
            appendLine("  \"debug_send_attempts\": $debugSendAttempts,")
            appendLine("  \"debug_send_retry_delay_ms\": $debugSendRetryDelayMs,")
            appendLine("  \"debug_send_results\": ${debugSendResultsJson(debugSendResults)},")
            appendLine("  \"debug_transport_error\": ${export.transport.lastTransportError.jsonStringOrNull()},")
            appendLine("  \"debug_send_wait_ms\": $debugSendWaitMs,")
            appendLine("  \"debug_send_wait_satisfied\": ${debugSendWaitSatisfied ?: "null"},")
            appendLine("  \"start_foreground_wifi_direct\": $startForegroundWifiDirect,")
            appendLine("  \"ensure_wifi_direct_group_owner\": $ensureWifiDirectGroupOwner,")
            appendLine("  \"ensure_wifi_direct_group_owner_result\": ${ensureWifiDirectGroupOwnerResult.jsonStringOrNull()},")
            appendLine("  \"force_stop_mesh_before_start\": $forceStopMeshBeforeStart,")
            appendLine("  \"hold_after_export_ms\": $holdAfterExportMs,")
            appendLine("  \"p2p_visible_device_count\": ${export.transport.p2pVisibleDeviceCount},")
            appendLine("  \"p2p_service_found_count\": ${export.transport.p2pServiceFoundCount},")
            appendLine("  \"p2p_txt_record_count\": ${export.transport.p2pTxtRecordCount},")
            appendLine("  \"p2p_txt_rejected_count\": ${export.transport.p2pTxtRejectedCount},")
            appendLine("  \"p2p_txt_bound_peer_count\": ${export.transport.p2pTxtBoundPeerCount},")
            appendLine("  \"p2p_unbound_visible_device_count\": ${export.transport.p2pUnboundVisibleDeviceCount},")
            appendLine("  \"wifi_direct_last_binding_error\": ${export.transport.wifiDirectLastBindingError.jsonStringOrNull()},")
            appendLine("  \"wifi_direct_group_role\": ${export.transport.wifiDirectGroupRole.jsonStringOrNull()},")
            appendLine("  \"wifi_direct_group_owner_address\": ${export.transport.wifiDirectGroupOwnerAddress.jsonStringOrNull()},")
            appendLine("  \"wifi_direct_local_p2p_address\": ${export.transport.wifiDirectLocalP2pAddress.jsonStringOrNull()},")
            appendLine("  \"wifi_direct_endpoint_binding_state\": ${export.transport.wifiDirectEndpointBindingState.jsonStringOrNull()},")
            appendLine("  \"wifi_direct_endpoint_binding_reason\": ${export.transport.wifiDirectEndpointBindingReason.jsonStringOrNull()},")
            appendLine("  \"wifi_direct_relationship_peer_fingerprint_prefix\": ${export.transport.wifiDirectRelationshipPeerFingerprintPrefix.jsonStringOrNull()},")
            appendLine("  \"wifi_direct_last_connect_device_address\": ${export.transport.wifiDirectLastConnectDeviceAddress.jsonStringOrNull()},")
            appendLine("  \"wifi_direct_last_connect_device_name\": ${export.transport.wifiDirectLastConnectDeviceName.jsonStringOrNull()},")
            appendLine("  \"wifi_direct_last_connect_group_owner_intent\": ${export.transport.wifiDirectLastConnectGroupOwnerIntent ?: "null"},")
            appendLine("  \"wifi_direct_last_connect_result\": ${export.transport.wifiDirectLastConnectResult.jsonStringOrNull()},")
            appendLine("  \"wifi_direct_last_connect_failure_reason\": ${export.transport.wifiDirectLastConnectFailureReason ?: "null"},")
            appendLine("  \"wifi_direct_connect_attempts\": ${export.transport.wifiDirectConnectAttempts.connectAttemptsJson()},")
            appendLine("  \"wifi_direct_discovered_peers\": ${export.transport.wifiDirectDiscoveredPeers.peerEndpointsJson()},")
            appendLine("  \"wifi_direct_visible_devices\": ${export.transport.wifiDirectVisibleDevices.visibleDevicesJson()},")
            appendLine("  \"wifi_direct_txt_records\": ${export.transport.wifiDirectTxtRecords.txtRecordsJson()},")
            appendLine("  \"wifi_direct_bound_endpoints\": ${export.transport.wifiDirectBoundEndpoints.boundEndpointsJson()},")
            appendLine("  \"wifi_direct_nearby_wifi_devices_required\": ${export.transport.wifiDirectPermissions?.nearbyWifiDevicesRequired ?: false},")
            appendLine("  \"wifi_direct_nearby_wifi_devices_granted\": ${export.transport.wifiDirectPermissions?.nearbyWifiDevicesGranted ?: false},")
            appendLine("  \"wifi_direct_fine_location_required\": ${export.transport.wifiDirectPermissions?.fineLocationRequired ?: false},")
            appendLine("  \"wifi_direct_fine_location_declared\": ${export.transport.wifiDirectPermissions?.fineLocationDeclared ?: false},")
            appendLine("  \"wifi_direct_fine_location_granted\": ${export.transport.wifiDirectPermissions?.fineLocationGranted ?: false},")
            appendLine("  \"wifi_direct_fine_location_app_op\": ${export.transport.wifiDirectPermissions?.fineLocationAppOp.jsonStringOrNull()},")
            appendLine("  \"wifi_direct_permission_warning\": ${export.transport.wifiDirectPermissions?.warning.jsonStringOrNull()},")
            appendLine("  \"sync_after_debug_send\": $syncAfterDebugSend,")
            appendLine("  \"sync_before_export\": $syncBeforeExport,")
            appendLine("  \"sync_attempts\": $syncAttempts,")
            appendLine("  \"start_mesh_settle_ms\": $startMeshSettleMs,")
            appendLine("  \"reuse_running_mesh\": $reuseRunningMesh,")
            appendLine("  \"identity_fingerprint\": ${transportRelationshipAlignment.identityFingerprint.jsonStringOrNull()},")
            appendLine("  \"identity_fingerprint_prefix\": ${transportRelationshipAlignment.identityFingerprintPrefix.jsonStringOrNull()},")
            appendLine("  \"sendable_relationship_count\": ${transportRelationshipAlignment.sendableRelationshipCount},")
            appendLine("  \"first_sendable_relationship_id\": ${transportRelationshipAlignment.firstSendableRelationshipId.jsonStringOrNull()},")
            appendLine("  \"first_sendable_relationship_fingerprint\": ${transportRelationshipAlignment.firstSendableRelationshipFingerprint.jsonStringOrNull()},")
            appendLine("  \"first_sendable_relationship_fingerprint_prefix\": ${transportRelationshipAlignment.firstSendableRelationshipFingerprintPrefix.jsonStringOrNull()},")
            appendLine("  \"first_sendable_relationship_crypto_profile_id\": ${transportRelationshipAlignment.firstSendableRelationshipCryptoProfileId.jsonStringOrNull()},")
            appendLine("  \"first_sendable_relationship_session_profile_id\": ${transportRelationshipAlignment.firstSendableRelationshipSessionProfileId.jsonStringOrNull()},")
            appendLine("  \"first_sendable_relationship_admission_decision_hash\": ${transportRelationshipAlignment.firstSendableRelationshipAdmissionDecisionHash.jsonStringOrNull()},")
            appendLine("  \"first_sendable_relationship_profile_policy_version\": ${transportRelationshipAlignment.firstSendableRelationshipProfilePolicyVersion ?: "null"},")
            appendLine("  \"observed_peer_fingerprints\": ${transportRelationshipAlignment.observedPeerFingerprints.jsonStringList()},")
            appendLine("  \"observed_peer_fingerprint_prefixes\": ${transportRelationshipAlignment.observedPeerFingerprintPrefixes.jsonStringList()},")
            appendLine("  \"relationship_peer_seen_by_transport\": ${transportRelationshipAlignment.relationshipPeerSeenByTransport},")
            appendLine("  \"last_sync_summary\": ${lastSyncSummary?.let { "\"$it\"" } ?: "null"},")
            appendLine("  \"target_recent_message_ids\": ${export.targetDelivery.recentMessages.map { it.messageId }.jsonStringList()},")
            appendLine("  \"target_received_packet_ids\": ${export.targetDelivery.receivedPackets.map { it.packetId }.jsonStringList()},")
            appendLine("  \"target_received_packet_message_ids\": ${export.targetDelivery.receivedPackets.mapNotNull { it.messageId }.jsonStringList()},")
            appendLine("  \"recent_rejected_inbound_packets\": ${export.recentRejectedInboundPackets.rejectedInboundPacketsJson()},")
            appendLine("  \"selected_route\": \"${export.transport.selectedRoute}\",")
            appendLine("  \"enabled_transport_modes\": [${export.transport.enabledTransportModes.joinToString { "\"$it\"" }}],")
            appendLine("  \"wifi_direct_active\": ${export.transport.wifiDirect?.active ?: false},")
            appendLine("  \"evidence_mode\": \"${export.debugSmoke.evidenceMode}\",")
            appendLine("  \"unknown_peer_injected\": ${export.debugSmoke.unknownPeerInjected},")
            appendLine("  \"wrong_recipient_injected\": ${export.debugSmoke.wrongRecipientInjected},")
            appendLine("  \"duplicate_injected\": ${export.debugSmoke.duplicateInjected},")
            appendLine("  \"json_file\": \"$ROUTE_JSON_FILE\",")
            appendLine("  \"markdown_file\": \"$ROUTE_MD_FILE\"")
            appendLine("}")
        }

    private fun List<com.disser.kraken.mesh.MeshEvidenceRejectedInboundPacketExport>.rejectedInboundPacketsJson(): String =
        joinToString(prefix = "[", postfix = "]") { packet ->
            buildString {
                append("{")
                append("\"packet_id\": ${packet.packetId.jsonStringOrNull()}, ")
                append("\"message_id\": ${packet.messageId.jsonStringOrNull()}, ")
                append("\"reason\": ${packet.reason.jsonStringOrNull()}, ")
                append("\"sender_fingerprint\": ${packet.senderFingerprint.jsonStringOrNull()}, ")
                append("\"recipient_fingerprint\": ${packet.recipientFingerprint.jsonStringOrNull()}, ")
                append("\"relationship_id\": ${packet.relationshipId.jsonStringOrNull()}, ")
                append("\"recipient_matches_local\": ${packet.recipientMatchesLocal}, ")
                append("\"recipient_normalized_matches_local\": ${packet.recipientNormalizedMatchesLocal}, ")
                append("\"relationship_id_known\": ${packet.relationshipIdKnown}, ")
                append("\"sender_fingerprint_known\": ${packet.senderFingerprintKnown}, ")
                append("\"sender_fingerprint_normalized_known\": ${packet.senderFingerprintNormalizedKnown}, ")
                append("\"relationship_and_sender_known\": ${packet.relationshipAndSenderKnown}, ")
                append("\"local_identity_public_key_matches_relationship\": ${packet.localIdentityPublicKeyMatchesRelationship}, ")
                append("\"rejected_at_epoch_millis\": ${packet.rejectedAtEpochMillis}")
                append("}")
            }
        }

    private fun debugSendResultsJson(results: List<DebugDirectSendResult>): String =
        results.joinToString(prefix = "[", postfix = "]") { result ->
            buildString {
                append("{")
                append("\"success\": ${result.success}, ")
                append("\"error\": ${result.error?.name.jsonStringOrNull()}, ")
                append("\"transport_error\": ${result.transportError.jsonStringOrNull()}, ")
                append("\"packet_id\": ${result.packetId.jsonStringOrNull()}, ")
                append("\"message_id\": ${result.message?.messageId.jsonStringOrNull()}")
                append("}")
            }
        }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun String?.jsonStringOrNull(): String =
        this?.let { "\"${escapeJson(it)}\"" } ?: "null"

    private fun List<String>.jsonStringList(): String =
        joinToString(prefix = "[", postfix = "]") { "\"${escapeJson(it)}\"" }

    private fun List<WifiDirectPeerEndpointDiagnostic>.peerEndpointsJson(): String =
        joinToString(prefix = "[", postfix = "]") { peer ->
            buildString {
                append("{")
                append("\"fingerprint_prefix\": ${peer.fingerprintPrefix.jsonStringOrNull()}, ")
                append("\"device_address\": ${peer.deviceAddress.jsonStringOrNull()}, ")
                append("\"device_name\": ${peer.deviceName.jsonStringOrNull()}, ")
                append("\"host\": ${peer.host.jsonStringOrNull()}, ")
                append("\"port\": ${peer.port ?: "null"}, ")
                append("\"binding_state\": ${peer.bindingState.jsonStringOrNull()}, ")
                append("\"binding_source\": ${peer.bindingSource.jsonStringOrNull()}, ")
                append("\"binding_reason\": ${peer.bindingReason.jsonStringOrNull()}")
                append("}")
            }
        }

    private fun List<WifiDirectConnectAttemptDiagnostic>.connectAttemptsJson(): String =
        joinToString(prefix = "[", postfix = "]") { attempt ->
            buildString {
                append("{")
                append("\"attempt\": ${attempt.attempt}, ")
                append("\"group_owner_intent\": ${attempt.groupOwnerIntent}, ")
                append("\"result\": ${attempt.result.jsonStringOrNull()}, ")
                append("\"failure_reason\": ${attempt.failureReason ?: "null"}, ")
                append("\"failure_reason_name\": ${attempt.failureReasonName.jsonStringOrNull()}, ")
                append("\"stop_peer_discovery_result\": ${attempt.stopPeerDiscoveryResult.jsonStringOrNull()}, ")
                append("\"pre_connect_cancel_result\": ${attempt.preConnectCancelResult.jsonStringOrNull()}")
                append("}")
            }
        }

    private fun List<WifiDirectTxtRecordDiagnostic>.txtRecordsJson(): String =
        joinToString(prefix = "[", postfix = "]") { record ->
            buildString {
                append("{")
                append("\"device_address\": ${record.deviceAddress.jsonStringOrNull()}, ")
                append("\"device_name\": ${record.deviceName.jsonStringOrNull()}, ")
                append("\"fingerprint_prefix\": ${record.fingerprintPrefix.jsonStringOrNull()}, ")
                append("\"port\": ${record.port ?: "null"}, ")
                append("\"keys\": ${record.keys.jsonStringList()}, ")
                append("\"accepted\": ${record.accepted}, ")
                append("\"reason\": ${record.reason.jsonStringOrNull()}")
                append("}")
            }
        }

    private fun List<WifiDirectVisibleDeviceDiagnostic>.visibleDevicesJson(): String =
        joinToString(prefix = "[", postfix = "]") { device ->
            buildString {
                append("{")
                append("\"device_address\": ${device.deviceAddress.jsonStringOrNull()}, ")
                append("\"device_name\": ${device.deviceName.jsonStringOrNull()}, ")
                append("\"status\": ${device.status.jsonStringOrNull()}")
                append("}")
            }
        }

    private fun List<WifiDirectBoundEndpointDiagnostic>.boundEndpointsJson(): String =
        joinToString(prefix = "[", postfix = "]") { endpoint ->
            buildString {
                append("{")
                append("\"fingerprint_prefix\": ${endpoint.fingerprintPrefix.jsonStringOrNull()}, ")
                append("\"device_address\": ${endpoint.deviceAddress.jsonStringOrNull()}, ")
                append("\"device_name\": ${endpoint.deviceName.jsonStringOrNull()}, ")
                append("\"host\": ${endpoint.host.jsonStringOrNull()}, ")
                append("\"port\": ${endpoint.port}, ")
                append("\"binding_source\": ${endpoint.bindingSource.jsonStringOrNull()}")
                append("}")
            }
        }

    private fun buildTransportReadiness(
        context: Context,
        snapshot: MeshServiceSnapshot,
    ): MeshEvidenceTransportExport {
        val readiness = TransportReadinessMonitor.snapshot(context, snapshot)
        val wifiDirectPermissions = WifiDirectPermissions.permissionDiagnostics(context)
        return MeshEvidenceTransportExport(
            lanWifi = readiness.wifi.toEvidence("lan-wifi"),
            ble = readiness.bluetooth.toEvidence("bluetooth"),
            wifiDirect = readiness.wifiDirect.toEvidence("wifi-direct"),
            wifiDirectPermissions = wifiDirectPermissions.toEvidence(),
            recentRouteAttempts = snapshot.transportDiagnostics.recentRouteAttempts.map {
                MeshEvidenceRouteAttemptExport(
                    path = it.route,
                    peerId = "unknown",
                    peerFingerprint = "unknown",
                    success = it.success,
                    error = it.error,
                    attemptedAtEpochMillis = it.atEpochMillis,
                )
            },
        )
    }

    private fun WifiDirectPermissionDiagnostics.toEvidence(): MeshEvidenceWifiDirectPermissionExport =
        MeshEvidenceWifiDirectPermissionExport(
            nearbyWifiDevicesRequired = nearbyWifiDevicesRequired,
            nearbyWifiDevicesDeclared = nearbyWifiDevicesDeclared,
            nearbyWifiDevicesGranted = nearbyWifiDevicesGranted,
            fineLocationRequired = fineLocationRequired,
            fineLocationDeclared = fineLocationDeclared,
            fineLocationGranted = fineLocationGranted,
            fineLocationAppOp = fineLocationAppOpMode,
            warning = warning,
        )

    private fun TransportPathReadiness.toEvidence(pathLabel: String): MeshEvidenceTransportPathExport =
        MeshEvidenceTransportPathExport(
            permissionGranted = permissionGranted,
            radioEnabled = radioEnabled,
            serviceAvailable = serviceRunning,
            active = ready,
            inactiveReasons = buildList {
                if (!permissionGranted) add("$pathLabel-permission-missing")
                if (!radioEnabled) add("$pathLabel-radio-disabled")
                if (!transportImplemented) add("$pathLabel-transport-not-implemented")
                if (!serviceRunning) add("$pathLabel-service-unavailable")
            },
        )

    private fun errorResultJson(error: Throwable): String {
        val escapedMessage = (error.message ?: error::class.java.simpleName)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return buildString {
            appendLine("{")
            appendLine("  \"action\": \"$ACTION_EXPORT_ROUTE_EVIDENCE\",")
            appendLine("  \"error\": \"$escapedMessage\"")
            appendLine("}")
        }
    }

    companion object {
        const val ACTION_EXPORT_ROUTE_EVIDENCE = "com.disser.kraken.debug.EXPORT_ROUTE_EVIDENCE"
        const val EXTRA_START_MESH_BEFORE_EXPORT = "start_mesh_before_export"
        const val EXTRA_RUN_LOCAL_HOSTILE_PROBE = "run_local_hostile_probe"
        const val EXTRA_TRANSPORT_PROFILE = "transport_profile"
        const val EXTRA_MANUAL_PEER_FINGERPRINT = "manual_peer_fingerprint"
        const val EXTRA_MANUAL_PEER_HOST = "manual_peer_host"
        const val EXTRA_MANUAL_PEER_PORT = "manual_peer_port"
        const val EXTRA_DEBUG_WIFI_DIRECT_PEER_DEVICE_ADDRESS = "debug_wifi_direct_peer_device_address"
        const val EXTRA_DEBUG_WIFI_DIRECT_PEER_DEVICE_NAME = "debug_wifi_direct_peer_device_name"
        const val EXTRA_DEBUG_WIFI_DIRECT_PEER_PORT = "debug_wifi_direct_peer_port"
        const val EXTRA_DEBUG_SEND_BODY = "debug_send_body"
        const val EXTRA_DEBUG_SEND_WAIT_MS = "debug_send_wait_ms"
        const val EXTRA_DEBUG_SEND_ATTEMPTS = "debug_send_attempts"
        const val EXTRA_DEBUG_SEND_RETRY_DELAY_MS = "debug_send_retry_delay_ms"
        const val EXTRA_SYNC_AFTER_DEBUG_SEND = "sync_after_debug_send"
        const val EXTRA_SYNC_BEFORE_EXPORT = "sync_before_export"
        const val EXTRA_SYNC_ATTEMPTS = "sync_attempts"
        const val EXTRA_START_MESH_SETTLE_MS = "start_mesh_settle_ms"
        const val EXTRA_REUSE_RUNNING_MESH = "reuse_running_mesh"
        const val EXTRA_START_FOREGROUND_WIFI_DIRECT = "start_foreground_wifi_direct"
        const val EXTRA_ENSURE_WIFI_DIRECT_GROUP_OWNER = "ensure_wifi_direct_group_owner"
        const val EXTRA_FORCE_STOP_MESH_BEFORE_START = "force_stop_mesh_before_start"
        const val EXTRA_HOLD_AFTER_EXPORT_MS = "hold_after_export_ms"
        const val TRANSPORT_PROFILE_ALL = "all"
        const val TRANSPORT_PROFILE_HOTSPOT_COMPATIBLE = "hotspot-compatible"
        const val TRANSPORT_PROFILE_WIFI_DIRECT_ONLY = "wifi-direct-only"
        const val TRANSPORT_PROFILE_LAN_ONLY = "lan-only"
        const val ROUTE_JSON_FILE = "route_specific_evidence_latest.json"
        const val ROUTE_MD_FILE = "route_specific_evidence_summary_latest.md"
        const val COMMAND_RESULT_FILE = "debug_evidence_command_result.json"
        private const val DEFAULT_START_MESH_SETTLE_MS = 2_000
        private const val MAX_START_MESH_SETTLE_MS = 60_000
        private const val MAX_HOLD_AFTER_EXPORT_MS = 120_000
        private const val MAX_DEBUG_SEND_WAIT_MS = 120_000
        private const val DEFAULT_DEBUG_SEND_ATTEMPTS = 1
        private const val MAX_DEBUG_SEND_ATTEMPTS = 8
        private const val DEFAULT_DEBUG_SEND_RETRY_DELAY_MS = 1_000
        private const val MAX_DEBUG_SEND_RETRY_DELAY_MS = 30_000
        private const val DEBUG_SEND_WAIT_POLL_MS = 1_000L
        private const val SYNC_ATTEMPT_SETTLE_MS = 1_000L
        private const val DEFAULT_SYNC_ATTEMPTS = 1
        private const val MAX_SYNC_ATTEMPTS = 8
    }
}

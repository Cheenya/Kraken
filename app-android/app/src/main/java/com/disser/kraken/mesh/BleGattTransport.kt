package com.disser.kraken.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.disser.kraken.invite.InvitePayloadCodec
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
private data class BlePeerIdentity(
    @SerialName("peer_id")
    val peerId: String,
    val fingerprint: String,
    @SerialName("display_name")
    val displayName: String? = null,
)

class BleGattTransport(
    context: Context,
    private val localPeer: DiscoveredPeer,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PeerTransport {
    override val modeId: String = "ble-gatt"

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val reassembler = BleFrameReassembler(clock)
    private val peers = Collections.synchronizedMap(linkedMapOf<String, BlePeer>())
    private val incoming = Collections.synchronizedList(mutableListOf<ReceivedPacket>())
    private val clientGatts = ConcurrentHashMap<String, BluetoothGatt>()
    private val pendingWrites = ConcurrentHashMap<String, ArrayBlockingQueue<Boolean>>()
    private val writeLocks = ConcurrentHashMap<String, Any>()
    @Volatile
    private var running = false
    @Volatile
    private var diagnostics = MeshTransportDiagnostics(transportModes = listOf(modeId))
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    override fun start() {
        if (running) return
        running = true
        updateDiagnostics {
            it.copy(
                startedAtEpochMillis = clock(),
                transportModes = listOf(modeId),
                registrationState = "starting",
                discoveryState = "starting",
            )
        }
        if (!BlePermissions.isRuntimeSupported()) {
            updateDiagnostics {
                it.copy(
                    registrationState = "unsupported-pre-android-12",
                    discoveryState = "unsupported-pre-android-12",
                    bleAdvertisingState = "unsupported-pre-android-12",
                    bleScanningState = "unsupported-pre-android-12",
                    bleGattServerState = "unsupported-pre-android-12",
                    lastError = "ble-unsupported-pre-android-12",
                )
            }
            return
        }
        if (!BlePermissions.hasRuntimePermissions(appContext)) {
            updateDiagnostics {
                it.copy(
                    registrationState = "permission-missing",
                    discoveryState = "permission-missing",
                    bleAdvertisingState = "permission-missing",
                    bleScanningState = "permission-missing",
                    bleGattServerState = "permission-missing",
                    lastError = "ble-permission-missing",
                )
            }
            return
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            updateDiagnostics {
                it.copy(
                    registrationState = "adapter-off",
                    discoveryState = "adapter-off",
                    bleAdvertisingState = "adapter-off",
                    bleScanningState = "adapter-off",
                    bleGattServerState = "adapter-off",
                    lastError = "ble-adapter-off",
                )
            }
            return
        }
        startGattServer()
        startAdvertising(adapter)
        startScanning(adapter)
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        running = false
        runCatching { scanner?.stopScan(scanCallback) }
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        runCatching { gattServer?.close() }
        clientGatts.values.forEach { gatt -> runCatching { gatt.close() } }
        clientGatts.clear()
        pendingWrites.clear()
        synchronized(peers) { peers.clear() }
        scanner = null
        advertiser = null
        gattServer = null
        updateDiagnostics {
            it.copy(
                registrationState = "stopped",
                discoveryState = "stopped",
                bleAdvertisingState = "stopped",
                bleScanningState = "stopped",
                bleGattServerState = "stopped",
                bleConnectedPeerCount = 0,
                discoveredPeerCount = 0,
            )
        }
    }

    override fun observePeers(): List<DiscoveredPeer> {
        val observed = synchronized(peers) { peers.values.map { it.peer }.distinctBy { it.fingerprint } }
        updateDiagnostics {
            it.copy(
                discoveredPeerCount = observed.size,
                bleConnectedPeerCount = observed.size,
            )
        }
        return observed
    }

    override fun send(peer: DiscoveredPeer, packet: KrakenPacket): TransportSendResult {
        val blePeer = synchronized(peers) {
            peers.values.firstOrNull { it.peer.fingerprint == peer.fingerprint && it.writeCharacteristic != null }
        } ?: return TransportSendResult(false, "ble-peer-not-ready").also { recordSendFailure("ble-peer-not-ready") }
        val gatt = clientGatts[blePeer.deviceAddress]
            ?: return TransportSendResult(false, "ble-gatt-not-connected").also { recordSendFailure("ble-gatt-not-connected") }
        val characteristic = blePeer.writeCharacteristic
            ?: return TransportSendResult(false, "ble-write-characteristic-missing").also { recordSendFailure("ble-write-characteristic-missing") }
        val chunks = runCatching {
            BleFrameCodec.encodeChunks(packet, localPeer)
        }.getOrElse { error ->
            recordMalformed(error.message ?: "ble-frame-encode-failed")
            return TransportSendResult(false, "ble-frame:${error.message ?: "encode-failed"}")
        }
        val lock = writeLocks.getOrPut(blePeer.deviceAddress) { Any() }
        synchronized(lock) {
            chunks.forEach { chunk ->
                val sent = writeChunkBlocking(gatt, characteristic, chunk)
                if (!sent) {
                    recordSendFailure("ble-write-failed")
                    return TransportSendResult(false, "ble-write-failed")
                }
            }
        }
        updateDiagnostics { it.copy(lastError = null) }
        return TransportSendResult(true)
    }

    override fun observePackets(): List<ReceivedPacket> {
        synchronized(incoming) {
            val packets = incoming.toList()
            incoming.clear()
            return packets
        }
    }

    override fun diagnostics(): MeshTransportDiagnostics {
        val observed = observePeers()
        return diagnostics.copy(
            transportModes = listOf(modeId),
            discoveredPeerCount = observed.size,
            bleConnectedPeerCount = synchronized(peers) { peers.size },
            peerRouteEvidence = observed.map { peer ->
                DiscoveredPeerRouteEvidence(
                    fingerprint = peer.fingerprint,
                    transportId = modeId,
                    observedAtEpochMillis = clock(),
                )
            },
            peerFingerprints = observed.map { it.fingerprint }.distinct(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        val server = runCatching { bluetoothManager?.openGattServer(appContext, serverCallback) }.getOrNull()
        if (server == null) {
            updateDiagnostics {
                it.copy(
                    bleGattServerState = "failed",
                    registrationState = "gatt-server-failed",
                    lastError = "ble-gatt-server-failed",
                )
            }
            return
        }
        gattServer = server
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            addCharacteristic(
                BluetoothGattCharacteristic(
                    IDENTITY_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ,
                ),
            )
            addCharacteristic(
                BluetoothGattCharacteristic(
                    PACKET_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE,
                ),
            )
        }
        val added = runCatching { server.addService(service) }.getOrDefault(false)
        updateDiagnostics {
            it.copy(
                bleGattServerState = if (added) "ready" else "add-service-failed",
                registrationState = if (added) "gatt-server-ready" else "gatt-server-add-service-failed",
                lastError = if (added) null else "ble-gatt-add-service-failed",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(adapter: BluetoothAdapter) {
        val currentAdvertiser = adapter.bluetoothLeAdvertiser
        if (currentAdvertiser == null) {
            updateDiagnostics {
                it.copy(
                    bleAdvertisingState = "unavailable",
                    lastError = "ble-advertiser-unavailable",
                )
            }
            return
        }
        advertiser = currentAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()
        runCatching {
            currentAdvertiser.startAdvertising(settings, data, advertiseCallback)
            updateDiagnostics { it.copy(bleAdvertisingState = "starting") }
        }.onFailure { error ->
            updateDiagnostics {
                it.copy(
                    bleAdvertisingState = "failed",
                    lastError = "ble-advertise:${error.message ?: error::class.java.simpleName}",
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning(adapter: BluetoothAdapter) {
        val currentScanner = adapter.bluetoothLeScanner
        if (currentScanner == null) {
            updateDiagnostics {
                it.copy(
                    bleScanningState = "unavailable",
                    discoveryState = "scanner-unavailable",
                    lastError = "ble-scanner-unavailable",
                )
            }
            return
        }
        scanner = currentScanner
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        runCatching {
            currentScanner.startScan(listOf(filter), settings, scanCallback)
            updateDiagnostics {
                it.copy(
                    bleScanningState = "started",
                    discoveryState = "ble-scan-started",
                    lastError = null,
                )
            }
        }.onFailure { error ->
            updateDiagnostics {
                it.copy(
                    bleScanningState = "failed",
                    discoveryState = "ble-scan-failed",
                    lastError = "ble-scan:${error.message ?: error::class.java.simpleName}",
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(result: ScanResult) {
        if (!running) return
        val device = result.device ?: return
        val address = device.address ?: return
        if (clientGatts.containsKey(address)) return
        val gatt = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(appContext, false, gattCallback)
            }
        }.getOrElse { error ->
            updateDiagnostics {
                it.copy(lastError = "ble-connect:${error.message ?: error::class.java.simpleName}")
            }
            null
        } ?: return
        clientGatts[address] = gatt
    }

    @SuppressLint("MissingPermission")
    private fun writeChunkBlocking(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        bytes: ByteArray,
    ): Boolean {
        val address = gatt.device?.address ?: return false
        val queue = ArrayBlockingQueue<Boolean>(1)
        pendingWrites[address] = queue
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = bytes
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        }.getOrDefault(false)
        if (!started) {
            pendingWrites.remove(address)
            return false
        }
        return queue.poll(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS) == true
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            updateDiagnostics {
                it.copy(
                    bleAdvertisingState = "started",
                    registrationState = "ble-advertising-started",
                    lastError = null,
                )
            }
        }

        override fun onStartFailure(errorCode: Int) {
            updateDiagnostics {
                it.copy(
                    bleAdvertisingState = "failed:$errorCode",
                    registrationState = "ble-advertising-failed:$errorCode",
                    lastError = "ble-advertising-failed:$errorCode",
                )
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            connect(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { connect(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            updateDiagnostics {
                it.copy(
                    bleScanningState = "failed:$errorCode",
                    discoveryState = "ble-scan-failed:$errorCode",
                    lastError = "ble-scan-failed:$errorCode",
                )
            }
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = if (characteristic.uuid == IDENTITY_CHARACTERISTIC_UUID) {
                localPeerIdentityBytes().dropOffset(offset)
            } else {
                null
            }
            val status = if (value == null) BluetoothGatt.GATT_FAILURE else BluetoothGatt.GATT_SUCCESS
            sendGattResponse(device, requestId, status, offset, value)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val ok = characteristic.uuid == PACKET_CHARACTERISTIC_UUID && offset == 0 && handleIncomingChunk(value)
            if (responseNeeded) {
                sendGattResponse(
                    device,
                    requestId,
                    if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                    offset,
                    null,
                )
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device?.address ?: return
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                removePeer(address)
                clientGatts.remove(address)
                runCatching { gatt.close() }
                updateDiagnostics {
                    it.copy(
                        bleConnectedPeerCount = synchronized(peers) { peers.size },
                        lastError = if (status == BluetoothGatt.GATT_SUCCESS) it.lastError else "ble-connection:$status",
                    )
                }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runCatching { gatt.requestMtu(517) }.getOrElse {
                    runCatching { gatt.discoverServices() }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            runCatching { gatt.discoverServices() }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateDiagnostics { it.copy(lastError = "ble-services:$status") }
                return
            }
            val service = gatt.getService(SERVICE_UUID) ?: return
            val identityCharacteristic = service.getCharacteristic(IDENTITY_CHARACTERISTIC_UUID) ?: return
            val packetCharacteristic = service.getCharacteristic(PACKET_CHARACTERISTIC_UUID)
            val address = gatt.device?.address ?: return
            if (packetCharacteristic != null) {
                synchronized(peers) {
                    peers[address] = (peers[address] ?: BlePeer(address, DiscoveredPeer("ble-$address", "", null)))
                        .copy(writeCharacteristic = packetCharacteristic)
                }
            }
            runCatching { gatt.readCharacteristic(identityCharacteristic) }
        }

        @Deprecated("Deprecated in Android API")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            @Suppress("DEPRECATION")
            handleCharacteristicRead(gatt, characteristic, characteristic.value, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleCharacteristicRead(gatt, characteristic, value, status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val address = gatt.device?.address ?: return
            pendingWrites.remove(address)?.offer(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    private fun handleCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        if (status != BluetoothGatt.GATT_SUCCESS || characteristic.uuid != IDENTITY_CHARACTERISTIC_UUID) return
        val identity = runCatching {
            InvitePayloadCodec.json.decodeFromString<BlePeerIdentity>(value.decodeToString())
        }.getOrNull() ?: return
        if (identity.fingerprint == localPeer.fingerprint || identity.fingerprint.isBlank()) return
        val address = gatt.device?.address ?: return
        val service = gatt.getService(SERVICE_UUID)
        val writeCharacteristic = service?.getCharacteristic(PACKET_CHARACTERISTIC_UUID)
        synchronized(peers) {
            peers[address] = BlePeer(
                deviceAddress = address,
                peer = DiscoveredPeer(
                    peerId = identity.peerId.ifBlank { "ble-$address" },
                    fingerprint = identity.fingerprint,
                    displayName = identity.displayName,
                ),
                writeCharacteristic = writeCharacteristic,
            )
        }
        updateDiagnostics {
            it.copy(
                discoveredPeerCount = synchronized(peers) { peers.size },
                bleConnectedPeerCount = synchronized(peers) { peers.size },
                lastError = null,
            )
        }
    }

    private fun handleIncomingChunk(value: ByteArray): Boolean {
        val result = runCatching { BleFrameCodec.decodeChunk(value) }
            .mapCatching { chunk -> reassembler.accept(chunk).getOrThrow() }
        val envelope = result.getOrElse { error ->
            recordMalformed(error.message ?: "ble-malformed")
            return false
        } ?: return true
        if (envelope.senderFingerprint == localPeer.fingerprint) return true
        synchronized(peers) {
            peers.putIfAbsent(
                envelope.senderFingerprint,
                BlePeer(
                    deviceAddress = envelope.senderFingerprint,
                    peer = DiscoveredPeer(
                        peerId = envelope.senderPeerId,
                        fingerprint = envelope.senderFingerprint,
                        displayName = envelope.senderDisplayName,
                    ),
                ),
            )
        }
        incoming += ReceivedPacket(
            fromPeer = DiscoveredPeer(
                peerId = envelope.senderPeerId,
                fingerprint = envelope.senderFingerprint,
                displayName = envelope.senderDisplayName,
            ),
            packet = envelope.packet,
            receivedAtEpochMillis = clock(),
        )
        updateDiagnostics {
            it.copy(
                inboundPackets = it.inboundPackets + 1,
                acceptedConnections = it.acceptedConnections + 1,
                discoveredPeerCount = synchronized(peers) { peers.size },
                lastError = null,
            )
        }
        return true
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        bluetoothManager?.adapter

    private fun localPeerIdentityBytes(): ByteArray =
        InvitePayloadCodec.json.encodeToString(
            BlePeerIdentity(
                peerId = localPeer.peerId,
                fingerprint = localPeer.fingerprint,
                displayName = localPeer.displayName,
            ),
        ).encodeToByteArray()

    @SuppressLint("MissingPermission")
    private fun sendGattResponse(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ) {
        if (!BlePermissions.hasRuntimePermissions(appContext)) return
        runCatching { gattServer?.sendResponse(device, requestId, status, offset, value) }
    }

    private fun ByteArray.dropOffset(offset: Int): ByteArray? =
        if (offset in 0..size) copyOfRange(offset, size) else null

    private fun removePeer(address: String) {
        synchronized(peers) { peers.remove(address) }
    }

    private fun recordMalformed(reason: String) {
        updateDiagnostics {
            it.copy(
                malformedFramesDropped = it.malformedFramesDropped + 1,
                lastError = "ble-malformed:$reason",
            )
        }
    }

    private fun recordSendFailure(reason: String) {
        updateDiagnostics {
            it.copy(
                sendFailures = it.sendFailures + 1,
                lastError = "ble-send:$reason",
            )
        }
    }

    private fun updateDiagnostics(update: (MeshTransportDiagnostics) -> MeshTransportDiagnostics) {
        diagnostics = update(diagnostics).copy(transportModes = listOf(modeId))
    }

    private data class BlePeer(
        val deviceAddress: String,
        val peer: DiscoveredPeer,
        val writeCharacteristic: BluetoothGattCharacteristic? = null,
    )

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("58a1257c-f4a8-48c8-99d5-917b9863d7c4")
        val IDENTITY_CHARACTERISTIC_UUID: UUID = UUID.fromString("58a1257d-f4a8-48c8-99d5-917b9863d7c4")
        val PACKET_CHARACTERISTIC_UUID: UUID = UUID.fromString("58a1257e-f4a8-48c8-99d5-917b9863d7c4")
        private const val WRITE_TIMEOUT_MS = 2_500L
    }
}

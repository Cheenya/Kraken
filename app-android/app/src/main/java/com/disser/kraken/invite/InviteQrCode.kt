package com.disser.kraken.invite

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.disser.kraken.qr.KrakenQrPayloadCodec

data class InviteQrCodeMatrix(
    val width: Int,
    val height: Int,
    val modules: List<Boolean>,
) {
    init {
        require(width > 0) { "Ширина QR должна быть положительной." }
        require(height > 0) { "Высота QR должна быть положительной." }
        require(modules.size == width * height) { "Размер QR не совпадает с количеством модулей." }
    }

    fun isDark(x: Int, y: Int): Boolean = modules[y * width + x]
}

data class InviteQrDisplayModel(
    val payloadJson: String,
    val qrContent: String,
    val inviteId: String,
    val shortInviteId: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long?,
    val stateLabel: String,
    val expiresInLabel: String,
    val qrMatrix: InviteQrCodeMatrix,
)

enum class InviteLifecycleState {
    AVAILABLE,
    REVOKED,
    CONSUMED,
    EXPIRED,
}

object InviteLifecycleFormatter {
    fun stateFor(
        payload: OneTimeInvitePayload,
        revoked: Boolean,
        consumed: Boolean = false,
        nowEpochMillis: Long,
    ): InviteLifecycleState =
        when {
            revoked -> InviteLifecycleState.REVOKED
            consumed -> InviteLifecycleState.CONSUMED
            payload.expiresAtEpochMillis != null && nowEpochMillis >= payload.expiresAtEpochMillis -> InviteLifecycleState.EXPIRED
            else -> InviteLifecycleState.AVAILABLE
        }

    fun shortInviteId(inviteId: String): String {
        val normalized = inviteId.removePrefix("invite-")
        return normalized.take(8).ifBlank { inviteId.take(8) }
    }

    fun expiresInLabel(expiresAtEpochMillis: Long?, nowEpochMillis: Long): String {
        if (expiresAtEpochMillis == null) return "Без срока"
        val remainingMillis = expiresAtEpochMillis - nowEpochMillis
        if (remainingMillis <= 0) return "Истёк"
        val totalSeconds = remainingMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes > 0 -> "${minutes}м ${seconds}с"
            else -> "${seconds}с"
        }
    }

    fun stateLabel(state: InviteLifecycleState): String =
        when (state) {
            InviteLifecycleState.AVAILABLE -> "ДОСТУПЕН"
            InviteLifecycleState.REVOKED -> "ОТОЗВАН"
            InviteLifecycleState.CONSUMED -> "ИСПОЛЬЗОВАН"
            InviteLifecycleState.EXPIRED -> "ИСТЁК"
        }
}

object InviteQrCodeGenerator {
    fun generate(payload: String, size: Int = DEFAULT_SIZE): Result<InviteQrCodeMatrix> = runCatching {
        val trimmed = payload.trim()
        require(trimmed.isNotBlank()) { "QR пустой." }
        require(size > 0) { "Размер QR должен быть положительным." }

        val bitMatrix = QRCodeWriter().encode(trimmed, BarcodeFormat.QR_CODE, size, size)
        val modules = List(bitMatrix.width * bitMatrix.height) { index ->
            val x = index % bitMatrix.width
            val y = index / bitMatrix.width
            bitMatrix[x, y]
        }
        InviteQrCodeMatrix(
            width = bitMatrix.width,
            height = bitMatrix.height,
            modules = modules,
        )
    }

    private const val DEFAULT_SIZE = 720
}

object InviteQrDisplayModelFactory {
    fun create(
        payload: OneTimeInvitePayload,
        payloadJson: String,
        state: InviteLifecycleState = InviteLifecycleState.AVAILABLE,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Result<InviteQrDisplayModel> = runCatching {
        val qrContent = KrakenQrPayloadCodec.encodePayload(payloadJson).getOrThrow()
        val matrix = InviteQrCodeGenerator.generate(qrContent).getOrThrow()
        InviteQrDisplayModel(
            payloadJson = payloadJson,
            qrContent = qrContent,
            inviteId = payload.inviteId,
            shortInviteId = InviteLifecycleFormatter.shortInviteId(payload.inviteId),
            createdAtEpochMillis = payload.createdAtEpochMillis,
            expiresAtEpochMillis = payload.expiresAtEpochMillis,
            stateLabel = InviteLifecycleFormatter.stateLabel(state),
            expiresInLabel = InviteLifecycleFormatter.expiresInLabel(payload.expiresAtEpochMillis, nowEpochMillis),
            qrMatrix = matrix,
        )
    }
}

package com.disser.kraken.qr

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.InviteImportResult
import com.disser.kraken.invite.InviteImportService
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.relationship.Relationship

sealed class QrScanImportResult {
    data class Success(val pendingImport: PendingInviteImport) : QrScanImportResult()
    data class HandshakeResponseAccepted(
        val relationship: Relationship,
        val confirmationPayloadJson: String,
    ) : QrScanImportResult()
    data class HandshakeConfirmationAccepted(
        val relationship: Relationship,
        val realmMembershipApplied: Boolean = false,
    ) : QrScanImportResult()
    data class LanEndpointAccepted(
        val fingerprint: String,
        val host: String,
        val port: Int,
        val displayName: String? = null,
    ) : QrScanImportResult()
    data class KnownContact(
        val relationship: Relationship,
    ) : QrScanImportResult()
    data class Error(val message: String) : QrScanImportResult()
}

fun QrScanImportResult.isTerminalSuccess(): Boolean =
    this is QrScanImportResult.Success ||
        this is QrScanImportResult.HandshakeResponseAccepted ||
        this is QrScanImportResult.HandshakeConfirmationAccepted ||
        this is QrScanImportResult.LanEndpointAccepted ||
        this is QrScanImportResult.KnownContact

class QrScanImportService(
    private val inviteImportService: InviteImportService = InviteImportService(),
) {
    fun importScannedText(
        scannedText: String,
        localIdentity: LocalIdentity?,
        existingImports: List<PendingInviteImport>,
    ): QrScanImportResult {
        val rawJson = KrakenQrPayloadCodec.normalizeScannedText(scannedText).getOrElse {
            return QrScanImportResult.Error(INVALID_QR_MESSAGE)
        }

        return when (
            val result = inviteImportService.import(
                rawJson = rawJson,
                localIdentity = localIdentity,
                existingImports = existingImports,
            )
        ) {
            is InviteImportResult.Error -> QrScanImportResult.Error(friendlyInviteImportError(result.reason))
            is InviteImportResult.Success -> QrScanImportResult.Success(result.pendingImport)
        }
    }

    private fun friendlyInviteImportError(reason: String): String =
        when (reason) {
            "Ключ уже знаком" -> "Ключ уже знаком"
            "Приглашение уже добавлено." -> "Приглашение уже добавлено."
            "Нельзя добавить собственный QR." -> "Нельзя добавить собственный QR."
            else -> INVALID_QR_MESSAGE
        }

    companion object {
        const val INVALID_QR_MESSAGE = "Этот QR не является корректным приглашением Kraken."
    }
}

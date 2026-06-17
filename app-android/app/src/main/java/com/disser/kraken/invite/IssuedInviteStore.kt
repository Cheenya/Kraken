package com.disser.kraken.invite

import android.content.Context
import com.disser.kraken.handshake.KnownInviteLifecycle
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

class IssuedInviteStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.ISSUED_INVITES, Context.MODE_PRIVATE)
    private val listSerializer = ListSerializer(IssuedInviteRecord.serializer())

    fun load(): List<IssuedInviteRecord> {
        val encoded = preferences.getString(KrakenStorageKeys.IssuedInvites.RECORDS, null) ?: return emptyList()
        return runCatching {
            InvitePayloadCodec.json.decodeFromString(listSerializer, encoded)
        }.getOrDefault(emptyList())
    }

    fun add(payload: OneTimeInvitePayload): List<IssuedInviteRecord> {
        val record = IssuedInviteRecord.fromPayload(payload)
        val updated = load().filterNot { it.inviteId == record.inviteId } + record
        save(updated)
        return updated
    }

    fun markConsumed(
        inviteId: String,
        consumedAtEpochMillis: Long = System.currentTimeMillis(),
        consumedByPublicKey: String? = null,
    ): List<IssuedInviteRecord> {
        val updated = load().map { record ->
            if (record.inviteId == inviteId) {
                record.copy(
                    consumed = true,
                    consumedAtEpochMillis = consumedAtEpochMillis,
                    consumedByPublicKey = consumedByPublicKey,
                )
            } else {
                record
            }
        }
        save(updated)
        return updated
    }

    fun revoke(inviteId: String): List<IssuedInviteRecord> {
        val updated = load().map { record ->
            if (record.inviteId == inviteId) record.copy(revoked = true) else record
        }
        save(updated)
        return updated
    }

    fun clear(): List<IssuedInviteRecord> {
        save(emptyList())
        return emptyList()
    }

    fun save(records: List<IssuedInviteRecord>) {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.IssuedInvites.RECORDS, InvitePayloadCodec.json.encodeToString(listSerializer, records))
            .apply()
    }
}

fun IssuedInviteRecord.toKnownInviteLifecycle(): KnownInviteLifecycle =
    KnownInviteLifecycle(
        inviteId = inviteId,
        revoked = revoked,
        consumed = consumed,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )

fun IssuedInviteRecord.isUsableAt(nowEpochMillis: Long): Boolean =
    !revoked &&
        !consumed &&
        (expiresAtEpochMillis == null || nowEpochMillis < expiresAtEpochMillis)

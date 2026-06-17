package com.disser.kraken.identity

import android.content.Context
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class IdentityStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.IDENTITY, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): LocalIdentity? {
        val encoded = preferences.getString(KrakenStorageKeys.Identity.LOCAL_IDENTITY, null) ?: return null
        return runCatching { json.decodeFromString<LocalIdentity>(encoded) }.getOrNull()
    }

    fun create(displayName: String, keyProvider: IdentityKeyProvider): LocalIdentity {
        val cleanDisplayName = displayName.trim()
        require(cleanDisplayName.isNotEmpty()) { "Display name is required." }

        val keypair = keyProvider.generateIdentityKeypair()
        val identity = LocalIdentity(
            identityId = UUID.randomUUID().toString(),
            displayName = cleanDisplayName,
            publicKeyEncoded = keypair.publicKeyEncoded,
            privateKeyReference = keypair.privateKeyReference,
            fingerprint = FingerprintFormatter.shortFingerprint(keypair.publicKeyEncoded),
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        save(identity)
        return identity
    }

    fun updateDisplayName(identity: LocalIdentity, displayName: String): LocalIdentity {
        val cleanDisplayName = displayName.trim()
        require(cleanDisplayName.isNotEmpty()) { "Display name is required." }

        val updated = identity.copy(displayName = cleanDisplayName)
        save(updated)
        return updated
    }

    private fun save(identity: LocalIdentity) {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.Identity.LOCAL_IDENTITY, json.encodeToString(identity))
            .apply()
    }
}

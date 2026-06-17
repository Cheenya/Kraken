package com.disser.kraken.identity

import kotlinx.serialization.Serializable

@Serializable
data class LocalIdentity(
    val identityId: String,
    val displayName: String,
    val publicKeyEncoded: String,
    val privateKeyReference: String,
    val fingerprint: String,
    val createdAtEpochMillis: Long,
)

@Serializable
data class IdentityKeypair(
    val publicKeyEncoded: String,
    val privateKeyReference: String,
)

data class IdentityState(
    val identity: LocalIdentity?,
) {
    val hasIdentity: Boolean = identity != null
}

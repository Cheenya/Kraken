package com.disser.kraken.invite

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object InvitePayloadCodec {
    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(payload: OneTimeInvitePayload): String = json.encodeToString(payload)

    fun decode(rawJson: String): Result<OneTimeInvitePayload> =
        runCatching { json.decodeFromString<OneTimeInvitePayload>(rawJson) }
            .recoverCatching { error ->
                if (error is SerializationException || error is IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid invite JSON.")
                }
                throw error
            }
}

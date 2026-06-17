package com.disser.kraken.mesh

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LanEndpointPayload(
    val type: String = TYPE,
    val version: Int = VERSION,
    val fingerprint: String,
    @SerialName("display_name")
    val displayName: String? = null,
    val host: String,
    val port: Int,
) {
    companion object {
        const val TYPE = "kraken_lan_endpoint"
        const val VERSION = 1
    }
}

object LanEndpointPayloadCodec {
    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(payload: LanEndpointPayload): String =
        json.encodeToString(payload)

    fun decode(rawJson: String): Result<LanEndpointPayload> =
        runCatching {
            val payload = json.decodeFromString<LanEndpointPayload>(rawJson)
            require(payload.type == LanEndpointPayload.TYPE) { "Invalid LAN endpoint payload type." }
            require(payload.version == LanEndpointPayload.VERSION) { "Unsupported LAN endpoint payload version." }
            require(payload.fingerprint.isNotBlank()) { "Missing LAN endpoint fingerprint." }
            require(payload.host.isNotBlank()) { "Missing LAN endpoint host." }
            require(payload.port in 1..65535) { "Invalid LAN endpoint port." }
            payload
        }.recoverCatching { error ->
            if (error is SerializationException || error is IllegalArgumentException) {
                throw IllegalArgumentException("Invalid LAN endpoint payload.")
            }
            throw error
        }
}

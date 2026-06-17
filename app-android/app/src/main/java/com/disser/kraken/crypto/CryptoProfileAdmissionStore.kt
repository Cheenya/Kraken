package com.disser.kraken.crypto

import android.content.Context
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.builtins.ListSerializer

interface CryptoProfileAdmissionStore {
    fun load(): List<ProductCryptoAdmissionResult>
    fun find(
        profileHash: String,
        nativeBackendVersion: String,
        policyVersion: Int,
    ): ProductCryptoAdmissionResult?
    fun upsert(result: ProductCryptoAdmissionResult): List<ProductCryptoAdmissionResult>
}

object NoOpCryptoProfileAdmissionStore : CryptoProfileAdmissionStore {
    override fun load(): List<ProductCryptoAdmissionResult> = emptyList()

    override fun find(
        profileHash: String,
        nativeBackendVersion: String,
        policyVersion: Int,
    ): ProductCryptoAdmissionResult? = null

    override fun upsert(result: ProductCryptoAdmissionResult): List<ProductCryptoAdmissionResult> = listOf(result)
}

object CryptoProfileAdmissionStoragePolicy {
    const val MAX_ADMISSION_RESULTS = 100

    fun prune(results: List<ProductCryptoAdmissionResult>): List<ProductCryptoAdmissionResult> =
        results
            .distinctBy { "${it.profileHash}:${it.nativeBackendVersion}:${it.policyVersion}" }
            .sortedBy { it.evaluatedAtEpochMillis }
            .takeLast(MAX_ADMISSION_RESULTS)
}

class SharedPreferencesCryptoProfileAdmissionStore(context: Context) : CryptoProfileAdmissionStore {
    private val preferences = context.getSharedPreferences(
        KrakenStorageKeys.Preferences.CRYPTO_PROFILE_ADMISSIONS,
        Context.MODE_PRIVATE,
    )
    private val serializer = ListSerializer(ProductCryptoAdmissionResult.serializer())

    override fun load(): List<ProductCryptoAdmissionResult> =
        CryptoProfileAdmissionStoragePolicy.prune(
            preferences.getString(KrakenStorageKeys.CryptoProfileAdmissions.RESULTS, null)
                ?.let { encoded ->
                    runCatching { InvitePayloadCodec.json.decodeFromString(serializer, encoded) }.getOrDefault(emptyList())
                }
                .orEmpty(),
        )

    override fun find(
        profileHash: String,
        nativeBackendVersion: String,
        policyVersion: Int,
    ): ProductCryptoAdmissionResult? =
        load().firstOrNull {
            it.profileHash == profileHash &&
                it.nativeBackendVersion == nativeBackendVersion &&
                it.policyVersion == policyVersion
        }

    override fun upsert(result: ProductCryptoAdmissionResult): List<ProductCryptoAdmissionResult> {
        val updated = CryptoProfileAdmissionStoragePolicy.prune(
            load().filterNot {
                it.profileHash == result.profileHash &&
                    it.nativeBackendVersion == result.nativeBackendVersion &&
                    it.policyVersion == result.policyVersion
            } + result,
        )
        save(updated)
        return updated
    }

    private fun save(results: List<ProductCryptoAdmissionResult>) {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(KrakenStorageKeys.CryptoProfileAdmissions.RESULTS, InvitePayloadCodec.json.encodeToString(serializer, results))
            .apply()
    }
}

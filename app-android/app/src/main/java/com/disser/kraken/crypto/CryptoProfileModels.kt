package com.disser.kraken.crypto

import com.disser.kraken.nativecore.NativeAdamovaResult
import com.disser.kraken.nativecore.NativeCoreBridge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.security.MessageDigest

object KrakenCryptoProfileDefaults {
    const val STANDARD_PROFILE_ID = "standard-reviewed-primitives-v1"
    const val STANDARD_PROFILE_HASH = "sha256:standard-reviewed-primitives-v1:not-applicable"
    const val STANDARD_ADMISSION_DECISION_HASH = "sha256:standard-reviewed-primitives-v1:not-applicable:v1"
    const val ADMISSION_POLICY_VERSION = 1
    const val STANDARD_NATIVE_BACKEND_VERSION = "not-applicable-standard-profile"
}

@Serializable
enum class CryptoProfileKind {
    STANDARD_REVIEWED_PRIMITIVES,
    EXPERIMENTAL_ADAMOVA_CURVE_PROFILE,
}

@Serializable
data class KrakenCryptoProfile(
    @SerialName("profile_id")
    val profileId: String,
    @SerialName("profile_version")
    val profileVersion: Int,
    @SerialName("profile_kind")
    val profileKind: CryptoProfileKind,
    @SerialName("curve_a")
    val curveA: String? = null,
    @SerialName("curve_b")
    val curveB: String? = null,
    @SerialName("profile_hash")
    val profileHash: String = CryptoProfileHasher.hashProfile(
        profileId = profileId,
        profileVersion = profileVersion,
        profileKind = profileKind,
        curveA = curveA,
        curveB = curveB,
    ),
    @SerialName("evidence_asset_path")
    val evidenceAssetPath: String? = null,
    @SerialName("required_reference_status")
    val requiredReferenceStatus: String? = null,
) {
    fun curveAOrNull(): BigInteger? = curveA?.toBigIntegerOrNull()
    fun curveBOrNull(): BigInteger? = curveB?.toBigIntegerOrNull()
}

@Serializable
enum class AdamovaAdmissionDecision {
    ACCEPT,
    REJECT_SINGULAR,
    REJECT_SMALL_TORSION_RISK,
    REFERENCE_VALIDATION_REQUIRED,
    SIZE_GUARDED,
    NATIVE_UNAVAILABLE,
    NOT_APPLICABLE_STANDARD_PROFILE,
}

@Serializable
data class ProductCryptoAdmissionResult(
    @SerialName("profile_id")
    val profileId: String,
    @SerialName("profile_hash")
    val profileHash: String,
    val decision: AdamovaAdmissionDecision,
    @SerialName("decision_hash")
    val decisionHash: String,
    @SerialName("policy_version")
    val policyVersion: Int = KrakenCryptoProfileDefaults.ADMISSION_POLICY_VERSION,
    @SerialName("native_backend_version")
    val nativeBackendVersion: String,
    @SerialName("classification_case")
    val classificationCase: String? = null,
    @SerialName("risk_flags")
    val riskFlags: List<String> = emptyList(),
    @SerialName("evaluated_at_epoch_millis")
    val evaluatedAtEpochMillis: Long,
) {
    val acceptedForPacketPolicy: Boolean
        get() = decision in setOf(
            AdamovaAdmissionDecision.ACCEPT,
            AdamovaAdmissionDecision.NOT_APPLICABLE_STANDARD_PROFILE,
        )
}

interface AdamovaNativeValidator {
    fun status(): String
    fun classify(a: BigInteger, b: BigInteger): NativeAdamovaResult?
}

object NativeAdamovaValidator : AdamovaNativeValidator {
    override fun status(): String = NativeCoreBridge.statusOrUnavailable()
    override fun classify(a: BigInteger, b: BigInteger): NativeAdamovaResult? =
        NativeCoreBridge.classifyAdamovaOrNull(a, b)
}

interface CryptoProfileRegistry {
    fun find(profileId: String): KrakenCryptoProfile?
}

object DefaultCryptoProfileRegistry : CryptoProfileRegistry {
    val standardProfile: KrakenCryptoProfile =
        KrakenCryptoProfile(
            profileId = KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID,
            profileVersion = 1,
            profileKind = CryptoProfileKind.STANDARD_REVIEWED_PRIMITIVES,
            profileHash = KrakenCryptoProfileDefaults.STANDARD_PROFILE_HASH,
        )

    val largeCoefficientResearchProfile: KrakenCryptoProfile =
        KrakenCryptoProfile(
            profileId = "experimental-adamova-lc32-prime-offsets-v1",
            profileVersion = 1,
            profileKind = CryptoProfileKind.EXPERIMENTAL_ADAMOVA_CURVE_PROFILE,
            curveA = "65537",
            curveB = "104729",
            evidenceAssetPath = "research/examples/lc32_prime_offsets_no_two_torsion.json",
            requiredReferenceStatus = "совпадает с SageMath",
        )

    private val profiles: Map<String, KrakenCryptoProfile> =
        listOf(standardProfile, largeCoefficientResearchProfile).associateBy { it.profileId }

    override fun find(profileId: String): KrakenCryptoProfile? = profiles[profileId]
}

class ProductCryptoAdmissionGate(
    private val validator: AdamovaNativeValidator = NativeAdamovaValidator,
    private val registry: CryptoProfileRegistry = DefaultCryptoProfileRegistry,
    private val admissionStore: CryptoProfileAdmissionStore = NoOpCryptoProfileAdmissionStore,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun evaluate(profileId: String?): ProductCryptoAdmissionResult? {
        val resolvedProfileId = profileId ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID
        val profile = registry.find(resolvedProfileId) ?: return null
        return evaluate(profile)
    }

    fun evaluate(profile: KrakenCryptoProfile): ProductCryptoAdmissionResult {
        val backendVersion = backendVersionFor(profile)
        admissionStore.find(
            profileHash = profile.profileHash,
            nativeBackendVersion = backendVersion,
            policyVersion = KrakenCryptoProfileDefaults.ADMISSION_POLICY_VERSION,
        )?.let { return it }

        val result = when (profile.profileKind) {
            CryptoProfileKind.STANDARD_REVIEWED_PRIMITIVES -> standardResult(profile)
            CryptoProfileKind.EXPERIMENTAL_ADAMOVA_CURVE_PROFILE -> evaluateExperimental(profile, backendVersion)
        }
        admissionStore.upsert(result)
        return result
    }

    fun packetAdmissionAccepted(
        cryptoProfileId: String?,
        admissionDecisionHash: String?,
        profilePolicyVersion: Int?,
    ): Boolean {
        val result = evaluate(cryptoProfileId) ?: return false
        return result.acceptedForPacketPolicy &&
            result.decisionHash == admissionDecisionHash &&
            result.policyVersion == profilePolicyVersion
    }

    private fun standardResult(profile: KrakenCryptoProfile): ProductCryptoAdmissionResult =
        ProductCryptoAdmissionResult(
            profileId = profile.profileId,
            profileHash = profile.profileHash,
            decision = AdamovaAdmissionDecision.NOT_APPLICABLE_STANDARD_PROFILE,
            decisionHash = KrakenCryptoProfileDefaults.STANDARD_ADMISSION_DECISION_HASH,
            nativeBackendVersion = KrakenCryptoProfileDefaults.STANDARD_NATIVE_BACKEND_VERSION,
            evaluatedAtEpochMillis = now(),
        )

    private fun evaluateExperimental(
        profile: KrakenCryptoProfile,
        backendVersion: String,
    ): ProductCryptoAdmissionResult {
        val a = profile.curveAOrNull()
        val b = profile.curveBOrNull()
        if (a == null || b == null) {
            return resultFor(
                profile = profile,
                decision = AdamovaAdmissionDecision.REFERENCE_VALIDATION_REQUIRED,
                nativeBackendVersion = backendVersion,
                riskFlags = listOf("missing_or_invalid_curve_coefficients"),
            )
        }
        val native = validator.classify(a, b)
            ?: return resultFor(
                profile = profile,
                decision = AdamovaAdmissionDecision.NATIVE_UNAVAILABLE,
                nativeBackendVersion = backendVersion,
                riskFlags = listOf("native_adamova_backend_unavailable"),
            )

        val riskFlags = buildList {
            if (native.singular) add("singular")
            if (native.twoTorsionRootCount > 0) add("rational_2_torsion")
            if (native.hasThreeTorsionIndicator || native.threeTorsionRootCount > 0) add("three_torsion_indicator")
            if (native.hasThreeTorsionInconsistency) add("three_torsion_inconsistency")
            if (native.classificationCase == "SIZE_GUARDED" || native.earlyStopHit) add("size_guarded")
        }
        val decision = when {
            native.singular -> AdamovaAdmissionDecision.REJECT_SINGULAR
            native.classificationCase == "SIZE_GUARDED" || native.earlyStopHit -> AdamovaAdmissionDecision.SIZE_GUARDED
            native.twoTorsionRootCount > 0 ||
                native.hasThreeTorsionIndicator ||
                native.threeTorsionRootCount > 0 ->
                AdamovaAdmissionDecision.REJECT_SMALL_TORSION_RISK
            native.hasThreeTorsionInconsistency -> AdamovaAdmissionDecision.REFERENCE_VALIDATION_REQUIRED
            else -> AdamovaAdmissionDecision.ACCEPT
        }
        return resultFor(
            profile = profile,
            decision = decision,
            nativeBackendVersion = backendVersion,
            classificationCase = native.classificationCase,
            riskFlags = riskFlags,
        )
    }

    private fun resultFor(
        profile: KrakenCryptoProfile,
        decision: AdamovaAdmissionDecision,
        nativeBackendVersion: String,
        classificationCase: String? = null,
        riskFlags: List<String> = emptyList(),
    ): ProductCryptoAdmissionResult =
        ProductCryptoAdmissionResult(
            profileId = profile.profileId,
            profileHash = profile.profileHash,
            decision = decision,
            decisionHash = CryptoProfileHasher.hashAdmissionDecision(
                profileHash = profile.profileHash,
                decision = decision,
                nativeBackendVersion = nativeBackendVersion,
                policyVersion = KrakenCryptoProfileDefaults.ADMISSION_POLICY_VERSION,
                classificationCase = classificationCase,
                riskFlags = riskFlags,
            ),
            nativeBackendVersion = nativeBackendVersion,
            classificationCase = classificationCase,
            riskFlags = riskFlags,
            evaluatedAtEpochMillis = now(),
        )

    private fun backendVersionFor(profile: KrakenCryptoProfile): String =
        if (profile.profileKind == CryptoProfileKind.STANDARD_REVIEWED_PRIMITIVES) {
            KrakenCryptoProfileDefaults.STANDARD_NATIVE_BACKEND_VERSION
        } else {
            validator.status()
        }
}

object CryptoProfileHasher {
    fun hashProfile(
        profileId: String,
        profileVersion: Int,
        profileKind: CryptoProfileKind,
        curveA: String?,
        curveB: String?,
    ): String =
        sha256(
            listOf(
                "profile_id=$profileId",
                "profile_version=$profileVersion",
                "profile_kind=$profileKind",
                "curve_a=${curveA ?: "-"}",
                "curve_b=${curveB ?: "-"}",
            ).joinToString("|"),
        )

    fun hashAdmissionDecision(
        profileHash: String,
        decision: AdamovaAdmissionDecision,
        nativeBackendVersion: String,
        policyVersion: Int,
        classificationCase: String?,
        riskFlags: List<String>,
    ): String =
        sha256(
            listOf(
                "profile_hash=$profileHash",
                "decision=$decision",
                "native_backend_version=$nativeBackendVersion",
                "policy_version=$policyVersion",
                "classification_case=${classificationCase ?: "-"}",
                "risk_flags=${riskFlags.sorted().joinToString(",")}",
            ).joinToString("|"),
        )

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }
}

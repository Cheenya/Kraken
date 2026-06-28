package com.disser.kraken.crypto

import com.disser.kraken.nativecore.NativeAdamovaResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class AdamovaBoundCryptoEnvelopeTest {
    @Test
    fun acceptedExperimentalProfileIsBoundIntoAeadAssociatedData() {
        val profile = experimentalProfile(profileId = "experimental-accepted-message-v1")
        val gate = admissionGateFor(profile, nativeResult(classificationCase = "A4"))
        val admission = gate.evaluate(profile)
        val aead = RecordingAeadProvider()
        val envelope = AdamovaBoundCryptoEnvelope(gate)
        val plaintext = Plaintext("secret".encodeToByteArray())

        val ciphertext = envelope.seal(
            plaintext = plaintext,
            key = DerivedKey(ByteArray(32) { 7 }),
            context = context(profile, admission),
            aead = aead,
        )

        assertEquals("adamova-bound-test-aead", ciphertext.algorithm)
        val associatedData = requireNotNull(aead.lastSealAssociatedData).decodeToString()
        assertTrue(associatedData.contains("kraken-message-crypto-v1"))
        assertTrue(associatedData.contains("adamova_profile_id=${profile.profileId}"))
        assertTrue(associatedData.contains("adamova_admission_decision_hash=${admission.decisionHash}"))
    }

    @Test
    fun rejectedExperimentalProfileCannotBeSealed() {
        val profile = experimentalProfile(profileId = "experimental-rejected-message-v1")
        val gate = admissionGateFor(profile, nativeResult(twoTorsionRootCount = 1, classificationCase = "A5"))
        val admission = gate.evaluate(profile)
        val envelope = AdamovaBoundCryptoEnvelope(gate)

        val error = assertThrows(AdamovaCryptoPolicyException::class.java) {
            envelope.seal(
                plaintext = Plaintext("secret".encodeToByteArray()),
                key = DerivedKey(ByteArray(32) { 1 }),
                context = context(profile, admission),
                aead = RecordingAeadProvider(),
            )
        }

        assertTrue(error.message.orEmpty().contains("отклонён"))
    }

    @Test
    fun tamperedAdmissionHashCannotOpenCiphertext() {
        val profile = experimentalProfile(profileId = "experimental-tamper-message-v1")
        val gate = admissionGateFor(profile, nativeResult(classificationCase = "A4"))
        val admission = gate.evaluate(profile)
        val envelope = AdamovaBoundCryptoEnvelope(gate)
        val aead = RecordingAeadProvider()
        val context = context(profile, admission)
        val ciphertext = envelope.seal(
            plaintext = Plaintext("secret".encodeToByteArray()),
            key = DerivedKey(ByteArray(32) { 2 }),
            context = context,
            aead = aead,
        )

        val error = assertThrows(AdamovaCryptoPolicyException::class.java) {
            envelope.open(
                ciphertext = ciphertext,
                key = DerivedKey(ByteArray(32) { 2 }),
                context = context.copy(admissionDecisionHash = "sha256:tampered"),
                aead = aead,
            )
        }

        assertTrue(error.message.orEmpty().contains("не совпадает"))
    }

    private fun context(
        profile: KrakenCryptoProfile,
        admission: ProductCryptoAdmissionResult,
    ): AdamovaCryptoPolicyContext =
        AdamovaCryptoPolicyContext(
            profileId = profile.profileId,
            profileHash = admission.profileHash,
            admissionDecisionHash = admission.decisionHash,
            profilePolicyVersion = admission.policyVersion,
            nativeBackendVersion = admission.nativeBackendVersion,
            sessionProfileId = "session-relationship-1-${profile.profileId}",
            relationshipId = "relationship-1",
        )

    private fun experimentalProfile(
        profileId: String,
        a: String = "65537",
        b: String = "104729",
    ): KrakenCryptoProfile =
        KrakenCryptoProfile(
            profileId = profileId,
            profileVersion = 1,
            profileKind = CryptoProfileKind.EXPERIMENTAL_ADAMOVA_CURVE_PROFILE,
            curveA = a,
            curveB = b,
        )

    private fun admissionGateFor(
        profile: KrakenCryptoProfile,
        nativeResult: NativeAdamovaResult?,
    ): ProductCryptoAdmissionGate =
        ProductCryptoAdmissionGate(
            validator = FakeAdamovaValidator(nativeResult),
            registry = SingleProfileRegistry(profile),
            now = { 1_700_000_000_000 },
        )

    private fun nativeResult(
        twoTorsionRootCount: Int = 0,
        hasThreeTorsionIndicator: Boolean = false,
        classificationCase: String = "A4",
    ): NativeAdamovaResult =
        NativeAdamovaResult(
            a = BigInteger.valueOf(65537),
            b = BigInteger.valueOf(104729),
            singular = false,
            discriminant = BigInteger.ONE,
            twoTorsionRootCount = twoTorsionRootCount,
            twoTorsionRoots = emptyList(),
            threeTorsionRootCount = 0,
            threeTorsionRoots = emptyList(),
            hasThreeTorsionIndicator = hasThreeTorsionIndicator,
            hasThreeTorsionInconsistency = false,
            classificationCase = classificationCase,
            roots3CandidatesTotal = 0,
            roots3RejectedMod = 0,
            roots3RejectedBound = 0,
            roots3PassedFilters = 0,
            roots3ExactChecked = 0,
            roots3ExactZero = 0,
            roots3SquarecheckPass = 0,
            divisorCountA2 = 0,
            factorizationSteps = 0,
            xSquare = emptyList(),
            earlyStopHit = false,
        )

    private class SingleProfileRegistry(private val profile: KrakenCryptoProfile) : CryptoProfileRegistry {
        override fun find(profileId: String): KrakenCryptoProfile? =
            profile.takeIf { it.profileId == profileId }
    }

    private class FakeAdamovaValidator(
        private val result: NativeAdamovaResult?,
    ) : AdamovaNativeValidator {
        override fun status(): String = "fake-native-adamova-v1"
        override fun classify(a: BigInteger, b: BigInteger): NativeAdamovaResult? = result
    }

    private class RecordingAeadProvider : AeadProvider {
        var lastSealAssociatedData: ByteArray? = null

        override fun seal(
            plaintext: Plaintext,
            key: DerivedKey,
            associatedData: ByteArray,
        ): Ciphertext {
            lastSealAssociatedData = associatedData
            return Ciphertext(
                bytes = plaintext.bytes + associatedData,
                recipientPublicKey = "recipient",
                algorithm = "adamova-bound-test-aead",
            )
        }

        override fun open(
            ciphertext: Ciphertext,
            key: DerivedKey,
            associatedData: ByteArray,
        ): Plaintext {
            val suffix = associatedData
            assertArrayEquals(suffix, ciphertext.bytes.takeLast(suffix.size).toByteArray())
            return Plaintext(ciphertext.bytes.dropLast(suffix.size).toByteArray())
        }
    }
}

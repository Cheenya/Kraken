package com.disser.kraken.crypto

import com.disser.kraken.nativecore.NativeAdamovaResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class ProductCryptoAdmissionGateTest {
    @Test
    fun standardProfileBypassesAdamovaAsNotApplicable() {
        val validator = FakeAdamovaValidator()
        val gate = ProductCryptoAdmissionGate(
            validator = validator,
            now = { 1_700_000_000_000 },
        )

        val result = gate.evaluate(DefaultCryptoProfileRegistry.standardProfile)

        assertEquals(AdamovaAdmissionDecision.NOT_APPLICABLE_STANDARD_PROFILE, result.decision)
        assertTrue(result.acceptedForPacketPolicy)
        assertEquals(KrakenCryptoProfileDefaults.STANDARD_ADMISSION_DECISION_HASH, result.decisionHash)
        assertEquals(KrakenCryptoProfileDefaults.STANDARD_NATIVE_BACKEND_VERSION, result.nativeBackendVersion)
        assertEquals(0, validator.classifyCalls)
    }

    @Test
    fun experimentalProfileAcceptsWhenNativeFindsNoSmallTorsionRisk() {
        val profile = experimentalProfile(a = "65537", b = "104729")
        val gate = ProductCryptoAdmissionGate(
            validator = FakeAdamovaValidator(
                result = nativeResult(classificationCase = "A4"),
            ),
            now = { 1_700_000_000_000 },
        )

        val result = gate.evaluate(profile)

        assertEquals(AdamovaAdmissionDecision.ACCEPT, result.decision)
        assertTrue(result.acceptedForPacketPolicy)
        assertEquals("A4", result.classificationCase)
        assertTrue(result.riskFlags.isEmpty())
    }

    @Test
    fun singularExperimentalProfileIsRejected() {
        val gate = ProductCryptoAdmissionGate(
            validator = FakeAdamovaValidator(
                result = nativeResult(singular = true, classificationCase = "SINGULAR"),
            ),
        )

        val result = gate.evaluate(experimentalProfile())

        assertEquals(AdamovaAdmissionDecision.REJECT_SINGULAR, result.decision)
        assertFalse(result.acceptedForPacketPolicy)
        assertEquals(listOf("singular"), result.riskFlags)
    }

    @Test
    fun twoTorsionExperimentalProfileIsRejected() {
        val gate = ProductCryptoAdmissionGate(
            validator = FakeAdamovaValidator(
                result = nativeResult(twoTorsionRootCount = 1, classificationCase = "A5"),
            ),
        )

        val result = gate.evaluate(experimentalProfile())

        assertEquals(AdamovaAdmissionDecision.REJECT_SMALL_TORSION_RISK, result.decision)
        assertFalse(result.acceptedForPacketPolicy)
        assertEquals(listOf("rational_2_torsion"), result.riskFlags)
    }

    @Test
    fun threeTorsionIndicatorExperimentalProfileIsRejected() {
        val gate = ProductCryptoAdmissionGate(
            validator = FakeAdamovaValidator(
                result = nativeResult(hasThreeTorsionIndicator = true, threeTorsionRootCount = 1, classificationCase = "A1"),
            ),
        )

        val result = gate.evaluate(experimentalProfile())

        assertEquals(AdamovaAdmissionDecision.REJECT_SMALL_TORSION_RISK, result.decision)
        assertFalse(result.acceptedForPacketPolicy)
        assertTrue("three_torsion_indicator" in result.riskFlags)
    }

    @Test
    fun sizeGuardedExperimentalProfileIsNotAcceptedAutomatically() {
        val gate = ProductCryptoAdmissionGate(
            validator = FakeAdamovaValidator(
                result = nativeResult(classificationCase = "SIZE_GUARDED", earlyStopHit = true),
            ),
        )

        val result = gate.evaluate(experimentalProfile())

        assertEquals(AdamovaAdmissionDecision.SIZE_GUARDED, result.decision)
        assertFalse(result.acceptedForPacketPolicy)
        assertTrue("size_guarded" in result.riskFlags)
    }

    @Test
    fun nativeUnavailableFailsClosedForExperimentalProfile() {
        val gate = ProductCryptoAdmissionGate(
            validator = FakeAdamovaValidator(result = null),
        )

        val result = gate.evaluate(experimentalProfile())

        assertEquals(AdamovaAdmissionDecision.NATIVE_UNAVAILABLE, result.decision)
        assertFalse(result.acceptedForPacketPolicy)
        assertEquals(listOf("native_adamova_backend_unavailable"), result.riskFlags)
    }

    @Test
    fun cachedResultIsReusedForSameProfileBackendAndPolicy() {
        val store = InMemoryAdmissionStore()
        val validator = FakeAdamovaValidator(result = nativeResult(classificationCase = "A4"))
        val gate = ProductCryptoAdmissionGate(
            validator = validator,
            admissionStore = store,
            now = { 1_700_000_000_000 },
        )
        val profile = experimentalProfile()

        val first = gate.evaluate(profile)
        val second = gate.evaluate(profile)

        assertEquals(first, second)
        assertEquals(1, validator.classifyCalls)
        assertEquals(1, store.load().size)
    }

    private fun experimentalProfile(
        a: String = "65537",
        b: String = "104729",
    ): KrakenCryptoProfile =
        KrakenCryptoProfile(
            profileId = "experimental-test-profile",
            profileVersion = 1,
            profileKind = CryptoProfileKind.EXPERIMENTAL_ADAMOVA_CURVE_PROFILE,
            curveA = a,
            curveB = b,
        )

    private fun nativeResult(
        singular: Boolean = false,
        twoTorsionRootCount: Int = 0,
        threeTorsionRootCount: Int = 0,
        hasThreeTorsionIndicator: Boolean = false,
        classificationCase: String = "A4",
        earlyStopHit: Boolean = false,
    ): NativeAdamovaResult =
        NativeAdamovaResult(
            a = BigInteger.valueOf(65537),
            b = BigInteger.valueOf(104729),
            singular = singular,
            discriminant = BigInteger.ONE,
            twoTorsionRootCount = twoTorsionRootCount,
            twoTorsionRoots = emptyList(),
            threeTorsionRootCount = threeTorsionRootCount,
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
            earlyStopHit = earlyStopHit,
        )

    private class FakeAdamovaValidator(
        private val result: NativeAdamovaResult? = null,
    ) : AdamovaNativeValidator {
        var classifyCalls = 0
        override fun status(): String = "fake-native-adamova-v1"
        override fun classify(a: BigInteger, b: BigInteger): NativeAdamovaResult? {
            classifyCalls += 1
            return result
        }
    }

    private class InMemoryAdmissionStore : CryptoProfileAdmissionStore {
        private var results = emptyList<ProductCryptoAdmissionResult>()
        override fun load(): List<ProductCryptoAdmissionResult> = results
        override fun find(
            profileHash: String,
            nativeBackendVersion: String,
            policyVersion: Int,
        ): ProductCryptoAdmissionResult? =
            results.firstOrNull {
                it.profileHash == profileHash &&
                    it.nativeBackendVersion == nativeBackendVersion &&
                    it.policyVersion == policyVersion
            }

        override fun upsert(result: ProductCryptoAdmissionResult): List<ProductCryptoAdmissionResult> {
            results = CryptoProfileAdmissionStoragePolicy.prune(results + result)
            return results
        }
    }
}

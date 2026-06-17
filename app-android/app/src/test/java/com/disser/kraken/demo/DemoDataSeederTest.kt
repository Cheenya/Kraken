package com.disser.kraken.demo

import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.relationship.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDataSeederTest {
    private val identity = LocalIdentity(
        identityId = "local-demo-test",
        displayName = "Reviewer",
        publicKeyEncoded = "placeholder-pub:REVNTy1MT0NBTA",
        privateKeyReference = "placeholder-private-ref:local-demo-test",
        fingerprint = FingerprintFormatter.shortFingerprint("placeholder-pub:REVNTy1MT0NBTA"),
        createdAtEpochMillis = 1_700_000_000_000,
    )

    @Test
    fun relationshipSamplesAreVisiblyMarkedAsExamples() {
        val relationships = DemoDataSeeder.createRelationshipSamples(
            identity = identity,
            nowEpochMillis = 1_700_000_000_100,
        )

        assertEquals(3, relationships.size)
        relationships.forEach { relationship ->
            assertTrue(relationship.relationshipId.startsWith("demo-"))
            assertTrue(relationship.peerDisplayName.orEmpty().contains("пример", ignoreCase = true))
            assertTrue(DemoDataSeeder.isDemoRelationship(relationship))
        }
    }

    @Test
    fun relationshipSamplesCoverPendingActiveAndBlockedReviewStates() {
        val states = DemoDataSeeder.createRelationshipSamples(
            identity = identity,
            nowEpochMillis = 1_700_000_000_100,
        ).map { it.state }.toSet()

        assertTrue(RelationshipState.PENDING_IMPORT in states)
        assertTrue(RelationshipState.ACTIVE in states)
        assertTrue(RelationshipState.BLOCKED_BY_PEER in states)
    }

    @Test
    fun relationshipSamplesDoNotCreateRealmOrDiscoveryContext() {
        val relationships = DemoDataSeeder.createRelationshipSamples(
            identity = identity,
            nowEpochMillis = 1_700_000_000_100,
        )

        relationships.forEach { relationship ->
            assertEquals(identity.publicKeyEncoded, relationship.localIdentityPublicKey)
            assertEquals(null, relationship.realmId)
            assertFalse(relationship.peerDisplayName.orEmpty().contains("public", ignoreCase = true))
            assertFalse(relationship.peerDisplayName.orEmpty().contains("discover", ignoreCase = true))
        }
    }
}

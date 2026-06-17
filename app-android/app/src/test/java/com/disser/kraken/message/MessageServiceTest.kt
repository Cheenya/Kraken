package com.disser.kraken.message

import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.relationship.OfflineHandshakeRole
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MessageServiceTest {
    private val localIdentity = LocalIdentity(
        identityId = "local",
        displayName = "Alice",
        publicKeyEncoded = "placeholder-pub:alice",
        privateKeyReference = "placeholder-private-ref:alice",
        fingerprint = FingerprintFormatter.shortFingerprint("placeholder-pub:alice"),
        createdAtEpochMillis = 1_700_000_000_000,
    )

    @Test
    fun activeRelationshipCanCreateOutgoingMessageReadyForTransport() {
        val relationship = relationship(RelationshipState.ACTIVE)

        val message = MessageService.createOutgoingMessage(
            localIdentity = localIdentity,
            relationship = relationship,
            body = "  hello  ",
            nowEpochMillis = 1_700_000_000_100,
        )

        assertEquals("hello", message.body)
        assertEquals(MessageDirection.OUTGOING, message.direction)
        assertEquals(MessageStatus.READY_FOR_TRANSPORT, message.status)
        assertEquals(MessageService.conversationIdFor(relationship), message.conversationId)
    }

    @Test
    fun pendingRelationshipCannotCreateOutgoingMessage() {
        val relationship = relationship(RelationshipState.PENDING_HANDSHAKE)

        assertThrows(IllegalArgumentException::class.java) {
            MessageService.createOutgoingMessage(
                localIdentity = localIdentity,
                relationship = relationship,
                body = "hello",
            )
        }
    }

    @Test
    fun blankMessageRejected() {
        val relationship = relationship(RelationshipState.ACTIVE)

        assertThrows(IllegalArgumentException::class.java) {
            MessageService.createOutgoingMessage(
                localIdentity = localIdentity,
                relationship = relationship,
                body = "   ",
            )
        }
    }

    @Test
    fun relationshipMustBelongToLocalIdentity() {
        val relationship = relationship(RelationshipState.ACTIVE).copy(
            localIdentityPublicKey = "placeholder-pub:other-device",
        )

        assertThrows(IllegalArgumentException::class.java) {
            MessageService.createOutgoingMessage(
                localIdentity = localIdentity,
                relationship = relationship,
                body = "hello",
            )
        }
    }

    @Test
    fun pruneKeepsLastTwoHundredMessagesPerConversation() {
        val relationship = relationship(RelationshipState.ACTIVE)
        val conversationId = MessageService.conversationIdFor(relationship)
        val messages = (0 until 205).map { index ->
            LocalMessage(
                messageId = "message-$index",
                conversationId = conversationId,
                relationshipId = relationship.relationshipId,
                peerFingerprint = relationship.peerFingerprint,
                direction = MessageDirection.OUTGOING,
                status = MessageStatus.READY_FOR_TRANSPORT,
                body = "message $index",
                createdAtEpochMillis = index.toLong(),
                updatedAtEpochMillis = index.toLong(),
            )
        }

        val pruned = MessageService.pruneMessages(messages)

        assertEquals(MessageService.MAX_MESSAGES_PER_CONVERSATION, pruned.size)
        assertEquals("message-5", pruned.first().messageId)
        assertEquals("message-204", pruned.last().messageId)
    }

    @Test
    fun conversationOrderKeepsDelayedOlderIncomingInSenderPosition() {
        val relationship = relationship(RelationshipState.ACTIVE)
        val conversationId = MessageService.conversationIdFor(relationship)
        val live = localMessage(
            messageId = "message-live",
            conversationId = conversationId,
            createdAtEpochMillis = 2_000,
        )
        val delayedOlder = localMessage(
            messageId = "message-delayed",
            conversationId = conversationId,
            createdAtEpochMillis = 1_000,
            updatedAtEpochMillis = 9_000,
        )

        val ordered = MessageService.sortConversationMessages(listOf(live, delayedOlder))

        assertEquals(listOf("message-delayed", "message-live"), ordered.map { it.messageId })
    }

    @Test
    fun conversationOrderUsesMessageIdForSameTimestamp() {
        val relationship = relationship(RelationshipState.ACTIVE)
        val conversationId = MessageService.conversationIdFor(relationship)
        val laterId = localMessage("message-b", conversationId, createdAtEpochMillis = 1_000)
        val earlierId = localMessage("message-a", conversationId, createdAtEpochMillis = 1_000)

        val ordered = MessageService.sortConversationMessages(listOf(laterId, earlierId))

        assertEquals(listOf("message-a", "message-b"), ordered.map { it.messageId })
    }

    @Test
    fun pruneKeepsLastTwoHundredByConversationOrder() {
        val relationship = relationship(RelationshipState.ACTIVE)
        val conversationId = MessageService.conversationIdFor(relationship)
        val messages = (0 until 201).map { index ->
            localMessage(
                messageId = "message-${index.toString().padStart(3, '0')}",
                conversationId = conversationId,
                createdAtEpochMillis = 1_000,
            )
        }

        val pruned = MessageService.pruneMessages(messages)

        assertEquals(MessageService.MAX_MESSAGES_PER_CONVERSATION, pruned.size)
        assertEquals("message-001", pruned.first().messageId)
        assertEquals("message-200", pruned.last().messageId)
    }

    private fun relationship(state: RelationshipState): Relationship =
        Relationship(
            relationshipId = "relationship-1",
            localIdentityPublicKey = localIdentity.publicKeyEncoded,
            peerPublicKey = "placeholder-pub:bob",
            peerDisplayName = "Bob",
            peerFingerprint = "B0B0 B0B0 B0B0",
            realmId = null,
            state = state,
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_000,
            sourceInviteId = "invite-1",
            offlineHandshakeRole = OfflineHandshakeRole.INVITER,
        )

    private fun localMessage(
        messageId: String,
        conversationId: String,
        createdAtEpochMillis: Long,
        updatedAtEpochMillis: Long = createdAtEpochMillis,
    ): LocalMessage =
        LocalMessage(
            messageId = messageId,
            conversationId = conversationId,
            relationshipId = "relationship-1",
            peerFingerprint = "B0B0 B0B0 B0B0",
            direction = MessageDirection.INCOMING,
            status = MessageStatus.DELIVERED_TO_PEER,
            body = "hello",
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
}

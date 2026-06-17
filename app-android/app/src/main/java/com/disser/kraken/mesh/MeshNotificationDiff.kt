package com.disser.kraken.mesh

import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageDirection
import com.disser.kraken.message.MessageService

object MeshNotificationDiff {
    fun newIncomingMessages(
        before: List<LocalMessage>,
        after: List<LocalMessage>,
    ): List<LocalMessage> {
        val beforeIds = before.asSequence().map { it.messageId }.toSet()
        return after
            .filter { it.direction == MessageDirection.INCOMING }
            .filterNot { it.messageId in beforeIds }
            .let(MessageService::sortConversationMessages)
    }
}

package com.disser.kraken.ui.screens

import com.disser.kraken.message.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMeshStatusLabelTest {
    @Test
    fun deliveryLabelsUseMeshStatesWithoutReadReceipts() {
        assertEquals("ждёт маршрут", visibleMeshDeliveryLabel(MessageStatus.LOCAL_PENDING))
        assertEquals("ждёт маршрут", visibleMeshDeliveryLabel(MessageStatus.READY_FOR_TRANSPORT))
        assertEquals("отправлено", visibleMeshDeliveryLabel(MessageStatus.SENT_TO_TRANSPORT))
        assertEquals("ошибка", visibleMeshDeliveryLabel(MessageStatus.FAILED))
        assertEquals("доставлено", visibleMeshDeliveryLabel(MessageStatus.DELIVERED_TO_PEER))
    }

    @Test
    fun technicalMessageDetectionSeparatesPayloadsFromNormalText() {
        assertTrue(isTechnicalMessageBody("""{"message_id":"m1","body":"hello"}"""))
        assertTrue(isTechnicalMessageBody("packet_id=p1 payload_type=LOCAL_MESSAGE_JSON"))
        assertTrue(isTechnicalMessageBody("directed Wi-Fi Direct route trial samsung-to-xiaomi"))
        assertFalse(isTechnicalMessageBody("Привет, как дела?"))
    }

    @Test
    fun technicalMessagePreviewIsCompactSingleLine() {
        assertEquals(
            """{"message_id":"m1", "body":"hello"}""",
            technicalMessagePreview(
                """
                {"message_id":"m1",
                 "body":"hello"}
                """.trimIndent(),
            ),
        )
    }
}

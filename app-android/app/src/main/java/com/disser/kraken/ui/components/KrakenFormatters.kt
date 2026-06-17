package com.disser.kraken.ui.components

import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatEpoch(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("ru", "RU")).format(Date(epochMillis))

fun nonActiveRelationshipReason(relationship: Relationship): String =
    when (relationship.state) {
        RelationshipState.ACTIVE -> "Отправка сообщений будет реализована позже."
        RelationshipState.PENDING_IMPORT -> "Проверьте invite перед началом рукопожатия."
        RelationshipState.PENDING_HANDSHAKE -> "Рукопожатие должно быть принято перед общением."
        RelationshipState.UNLINK_REQUESTED -> "Идёт отвязка контакта."
        RelationshipState.UNLINKED -> "Контакт завершён. Нужно новое QR-приглашение."
        RelationshipState.BLOCKED_BY_PEER -> "Пир завершил контакт. Нужно новое QR-приглашение."
        RelationshipState.REJOIN_REQUIRED -> "Старый контакт нельзя использовать повторно."
    }

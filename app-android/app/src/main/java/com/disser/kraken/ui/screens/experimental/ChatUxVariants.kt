package com.disser.kraken.ui.screens.experimental

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import com.disser.kraken.ui.components.SectionHeader
import com.disser.kraken.ui.icons.KrakenIcons

private val ReferenceAccent = Color(0xFF2AABEE)
private val ReferenceOutgoing = Color(0xFF2B5278)
private val KrakenOutgoing = Color(0xFF063F3C)
private val AmoledIncoming = Color(0xFF111719)
private val DarkIncoming = Color(0xFF17212B)
private val BubbleText = Color(0xFFEAF2F4)
private val MutedText = Color(0xFF9AA7B2)
private val ErrorText = Color(0xFFFF7A7A)

@Composable
fun ChatUxVariants(relationships: List<Relationship>) {
    val activeRelationship = relationships.firstOrNull { it.state == RelationshipState.ACTIVE }
    val displayName = activeRelationship?.peerDisplayName ?: "Контакт"

    SectionHeader("Варианты чатов")
    BaselineChatVariant(displayName)
    AmoledChatVariant(displayName)
    DenseListChatVariant(displayName)
}

@Composable
private fun BaselineChatVariant(displayName: String) {
    VariantFrame(
        title = "A. Базовый чат",
        subtitle = "Светлый акцент, список без карточек, полноэкранный разговор и круглые аватары.",
        container = Color(0xFF0E1621),
    ) {
        ChatListMock(
            displayName = displayName,
            accent = ReferenceAccent,
            surface = Color(0xFF0E1621),
            rowDivider = Color(0xFF1C2A35),
            showUnread = true,
        )
        ConversationMock(
            displayName = displayName,
            accent = ReferenceAccent,
            outgoing = ReferenceOutgoing,
            incoming = DarkIncoming,
            composer = Color(0xFF17212B),
        )
    }
}

@Composable
private fun AmoledChatVariant(displayName: String) {
    VariantFrame(
        title = "B. Чёрный экран",
        subtitle = "Рекомендован для Kraken: настоящий чёрный фон и фирменный бирюзовый акцент.",
        container = Color.Black,
    ) {
        ChatListMock(
            displayName = displayName,
            accent = MaterialTheme.colorScheme.primary,
            surface = Color.Black,
            rowDivider = Color(0xFF141A1B),
            showUnread = false,
        )
        ConversationMock(
            displayName = displayName,
            accent = MaterialTheme.colorScheme.primary,
            outgoing = KrakenOutgoing,
            incoming = AmoledIncoming,
            composer = Color(0xFF101415),
        )
    }
}

@Composable
private fun DenseListChatVariant(displayName: String) {
    VariantFrame(
        title = "C. Компактный список",
        subtitle = "Для частого использования: максимум диалогов на экране и минимум декоративных отступов.",
        container = Color(0xFF0D1116),
    ) {
        DenseChatListMock(displayName)
        ConversationMock(
            displayName = displayName,
            accent = MaterialTheme.colorScheme.primary,
            outgoing = KrakenOutgoing,
            incoming = Color(0xFF12181B),
            composer = Color(0xFF111719),
            compact = true,
        )
    }
}

@Composable
private fun VariantFrame(
    title: String,
    subtitle: String,
    container: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = BubbleText, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            content()
        }
    }
}

@Composable
private fun ChatListMock(
    displayName: String,
    accent: Color,
    surface: Color,
    rowDivider: Color,
    showUnread: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = surface,
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            ChatTopLine("Чаты", accent)
            ChatRowPreview(displayName, "Вы: сообщение не отправлено", "01:21", accent, rowDivider, showUnread)
            ChatRowPreview("Аня", "BLE найден · можно отправлять", "Вчера", accent, rowDivider, false)
            ChatRowPreview("Группа проверки", "Новый QR принят", "Пн", accent, rowDivider, false)
        }
    }
}

@Composable
private fun DenseChatListMock(displayName: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0D1116),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            ChatTopLine("Чаты", MaterialTheme.colorScheme.primary)
            DenseChatRow(displayName, "Вы: сообщение не отправлено", "01:21")
            DenseChatRow("Аня", "Готово, увидимся через QR", "00:12")
            DenseChatRow("Группа проверки", "3 сообщения", "Вчера")
            DenseChatRow("Рабочий чат", "Документы пришли", "Пн")
        }
    }
}

@Composable
private fun ChatTopLine(title: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = BubbleText, fontWeight = FontWeight.SemiBold)
        Text("поиск", style = MaterialTheme.typography.labelMedium, color = accent)
    }
}

@Composable
private fun ChatRowPreview(
    title: String,
    subtitle: String,
    time: String,
    accent: Color,
    divider: Color,
    unread: Boolean,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(title, accent, 44.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = BubbleText, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(time, style = MaterialTheme.typography.labelSmall, color = MutedText)
                if (unread) {
                    UnreadDot(accent)
                } else {
                    Text("✓", style = MaterialTheme.typography.labelSmall, color = accent)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(divider),
        )
    }
}

@Composable
private fun DenseChatRow(title: String, subtitle: String, time: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title, MaterialTheme.colorScheme.primary, 38.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = BubbleText, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(time, style = MaterialTheme.typography.labelSmall, color = MutedText)
    }
}

@Composable
private fun ConversationMock(
    displayName: String,
    accent: Color,
    outgoing: Color,
    incoming: Color,
    composer: Color,
    compact: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
            ChatHeaderMock(displayName, accent)
            DatePillMock("Сегодня")
            BubbleMock("привет", incoming = true, incomingColor = incoming, outgoingColor = outgoing, time = "18:50")
            BubbleMock("сообщение не отправлено", incoming = false, incomingColor = incoming, outgoingColor = outgoing, time = "01:21 · ошибка")
            ComposerMock(accent, composer)
        }
    }
}

@Composable
private fun ChatHeaderMock(displayName: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KrakenIcons.Back, contentDescription = null, tint = BubbleText, modifier = Modifier.size(22.dp))
        Avatar(displayName, accent, 38.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(displayName, style = MaterialTheme.typography.bodyMedium, color = BubbleText, fontWeight = FontWeight.SemiBold)
            Text("активный контакт", style = MaterialTheme.typography.labelSmall, color = MutedText)
        }
        Icon(KrakenIcons.Contacts, contentDescription = null, tint = BubbleText, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DatePillMock(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFF182026)) {
            Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MutedText)
        }
    }
}

@Composable
private fun BubbleMock(
    text: String,
    incoming: Boolean,
    incomingColor: Color,
    outgoingColor: Color,
    time: String,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .widthIn(max = 240.dp)
                .align(if (incoming) Alignment.CenterStart else Alignment.CenterEnd),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (incoming) 4.dp else 18.dp,
                bottomEnd = if (incoming) 18.dp else 4.dp,
            ),
            color = if (incoming) incomingColor else outgoingColor,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = BubbleText)
                Text(
                    time,
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (time.contains("ошибка")) ErrorText else MutedText,
                )
            }
        }
    }
}

@Composable
private fun ComposerMock(accent: Color, composer: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = composer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Сообщение", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MutedText)
            Surface(shape = CircleShape, color = accent, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(KrakenIcons.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun Avatar(title: String, accent: Color, size: androidx.compose.ui.unit.Dp) {
    Surface(shape = CircleShape, color = accent.copy(alpha = 0.34f), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                title.take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = BubbleText,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun UnreadDot(accent: Color) {
    Surface(shape = CircleShape, color = accent, modifier = Modifier.size(18.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text("2", style = MaterialTheme.typography.labelSmall, color = Color.Black)
        }
    }
}

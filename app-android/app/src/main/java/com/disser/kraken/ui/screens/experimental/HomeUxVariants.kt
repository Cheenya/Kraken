package com.disser.kraken.ui.screens.experimental

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.ComplaintEvent
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.ui.components.MetricPill
import com.disser.kraken.ui.components.SectionHeader
import com.disser.kraken.ui.components.StateBadge

@Composable
fun HomeUxVariants(
    localIdentity: LocalIdentity?,
    pendingInvites: List<PendingInviteImport>,
    relationships: List<Relationship>,
    complaints: List<ComplaintEvent>,
    realmSnapshot: RealmSnapshot,
) {
    SectionHeader("Варианты главной")
    MessengerHubHomeVariant(localIdentity, pendingInvites, relationships, realmSnapshot)
    PrivacyOnboardingHomeVariant(localIdentity, pendingInvites)
    MeshOperationsHomeVariant(pendingInvites, relationships, complaints, realmSnapshot)
    LiquidGlassInspiredHomeVariant(localIdentity, pendingInvites, relationships, realmSnapshot)
}

@Composable
fun MessengerHubHomeVariant(
    localIdentity: LocalIdentity?,
    pendingInvites: List<PendingInviteImport>,
    relationships: List<Relationship>,
    realmSnapshot: RealmSnapshot,
) {
    VariantShell(
        title = "Центр сообщений",
        subtitle = "Недавние диалоги, профиль на устройстве и быстрые QR-действия.",
    ) {
        IdentityMini(localIdentity)
        relationships.take(3).forEach {
            CompactListRow(
                title = it.peerDisplayName ?: "Контакт",
                detail = it.peerFingerprint,
                badge = it.state.name,
            )
        }
        if (relationships.isEmpty()) {
            CompactListRow("Диалогов пока нет", "Сканируйте QR или загрузите тестовые данные.", "ПУСТО")
        }
        QuickActionRow("Мой QR", "Скан QR", "Сопряжение", "Реалмы")
        MetricRow(
            "Ожидают" to pendingInvites.size.toString(),
            "Чаты" to relationships.count { it.state.name == "ACTIVE" }.toString(),
            "Реалмы" to realmSnapshot.realms.size.toString(),
        )
    }
}

@Composable
fun PrivacyOnboardingHomeVariant(
    localIdentity: LocalIdentity?,
    pendingInvites: List<PendingInviteImport>,
) {
    VariantShell(
        title = "Первый запуск",
        subtitle = "Сначала профиль на устройстве и QR-подтверждение, потом чаты.",
    ) {
        StepRow("1", "Создать локальную личность", if (localIdentity == null) "нужно" else "готово")
        StepRow("2", "Показать одноразовый QR", "только рукопожатие")
        StepRow("3", "Сканировать приглашение", "${pendingInvites.size} ожидают")
        StepRow("4", "Завершить рукопожатие", "без обхода доступа")
        localIdentity?.let { IdentityMini(it) }
    }
}

@Composable
fun MeshOperationsHomeVariant(
    pendingInvites: List<PendingInviteImport>,
    relationships: List<Relationship>,
    complaints: List<ComplaintEvent>,
    realmSnapshot: RealmSnapshot,
) {
    VariantShell(
        title = "Панель проверки связи",
        subtitle = "Технический вид для реалмов, relay и локальных проверок.",
    ) {
        MetricRow(
            "Ожидают" to pendingInvites.size.toString(),
            "Контакты" to relationships.size.toString(),
            "Реалмы" to realmSnapshot.realms.size.toString(),
        )
        MetricRow(
            "Жалобы" to complaints.size.toString(),
            "Лимит" to realmSnapshot.realms.firstOrNull()?.capacityState?.capacity?.toString().orEmpty().ifBlank { "500" },
            "Ретрансляция" to "локально",
        )
        realmSnapshot.realms.take(2).forEach {
            CompactListRow(it.name, "${it.capacityState.memberCount}/${it.capacityState.capacity} участников", it.localState.name)
        }
        CompactListRow("Буфер передачи", "Симулятор пакетов и LAN-проверка остаются локальными.", "МОДЕЛЬ")
    }
}

@Composable
fun LiquidGlassInspiredHomeVariant(
    localIdentity: LocalIdentity?,
    pendingInvites: List<PendingInviteImport>,
    relationships: List<Relationship>,
    realmSnapshot: RealmSnapshot,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Стеклянная главная",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Тёмные слои, бирюзовый акцент и оригинальная реализация.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GlassPanel("Kraken", localIdentity?.displayName ?: "Личность не создана", "по QR")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            GlassPanel("QR", "Рукопожатие", pendingInvites.size.toString(), Modifier.weight(1f))
            GlassPanel("Чаты", "Активных ${relationships.count { it.state.name == "ACTIVE" }}", relationships.size.toString(), Modifier.weight(1f))
        }
        GlassPanel("Реалмы", "Без публичного поиска", realmSnapshot.realms.size.toString())
    }
}

@Composable
private fun VariantShell(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            content()
        }
    }
}

@Composable
private fun IdentityMini(localIdentity: LocalIdentity?) {
    CompactListRow(
        title = localIdentity?.displayName ?: "Личность не создана",
        detail = localIdentity?.fingerprint ?: "Сначала создайте локальную личность.",
        badge = if (localIdentity == null) "НУЖНО" else "НА УСТРОЙСТВЕ",
    )
}

@Composable
private fun CompactListRow(title: String, detail: String, badge: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StateBadge(badge)
        }
    }
}

@Composable
private fun StepRow(number: String, title: String, state: String) {
    CompactListRow("$number. $title", "Без аккаунта и публичного поиска.", state)
}

@Composable
private fun QuickActionRow(vararg labels: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(vararg metrics: Pair<String, String>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.forEach { (label, value) ->
            MetricPill(label, value, Modifier.weight(1f))
        }
    }
}

@Composable
private fun GlassPanel(
    title: String,
    detail: String,
    badge: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StateBadge(badge)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

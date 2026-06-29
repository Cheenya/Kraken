package com.disser.kraken.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.disser.kraken.handshake.HandshakePayloadCodec
import com.disser.kraken.handshake.OfflineHandshakeService
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipService
import com.disser.kraken.relationship.RelationshipState

internal data class HandshakeQrPayloadView(
    val title: String,
    val payloadJson: String,
    val details: List<String>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RelationshipHandshakeActions(
    navController: NavHostController,
    relationship: Relationship,
    localIdentity: LocalIdentity?,
    realmSnapshot: RealmSnapshot,
    handshakeService: OfflineHandshakeService,
    onRelationshipUpdated: (Relationship) -> Unit,
    onOpenChat: (Relationship) -> Unit,
    onPayloadReady: (HandshakeQrPayloadView) -> Unit,
    onError: (String) -> Unit,
) {
    var showManualFallback by remember(relationship.relationshipId) { mutableStateOf(false) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (relationship.state == RelationshipState.ACTIVE) {
            TextButton(onClick = { onOpenChat(relationship) }) {
                Text("Открыть чат")
            }
        }
        if (relationship.state in setOf(RelationshipState.PENDING_IMPORT, RelationshipState.PENDING_HANDSHAKE) && !showManualFallback) {
            TextButton(onClick = { showManualFallback = true }) {
                Text("Не получилось через Bluetooth?")
            }
        }
        if (relationship.state == RelationshipState.PENDING_IMPORT && showManualFallback) {
            TextButton(
                onClick = {
                    val identity = localIdentity
                    if (identity == null) {
                        onError("Сначала создайте профиль Kraken.")
                    } else {
                        val handshaking = RelationshipService.startHandshake(relationship)
                        onRelationshipUpdated(handshaking)
                        handshakeService.generateResponsePayload(identity, handshaking)
                            .fold(
                                onSuccess = { payload ->
                                    onPayloadReady(
                                        HandshakeQrPayloadView(
                                            title = "QR подтверждения",
                                            payloadJson = HandshakePayloadCodec.encodeResponse(payload),
                                            details = listOf(
                                                "Покажите этот QR устройству, которое создало приглашение.",
                                                "Этот QR завершает подтверждение контакта.",
                                                "После сканирования контакт продолжит сопряжение.",
                                            ),
                                        )
                                    )
                                },
                                onFailure = { onError(it.message ?: "Не удалось создать QR подтверждения.") },
                            )
                    }
                }
            ) {
                Text("Завершить через QR")
            }
        }
        if (relationship.state == RelationshipState.PENDING_HANDSHAKE && showManualFallback) {
            TextButton(
                onClick = {
                    val identity = localIdentity
                    if (identity == null) {
                        onError("Сначала создайте профиль Kraken.")
                    } else {
                        handshakeService.generateResponsePayload(identity, relationship)
                            .fold(
                                onSuccess = { payload ->
                                    onPayloadReady(
                                        HandshakeQrPayloadView(
                                            title = "QR подтверждения",
                                            payloadJson = HandshakePayloadCodec.encodeResponse(payload),
                                            details = listOf(
                                                "Покажите этот QR устройству, которое создало приглашение.",
                                                "Это устройство пока ждёт завершение сопряжения.",
                                                "Этот QR завершает подтверждение контакта.",
                                            ),
                                        )
                                    )
                                },
                                onFailure = { onError(it.message ?: "Не удалось создать QR подтверждения.") },
                            )
                    }
                }
            ) {
                Text("Показать QR")
            }
        }
        if (relationship.state == RelationshipState.PENDING_HANDSHAKE && showManualFallback) {
            OutlinedButton(onClick = { navController.navigate(KrakenRoute.QrScanner.route) }) {
                Text("Сканировать QR")
            }
        }
    }
}

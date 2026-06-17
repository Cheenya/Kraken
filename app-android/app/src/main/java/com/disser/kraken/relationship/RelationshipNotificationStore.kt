package com.disser.kraken.relationship

import android.content.Context
import com.disser.kraken.storage.KrakenStorageKeys

class RelationshipNotificationStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        KrakenStorageKeys.Preferences.RELATIONSHIP_NOTIFICATIONS,
        Context.MODE_PRIVATE,
    )

    fun loadMutedRelationshipIds(): Set<String> =
        preferences.getStringSet(KrakenStorageKeys.RelationshipNotifications.MUTED_RELATIONSHIP_IDS, emptySet())
            ?.toSet()
            .orEmpty()

    fun isMuted(relationshipId: String): Boolean =
        relationshipId in loadMutedRelationshipIds()

    fun setMuted(relationshipId: String, muted: Boolean): Set<String> {
        val updated = if (muted) {
            loadMutedRelationshipIds() + relationshipId
        } else {
            loadMutedRelationshipIds() - relationshipId
        }
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putStringSet(KrakenStorageKeys.RelationshipNotifications.MUTED_RELATIONSHIP_IDS, updated)
            .apply()
        return updated
    }
}

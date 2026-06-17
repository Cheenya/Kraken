package com.disser.kraken.mesh

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.realm.RealmCommunicationPolicy
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.Relationship

object MeshForegroundPolicy {
    fun shouldAutoStartLan(
        localIdentity: LocalIdentity?,
        relationships: List<Relationship>,
        realmSnapshot: RealmSnapshot,
        meshState: MeshState,
    ): Boolean {
        val identity = localIdentity ?: return false
        if (meshState !in setOf(MeshState.OFF, MeshState.ERROR)) return false
        return relationships.any { relationship ->
            RealmCommunicationPolicy.canUseRelationship(
                localIdentity = identity,
                relationship = relationship,
                realmSnapshot = realmSnapshot,
            ).allowed
        }
    }
}

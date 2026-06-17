package com.disser.kraken.realm

import android.content.Context
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.storage.KrakenStorageKeys
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

class RealmStore(context: Context) {
    private val preferences = context.getSharedPreferences(KrakenStorageKeys.Preferences.REALMS, Context.MODE_PRIVATE)
    private val realmSerializer = ListSerializer(Realm.serializer())
    private val certificateSerializer = ListSerializer(MembershipCertificate.serializer())
    private val inviteEdgeSerializer = ListSerializer(InviteEdge.serializer())
    private val pendingRequestSerializer = ListSerializer(PendingMembershipRequest.serializer())

    fun loadRealms(): List<Realm> =
        loadList(KrakenStorageKeys.Realms.LIST, realmSerializer)

    fun loadMembershipCertificates(): List<MembershipCertificate> =
        loadList(KrakenStorageKeys.Realms.MEMBERSHIP_CERTIFICATES, certificateSerializer)

    fun loadInviteEdges(): List<InviteEdge> =
        loadList(KrakenStorageKeys.Realms.INVITE_EDGES, inviteEdgeSerializer)

    fun loadPendingRequests(): List<PendingMembershipRequest> =
        loadList(KrakenStorageKeys.Realms.PENDING_REQUESTS, pendingRequestSerializer)

    fun addRealmCreation(creation: DemoRealmCreation): RealmSnapshot {
        val realms = loadRealms() + creation.realm
        val certificates = loadMembershipCertificates() + creation.membershipCertificate
        saveList(KrakenStorageKeys.Realms.LIST, realmSerializer, realms)
        saveList(KrakenStorageKeys.Realms.MEMBERSHIP_CERTIFICATES, certificateSerializer, certificates)
        return snapshot()
    }

    fun addDemoRealm(creation: DemoRealmCreation): RealmSnapshot =
        addRealmCreation(creation)

    fun updateRealm(realm: Realm): RealmSnapshot {
        val realms = loadRealms().map { current ->
            if (current.realmId == realm.realmId) realm else current
        }
        saveList(KrakenStorageKeys.Realms.LIST, realmSerializer, realms)
        return snapshot()
    }

    fun deleteLocalRealmRecord(realmId: String): RealmSnapshot {
        saveList(
            KrakenStorageKeys.Realms.LIST,
            realmSerializer,
            loadRealms().filterNot { it.realmId == realmId },
        )
        saveList(
            KrakenStorageKeys.Realms.MEMBERSHIP_CERTIFICATES,
            certificateSerializer,
            loadMembershipCertificates().filterNot { it.realmId == realmId },
        )
        saveList(
            KrakenStorageKeys.Realms.INVITE_EDGES,
            inviteEdgeSerializer,
            loadInviteEdges().filterNot { it.realmId == realmId },
        )
        saveList(
            KrakenStorageKeys.Realms.PENDING_REQUESTS,
            pendingRequestSerializer,
            loadPendingRequests().filterNot { it.realmId == realmId },
        )
        return snapshot()
    }

    fun addPendingRequest(request: PendingMembershipRequest): RealmSnapshot {
        val requests = loadPendingRequests()
            .filterNot { it.requestId == request.requestId }
            .plus(request)
        saveList(KrakenStorageKeys.Realms.PENDING_REQUESTS, pendingRequestSerializer, requests)
        return snapshot()
    }

    fun updatePendingRequest(request: PendingMembershipRequest): RealmSnapshot {
        val requests = loadPendingRequests().map { current ->
            if (current.requestId == request.requestId) request else current
        }
        saveList(KrakenStorageKeys.Realms.PENDING_REQUESTS, pendingRequestSerializer, requests)
        return snapshot()
    }

    fun addMembershipCertificate(certificate: MembershipCertificate): RealmSnapshot {
        val certificates = loadMembershipCertificates()
            .filterNot { it.membershipId == certificate.membershipId }
            .plus(certificate)
        saveList(KrakenStorageKeys.Realms.MEMBERSHIP_CERTIFICATES, certificateSerializer, certificates)
        return snapshot()
    }

    fun applyApprovalOutcome(outcome: ApprovalOutcome): RealmSnapshot =
        saveSnapshot(RealmService.applyApprovalOutcome(snapshot(), outcome))

    fun updateMembershipCertificate(certificate: MembershipCertificate): RealmSnapshot {
        val certificates = loadMembershipCertificates().map { current ->
            if (current.membershipId == certificate.membershipId) certificate else current
        }
        saveList(KrakenStorageKeys.Realms.MEMBERSHIP_CERTIFICATES, certificateSerializer, certificates)
        return snapshot()
    }

    fun removeMembershipCertificate(realmId: String, membershipId: String): RealmSnapshot {
        val certificates = loadMembershipCertificates().filterNot { it.membershipId == membershipId }
        val realms = loadRealms().map { realm ->
            if (realm.realmId == realmId) {
                realm.copy(
                    capacityState = realm.capacityState.copy(
                        memberCount = certificates.count { it.realmId == realmId },
                        epoch = System.currentTimeMillis(),
                    )
                )
            } else {
                realm
            }
        }
        saveList(KrakenStorageKeys.Realms.MEMBERSHIP_CERTIFICATES, certificateSerializer, certificates)
        saveList(KrakenStorageKeys.Realms.LIST, realmSerializer, realms)
        return snapshot()
    }

    fun snapshot(): RealmSnapshot =
        RealmSnapshot(
            realms = loadRealms(),
            membershipCertificates = loadMembershipCertificates(),
            inviteEdges = loadInviteEdges(),
            pendingRequests = loadPendingRequests(),
        )

    fun saveSnapshot(snapshot: RealmSnapshot): RealmSnapshot {
        saveList(KrakenStorageKeys.Realms.LIST, realmSerializer, snapshot.realms)
        saveList(KrakenStorageKeys.Realms.MEMBERSHIP_CERTIFICATES, certificateSerializer, snapshot.membershipCertificates)
        saveList(KrakenStorageKeys.Realms.INVITE_EDGES, inviteEdgeSerializer, snapshot.inviteEdges)
        saveList(KrakenStorageKeys.Realms.PENDING_REQUESTS, pendingRequestSerializer, snapshot.pendingRequests)
        return snapshot()
    }

    private fun <T> loadList(key: String, serializer: kotlinx.serialization.KSerializer<List<T>>): List<T> {
        val encoded = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            InvitePayloadCodec.json.decodeFromString(serializer, encoded)
        }.getOrDefault(emptyList())
    }

    private fun <T> saveList(
        key: String,
        serializer: kotlinx.serialization.KSerializer<List<T>>,
        values: List<T>,
    ) {
        preferences.edit()
            .putInt(KrakenStorageKeys.KEY_SCHEMA_VERSION, KrakenStorageKeys.SCHEMA_VERSION)
            .putString(key, InvitePayloadCodec.json.encodeToString(serializer, values))
            .apply()
    }
}

data class RealmSnapshot(
    val realms: List<Realm>,
    val membershipCertificates: List<MembershipCertificate>,
    val inviteEdges: List<InviteEdge>,
    val pendingRequests: List<PendingMembershipRequest>,
)

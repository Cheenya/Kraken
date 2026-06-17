package com.disser.kraken.storage

object KrakenStorageKeys {
    const val SCHEMA_VERSION = 1
    const val KEY_SCHEMA_VERSION = "storage_schema_version"

    object Preferences {
        const val IDENTITY = "kraken_identity_store"
        const val ISSUED_INVITES = "kraken_issued_invite_store"
        const val PENDING_INVITES = "kraken_pending_invite_store"
        const val RELATIONSHIPS = "kraken_relationship_store"
        const val COMPLAINTS = "kraken_complaint_store"
        const val REALMS = "kraken_realm_store"
        const val RELAY_POLICY = "kraken_relay_policy_store"
        const val CHANNELS = "kraken_channel_store"
        const val SMALL_GROUPS = "kraken_small_group_store"
        const val APPEARANCE = "kraken_appearance_store"
        const val RESEARCH_ATTACK = "kraken_research_attack_store"
        const val MESSAGES = "kraken_message_store"
        const val PACKET_OUTBOX = "kraken_packet_outbox_store"
        const val PACKET_INBOX = "kraken_packet_inbox_store"
        const val PACKET_SEEN = "kraken_packet_seen_store"
        const val RECEIPTS = "kraken_receipt_store"
        const val MESH_RUNTIME = "kraken_mesh_runtime_store"
        const val RELATIONSHIP_NOTIFICATIONS = "kraken_relationship_notification_store"
        const val NOTIFICATION_INBOX = "kraken_notification_inbox_store"
        const val CHAT_PREFERENCES = "kraken_chat_preferences_store"
        const val CRYPTO_PROFILE_ADMISSIONS = "kraken_crypto_profile_admission_store"
    }

    object Identity {
        const val LOCAL_IDENTITY = "local_identity"
    }

    object PendingInvites {
        const val IMPORTS = "pending_invite_imports"
    }

    object IssuedInvites {
        const val RECORDS = "issued_invite_records"
    }

    object Relationships {
        const val LIST = "relationships"
    }

    object Complaints {
        const val LIST = "complaints"
    }

    object Realms {
        const val LIST = "realms"
        const val MEMBERSHIP_CERTIFICATES = "membership_certificates"
        const val INVITE_EDGES = "invite_edges"
        const val PENDING_REQUESTS = "pending_membership_requests"
    }

    object RelayPolicy {
        const val STATE = "relay_policy"
    }

    object Channels {
        const val LIST = "channels"
        const val MEMBERSHIPS = "memberships"
        const val MESSAGES = "messages"
    }

    object SmallGroups {
        const val LIST = "groups"
        const val MEMBERS = "members"
        const val MESSAGES = "messages"
    }

    object Appearance {
        const val UI_STYLE = "ui_style"
        const val QUIET_GRAPHITE_DEFAULT_APPLIED = "quiet_graphite_default_applied"
    }

    object ResearchAttack {
        const val LATEST_LOG = "latest_log"
    }

    object Messages {
        const val LIST = "messages"
        const val SAVED_LIST = "saved_messages"
    }

    object Packets {
        const val OUTBOX = "outbox_packets"
        const val INBOX = "inbox_packets"
        const val SEEN = "seen_packet_ids"
        const val RECEIPTS = "receipts"
    }

    object MeshRuntime {
        const val MESH_ENABLED = "mesh_enabled"
        const val WIFI_DIRECT_ENABLED = "wifi_direct_enabled"
        const val TRANSPORT_PROFILE = "transport_profile"
        const val LAST_SERVICE_STARTED_AT = "last_service_started_at_epoch_millis"
    }

    object RelationshipNotifications {
        const val MUTED_RELATIONSHIP_IDS = "muted_relationship_ids"
    }

    object NotificationInbox {
        const val MESSAGES = "notification_messages"
    }

    object ChatPreferences {
        const val QUICK_REACTION = "quick_reaction"
        const val GLOBAL_BACKGROUND = "global_background"
        const val RELATIONSHIP_BACKGROUND_PREFIX = "relationship_background_"
    }

    object CryptoProfileAdmissions {
        const val RESULTS = "crypto_profile_admission_results"
    }
}

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any, Callable


class RelayAttackMode(str, Enum):
    NORMAL = "normal"
    DROP = "drop"
    DUPLICATE = "duplicate"
    TAMPER = "tamper"


@dataclass(frozen=True, slots=True)
class LanPeer:
    fingerprint: str
    host: str
    port: int


@dataclass(frozen=True, slots=True)
class RelayDecision:
    accepted: bool
    reason: str
    forwarded: bool
    queued: bool
    dropped: bool


@dataclass(slots=True)
class RelayStats:
    accepted: int = 0
    forwarded: int = 0
    queued: int = 0
    dropped: int = 0
    duplicated: int = 0
    tampered: int = 0
    rejected: int = 0


class DesktopLanRelay:
    """Research-only LAN relay simulator used by the desktop preflight artifact."""

    def __init__(
        self,
        fingerprint: str,
        attack_mode: RelayAttackMode = RelayAttackMode.NORMAL,
        now_epoch_millis: Callable[[], int] | None = None,
        send_fn: Callable[[str, int, dict[str, Any]], bool] | None = None,
    ) -> None:
        self.fingerprint = fingerprint
        self.attack_mode = attack_mode
        self.now_epoch_millis = now_epoch_millis or (lambda: 0)
        self.send_fn = send_fn or (lambda _host, _port, _packet: True)
        self.peers: dict[str, LanPeer] = {}
        self.stats = RelayStats()

    def remember_peer(self, peer: LanPeer) -> None:
        self.peers[peer.fingerprint] = peer

    def handle_packet(self, packet: dict[str, Any], source_host: str) -> RelayDecision:
        del source_host
        recipient = str(packet.get("recipient_fingerprint", ""))
        peer = self.peers.get(recipient)
        if peer is None:
            self.stats.queued += 1
            return RelayDecision(True, "recipient-not-seen-yet", False, True, False)

        if int(packet.get("expires_at_epoch_millis", 0)) <= self.now_epoch_millis():
            self.stats.rejected += 1
            return RelayDecision(False, "packet-expired", False, False, True)

        if int(packet.get("ttl_hops", 0)) <= 0:
            self.stats.rejected += 1
            return RelayDecision(False, "ttl-exhausted", False, False, True)

        self.stats.accepted += 1
        if self.attack_mode == RelayAttackMode.DROP:
            self.stats.dropped += 1
            return RelayDecision(True, "attack-drop", False, False, True)

        forwarded = dict(packet)
        forwarded["ttl_hops"] = int(forwarded.get("ttl_hops", 0)) - 1
        if self.attack_mode == RelayAttackMode.TAMPER:
            forwarded["payload_json"] = "{\"tampered\":true}"
            self.stats.tampered += 1

        sent = self.send_fn(peer.host, peer.port, forwarded)
        if self.attack_mode == RelayAttackMode.DUPLICATE:
            self.send_fn(peer.host, peer.port, dict(forwarded))
            self.stats.duplicated += 1

        self.stats.forwarded += 1 if sent else 0
        return RelayDecision(True, f"attack-{self.attack_mode.value}", bool(sent), False, False)

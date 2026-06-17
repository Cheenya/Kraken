from __future__ import annotations

from dataclasses import dataclass
from itertools import combinations


@dataclass(frozen=True, slots=True)
class Peer:
    peer_id: str


@dataclass(frozen=True, slots=True)
class PairwiseSession:
    left: Peer
    right: Peer

    @property
    def session_id(self) -> str:
        names = sorted([self.left.peer_id, self.right.peer_id])
        return f"{names[0]}::{names[1]}"


class MeshKeyGraph:
    def __init__(self, peers: list[Peer]) -> None:
        if len({peer.peer_id for peer in peers}) != len(peers):
            raise ValueError("Peer identifiers must be unique")
        self.peers = peers
        self.sessions = [PairwiseSession(left, right) for left, right in combinations(peers, 2)]

    @property
    def peer_count(self) -> int:
        return len(self.peers)

    @property
    def pairwise_session_count(self) -> int:
        return len(self.sessions)

    @property
    def expected_pairwise_session_count(self) -> int:
        return self.peer_count * (self.peer_count - 1) // 2

    def metrics(self) -> dict[str, int]:
        return {
            "peer_count": self.peer_count,
            "session_count_created": self.pairwise_session_count,
            "expected_session_count": self.expected_pairwise_session_count,
            "total_key_ops": self.pairwise_session_count,
        }

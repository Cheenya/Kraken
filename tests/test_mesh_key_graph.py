import pytest

from disser_messenger.mesh import MeshKeyGraph, Peer


@pytest.mark.parametrize(
    ("peer_count", "expected_sessions"),
    [
        (0, 0),
        (1, 0),
        (2, 1),
        (3, 3),
        (4, 6),
        (10, 45),
    ],
)
def test_pairwise_session_count_formula(peer_count: int, expected_sessions: int) -> None:
    peers = [Peer(peer_id=f"peer_{index}") for index in range(peer_count)]
    graph = MeshKeyGraph(peers)
    assert graph.pairwise_session_count == expected_sessions
    assert graph.expected_pairwise_session_count == expected_sessions


def test_duplicate_peer_ids_are_rejected() -> None:
    with pytest.raises(ValueError):
        MeshKeyGraph([Peer("alice"), Peer("alice")])


def test_session_ids_are_stable_and_sorted() -> None:
    graph = MeshKeyGraph([Peer("bob"), Peer("alice")])
    assert graph.sessions[0].session_id == "alice::bob"

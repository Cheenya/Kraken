from __future__ import annotations

import socket
import threading
from collections.abc import Callable

from .codec import KrakenPacketPolicyValidator, LanFrameCodec
from .events import LanEventDirection, LanEventStatus, LanTransferEvent
from .models import LanEndpoint, LanFrameEnvelope, epoch_millis, utc_now


class WindowsLanTcpSender:
    def __init__(self, now: Callable[[], int] | None = None) -> None:
        self._now = now or (lambda: epoch_millis(utc_now()))

    def send(self, envelope: LanFrameEnvelope, endpoint: LanEndpoint, timeout_seconds: float = 8) -> LanTransferEvent:
        target = f"{endpoint.host}:{endpoint.port}"
        try:
            frame = LanFrameCodec.encode_envelope(envelope)
            with socket.create_connection((endpoint.host, endpoint.port), timeout=timeout_seconds) as sock:
                sock.settimeout(timeout_seconds)
                sock.sendall(frame)
                ack = sock.recv(1)
            status = LanEventStatus.ACKED if ack == bytes([LanFrameCodec.ack_byte]) else LanEventStatus.FAILED
            error = None if status is LanEventStatus.ACKED else "ack-missing"
        except OSError as exc:
            status = LanEventStatus.FAILED
            error = str(exc)
        except ValueError as exc:
            status = LanEventStatus.FAILED
            error = str(exc)

        return LanTransferEvent(
            direction=LanEventDirection.OUTBOUND,
            status=status,
            at_epoch_millis=self._now(),
            source=f"windows:{envelope.sender_reply_port}" if envelope.sender_reply_port else None,
            target=target,
            packet_id=envelope.packet.packet_id,
            message_id=envelope.packet.message_id,
            payload_json=envelope.packet.payload_json,
            sender_display_name=envelope.sender_display_name,
            sender_fingerprint=envelope.sender_fingerprint,
            recipient_fingerprint=envelope.packet.recipient_fingerprint,
            relationship_id=envelope.packet.relationship_id,
            error=error,
        )


class WindowsLanTcpListener:
    def __init__(self, now: Callable[[], int] | None = None) -> None:
        self._now = now or (lambda: epoch_millis(utc_now()))
        self._socket: socket.socket | None = None
        self._thread: threading.Thread | None = None
        self._stop = threading.Event()
        self._policy = KrakenPacketPolicyValidator()
        self.local_port: int | None = None

    def start(self, host: str, port: int, on_event: Callable[[LanTransferEvent], None]) -> int:
        self.stop()
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind((host, port))
        sock.listen()
        sock.settimeout(0.2)
        self._socket = sock
        self.local_port = int(sock.getsockname()[1])
        self._stop.clear()
        self._thread = threading.Thread(target=self._accept_loop, args=(host, on_event), daemon=True)
        self._thread.start()
        return self.local_port

    def stop(self) -> None:
        self._stop.set()
        if self._socket is not None:
            try:
                self._socket.close()
            except OSError:
                pass
        if self._thread is not None and self._thread.is_alive():
            self._thread.join(timeout=1)
        self._socket = None
        self._thread = None
        self.local_port = None

    def _accept_loop(self, host: str, on_event: Callable[[LanTransferEvent], None]) -> None:
        while not self._stop.is_set():
            try:
                assert self._socket is not None
                connection, address = self._socket.accept()
            except TimeoutError:
                continue
            except OSError:
                break
            with connection:
                event = self._handle_connection(connection, address, host)
                on_event(event)

    def _handle_connection(self, connection: socket.socket, address: tuple[str, int], host: str) -> LanTransferEvent:
        source = f"{address[0]}:{address[1]}"
        target = f"{host}:{self.local_port}" if self.local_port else None
        try:
            length_bytes = _recv_exact(connection, 4)
            length = int.from_bytes(length_bytes, "big")
            if length <= 0 or length > LanFrameCodec.max_frame_bytes:
                raise ValueError("invalid-frame-length")
            payload = _recv_exact(connection, length)
            envelope = LanFrameCodec.decode_envelope_payload(payload)
            self._policy.accept_inbound(envelope.packet, now_millis=self._now())
            connection.sendall(bytes([LanFrameCodec.ack_byte]))
            return LanTransferEvent(
                direction=LanEventDirection.INBOUND,
                status=LanEventStatus.ACCEPTED,
                at_epoch_millis=self._now(),
                source=source,
                target=target,
                packet_id=envelope.packet.packet_id,
                message_id=envelope.packet.message_id,
                payload_json=envelope.packet.payload_json,
                sender_display_name=envelope.sender_display_name,
                sender_fingerprint=envelope.sender_fingerprint,
                recipient_fingerprint=envelope.packet.recipient_fingerprint,
                relationship_id=envelope.packet.relationship_id,
            )
        except (OSError, ValueError) as exc:
            return LanTransferEvent(
                direction=LanEventDirection.INBOUND,
                status=LanEventStatus.FAILED,
                at_epoch_millis=self._now(),
                source=source,
                target=target,
                packet_id=None,
                message_id=None,
                error=str(exc),
            )


def _recv_exact(connection: socket.socket, byte_count: int) -> bytes:
    chunks: list[bytes] = []
    remaining = byte_count
    while remaining > 0:
        chunk = connection.recv(remaining)
        if not chunk:
            raise ValueError("truncated-frame")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)

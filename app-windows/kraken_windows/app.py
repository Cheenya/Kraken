from __future__ import annotations

import json
import sys
from datetime import UTC, datetime
from pathlib import Path

from .core import HandshakeQrCodec, KrakenDesktopSimulator, LanFrameCodec, LanFrameEnvelope, KrakenPacket
from .core.models import MessageDirection, PeerRouteKind, epoch_millis, state_to_jsonable


class MissingPySide6(RuntimeError):
    pass


def _load_qt():
    try:
        from PySide6.QtCore import Qt
        from PySide6.QtWidgets import (
            QApplication,
            QComboBox,
            QFormLayout,
            QGridLayout,
            QGroupBox,
            QHBoxLayout,
            QLabel,
            QLineEdit,
            QListWidget,
            QMainWindow,
            QMessageBox,
            QPushButton,
            QPlainTextEdit,
            QSplitter,
            QStackedWidget,
            QTableWidget,
            QTableWidgetItem,
            QVBoxLayout,
            QWidget,
        )
    except ModuleNotFoundError as exc:
        raise MissingPySide6(
            "PySide6 не установлен. Выполните: python -m pip install -r app-windows/requirements.txt"
        ) from exc

    return {
        "QApplication": QApplication,
        "QComboBox": QComboBox,
        "QFormLayout": QFormLayout,
        "QGridLayout": QGridLayout,
        "QGroupBox": QGroupBox,
        "QHBoxLayout": QHBoxLayout,
        "QLabel": QLabel,
        "QLineEdit": QLineEdit,
        "QListWidget": QListWidget,
        "QMainWindow": QMainWindow,
        "QMessageBox": QMessageBox,
        "QPlainTextEdit": QPlainTextEdit,
        "QPushButton": QPushButton,
        "QSplitter": QSplitter,
        "QStackedWidget": QStackedWidget,
        "QTableWidget": QTableWidget,
        "QTableWidgetItem": QTableWidgetItem,
        "QVBoxLayout": QVBoxLayout,
        "QWidget": QWidget,
        "Qt": Qt,
    }


def create_main_window(qt):
    QApplication = qt["QApplication"]
    QComboBox = qt["QComboBox"]
    QFormLayout = qt["QFormLayout"]
    QGridLayout = qt["QGridLayout"]
    QGroupBox = qt["QGroupBox"]
    QHBoxLayout = qt["QHBoxLayout"]
    QLabel = qt["QLabel"]
    QLineEdit = qt["QLineEdit"]
    QListWidget = qt["QListWidget"]
    QMainWindow = qt["QMainWindow"]
    QMessageBox = qt["QMessageBox"]
    QPlainTextEdit = qt["QPlainTextEdit"]
    QPushButton = qt["QPushButton"]
    QSplitter = qt["QSplitter"]
    QStackedWidget = qt["QStackedWidget"]
    QTableWidget = qt["QTableWidget"]
    QTableWidgetItem = qt["QTableWidgetItem"]
    QVBoxLayout = qt["QVBoxLayout"]
    QWidget = qt["QWidget"]
    Qt = qt["Qt"]

    class MainWindow(QMainWindow):
        def __init__(self) -> None:
            super().__init__()
            self.setWindowTitle("Kraken Desktop for Windows")
            self.resize(1120, 720)
            self.simulator = KrakenDesktopSimulator()
            self.state = self.simulator.make_initial_state()
            self.selected_relationship_id = "rel-xiaomi"

            self.sidebar = QListWidget()
            self.sidebar.addItems(["Обзор", "Чат", "Маршруты", "QR", "Настройки"])
            self.sidebar.setFixedWidth(180)

            self.pages = QStackedWidget()
            self.overview_page = self._build_overview_page()
            self.chat_page = self._build_chat_page()
            self.routes_page = self._build_routes_page()
            self.qr_page = self._build_qr_page()
            self.settings_page = self._build_settings_page()
            for page in (self.overview_page, self.chat_page, self.routes_page, self.qr_page, self.settings_page):
                self.pages.addWidget(page)

            splitter = QSplitter()
            splitter.addWidget(self.sidebar)
            splitter.addWidget(self.pages)
            splitter.setStretchFactor(1, 1)
            self.setCentralWidget(splitter)

            self.sidebar.currentRowChanged.connect(self.pages.setCurrentIndex)
            self.sidebar.setCurrentRow(0)
            self.refresh()

        def _build_overview_page(self):
            page = QWidget()
            layout = QVBoxLayout(page)

            self.event_label = QLabel()
            self.identity_label = QLabel()
            self.admission_label = QLabel()
            self.identity_name_input = QLineEdit()
            self.identity_name_input.setPlaceholderText("Kraken Windows")

            create_identity = QPushButton("Создать профиль")
            create_identity.clicked.connect(self.create_identity)
            accept_admission = QPushButton("Принять криптопрофиль")
            accept_admission.clicked.connect(lambda: self.evaluate_admission(True))
            reject_admission = QPushButton("Отклонить рискованный")
            reject_admission.clicked.connect(lambda: self.evaluate_admission(False))

            form = QFormLayout()
            form.addRow("Последнее событие", self.event_label)
            form.addRow("Профиль Kraken", self.identity_label)
            form.addRow("Допуск профиля", self.admission_label)
            form.addRow("Имя", self.identity_name_input)

            buttons = QHBoxLayout()
            buttons.addWidget(create_identity)
            buttons.addWidget(accept_admission)
            buttons.addWidget(reject_admission)

            scope = QLabel(
                "Windows-версия проверяет LAN/BLE/QR-совместимость и локальное состояние. "
                "Радиомаршруты Android проверяются отдельными телефонными прогонами."
            )
            scope.setWordWrap(True)

            layout.addLayout(form)
            layout.addLayout(buttons)
            layout.addWidget(scope)
            layout.addStretch()
            return page

        def _build_chat_page(self):
            page = QWidget()
            layout = QVBoxLayout(page)

            self.relationship_combo = QComboBox()
            self.relationship_combo.currentIndexChanged.connect(self.on_relationship_changed)
            self.message_list = QPlainTextEdit()
            self.message_list.setReadOnly(True)
            self.message_input = QLineEdit()
            self.message_input.setPlaceholderText("Сообщение")

            send_button = QPushButton("Отправить")
            send_button.clicked.connect(self.send_message)
            ack_button = QPushButton("ACK доставки")
            ack_button.clicked.connect(self.confirm_delivery)
            activate_button = QPushButton("Активировать связь")
            activate_button.clicked.connect(self.activate_selected)

            self.import_peer_input = QLineEdit()
            self.import_peer_input.setPlaceholderText("Имя устройства")
            import_button = QPushButton("Добавить устройство")
            import_button.clicked.connect(self.import_peer)

            row = QHBoxLayout()
            row.addWidget(send_button)
            row.addWidget(ack_button)
            row.addWidget(activate_button)

            import_row = QHBoxLayout()
            import_row.addWidget(self.import_peer_input)
            import_row.addWidget(import_button)

            layout.addWidget(self.relationship_combo)
            layout.addWidget(self.message_list, 1)
            layout.addWidget(self.message_input)
            layout.addLayout(row)
            layout.addLayout(import_row)
            return page

        def _build_routes_page(self):
            page = QWidget()
            layout = QVBoxLayout(page)

            self.routes_table = QTableWidget(0, 6)
            self.routes_table.setHorizontalHeaderLabels(["Устройство", "Отпечаток", "Маршрут", "Канал", "Скорость", "Переходы"])

            cycle_button = QPushButton("Сменить маршрут")
            cycle_button.clicked.connect(self.cycle_route)
            preview_button = QPushButton("Показать LAN-кадр")
            preview_button.clicked.connect(self.preview_lan_frame)
            evidence_button = QPushButton("Сохранить evidence")
            evidence_button.clicked.connect(self.save_evidence)
            self.lan_preview = QPlainTextEdit()
            self.lan_preview.setReadOnly(True)

            buttons = QHBoxLayout()
            buttons.addWidget(cycle_button)
            buttons.addWidget(preview_button)
            buttons.addWidget(evidence_button)

            layout.addWidget(self.routes_table, 1)
            layout.addLayout(buttons)
            layout.addWidget(self.lan_preview, 1)
            return page

        def _build_qr_page(self):
            page = QWidget()
            layout = QVBoxLayout(page)

            self.qr_input = QPlainTextEdit()
            self.qr_input.setPlaceholderText("Вставьте JSON, kraken://qr, intent://qr или ссылку https://kraken.local/qr")
            self.qr_output = QPlainTextEdit()
            self.qr_output.setReadOnly(True)

            normalize_button = QPushButton("Распознать QR")
            normalize_button.clicked.connect(self.normalize_qr)
            encode_button = QPushButton("Собрать ссылку QR")
            encode_button.clicked.connect(self.encode_qr)

            buttons = QHBoxLayout()
            buttons.addWidget(normalize_button)
            buttons.addWidget(encode_button)

            layout.addWidget(QLabel("Данные QR"))
            layout.addWidget(self.qr_input, 1)
            layout.addLayout(buttons)
            layout.addWidget(QLabel("Результат"))
            layout.addWidget(self.qr_output, 1)
            return page

        def _build_settings_page(self):
            page = QWidget()
            layout = QVBoxLayout(page)

            box = QGroupBox("Проверки")
            grid = QGridLayout(box)
            grid.addWidget(QLabel("Smoke ядра"), 0, 0)
            grid.addWidget(QLabel("python -m unittest discover -s tests"), 0, 1)
            grid.addWidget(QLabel("Компиляция"), 1, 0)
            grid.addWidget(QLabel("python -m compileall kraken_windows tests"), 1, 1)
            grid.addWidget(QLabel("Запуск Windows"), 2, 0)
            grid.addWidget(QLabel("run_windows.bat"), 2, 1)

            note = QLabel(
                "BLE и LAN-кодеки сверяют совместимость с Android/macOS-пакетами. "
                "Нативный Windows Bluetooth/Wi-Fi Direct здесь не реализуется."
            )
            note.setWordWrap(True)
            layout.addWidget(box)
            layout.addWidget(note)
            layout.addStretch()
            return page

        def refresh(self) -> None:
            self.event_label.setText(self.state.last_event)
            if self.state.local_identity is None:
                self.identity_label.setText("не создана")
            else:
                identity = self.state.local_identity
                self.identity_label.setText(f"{identity.display_name} · {self._compact_fingerprint(identity.fingerprint)}")

            admission = self.state.admission_result
            self.admission_label.setText(self._admission_summary(admission))

            current = self.selected_relationship_id
            self.relationship_combo.blockSignals(True)
            self.relationship_combo.clear()
            for relationship in self.state.relationships:
                self.relationship_combo.addItem(
                    f"{relationship.peer_display_name} · {relationship.state.title}",
                    relationship.relationship_id,
                )
            selected_index = max(0, next((idx for idx, r in enumerate(self.state.relationships) if r.relationship_id == current), 0))
            self.relationship_combo.setCurrentIndex(selected_index)
            self.selected_relationship_id = self.relationship_combo.currentData()
            self.relationship_combo.blockSignals(False)

            self._refresh_messages()
            self._refresh_routes()

        def _refresh_messages(self) -> None:
            relationship_id = self.selected_relationship_id
            lines = []
            for message in self.state.messages:
                if message.relationship_id != relationship_id:
                    continue
                arrow = "<-" if message.direction is MessageDirection.INCOMING else "->"
                lines.append(f"{arrow} {message.status.title}: {message.body}")
            self.message_list.setPlainText("\n".join(lines))

        def _refresh_routes(self) -> None:
            self.routes_table.setRowCount(len(self.state.routes))
            for row, route in enumerate(self.state.routes):
                relationship = next(
                    (item for item in self.state.relationships if item.relationship_id == route.relationship_id),
                    None,
                )
                values = [
                    relationship.peer_display_name if relationship else route.relationship_id,
                    route.peer_fingerprint,
                    route.kind.title,
                    self._transport_title(route.transport_id),
                    self._bandwidth_title(route.bandwidth_class.value),
                    "" if route.hop_count is None else str(route.hop_count),
                ]
                for column, value in enumerate(values):
                    self.routes_table.setItem(row, column, QTableWidgetItem(value))
            self.routes_table.resizeColumnsToContents()

        def on_relationship_changed(self) -> None:
            self.selected_relationship_id = self.relationship_combo.currentData()
            self._refresh_messages()

        def create_identity(self) -> None:
            self.state = self.simulator.create_identity(self.state, self.identity_name_input.text())
            self.refresh()

        def evaluate_admission(self, experimental: bool) -> None:
            self.state = self.simulator.evaluate_admission(self.state, experimental)
            self.refresh()

        def import_peer(self) -> None:
            self.state = self.simulator.import_peer(self.state, self.import_peer_input.text())
            self.import_peer_input.clear()
            self.selected_relationship_id = self.state.relationships[0].relationship_id
            self.refresh()

        def activate_selected(self) -> None:
            self.state = self.simulator.activate_relationship(self.state, self.selected_relationship_id)
            self.refresh()

        def send_message(self) -> None:
            self.state = self.simulator.send_message(self.state, self.selected_relationship_id, self.message_input.text())
            self.message_input.clear()
            self.refresh()

        def confirm_delivery(self) -> None:
            self.state = self.simulator.confirm_latest_delivery(self.state, self.selected_relationship_id)
            self.refresh()

        def cycle_route(self) -> None:
            self.state = self.simulator.cycle_route(self.state, self.selected_relationship_id)
            self.refresh()

        def preview_lan_frame(self) -> None:
            relationship = next(
                item for item in self.state.relationships if item.relationship_id == self.selected_relationship_id
            )
            now = datetime.now(tz=UTC)
            now_ms = epoch_millis(now)
            packet = KrakenPacket(
                packet_id="packet-windows-preview",
                sender_fingerprint="WINDOWSDESKTOP001",
                recipient_fingerprint=relationship.peer_fingerprint,
                relationship_id=relationship.relationship_id,
                conversation_id=f"desktop-{relationship.relationship_id}",
                message_id="message-windows-preview",
                created_at_epoch_millis=now_ms,
                expires_at_epoch_millis=now_ms + 300_000,
                payload_json=json.dumps({"message_id": "message-windows-preview", "body": "hello from Windows"}, ensure_ascii=False),
            )
            envelope = LanFrameEnvelope(
                sender_peer_id="windows-desktop",
                sender_fingerprint="WINDOWSDESKTOP001",
                sender_display_name="Windows Kraken",
                sender_reply_port=43191,
                packet=packet,
            )
            frame = LanFrameCodec.encode_envelope(envelope)
            decoded = LanFrameCodec.decode_frame(frame)
            self.lan_preview.setPlainText(
                f"Размер кадра: {len(frame)} байт\n"
                f"Префикс длины: {int.from_bytes(frame[:4], 'big')}\n"
                f"Отправитель: {decoded.sender_peer_id}\n"
                f"Сообщение: {decoded.packet.message_id}\n"
                f"Данные: {decoded.packet.payload_json}"
            )

        def save_evidence(self) -> None:
            root = Path(__file__).resolve().parents[1]
            out_dir = root / "output" / "windows-evidence" / datetime.now(tz=UTC).strftime("%Y%m%d-%H%M%S")
            out_dir.mkdir(parents=True, exist_ok=True)
            (out_dir / "state.json").write_text(
                json.dumps(state_to_jsonable(self.state), ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            QMessageBox.information(self, "Evidence сохранено", str(out_dir))

        def normalize_qr(self) -> None:
            raw = self.qr_input.toPlainText()
            normalized = HandshakeQrCodec.normalized_scanned_payload(raw)
            kind = HandshakeQrCodec.detect_kind(raw)
            summary = HandshakeQrCodec.payload_summary(raw)
            self.qr_output.setPlainText(f"Тип: {kind}\n{summary}\n\n{normalized}")

        def encode_qr(self) -> None:
            try:
                encoded = HandshakeQrCodec.encoded_qr_payload(self.qr_input.toPlainText())
            except ValueError as exc:
                self.qr_output.setPlainText(f"Не удалось собрать ссылку: {exc}")
                return
            self.qr_output.setPlainText(encoded)

        @staticmethod
        def _compact_fingerprint(value: str) -> str:
            if len(value) <= 10:
                return value
            return f"{value[:6]}...{value[-4:]}"

        @staticmethod
        def _transport_title(value: str | None) -> str:
            if not value:
                return ""
            known = {
                "windows-lan": "LAN Windows",
                "windows-ble": "BLE Windows",
                "macos-lan-adb-bridge": "LAN/ADB",
            }
            return known.get(value, value)

        @staticmethod
        def _bandwidth_title(value: str) -> str:
            return {
                "NONE": "нет",
                "LOW": "низкая",
                "MEDIUM": "средняя",
                "HIGH": "высокая",
            }.get(value, value)

        @staticmethod
        def _risk_flag_title(value: str) -> str:
            return {
                "rational_2_torsion": "точки кручения порядка 2",
                "rational_3_torsion": "точки кручения порядка 3",
            }.get(value, value.replace("_", " "))

        def _admission_summary(self, admission) -> str:
            flags = ", ".join(self._risk_flag_title(flag) for flag in admission.risk_flags)
            if flags:
                return f"{admission.decision.title}; риск: {flags}"
            return admission.decision.title

    return MainWindow


def main() -> int:
    if "--smoke" in sys.argv:
        return run_smoke()
    try:
        qt = _load_qt()
    except MissingPySide6 as exc:
        print(exc)
        return 2
    QApplication = qt["QApplication"]
    MainWindow = create_main_window(qt)
    app = QApplication([])
    window = MainWindow()
    window.show()
    return app.exec()


def run_smoke() -> int:
    simulator = KrakenDesktopSimulator()
    state = simulator.make_initial_state()
    state = simulator.send_message(state, "rel-xiaomi", "hello windows")
    latest = state.messages[-1]
    if latest.status.value != "SENT_TO_TRANSPORT":
        print("smoke failed: active peer did not enter SENT_TO_TRANSPORT")
        return 1

    now = datetime.now(tz=UTC)
    now_ms = epoch_millis(now)
    packet = KrakenPacket(
        packet_id="packet-windows-smoke",
        sender_fingerprint="WINDOWSDESKTOP001",
        recipient_fingerprint="A17C9E2048F0DA11",
        relationship_id="rel-xiaomi",
        conversation_id="desktop-rel-xiaomi",
        message_id="message-windows-smoke",
        created_at_epoch_millis=now_ms,
        expires_at_epoch_millis=now_ms + 300_000,
        payload_json=json.dumps({"message_id": "message-windows-smoke", "body": "hello"}, ensure_ascii=False),
    )
    envelope = LanFrameEnvelope(
        sender_peer_id="windows-desktop",
        sender_fingerprint="WINDOWSDESKTOP001",
        sender_display_name="Windows Kraken",
        sender_reply_port=43191,
        packet=packet,
    )
    decoded = LanFrameCodec.decode_frame(LanFrameCodec.encode_envelope(envelope))
    if decoded != envelope:
        print("smoke failed: LAN frame did not round-trip")
        return 1

    print("Smoke-проверка Kraken Windows: OK")
    return 0

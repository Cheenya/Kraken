from __future__ import annotations

import json
from pathlib import Path

from .models import KrakenDesktopState, state_from_dict, state_to_jsonable


class JsonStateStore:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.state_path = root / "state.json"

    def load_state(self) -> KrakenDesktopState | None:
        if not self.state_path.exists():
            return None
        return state_from_dict(json.loads(self.state_path.read_text(encoding="utf-8")))

    def save_state(self, state: KrakenDesktopState) -> None:
        self.root.mkdir(parents=True, exist_ok=True)
        self.state_path.write_text(
            json.dumps(state_to_jsonable(state), ensure_ascii=False, indent=2, sort_keys=True),
            encoding="utf-8",
        )

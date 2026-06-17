import json
import re
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
PROTOCOL_SPEC_DIR = ROOT / "protocol-spec"
SCHEMA_DIR = PROTOCOL_SPEC_DIR / "schemas"
README = PROTOCOL_SPEC_DIR / "README.md"

REQUIRED_SCHEMA_SECTIONS = {
    "purpose",
    "scope",
    "fields",
    "validation rules",
    "privacy and security notes",
    "json example",
}

FORBIDDEN_JSON_KEYS = {
    "phone",
    "phone_number",
    "email",
    "login",
    "username",
    "password",
    "imei",
    "android_id",
    "device_id",
    "mac",
    "mac_address",
    "serial",
    "serial_number",
    "hardware_fingerprint",
    "public_discovery",
    "discovery",
    "nearby_discovery",
    "global_search",
}

CORE_POLICY_PHRASES = {
    "no public discovery": ("no public discovery",),
    "invite-only": ("invite-only", "invite only"),
    "one-time QR": ("one-time qr", "one time qr"),
    "mandatory handshake": ("mandatory handshake",),
    "membership certificate": ("membership certificate",),
    "new key means new user": ("new key means new user", "new identity key means a new user"),
    "bilateral unlink": ("bilateral unlink", "unlink is bilateral"),
    "device identifiers must not be used as key material": (
        "device identifiers must not be used as key material",
        "device identifiers must never be key material",
    ),
    "tombstone deletion is best-effort": (
        "tombstone deletion is best-effort",
        "deletion is best-effort",
    ),
    "direct messages are primary": ("direct messages are primary",),
}


def find_markdown_files() -> list[Path]:
    return [README, *sorted(SCHEMA_DIR.glob("*.md"))]


def extract_fenced_json_blocks(text: str) -> list[str]:
    return re.findall(r"```json\s*\n(.*?)\n```", text, flags=re.DOTALL | re.IGNORECASE)


def walk_json_keys(value: Any) -> Iterable[str]:
    if isinstance(value, dict):
        for key, child in value.items():
            yield str(key)
            yield from walk_json_keys(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk_json_keys(child)


def normalize_heading_text(text: str) -> str:
    normalized = text.strip().lower()
    normalized = re.sub(r"^\s*#{2,3}\s+", "", normalized)
    normalized = re.sub(r"\s+", " ", normalized)
    return normalized


def schema_files() -> list[Path]:
    return sorted(SCHEMA_DIR.glob("*.md"))


def test_schema_docs_have_required_sections() -> None:
    for path in schema_files():
        headings = {
            normalize_heading_text(line)
            for line in path.read_text(encoding="utf-8").splitlines()
            if re.match(r"^\s*#{2,3}\s+", line)
        }
        missing = REQUIRED_SCHEMA_SECTIONS - headings
        assert not missing, f"{path.relative_to(ROOT)} missing sections: {sorted(missing)}"


def test_fenced_json_blocks_parse() -> None:
    parsed_blocks = 0
    for path in find_markdown_files():
        for block in extract_fenced_json_blocks(path.read_text(encoding="utf-8")):
            parsed_blocks += 1
            try:
                json.loads(block)
            except json.JSONDecodeError as exc:
                raise AssertionError(
                    f"{path.relative_to(ROOT)} has invalid JSON block: {exc}"
                ) from exc
    assert parsed_blocks > 0, "Expected at least one fenced json block in protocol spec docs"


def test_json_examples_do_not_use_forbidden_fields() -> None:
    violations: list[str] = []
    for path in find_markdown_files():
        for block_index, block in enumerate(
            extract_fenced_json_blocks(path.read_text(encoding="utf-8")), start=1
        ):
            payload = json.loads(block)
            for key in walk_json_keys(payload):
                if key.lower() in FORBIDDEN_JSON_KEYS:
                    violations.append(f"{path.relative_to(ROOT)} block {block_index}: {key}")

    assert not violations, "Forbidden JSON keys found:\n" + "\n".join(violations)


def test_readme_links_to_every_schema_file() -> None:
    readme_text = README.read_text(encoding="utf-8")
    missing_links = [
        path.name for path in schema_files() if f"schemas/{path.name}" not in readme_text
    ]
    assert not missing_links, f"README missing schema links: {missing_links}"


def test_readme_mentions_core_policy_phrases() -> None:
    readme_text = README.read_text(encoding="utf-8").lower()
    missing = [
        concept
        for concept, variants in CORE_POLICY_PHRASES.items()
        if not any(variant in readme_text for variant in variants)
    ]
    assert not missing, f"README missing core policy concepts: {missing}"

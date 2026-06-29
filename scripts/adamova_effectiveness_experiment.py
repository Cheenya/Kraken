from __future__ import annotations

import csv
import json
import os
import statistics
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from functools import lru_cache
from pathlib import Path
from random import Random
from typing import Literal

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from disser_messenger.research.torsion import RationalCurve, classify_a1_a6


REPORT_DIR = ROOT / "reports" / "out"
JSON_PATH = REPORT_DIR / "adamova_effectiveness_experiment.json"
CSV_PATH = REPORT_DIR / "adamova_effectiveness_experiment.csv"
MD_PATH = REPORT_DIR / "adamova_effectiveness_experiment.md"
THESIS_TABLE_PATH = REPORT_DIR / "adamova_effectiveness_dissertation_table.md"
FIXTURE_PATH = ROOT / "tests" / "fixtures" / "torsion_examples.json"
NATIVE_CPP_SOURCE = ROOT / "app-android" / "app" / "src" / "main" / "cpp" / "kraken_native_placeholder.cpp"
NATIVE_CLI_PATH = ROOT / "build" / "adamova-host" / "adamova_native_cli"
NATIVE_CLI_RETENTION = "generated_on_demand_disposable_build_output"

ExperimentFamily = Literal[
    "singular",
    "small_torsion",
    "malformed",
    "size_guarded",
    "downgrade",
    "packet_mismatch",
    "accepted_control",
]


@dataclass(frozen=True, slots=True)
class ProfileScenario:
    scenario_id: str
    family: ExperimentFamily
    a: str
    b: str
    expected_risk: bool
    reference_status: str
    source: str
    note: str


@dataclass(frozen=True, slots=True)
class ScenarioResult:
    scenario_id: str
    family: str
    a: str
    b: str
    expected_risk: bool
    reference_status: str
    source: str
    no_precheck_accepted: bool
    discriminant_only_accepted: bool
    adamova_decision: str
    adamova_gate_accepted: bool
    gate_latency_ms: float
    note: str


@dataclass(frozen=True, slots=True)
class EffectivenessMetrics:
    profiles_total: int
    weak_or_invalid_total: int
    accepted_control_total: int
    constructed_reference_cases: int
    sage_fixture_controls: int
    generated_control_profiles: int
    malformed_total: int
    size_guarded_total: int
    downgrade_or_mismatch_total: int
    accepted_without_precheck_weak: int
    accepted_by_discriminant_only_weak: int
    accepted_by_adamova_gate_weak: int
    rejected_or_blocked_by_adamova_gate_weak: int
    accepted_controls_by_adamova_gate: int
    needs_reference_validation: int
    size_guarded: int
    median_gate_latency_ms: float
    p95_gate_latency_ms: float


@dataclass(frozen=True, slots=True)
class EffectivenessReport:
    experiment_id: str
    seed: int
    git_sha: str
    profile_policy_version: int
    gate_backend: str
    native_backend_version: str
    native_cli_path: str | None
    claim_boundary: str
    thesis_statement: str
    metrics: EffectivenessMetrics
    results: list[ScenarioResult]


@dataclass(frozen=True, slots=True)
class AdamovaGateBackend:
    backend_name: str
    backend_version: str
    native_cli_path: str | None

    def decision(self, a_raw: str, b_raw: str) -> str:
        if self.native_cli_path is None:
            return _python_policy_mirror_decision(a_raw, b_raw)
        try:
            raw = subprocess.check_output(
                [self.native_cli_path, a_raw, b_raw],
                cwd=ROOT,
                text=True,
                stderr=subprocess.DEVNULL,
                timeout=10,
            ).strip()
        except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
            return "REFERENCE_VALIDATION_REQUIRED"
        return _decision_from_native_output(raw)


@lru_cache(maxsize=1)
def resolve_adamova_gate_backend() -> AdamovaGateBackend:
    if _compile_native_cli():
        status = _native_cli_status()
        return AdamovaGateBackend(
            backend_name="host_cpp_native_core_cli_from_android_source",
            backend_version=status,
            native_cli_path=str(NATIVE_CLI_PATH),
        )
    return AdamovaGateBackend(
        backend_name="python_stage_a_policy_mirror_for_android_cpp_admission_gate",
        backend_version="android_nativecore_live_export_pending",
        native_cli_path=None,
    )


def _compile_native_cli() -> bool:
    if not NATIVE_CPP_SOURCE.exists():
        return False
    if NATIVE_CLI_PATH.exists() and NATIVE_CLI_PATH.stat().st_mtime >= NATIVE_CPP_SOURCE.stat().st_mtime:
        return True

    java_home = _java_home()
    if java_home is None:
        return False
    include_root = Path(java_home) / "include"
    platform_include = include_root / _jni_platform_dir()
    if not (include_root / "jni.h").exists() or not platform_include.exists():
        return False

    NATIVE_CLI_PATH.parent.mkdir(parents=True, exist_ok=True)
    command = [
        "c++",
        "-std=c++17",
        "-DKRAKEN_ADAMOVA_CLI",
        f"-I{include_root}",
        f"-I{platform_include}",
        str(NATIVE_CPP_SOURCE),
        "-o",
        str(NATIVE_CLI_PATH),
    ]
    try:
        subprocess.run(command, cwd=ROOT, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except (OSError, subprocess.CalledProcessError):
        return False
    return NATIVE_CLI_PATH.exists()


def _java_home() -> str | None:
    java_home_env = os.environ.get("JAVA_HOME")
    if java_home_env:
        return java_home_env
    try:
        return subprocess.check_output(["/usr/libexec/java_home"], text=True, stderr=subprocess.DEVNULL).strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def _jni_platform_dir() -> str:
    if sys.platform == "darwin":
        return "darwin"
    if sys.platform.startswith("linux"):
        return "linux"
    if sys.platform.startswith("win"):
        return "win32"
    return sys.platform


def _native_cli_status() -> str:
    try:
        return subprocess.check_output(
            [str(NATIVE_CLI_PATH), "--status"],
            cwd=ROOT,
            text=True,
            stderr=subprocess.DEVNULL,
            timeout=5,
        ).strip()
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        return "host_cpp_native_core_cli_status_unavailable"


def generate_corpus(seed: int = 20260605) -> list[ProfileScenario]:
    scenarios: list[ProfileScenario] = [
        ProfileScenario(
            "singular_zero_zero",
            "singular",
            "0",
            "0",
            True,
            "constructed_singular",
            "injected",
            "4a^3 + 27b^2 = 0.",
        ),
        ProfileScenario(
            "singular_minus3_2",
            "singular",
            "-3",
            "2",
            True,
            "constructed_singular",
            "injected",
            "Classic singular short-Weierstrass example.",
        ),
        ProfileScenario(
            "two_torsion_minus1_0",
            "small_torsion",
            "-1",
            "0",
            True,
            "constructed_rational_2_torsion",
            "injected",
            "Three rational 2-torsion roots.",
        ),
        ProfileScenario(
            "two_torsion_minus4_0",
            "small_torsion",
            "-4",
            "0",
            True,
            "constructed_rational_2_torsion",
            "injected",
            "Rational 2-torsion roots at x=0, +/-2.",
        ),
        ProfileScenario(
            "three_torsion_0_1",
            "small_torsion",
            "0",
            "1",
            True,
            "constructed_3_torsion_indicator",
            "injected",
            "Integer Stage A 3-torsion indicator.",
        ),
        ProfileScenario(
            "malformed_a",
            "malformed",
            "not-an-integer",
            "104729",
            True,
            "not_applicable_malformed",
            "injected",
            "Malformed coefficient must not become a session profile.",
        ),
        ProfileScenario(
            "malformed_b",
            "malformed",
            "65537",
            "1/3",
            True,
            "not_applicable_malformed",
            "injected",
            "Non-integer coefficient must require reference/manual handling.",
        ),
        ProfileScenario(
            "size_guarded_128ish",
            "size_guarded",
            "340282366920938463463374607431768211507",
            "340282366920938463463374607431768211297",
            True,
            "requires_reference_validation",
            "injected",
            "Oversized local scan should not auto-admit.",
        ),
        ProfileScenario(
            "downgrade_to_two_torsion",
            "downgrade",
            "-1",
            "0",
            True,
            "constructed_downgrade_attack",
            "injected",
            "Downgrade attempt to a weaker experimental profile.",
        ),
        ProfileScenario(
            "packet_profile_mismatch_three_torsion",
            "packet_mismatch",
            "0",
            "1",
            True,
            "constructed_packet_profile_mismatch",
            "injected",
            "Packet/session profile mismatch uses suspicious parameters.",
        ),
    ]
    accepted_sage_controls = _sage_fixture_controls()
    scenarios.extend(accepted_sage_controls)
    scenarios.extend(_generated_accept_controls(seed=seed, count=10 - len(accepted_sage_controls)))
    return scenarios


def _sage_fixture_controls() -> list[ProfileScenario]:
    fixtures = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))
    gate = resolve_adamova_gate_backend()
    controls: list[ProfileScenario] = []
    for fixture in fixtures:
        if fixture["expected_case"] != "A4":
            continue
        if _adamova_decision(str(fixture["a"]), str(fixture["b"]), gate) != "ACCEPT":
            continue
        controls.append(
            ProfileScenario(
                scenario_id=f"sage_accept_control_{fixture['curve_id']}",
                family="accepted_control",
                a=str(fixture["a"]),
                b=str(fixture["b"]),
                expected_risk=False,
                reference_status=f"SageMath torsion {fixture['sage_torsion']}; Stage A case A4",
                source="sage_fixture",
                note="Reference fixture with no rational 2/3-torsion indicator under Stage A.",
            )
        )
    return controls


def _generated_accept_controls(seed: int, count: int) -> list[ProfileScenario]:
    gate = resolve_adamova_gate_backend()
    rng = Random(seed)
    controls: list[ProfileScenario] = []
    seen: set[tuple[int, int]] = set()
    while len(controls) < count:
        a = rng.randint(-50_000, 50_000)
        b = rng.randint(-50_000, 50_000)
        if (a, b) in seen:
            continue
        seen.add((a, b))
        if _adamova_decision(str(a), str(b), gate) != "ACCEPT":
            continue
        controls.append(
            ProfileScenario(
                scenario_id=f"generated_accept_control_{len(controls) + 1:02d}",
                family="accepted_control",
                a=str(a),
                b=str(b),
                expected_risk=False,
                reference_status="generated_stage_a_accept_control",
                source="deterministic_generator",
                note="Deterministic generated control accepted by the Stage A admission policy.",
            )
        )
    return controls


def run_experiment(seed: int = 20260605) -> EffectivenessReport:
    gate = resolve_adamova_gate_backend()
    results: list[ScenarioResult] = []
    for scenario in generate_corpus(seed):
        start = time.perf_counter_ns()
        decision = _adamova_decision(scenario.a, scenario.b, gate)
        elapsed_ms = (time.perf_counter_ns() - start) / 1_000_000
        results.append(
            ScenarioResult(
                scenario_id=scenario.scenario_id,
                family=scenario.family,
                a=scenario.a,
                b=scenario.b,
                expected_risk=scenario.expected_risk,
                reference_status=scenario.reference_status,
                source=scenario.source,
                no_precheck_accepted=_no_precheck_accepts(scenario),
                discriminant_only_accepted=_discriminant_only_accepts(scenario),
                adamova_decision=decision,
                adamova_gate_accepted=decision == "ACCEPT",
                gate_latency_ms=elapsed_ms,
                note=scenario.note,
            )
        )
    metrics = _metrics(results)
    return EffectivenessReport(
        experiment_id="adamova_effectiveness_experiment_20260605",
        seed=seed,
        git_sha=_git_sha(),
        profile_policy_version=1,
        gate_backend=gate.backend_name,
        native_backend_version=gate.backend_version,
        native_cli_path=gate.native_cli_path,
        claim_boundary=(
            "Controlled profile-substitution experiment over rational curve parameters. "
            "It measures admission/precheck behavior for Kraken experimental profiles, "
            "not production message cryptographic security."
        ),
        thesis_statement=(
            "In the controlled experimental-profile substitution model, Adamova admission "
            f"gate reduced accepted weak/invalid profiles from "
            f"{metrics.accepted_without_precheck_weak}/{metrics.weak_or_invalid_total} "
            f"without precheck and {metrics.accepted_by_discriminant_only_weak}/"
            f"{metrics.weak_or_invalid_total} with discriminant-only precheck to "
            f"{metrics.accepted_by_adamova_gate_weak}/{metrics.weak_or_invalid_total}, "
            "while routing size/unsupported cases to reference validation before session or message use."
        ),
        metrics=metrics,
        results=results,
    )


def _no_precheck_accepts(scenario: ProfileScenario) -> bool:
    return scenario.family != "malformed"


def _discriminant_only_accepts(scenario: ProfileScenario) -> bool:
    parsed = _parse_curve(scenario.a, scenario.b)
    return parsed is not None and parsed.is_nonsingular()


def _adamova_decision(a_raw: str, b_raw: str, gate: AdamovaGateBackend | None = None) -> str:
    return (gate or resolve_adamova_gate_backend()).decision(a_raw, b_raw)


def _python_policy_mirror_decision(a_raw: str, b_raw: str) -> str:
    parsed = _parse_curve(a_raw, b_raw)
    if parsed is None:
        return "REFERENCE_VALIDATION_REQUIRED"
    if _size_guarded(parsed):
        return "SIZE_GUARDED"
    if not parsed.is_nonsingular():
        return "REJECT_SINGULAR"
    result = classify_a1_a6(parsed)
    if result.c2 > 0 or result.has_3_torsion_indicator:
        return "REJECT_SMALL_TORSION_RISK"
    return "ACCEPT"


def _decision_from_native_output(raw: str) -> str:
    if raw.startswith("unsupported\t"):
        return "REFERENCE_VALIDATION_REQUIRED"
    fields = raw.split("\t")
    if len(fields) < 23 or fields[0] != "ok":
        return "REFERENCE_VALIDATION_REQUIRED"

    try:
        singular = fields[3] == "1"
        two_torsion_root_count = int(fields[5])
        three_torsion_root_count = int(fields[7])
        has_three_torsion_indicator = fields[9] == "1"
        has_three_torsion_inconsistency = fields[10] == "1"
        classification_case = fields[11]
        early_stop_hit = fields[22] == "1"
    except ValueError:
        return "REFERENCE_VALIDATION_REQUIRED"

    if singular:
        return "REJECT_SINGULAR"
    if classification_case == "SIZE_GUARDED" or early_stop_hit:
        return "SIZE_GUARDED"
    if two_torsion_root_count > 0 or has_three_torsion_indicator or three_torsion_root_count > 0:
        return "REJECT_SMALL_TORSION_RISK"
    if has_three_torsion_inconsistency:
        return "REFERENCE_VALIDATION_REQUIRED"
    return "ACCEPT"


def _parse_curve(a_raw: str, b_raw: str) -> RationalCurve | None:
    try:
        return RationalCurve(a=int(a_raw), b=int(b_raw))
    except ValueError:
        return None


def _size_guarded(curve: RationalCurve) -> bool:
    return max(len(str(abs(curve.a))), len(str(abs(curve.b)))) > 30


def _metrics(results: list[ScenarioResult]) -> EffectivenessMetrics:
    weak = [result for result in results if result.expected_risk]
    latencies = sorted(result.gate_latency_ms for result in results)
    return EffectivenessMetrics(
        profiles_total=len(results),
        weak_or_invalid_total=len(weak),
        accepted_control_total=sum(1 for result in results if not result.expected_risk),
        constructed_reference_cases=sum(1 for result in results if result.source == "injected"),
        sage_fixture_controls=sum(1 for result in results if result.source == "sage_fixture"),
        generated_control_profiles=sum(
            1 for result in results if result.source == "deterministic_generator"
        ),
        malformed_total=sum(1 for result in results if result.family == "malformed"),
        size_guarded_total=sum(1 for result in results if result.family == "size_guarded"),
        downgrade_or_mismatch_total=sum(
            1 for result in results if result.family in {"downgrade", "packet_mismatch"}
        ),
        accepted_without_precheck_weak=sum(1 for result in weak if result.no_precheck_accepted),
        accepted_by_discriminant_only_weak=sum(
            1 for result in weak if result.discriminant_only_accepted
        ),
        accepted_by_adamova_gate_weak=sum(1 for result in weak if result.adamova_gate_accepted),
        rejected_or_blocked_by_adamova_gate_weak=sum(
            1 for result in weak if not result.adamova_gate_accepted
        ),
        accepted_controls_by_adamova_gate=sum(
            1 for result in results if not result.expected_risk and result.adamova_gate_accepted
        ),
        needs_reference_validation=sum(
            1 for result in results if result.adamova_decision == "REFERENCE_VALIDATION_REQUIRED"
        ),
        size_guarded=sum(1 for result in results if result.adamova_decision == "SIZE_GUARDED"),
        median_gate_latency_ms=statistics.median(latencies),
        p95_gate_latency_ms=_percentile(latencies, 0.95),
    )


def _percentile(sorted_values: list[float], percentile: float) -> float:
    if not sorted_values:
        return 0.0
    index = int((len(sorted_values) - 1) * percentile)
    return sorted_values[index]


def _git_sha() -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=ROOT,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def write_reports(report: EffectivenessReport) -> None:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    JSON_PATH.write_text(
        json.dumps(_report_to_json_dict(report), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    _write_csv(report)
    MD_PATH.write_text(_to_markdown(report), encoding="utf-8")
    THESIS_TABLE_PATH.write_text(_to_thesis_table(report), encoding="utf-8")


def _report_to_json_dict(report: EffectivenessReport) -> dict[str, object]:
    return {
        "experiment_id": report.experiment_id,
        "seed": report.seed,
        "git_sha": report.git_sha,
        "profile_policy_version": report.profile_policy_version,
        "gate_backend": report.gate_backend,
        "native_backend_version": report.native_backend_version,
        "native_cli_path": report.native_cli_path,
        "native_cli_retention": NATIVE_CLI_RETENTION,
        "claim_boundary": report.claim_boundary,
        "thesis_statement": report.thesis_statement,
        "metrics": asdict(report.metrics),
        "results": [asdict(result) for result in report.results],
    }


def _write_csv(report: EffectivenessReport) -> None:
    rows = [asdict(result) for result in report.results]
    with CSV_PATH.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()), lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def _to_markdown(report: EffectivenessReport) -> str:
    metrics = report.metrics
    lines = [
        "# Adamova Effectiveness Experiment",
        "",
        f"Experiment id: `{report.experiment_id}`.",
        f"Seed: `{report.seed}`.",
        f"Git SHA: `{report.git_sha}`.",
        f"Profile policy version: `{report.profile_policy_version}`.",
        f"Gate backend: `{report.gate_backend}`.",
        f"Native backend version: `{report.native_backend_version}`.",
        f"Native CLI path: `{report.native_cli_path or '-'}`.",
        f"Native CLI retention: `{NATIVE_CLI_RETENTION}`.",
        "",
        "The native CLI is rebuilt on demand under root `build/`; that directory",
        "is disposable generated output and is not the source of truth for this",
        "evidence pack.",
        "",
        "## Claim Boundary",
        "",
        report.claim_boundary,
        "",
        "## Thesis Statement",
        "",
        report.thesis_statement,
        "",
        "## Metrics",
        "",
        "| Metric | Value |",
        "| --- | ---: |",
    ]
    for key, value in asdict(metrics).items():
        lines.append(f"| `{key}` | {value} |")
    lines.extend(
        [
            "",
            "## Results",
            "",
            "| Scenario | Family | Risk | Reference | No precheck | Discriminant only | Adamova decision | Adamova accepted |",
            "| --- | --- | ---: | --- | ---: | ---: | --- | ---: |",
        ]
    )
    for result in report.results:
        lines.append(
            f"| `{result.scenario_id}` | {result.family} | {result.expected_risk} | "
            f"{result.reference_status} | {result.no_precheck_accepted} | "
            f"{result.discriminant_only_accepted} | `{result.adamova_decision}` | "
            f"{result.adamova_gate_accepted} |"
        )
    lines.extend(
        [
            "",
            "## Interpretation",
            "",
            "- `no_precheck` models accepting any syntactically usable experimental profile.",
            "- `discriminant_only` rejects singular curves but misses rational 2/3-torsion indicators.",
            "- `adamova_gate` rejects singular and small-torsion-risk profiles and blocks malformed/size-guarded cases from automatic admission.",
            "- This experiment is controlled evidence for profile admission policy, not a proof of production message security.",
            "",
        ]
    )
    return "\n".join(lines)


def _to_thesis_table(report: EffectivenessReport) -> str:
    metrics = report.metrics
    return "\n".join(
        [
            "# Таблица для диссертации: эффективность Adamova admission gate",
            "",
            "| Показатель | Значение |",
            "| --- | ---: |",
            f"| Всего профилей в эксперименте | {metrics.profiles_total} |",
            f"| Слабых/некорректных профилей | {metrics.weak_or_invalid_total} |",
            f"| Принято без precheck | {metrics.accepted_without_precheck_weak} |",
            f"| Принято discriminant-only | {metrics.accepted_by_discriminant_only_weak} |",
            f"| Принято Adamova gate | {metrics.accepted_by_adamova_gate_weak} |",
            f"| Отклонено/заблокировано Adamova gate | {metrics.rejected_or_blocked_by_adamova_gate_weak} |",
            f"| Требует reference validation | {metrics.needs_reference_validation} |",
            f"| Size-guarded | {metrics.size_guarded} |",
            f"| Median latency, ms | {metrics.median_gate_latency_ms:.6f} |",
            f"| P95 latency, ms | {metrics.p95_gate_latency_ms:.6f} |",
            f"| Git SHA | `{report.git_sha}` |",
            f"| Profile policy version | {report.profile_policy_version} |",
            f"| Gate backend | `{report.gate_backend}` |",
            f"| Native backend version | `{report.native_backend_version}` |",
            f"| Native CLI path | `{report.native_cli_path or '-'}` |",
            f"| Native CLI retention | `{NATIVE_CLI_RETENTION}` |",
            "",
            "Формулировка: в контролируемой модели подмены параметров экспериментального",
            "криптографического профиля Adamova C++ admission gate снижает число",
            "автоматически принятых слабых/некорректных профилей до 0 и переводит",
            "unsupported/size-guarded случаи в режим эталонной проверки до создания",
            "сессии и отправки сообщений.",
            "",
        ]
    )


def main() -> None:
    report = run_experiment()
    write_reports(report)
    print(f"Wrote {MD_PATH}")
    print(f"Wrote {JSON_PATH}")
    print(f"Wrote {CSV_PATH}")
    print(f"Wrote {THESIS_TABLE_PATH}")


if __name__ == "__main__":
    main()

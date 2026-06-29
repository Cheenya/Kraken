#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
import time
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_APK = REPO_ROOT / "app-android/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME = "com.disser.kraken"
MAIN_ACTIVITY = f"{PACKAGE_NAME}/.MainActivity"


def run_command(args: list[str]) -> dict[str, object]:
    try:
        completed = subprocess.run(
            args,
            cwd=REPO_ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        return {
            "command": args,
            "returncode": completed.returncode,
            "output": completed.stdout.strip(),
        }
    except FileNotFoundError:
        return {
            "command": args,
            "returncode": None,
            "output": "tool-not-found",
        }


def adb_args(device_id: str | None, *args: str) -> list[str]:
    command = ["adb"]
    if device_id:
        command.extend(["-s", device_id])
    command.extend(args)
    return command


def run_cold_launch_smoke(apk: Path, device_id: str | None) -> dict[str, object]:
    steps: dict[str, dict[str, object]] = {
        "adb_devices": run_command(["adb", "devices", "-l"]),
        "install": run_command(adb_args(device_id, "install", "-r", str(apk))),
        "force_stop": run_command(adb_args(device_id, "shell", "am", "force-stop", PACKAGE_NAME)),
        "clear_logcat": run_command(adb_args(device_id, "logcat", "-c")),
        "launch": run_command(adb_args(device_id, "shell", "am", "start", "-n", MAIN_ACTIVITY)),
    }
    time.sleep(3)
    steps["focused_window"] = run_command(adb_args(device_id, "shell", "dumpsys", "window"))
    steps["focused_activity"] = run_command(adb_args(device_id, "shell", "dumpsys", "activity", "activities"))
    steps["android_runtime"] = run_command(
        adb_args(device_id, "logcat", "-d", "-t", "500", "AndroidRuntime:E", "*:S")
    )
    steps["runtime_permissions"] = run_command(
        adb_args(
            device_id,
            "shell",
            "dumpsys",
            "package",
            PACKAGE_NAME,
        )
    )
    steps["bluetooth_manager"] = run_command(adb_args(device_id, "shell", "dumpsys", "bluetooth_manager"))
    steps["bluetooth_setting"] = run_command(adb_args(device_id, "shell", "settings", "get", "global", "bluetooth_on"))
    focused_output = "\n".join(
        str(steps[name]["output"])
        for name in ["focused_window", "focused_activity"]
    )
    runtime_output = str(steps["android_runtime"]["output"])
    install_ok = steps["install"]["returncode"] == 0 and "Success" in str(steps["install"]["output"])
    launch_ok = steps["launch"]["returncode"] == 0
    focused = PACKAGE_NAME in focused_output
    fatal_crash = "FATAL EXCEPTION" in runtime_output and PACKAGE_NAME in runtime_output
    runtime_permissions = str(steps["runtime_permissions"]["output"])
    bluetooth_permissions_granted = all(
        any(
            line.strip().startswith(f"{permission}:") and "granted=true" in line
            for line in runtime_permissions.splitlines()
        )
        for permission in [
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT",
        ]
    )
    bluetooth_setting = str(steps["bluetooth_setting"]["output"]).strip()
    return {
        "device_id": device_id,
        "package": PACKAGE_NAME,
        "main_activity": MAIN_ACTIVITY,
        "install_ok": install_ok,
        "launch_ok": launch_ok,
        "focused": focused,
        "fatal_crash": fatal_crash,
        "bluetooth_permissions_granted": bluetooth_permissions_granted,
        "bluetooth_setting": bluetooth_setting,
        "passed": install_ok and launch_ok and focused and not fatal_crash,
        "steps": steps,
    }


def find_android_tool(name: str) -> Path | None:
    candidates: list[Path] = []
    android_home = os.environ.get("ANDROID_HOME")
    if android_home:
        candidates.extend(Path(android_home).glob(f"**/{name}"))
    candidates.extend((Path.home() / "Library/Android/sdk").glob(f"**/{name}"))
    existing = sorted({path for path in candidates if path.is_file()})
    return existing[-1] if existing else None


def git_output(*args: str) -> str:
    result = run_command(["git", *args])
    return str(result["output"]).strip() if result["returncode"] == 0 else "unknown"


def generated_build_config() -> dict[str, str]:
    build_config = REPO_ROOT / "app-android/app/build/generated/source/buildConfig/debug/com/disser/kraken/BuildConfig.java"
    values: dict[str, str] = {}
    if not build_config.exists():
        return values
    for line in build_config.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line.startswith("public static final"):
            continue
        parts = line.replace(";", "").split(" = ", maxsplit=1)
        if len(parts) != 2:
            continue
        key = parts[0].split()[-1]
        values[key] = parts[1].strip().strip('"')
    return values


def apk_zip_summary(apk: Path) -> dict[str, object]:
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
    native_libs = sorted(name for name in names if name.startswith("lib/") and name.endswith(".so"))
    return {
        "entry_count": len(names),
        "native_libs": native_libs,
        "has_arm64_native_lib": any(name.startswith("lib/arm64-v8a/") for name in native_libs),
        "has_manifest": "AndroidManifest.xml" in names,
        "has_classes_dex": any(name.startswith("classes") and name.endswith(".dex") for name in names),
    }


def write_report(out_dir: Path, report: dict[str, object]) -> None:
    (out_dir / "preflight.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    summary = report["summary"]
    checks = report["checks"]
    lines = [
        "# Kraken phone preflight",
        "",
        f"Generated: `{report['generated_at']}`",
        f"APK: `{report['apk']['path']}`",
        f"APK size: `{report['apk']['size_bytes']}` bytes",
        f"Git: `{report['git']['sha']}` / `{report['git']['source_state']}`",
        "",
        "## Summary",
        "",
    ]
    for key, value in summary.items():
        lines.append(f"- `{key}`: `{value}`")
    lines.extend(["", "## Checks", ""])
    for key, value in checks.items():
        lines.append(f"- `{key}`: `{value}`")
    lines.extend(["", "## Permissions", ""])
    for permission in report["manifest"]["permissions"]:
        lines.append(f"- `{permission}`")
    lines.extend(["", "## Native libs", ""])
    native_libs = report["apk"]["zip"]["native_libs"]
    if native_libs:
        for lib in native_libs:
            lines.append(f"- `{lib}`")
    else:
        lines.append("- none")
    lines.extend(["", "## Tool outputs", ""])
    for name, command_report in report["tools"].items():
        output_file = out_dir / f"{name}.txt"
        output_file.write_text(str(command_report["output"]) + "\n", encoding="utf-8")
        lines.append(f"- `{name}`: returncode `{command_report['returncode']}`, output `{output_file.name}`")
    cold_launch = report.get("cold_launch_smoke")
    if isinstance(cold_launch, dict):
        lines.extend(["", "## Cold launch smoke", ""])
        for key in [
            "device_id",
            "install_ok",
            "launch_ok",
            "focused",
            "fatal_crash",
            "bluetooth_permissions_granted",
            "bluetooth_setting",
            "passed",
        ]:
            lines.append(f"- `{key}`: `{cold_launch.get(key)}`")
        steps = cold_launch.get("steps", {})
        if isinstance(steps, dict):
            lines.extend(["", "### ADB outputs", ""])
            for name, command_report in steps.items():
                if not isinstance(command_report, dict):
                    continue
                output_file = out_dir / f"cold_launch_{name}.txt"
                output_file.write_text(str(command_report.get("output", "")) + "\n", encoding="utf-8")
                lines.append(
                    f"- `{name}`: returncode `{command_report.get('returncode')}`, output `{output_file.name}`"
                )
    (out_dir / "preflight.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Create a Kraken APK phone preflight report.")
    parser.add_argument("--apk", type=Path, default=DEFAULT_APK)
    parser.add_argument("--out-dir", type=Path, default=None)
    parser.add_argument("--run-cold-launch-smoke", action="store_true")
    parser.add_argument("--device-id", default=None)
    args = parser.parse_args()

    apk = args.apk.resolve()
    if not apk.exists():
        raise SystemExit(f"APK not found: {apk}")

    stamp = time.strftime("%Y%m%d-%H%M%S")
    out_dir = args.out_dir or REPO_ROOT / "artifacts/phone-preflight" / stamp
    out_dir.mkdir(parents=True, exist_ok=True)

    aapt = find_android_tool("aapt")
    apkanalyzer = find_android_tool("apkanalyzer")
    tools = {
        "aapt_badging": run_command([str(aapt), "dump", "badging", str(apk)]) if aapt else {"returncode": None, "output": "aapt-not-found"},
        "aapt_permissions": run_command([str(aapt), "dump", "permissions", str(apk)]) if aapt else {"returncode": None, "output": "aapt-not-found"},
        "apkanalyzer_manifest": run_command([str(apkanalyzer), "manifest", "print", str(apk)]) if apkanalyzer else {"returncode": None, "output": "apkanalyzer-not-found"},
        "apkanalyzer_debuggable": run_command([str(apkanalyzer), "manifest", "debuggable", str(apk)]) if apkanalyzer else {"returncode": None, "output": "apkanalyzer-not-found"},
    }
    permissions_output = str(tools["aapt_permissions"]["output"])
    permissions = sorted(
        line.split("'", 2)[1]
        for line in permissions_output.splitlines()
        if "uses-permission:" in line and "'" in line
    )
    build_config = generated_build_config()
    source_state = build_config.get("SOURCE_STATE", "source_state_not_found")
    zip_summary = apk_zip_summary(apk)
    debuggable_output = str(tools["apkanalyzer_debuggable"]["output"]).strip().lower()
    debuggable = "true" in debuggable_output
    expected_permissions = {
        "android.permission.CAMERA",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.INTERNET",
        "android.permission.CHANGE_WIFI_MULTICAST_STATE",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.CHANGE_WIFI_STATE",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_CONNECT",
    }
    forbidden_permissions = {
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.GET_ACCOUNTS",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
    }
    permission_set = set(permissions)
    checks = {
        "apk_exists": apk.exists(),
        "manifest_present": zip_summary["has_manifest"],
        "classes_dex_present": zip_summary["has_classes_dex"],
        "arm64_native_lib_present": zip_summary["has_arm64_native_lib"],
        "debuggable": debuggable,
        "expected_permissions_present": expected_permissions.issubset(permission_set),
        "ble_permissions_present": {
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT",
        }.issubset(permission_set),
        "wifi_direct_fine_location_permission_present": "android.permission.ACCESS_FINE_LOCATION" in permission_set,
        "background_location_absent": "android.permission.ACCESS_BACKGROUND_LOCATION" not in permission_set,
        "forbidden_permissions_absent": not bool(permission_set & forbidden_permissions),
        "source_state_embedded": source_state.startswith("clean_commit_") or source_state.startswith("dirty_working_tree_based_on_"),
        "aapt_available": aapt is not None,
        "apkanalyzer_available": apkanalyzer is not None,
    }
    cold_launch_smoke = run_cold_launch_smoke(apk, args.device_id) if args.run_cold_launch_smoke else None
    if cold_launch_smoke is not None:
        checks["cold_launch_smoke_passed"] = bool(cold_launch_smoke["passed"])
    report = {
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "apk": {
            "path": str(apk),
            "size_bytes": apk.stat().st_size,
            "zip": zip_summary,
        },
        "git": {
            "sha": git_output("rev-parse", "--short", "HEAD"),
            "source_state": source_state,
            "status_porcelain": git_output("status", "--porcelain"),
        },
        "build_config": build_config,
        "manifest": {
            "permissions": permissions,
        },
        "summary": {
            "ready_for_install_smoke": all(
                bool(checks[key])
                for key in [
                    "apk_exists",
                    "manifest_present",
                    "classes_dex_present",
                    "arm64_native_lib_present",
                    "debuggable",
                    "expected_permissions_present",
                    "forbidden_permissions_absent",
                    "source_state_embedded",
                ]
            ),
            "apk_size_mb": round(apk.stat().st_size / (1024 * 1024), 2),
            "permission_count": len(permissions),
            "native_lib_count": len(zip_summary["native_libs"]),
        },
        "checks": checks,
        "tools": tools,
    }
    if cold_launch_smoke is not None:
        report["cold_launch_smoke"] = cold_launch_smoke
    write_report(out_dir, report)
    print(out_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

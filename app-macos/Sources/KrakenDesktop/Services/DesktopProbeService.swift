import Foundation

struct DesktopProbeResult: Equatable {
    var title: String
    var command: String
    var succeeded: Bool
    var output: String
}

struct DesktopProbeService {
    private let repoRoot: URL

    init(repoRoot: URL = DesktopRepoPaths.resolveRepoRoot()) {
        self.repoRoot = repoRoot
    }

    func adbDevices() -> DesktopProbeResult {
        run(title: "ADB devices", executable: "/usr/bin/env", arguments: ["adb", "devices", "-l"])
    }

    func desktopRelayPreflight() -> DesktopProbeResult {
        run(
            title: "Desktop relay preflight",
            executable: "/usr/bin/env",
            arguments: ["python3", "app-macos/script/kraken_desktop_relay_preflight.py"]
        )
    }

    private func run(title: String, executable: String, arguments: [String]) -> DesktopProbeResult {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: executable)
        process.arguments = arguments
        process.currentDirectoryURL = repoRoot

        let outputPipe = Pipe()
        process.standardOutput = outputPipe
        process.standardError = outputPipe

        do {
            try process.run()
            process.waitUntilExit()
            let data = outputPipe.fileHandleForReading.readDataToEndOfFile()
            let output = String(data: data, encoding: .utf8) ?? ""
            return DesktopProbeResult(
                title: title,
                command: ([executable] + arguments).joined(separator: " "),
                succeeded: process.terminationStatus == 0,
                output: output.trimmingCharacters(in: .whitespacesAndNewlines)
            )
        } catch {
            return DesktopProbeResult(
                title: title,
                command: ([executable] + arguments).joined(separator: " "),
                succeeded: false,
                output: error.localizedDescription
            )
        }
    }
}

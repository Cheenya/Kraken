import Foundation

enum DesktopRepoPaths {
    static func resolveRepoRoot() -> URL {
        if let value = ProcessInfo.processInfo.environment["KRAKEN_REPO_ROOT"], !value.isEmpty {
            return URL(fileURLWithPath: value)
        }
        let bundleURL = Bundle.main.bundleURL
        if bundleURL.pathExtension == "app" {
            return bundleURL
                .deletingLastPathComponent()
                .deletingLastPathComponent()
        }
        return URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
            .deletingLastPathComponent()
    }
}

// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "KrakenDesktop",
    platforms: [
        .macOS(.v14),
    ],
    products: [
        .library(name: "KrakenDesktopCore", targets: ["KrakenDesktopCore"]),
        .executable(name: "KrakenDesktop", targets: ["KrakenDesktop"]),
        .executable(name: "KrakenDesktopCoreSmoke", targets: ["KrakenDesktopCoreSmoke"]),
        .executable(name: "KrakenDesktopLanProbe", targets: ["KrakenDesktopLanProbe"]),
        .executable(name: "KrakenDesktopLanListenProbe", targets: ["KrakenDesktopLanListenProbe"]),
        .executable(name: "KrakenDesktopBleProbe", targets: ["KrakenDesktopBleProbe"]),
    ],
    targets: [
        .target(name: "KrakenDesktopCore"),
        .executableTarget(
            name: "KrakenDesktop",
            dependencies: ["KrakenDesktopCore"],
            resources: [
                .process("Resources"),
            ]
        ),
        .executableTarget(
            name: "KrakenDesktopCoreSmoke",
            dependencies: ["KrakenDesktopCore"]
        ),
        .executableTarget(
            name: "KrakenDesktopLanProbe",
            dependencies: ["KrakenDesktopCore"]
        ),
        .executableTarget(
            name: "KrakenDesktopLanListenProbe",
            dependencies: ["KrakenDesktopCore"]
        ),
        .executableTarget(
            name: "KrakenDesktopBleProbe",
            dependencies: ["KrakenDesktopCore"]
        ),
    ]
)

// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "KrakenIOS",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(name: "KrakenIOS", targets: ["KrakenIOS"]),
    ],
    targets: [
        .target(
            name: "KrakenIOS",
            path: "KrakenIOS",
            exclude: [
                "Info.plist",
                "KrakenIOSApp.swift",
                "Assets.xcassets",
                "Views",
            ],
            sources: [
                "Core",
                "Transport",
            ],
            linkerSettings: [
                .linkedLibrary("z"),
            ]
        ),
        .testTarget(
            name: "KrakenIOSTests",
            dependencies: ["KrakenIOS"],
            path: "KrakenIOSTests"
        ),
    ]
)

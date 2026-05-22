// swift-tools-version: 5.7
import PackageDescription

let package = Package(
    name: "NeonRush",
    platforms: [.macOS(.v12)],
    targets: [
        .executableTarget(
            name: "NeonRush",
            path: "Sources/NeonRush"
        )
    ]
)

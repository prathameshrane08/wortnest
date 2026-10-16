// swift-tools-version: 5.9

import PackageDescription
import AppleProductTypes

let package = Package(
    name: "WortNest",
    platforms: [
        .iOS("17.0")
    ],
    products: [
        .iOSApplication(
            name: "WortNest",
            targets: ["AppModule"],
            bundleIdentifier: "com.prathameshrane.wortnest",
            displayVersion: "1.1",
            bundleVersion: "2",
            appIcon: .placeholder(icon: .box),
            accentColor: .presetColor(.orange),
            supportedDeviceFamilies: [
                .pad,
                .phone
            ],
            supportedInterfaceOrientations: [
                .portrait,
                .landscapeRight,
                .landscapeLeft,
                .portraitUpsideDown(.when(deviceFamilies: [.pad]))
            ]
        )
    ],
    targets: [
        .executableTarget(
            name: "AppModule",
            path: ".",
            exclude: ["Package.swift"],
            resources: [
                .copy("Resources/dict.json"),
                .copy("Resources/verbs.json"),
                .copy("Resources/forms.json")
            ]
        )
    ]
)

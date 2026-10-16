// swift-tools-version: 5.9

import PackageDescription
import AppleProductTypes

let package = Package(
    name: "Wortkiste",
    platforms: [
        .iOS("17.0")
    ],
    products: [
        .iOSApplication(
            name: "Wortkiste",
            targets: ["AppModule"],
            bundleIdentifier: "com.prathameshrane.wortkiste",
            displayVersion: "1.0",
            bundleVersion: "1",
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

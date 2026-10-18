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
            name: "DE Täglich",   // the label under the home-screen icon; the full name is Deutsch Täglich
            targets: ["AppModule"],
            bundleIdentifier: "com.prathameshrane.wortnest",
            displayVersion: "1.3",
            bundleVersion: "4",
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
                .copy("Resources/forms.json"),
                .copy("Resources/examples.json")
            ]
        )
    ]
)

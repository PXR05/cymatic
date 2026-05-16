pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cymatic"
include(":app")

include(":decent-usb-audio-driver")
include(":decent-usb-audio-wrapper-media3")
include(":decent-media3-decoder-flac")

project(":decent-usb-audio-driver").projectDir =
    file("../decent-player/libs/decent-usb-audio-driver")
project(":decent-usb-audio-wrapper-media3").projectDir =
    file("../decent-player/libs/decent-usb-audio-wrapper-media3")
project(":decent-media3-decoder-flac").projectDir =
    file("../decent-player/libs/decent-media3-decoder-flac")

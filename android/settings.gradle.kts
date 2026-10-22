pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GameController"

// Pure-Kotlin core (model, protocol, HID reports, input engine). Builds and tests without the Android SDK.
include(":core")
// Pass -PcoreOnly=true to build/test the core on a machine without the Android SDK.
if (providers.gradleProperty("coreOnly").orNull != "true") {
    include(":app")
}

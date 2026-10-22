// Plugin versions live in settings.gradle.kts (pluginManagement). Loading the Kotlin plugin once
// here keeps it from being loaded twice (once per subproject). The Android and Compose plugins are
// applied only by :app, so `-PcoreOnly=true` builds :core without reaching Google's Maven (A27).
plugins {
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
}

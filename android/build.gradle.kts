// Plugin versions live in settings.gradle.kts (pluginManagement). Loading them once here keeps
// the Kotlin plugin from being loaded twice (once per subproject).
plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
}

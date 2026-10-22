// Plugin versions live in settings.gradle.kts (pluginManagement). Loading them once here keeps
// the Kotlin plugin from being loaded twice (once per subproject) and puts the Android plugin on
// the same classpath as Kotlin, which kotlin.android needs. With `-PcoreOnly=true` settings
// switches the root build file to build-core.gradle.kts, which skips the Android plugins (A27).
plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
}

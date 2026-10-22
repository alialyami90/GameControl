// Root build file used with `-PcoreOnly=true`: only :core is built, so nothing from Google's Maven
// is resolved (see build.gradle.kts and settings.gradle.kts).
plugins {
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
}

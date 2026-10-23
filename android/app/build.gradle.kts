plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.gamecontroller"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gamecontroller"
        minSdk = 28 // BluetoothHidDevice needs Android 9
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.0"
    }

    // Release signing comes from the environment (CI secrets), never from the repository:
    // GAMECTL_KEYSTORE (path to the .jks), GAMECTL_KEYSTORE_PASSWORD, GAMECTL_KEY_ALIAS (default gamectl),
    // and GAMECTL_KEY_PASSWORD when the key's password differs from the store's.
    // Without them (local builds, forks) the release APK is signed with the debug key.
    val releaseKeystore = System.getenv("GAMECTL_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = releaseKeystore
            storePassword = System.getenv("GAMECTL_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("GAMECTL_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "gamectl"
            // A PKCS12 store has one password for the store and its key; GAMECTL_KEY_PASSWORD overrides it for JKS.
            keyPassword = System.getenv("GAMECTL_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: System.getenv("GAMECTL_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

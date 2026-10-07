plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing is supplied by CI through environment variables (or Gradle -P properties).
// No keystore and no password is ever committed. When the values are absent (local
// development), the release build falls back to the debug signing config so it stays
// buildable — but a user-facing release MUST be signed with the stable keystore in CI.
fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingValue("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword = signingValue("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = releaseKeystorePath != null &&
    file(releaseKeystorePath).exists() &&
    releaseKeystorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "com.tariffia.panel"
    compileSdk = 34
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.tariffia.panel"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.0.2"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                // Match the STL used by the prebuilt libnode.so (DT_NEEDED libc++_shared.so).
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Keep native libraries uncompressed so the 16 KB page-size alignment of the
    // .so files inside the APK is meaningful and testable.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    signingConfigs {
        // Only created when CI provides a real keystore; nothing secret is stored here.
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Use the stable CI-provided keystore when available; otherwise fall back to
            // the debug key so a local release build still works. A user-facing release
            // must be produced by CI with the stable keystore configured.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.github.mwiede:jsch:0.2.26")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

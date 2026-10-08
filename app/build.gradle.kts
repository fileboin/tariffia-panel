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
    file(releaseKeystorePath).let { it.exists() && it.length() > 0L } &&
    releaseKeystorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

// Explicit, opt-in local-development fallback. It is OFF by default so a production
// release build can never be silently debug-signed; a local dev must ask for it.
val allowDebugReleaseSigning =
    (System.getenv("ALLOW_DEBUG_RELEASE_SIGNING") ?: "").equals("true", ignoreCase = true) ||
        (project.findProperty("ALLOW_DEBUG_RELEASE_SIGNING") as String?)?.equals("true", ignoreCase = true) == true

// Stable DEBUG signing identity: a committed, public TEST keystore (password "android").
// It is NOT a production/release key and must never be used to sign a release. Its only
// purpose is to give every CI `assembleDebug` APK ONE certificate, so a debug build can be
// updated in place by a later debug build (Android refuses updates signed by a different
// certificate; the default AGP debug keystore is generated fresh on every CI runner).
// Release signing above is untouched. See app/debug.keystore.README.md.
val debugKeystoreFile = file("debug.keystore")
val debugKeystorePassword = "android"
val debugKeyAlias = "androiddebugkey"
val debugKeyPassword = "android"

android {
    namespace = "com.tariffia.panel"
    compileSdk = 34
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.tariffia.panel"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.0.3"

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
        // Pin the debug signing to the committed stable keystore, overriding the per-run
        // ephemeral ~/.android/debug.keystore AGP would otherwise generate on a fresh CI
        // runner. DEBUG ONLY — never used for release.
        maybeCreate("debug").apply {
            storeFile = debugKeystoreFile
            storePassword = debugKeystorePassword
            keyAlias = debugKeyAlias
            keyPassword = debugKeyPassword
        }
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
            // Stable CI keystore when configured; an explicit, opt-in debug fallback for
            // local development only; otherwise left unsigned — and a release build is
            // failed clearly below rather than silently producing a mis-signed artifact.
            signingConfig = when {
                hasReleaseSigning -> signingConfigs.getByName("release")
                allowDebugReleaseSigning -> signingConfigs.getByName("debug")
                else -> null
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

// Fail clearly when a production release build is requested without signing material,
// instead of emitting a mis-signed (or unsigned) release artifact. Debug builds are
// unaffected. Set ALLOW_DEBUG_RELEASE_SIGNING=true only for an explicit local test build.
gradle.taskGraph.whenReady {
    val releasePackaging = allTasks.any { task ->
        task.project == project &&
            (task.name == "assembleRelease" || task.name == "bundleRelease" || task.name == "packageRelease")
    }
    if (releasePackaging && !hasReleaseSigning && !allowDebugReleaseSigning) {
        throw GradleException(
            "Release signing is not configured. Provide RELEASE_KEYSTORE_PATH, " +
                "RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD " +
                "(environment or -P properties), or set ALLOW_DEBUG_RELEASE_SIGNING=true to " +
                "produce an explicitly debug-signed local test build.",
        )
    }
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val unityLibraryPresent = rootProject.findProject(":unityLibrary") != null
val licensePublicKey = (System.getenv("PV_LICENSE_PUBLIC_KEY_B64") ?: "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val externalReleaseSigning = System.getenv("PV_EXTERNAL_SIGNING") == "true"

android {
    namespace = "com.puttvision.screen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.puttvision.screen"
        // API 28+ lets the compromised legacy signer be retired after an APK v3 proof-of-rotation handoff.
        minSdk = 28
        targetSdk = 36
        versionCode = System.getenv("PV_VERSION_CODE")?.toIntOrNull() ?: 105
        // Public builds receive PV_VERSION_NAME from the release workflow. Keep the local fallback
        // in the same semantic family so debug/dev APKs never expose an unrelated legacy version.
        versionName = System.getenv("PV_VERSION_NAME") ?: "0.7.0-dev"
        buildConfigField("String", "LICENSE_PUBLIC_KEY_B64", "\"$licensePublicKey\"")
        buildConfigField("boolean", "UNITY_RENDERER_AVAILABLE", unityLibraryPresent.toString())

        // V131: Filament/Filamat ship universal native AARs. PuttVision's supported
        // physical Android target is ARM64; filtering unused ABIs also keeps the embedded
        // V143 Godot runtime practical for self-update delivery.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Godot projects intentionally keep a small hidden .godot directory inside Android assets.
    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.gitignore:!.ds_store:!*.scc:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }

    // Filament, Godot and Unity all use the Android C++ runtime. Package one compatible ARM64 copy
    // rather than failing the merge task on duplicate libc++_shared.so entries.
    packaging {
        jniLibs {
            pickFirsts += setOf("**/libc++_shared.so")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("consumer") {
            dimension = "distribution"
            buildConfigField("boolean", "DEVELOPER_BUILD", "false")
        }
        create("developer") {
            dimension = "distribution"
            buildConfigField("boolean", "DEVELOPER_BUILD", "true")
        }
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("PUTTVISION_STORE_FILE")
            if (!ks.isNullOrBlank()) {
                storeFile = file(ks)
                storePassword = System.getenv("PUTTVISION_STORE_PASSWORD")
                keyAlias = System.getenv("PUTTVISION_KEY_ALIAS")
                keyPassword = System.getenv("PUTTVISION_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!externalReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")

    val cameraX = "1.6.1"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("androidx.camera:camera-video:$cameraX")

    val room = "2.7.2"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    kapt("androidx.room:room-compiler:$room")

    // V131 fallback renderer.
    val filament = "1.75.0"
    implementation("com.google.android.filament:filament-android:$filament")
    implementation("com.google.android.filament:filamat-android:$filament")

    // V149: 4.7.1 showed a real-device Android native crash in the plugin-free smoke path.
    // Pin back to the previous 4.7 stable AAR while a three-stage empty-scene/real-scene/full
    // diagnostic keeps the failure domain explicit on physical Android 16 devices.
    implementation("org.godotengine:godot:4.7.0.stable")

    // Unity is generated, not committed. Existing CI/dev builds remain unchanged until a Unity 6.3
    // export creates unity-export/unityLibrary; at that point this dependency activates automatically.
    if (unityLibraryPresent) {
        implementation(project(":unityLibrary"))
    }

    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260719")
}
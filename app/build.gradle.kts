plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

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
        versionName = System.getenv("PV_VERSION_NAME") ?: "1.2.0-v16"
        buildConfigField("String", "LICENSE_PUBLIC_KEY_B64", "\"$licensePublicKey\"")
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
            dimension = "distribution"")
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

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.camera.core.ExperimentalSessionConfig",
            "-opt-in=androidx.camera.video.ExperimentalHighSpeedVideo",
            "-opt-in=androidx.camera.camera2.interop.ExperimentalCamera2Interop"
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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

    // V131: physically based TV renderer. Filamat is used only to compile the tiny
    // original PuttVision material package once when the Filament surface is created.
    val filament = "1.75.0"
    implementation("com.google.android.filament:filament-android:$filament")
    implementation("com.google.android.filament:filamat-android:$filament")

    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260719")
}

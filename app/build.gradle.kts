plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.puttvision.screen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.puttvision.screen"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("PV_VERSION_CODE")?.toIntOrNull() ?: 103
        versionName = System.getenv("PV_VERSION_NAME") ?: "1.0.3-build-fix"
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
            signingConfig = signingConfigs.getByName("release")
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

    // Bundled/offline QR recognition for zero-touch calibration markers.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}

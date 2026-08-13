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
        minSdk = 30
        targetSdk = 35
        versionCode = (providers.environmentVariable("PV_VERSION_CODE").orNull?.toIntOrNull() ?: 1600)
        versionName = providers.environmentVariable("PV_VERSION_NAME").orNull ?: "1.2.0-v16"
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("consumer") {
            dimension = "distribution"
            buildConfigField("boolean", "DEVELOPER_FEATURES", "false")
        }
        create("developer") {
            dimension = "distribution"
            buildConfigField("boolean", "DEVELOPER_FEATURES", "true")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = providers.environmentVariable("PV_KEYSTORE_PATH").orNull
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = providers.environmentVariable("PV_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("PV_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("PV_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (providers.environmentVariable("PV_KEYSTORE_PATH").orNull.isNullOrBlank()) {
                throw GradleException("PV_KEYSTORE_PATH is required for release builds")
            }
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")

    val cameraX = "1.5.3"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("androidx.camera:camera-video:$cameraX")

    val room = "2.7.2"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    kapt("androidx.room:room-compiler:$room")

    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260719")
}

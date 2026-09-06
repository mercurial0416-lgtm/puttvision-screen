import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val unityExportDir = file("unity-export")
val unityLibraryDir = file("unity-export/unityLibrary")
val unityGradleProperties = file("unity-export/gradle.properties")
val unityProperties = Properties().apply {
    if (unityGradleProperties.isFile) {
        unityGradleProperties.inputStream().use(::load)
    }
}

// Unity 6000.2+ resolves SDK/NDK/toolchain data from properties generated beside unityLibrary.
// When the module is embedded into this Android root, inject those generated values before the
// Unity project evaluates instead of copying machine-local paths into this repository.
gradle.beforeProject {
    if (path == ":unityLibrary") {
        unityProperties.forEach { key, value ->
            extensions.extraProperties.set(key.toString(), value.toString())
        }
    }
}

dependencyResolutionManagement {
    // Unity's generated library may declare a local flatDir. Prefer this root's repositories while
    // allowing that generated project to evaluate without FAIL_ON_PROJECT_REPOS aborting the build.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        if (unityLibraryDir.isDirectory) {
            flatDir {
                dirs(unityLibraryDir.resolve("libs"))
            }
        }
    }
}

rootProject.name = "PuttVisionScreen"
include(":app")

if (unityLibraryDir.isDirectory && unityLibraryDir.resolve("build.gradle").isFile) {
    include(":unityLibrary")
    project(":unityLibrary").projectDir = unityLibraryDir
}

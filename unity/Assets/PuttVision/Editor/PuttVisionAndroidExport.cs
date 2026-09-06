using System;
using System.IO;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEngine;

namespace PuttVision.Editor
{
    public static class PuttVisionAndroidExport
    {
        private const string PrototypeScene = "Assets/PuttVision/Scenes/PuttVisionPrototype.unity";

        [MenuItem("PuttVision/Export Android Library")]
        public static void ExportAndroidLibrary()
        {
            PuttVisionSceneBuilder.BuildPrototypeScene();
            ConfigureAndroidPlayer();
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(PrototypeScene, true) };

            var repoRoot = Path.GetFullPath(Path.Combine(Application.dataPath, "..", ".."));
            var exportRoot = Path.Combine(repoRoot, "unity-export");
            if (Directory.Exists(exportRoot)) Directory.Delete(exportRoot, true);
            Directory.CreateDirectory(exportRoot);

            if (!EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.Android, BuildTarget.Android))
                throw new InvalidOperationException("Unable to switch Unity project to Android build target.");
            EditorUserBuildSettings.exportAsGoogleAndroidProject = true;

            var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions
            {
                scenes = new[] { PrototypeScene },
                locationPathName = exportRoot,
                target = BuildTarget.Android,
                targetGroup = BuildTargetGroup.Android,
                options = BuildOptions.AcceptExternalModificationsToPlayer,
            });

            if (report.summary.result != BuildResult.Succeeded)
                throw new InvalidOperationException(
                    $"PuttVision Unity Android export failed: {report.summary.result}, " +
                    $"errors={report.summary.totalErrors}, warnings={report.summary.totalWarnings}");

            var unityLibrary = Path.Combine(exportRoot, "unityLibrary");
            if (!File.Exists(Path.Combine(unityLibrary, "build.gradle")))
                throw new FileNotFoundException("Unity export did not create unityLibrary/build.gradle");
            if (!File.Exists(Path.Combine(exportRoot, "gradle.properties")))
                throw new FileNotFoundException("Unity export did not create gradle.properties");

            Debug.Log($"[PuttVision] Android library export complete: {unityLibrary}");
        }

        public static void ExportAndroidLibraryBatch() => ExportAndroidLibrary();

        private static void ConfigureAndroidPlayer()
        {
            PlayerSettings.companyName = "PuttVision";
            PlayerSettings.productName = "PuttVision Renderer";
            PlayerSettings.SetApplicationIdentifier(NamedBuildTarget.Android, "com.puttvision.screen.unityrenderer");
            PlayerSettings.Android.applicationEntry = AndroidApplicationEntry.Activity;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel28;
            PlayerSettings.Android.targetSdkVersion = AndroidSdkVersions.AndroidApiLevelAuto;
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.LandscapeLeft;
            PlayerSettings.SetScriptingBackend(NamedBuildTarget.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.SetIl2CppCompilerConfiguration(NamedBuildTarget.Android, Il2CppCompilerConfiguration.Release);
        }
    }
}

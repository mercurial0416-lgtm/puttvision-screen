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

            EditorBuildSettings.scenes = new[]
            {
                new EditorBuildSettingsScene(PrototypeScene, true),
            };

            var repoRoot = Path.GetFullPath(Path.Combine(Application.dataPath, "..", ".."));
            var exportRoot = Path.Combine(repoRoot, "unity-export");
            if (Directory.Exists(exportRoot))
                Directory.Delete(exportRoot, true);
            Directory.CreateDirectory(exportRoot);

            EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.Android, BuildTarget.Android);
            EditorUserBuildSettings.exportAsGoogleAndroidProject = true;

            var options = new BuildPlayerOptions
            {
                scenes = new[] { PrototypeScene },
                locationPathName = exportRoot,
                target = BuildTarget.Android,
                targetGroup = BuildTargetGroup.Android,
                options = BuildOptions.AcceptExternalModificationsToPlayer,
            };

            var report = BuildPipeline.BuildPlayer(options);
            if (report.summary.result != BuildResult.Succeeded)
                throw new InvalidOperationException(
                    $"PuttVision Unity Android export failed: {report.summary.result}, " +
                    $"errors={report.summary.totalErrors}, warnings={report.summary.totalWarnings}");

            var unityLibrary = Path.Combine(exportRoot, "unityLibrary");
            if (!Directory.Exists(unityLibrary))
                throw new DirectoryNotFoundException($"Unity export did not create {unityLibrary}");

            Debug.Log(
                $"[PuttVision] Android library export complete: {unityLibrary} " +
                $"({report.summary.totalSize / (1024.0 * 1024.0):F1} MiB build output)");
        }

        // Command-line entry for local/CI Unity installations:
        // Unity -batchmode -quit -projectPath unity -executeMethod
        // PuttVision.Editor.PuttVisionAndroidExport.ExportAndroidLibrary
        public static void ExportAndroidLibraryBatch() => ExportAndroidLibrary();

        private static void ConfigureAndroidPlayer()
        {
            PlayerSettings.companyName = "PuttVision";
            PlayerSettings.productName = "PuttVision Renderer";
            PlayerSettings.SetApplicationIdentifier(
                NamedBuildTarget.Android,
                "com.puttvision.screen.unityrenderer");

            // Force the classic Activity entry so the native host can launch UnityPlayerActivity
            // explicitly on Android's presentation display. Unity 6 defaults new projects to
            // GameActivity, so leaving this implicit would make the host contract ambiguous.
            PlayerSettings.Android.applicationEntry = AndroidApplicationEntry.Activity;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel28;
            PlayerSettings.Android.targetSdkVersion = AndroidSdkVersions.AndroidApiLevelAuto;
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            PlayerSettings.Android.androidIsGame = false;
            PlayerSettings.Android.androidTVCompatibility = false;
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.LandscapeLeft;

            PlayerSettings.SetScriptingBackend(
                NamedBuildTarget.Android,
                ScriptingImplementation.IL2CPP);
            PlayerSettings.SetIl2CppCompilerConfiguration(
                NamedBuildTarget.Android,
                Il2CppCompilerConfiguration.Release);
        }
    }
}

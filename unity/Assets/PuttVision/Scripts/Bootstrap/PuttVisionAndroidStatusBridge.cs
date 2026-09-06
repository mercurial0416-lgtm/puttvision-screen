using UnityEngine;

namespace PuttVision.Bootstrap
{
    public static class PuttVisionAndroidStatusBridge
    {
        private const string AndroidRuntimeClass = "com.puttvision.screen.UnityTvRuntime";

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void ReportReady()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using var runtime = new AndroidJavaClass(AndroidRuntimeClass);
                runtime.CallStatic("onUnityReady");
            }
            catch (System.Exception exception)
            {
                Debug.LogWarning($"[PuttVision] Android ready callback failed: {exception.Message}");
            }
#else
            Debug.Log("[PuttVision] Renderer scene ready.");
#endif
        }

        public static void ReportFailure(string message)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using var runtime = new AndroidJavaClass(AndroidRuntimeClass);
                runtime.CallStatic("onUnityFailure", message ?? "unknown");
            }
            catch
            {
            }
#endif
        }
    }
}

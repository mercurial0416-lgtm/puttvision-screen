using UnityEngine;

namespace PuttVision.Bootstrap
{
    public static class PuttVisionAndroidStatusBridge
    {
        private const string AndroidRuntimeClass = "com.puttvision.screen.UnityTvRuntime";
        private const string UnityPlayerClass = "com.unity3d.player.UnityPlayer";
        private const string DisplayIdExtra = "pv_display_id";

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void ReportReady()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                var displayId = CurrentDisplayId();
                if (displayId < 0)
                {
                    Debug.LogWarning("[PuttVision] Renderer ready callback missing display id.");
                    return;
                }

                using var runtime = new AndroidJavaClass(AndroidRuntimeClass);
                runtime.CallStatic("onUnityReady", displayId);
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
                var displayId = CurrentDisplayId();
                if (displayId < 0)
                    return;

                using var runtime = new AndroidJavaClass(AndroidRuntimeClass);
                runtime.CallStatic("onUnityFailure", displayId, message ?? "unknown");
            }
            catch
            {
            }
#endif
        }

#if UNITY_ANDROID && !UNITY_EDITOR
        private static int CurrentDisplayId()
        {
            using var player = new AndroidJavaClass(UnityPlayerClass);
            using var activity = player.GetStatic<AndroidJavaObject>("currentActivity");
            if (activity == null)
                return -1;

            using var intent = activity.Call<AndroidJavaObject>("getIntent");
            return intent?.Call<int>("getIntExtra", DisplayIdExtra, -1) ?? -1;
        }
#endif
    }
}

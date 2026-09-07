using UnityEngine;

namespace PuttVision.Bootstrap
{
    public static class PuttVisionAndroidStatusBridge
    {
        private const string AndroidRuntimeClass = "com.puttvision.screen.UnityTvRuntime";
        private const string UnityPlayerClass = "com.unity3d.player.UnityPlayer";
        private const string DisplayIdExtra = "pv_display_id";
        private const string LaunchSessionExtra = "pv_launch_session";

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void ReportReady()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                var displayId = CurrentDisplayId();
                var launchSession = CurrentLaunchSession();
                if (displayId < 0 || launchSession <= 0)
                {
                    Debug.LogWarning("[PuttVision] Renderer ready callback missing launch identity.");
                    return;
                }

                using var runtime = new AndroidJavaClass(AndroidRuntimeClass);
                runtime.CallStatic("onUnityReady", displayId, launchSession);
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
                var launchSession = CurrentLaunchSession();
                if (displayId < 0 || launchSession <= 0)
                    return;

                using var runtime = new AndroidJavaClass(AndroidRuntimeClass);
                runtime.CallStatic("onUnityFailure", displayId, launchSession, message ?? "unknown");
            }
            catch
            {
            }
#endif
        }

#if UNITY_ANDROID && !UNITY_EDITOR
        private static AndroidJavaObject CurrentIntent()
        {
            using var player = new AndroidJavaClass(UnityPlayerClass);
            using var activity = player.GetStatic<AndroidJavaObject>("currentActivity");
            return activity?.Call<AndroidJavaObject>("getIntent");
        }

        private static int CurrentDisplayId()
        {
            using var intent = CurrentIntent();
            return intent?.Call<int>("getIntExtra", DisplayIdExtra, -1) ?? -1;
        }

        private static long CurrentLaunchSession()
        {
            using var intent = CurrentIntent();
            return intent?.Call<long>("getLongExtra", LaunchSessionExtra, -1L) ?? -1L;
        }
#endif
    }
}

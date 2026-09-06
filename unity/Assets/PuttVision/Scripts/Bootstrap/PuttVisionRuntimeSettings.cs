using UnityEngine;

namespace PuttVision.Bootstrap
{
    public sealed class PuttVisionRuntimeSettings : MonoBehaviour
    {
        [SerializeField, Range(30, 120)] private int targetFrameRate = 60;
        [SerializeField, Range(60, 240)] private int simulationHz = 120;
        [SerializeField] private bool keepScreenAwake = true;

        private void Awake()
        {
            QualitySettings.vSyncCount = 0;
            Application.targetFrameRate = targetFrameRate;
            Time.fixedDeltaTime = 1f / simulationHz;

            if (keepScreenAwake)
                Screen.sleepTimeout = SleepTimeout.NeverSleep;
        }
    }
}

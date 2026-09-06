using System;
using System.Threading;
using UnityEngine;

namespace PuttVision.Telemetry
{
    [DisallowMultipleComponent]
    public sealed class PuttPhysicsFrameReceiver : MonoBehaviour
    {
        private PuttPhysicsFrame _latest;

        public static event Action<PuttPhysicsFrame> FrameReceived;
        public static event Action RendererReset;

        public void OnPhysicsFrameJson(string json)
        {
            if (string.IsNullOrWhiteSpace(json))
                return;

            PuttPhysicsFrame frame;
            try
            {
                frame = JsonUtility.FromJson<PuttPhysicsFrame>(json);
            }
            catch (Exception exception)
            {
                Debug.LogWarning($"[PuttVision] Invalid physics-frame JSON: {exception.Message}");
                return;
            }

            if (frame == null || !frame.IsUsable)
                return;

            // Presentation wants the newest authoritative pose, not a backlog of stale frames.
            Interlocked.Exchange(ref _latest, frame);
        }

        public void OnRendererReset(string _)
        {
            Interlocked.Exchange(ref _latest, null);
            RendererReset?.Invoke();
        }

        private void Update()
        {
            var frame = Interlocked.Exchange(ref _latest, null);
            if (frame != null)
                FrameReceived?.Invoke(frame);
        }

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void EnsureReceiverInstalled()
        {
            var telemetryReceiver = FindFirstObjectByType<PuttTelemetryReceiver>();
            if (telemetryReceiver == null)
                return;

            if (telemetryReceiver.GetComponent<PuttPhysicsFrameReceiver>() == null)
                telemetryReceiver.gameObject.AddComponent<PuttPhysicsFrameReceiver>();
        }
    }
}

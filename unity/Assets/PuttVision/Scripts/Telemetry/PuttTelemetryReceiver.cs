using System;
using System.Collections.Concurrent;
using UnityEngine;

namespace PuttVision.Telemetry
{
    [DisallowMultipleComponent]
    public sealed class PuttTelemetryReceiver : MonoBehaviour
    {
        private readonly ConcurrentQueue<PuttTelemetry> _pending = new ConcurrentQueue<PuttTelemetry>();

        public static event Action<PuttTelemetry> ShotReceived;

        /// <summary>
        /// Android bridge entry point. Keep this exact method name stable while using
        /// UnityPlayer.UnitySendMessage("PuttTelemetryReceiver", "OnTelemetryJson", json).
        /// </summary>
        public void OnTelemetryJson(string json)
        {
            if (string.IsNullOrWhiteSpace(json))
                return;

            PuttTelemetry telemetry;
            try
            {
                telemetry = JsonUtility.FromJson<PuttTelemetry>(json);
            }
            catch (Exception exception)
            {
                Debug.LogWarning($"[PuttVision] Invalid telemetry JSON: {exception.Message}");
                return;
            }

            if (telemetry == null)
                return;

            if (telemetry.schemaVersion != PuttTelemetry.CurrentSchemaVersion)
            {
                Debug.LogWarning(
                    $"[PuttVision] Unsupported telemetry schema {telemetry.schemaVersion}; " +
                    $"expected {PuttTelemetry.CurrentSchemaVersion}.");
                return;
            }

            if (!telemetry.IsSimulationReady)
            {
                Debug.LogWarning($"[PuttVision] Rejected unusable shot {telemetry.shotId}.");
                return;
            }

            // The transport is currently UnitySendMessage, but enqueueing here keeps the
            // contract safe if the transport is later replaced by a JNI/plugin callback.
            _pending.Enqueue(telemetry);
        }

        private void Update()
        {
            while (_pending.TryDequeue(out var telemetry))
                ShotReceived?.Invoke(telemetry);
        }

        private void OnDestroy()
        {
            while (_pending.TryDequeue(out _))
            {
            }
        }
    }
}

using System;
using System.Threading;
using UnityEngine;

namespace PuttVision.Telemetry
{
    [DisallowMultipleComponent]
    public sealed class PuttSurfaceGridReceiver : MonoBehaviour
    {
        private PuttSurfaceGrid _latest;
        public static event Action<PuttSurfaceGrid> SurfaceGridReceived;

        public void OnSurfaceGridJson(string json)
        {
            if (string.IsNullOrWhiteSpace(json))
                return;

            PuttSurfaceGrid grid;
            try { grid = JsonUtility.FromJson<PuttSurfaceGrid>(json); }
            catch (Exception exception)
            {
                Debug.LogWarning($"[PuttVision] Invalid surface-grid JSON: {exception.Message}");
                return;
            }

            if (grid == null || !grid.TryDecode())
            {
                Debug.LogWarning("[PuttVision] Rejected invalid surface grid.");
                return;
            }

            Interlocked.Exchange(ref _latest, grid);
        }

        private void Update()
        {
            var grid = Interlocked.Exchange(ref _latest, null);
            if (grid != null)
                SurfaceGridReceived?.Invoke(grid);
        }

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void EnsureInstalled()
        {
            var receiver = FindFirstObjectByType<PuttTelemetryReceiver>();
            if (receiver != null && receiver.GetComponent<PuttSurfaceGridReceiver>() == null)
                receiver.gameObject.AddComponent<PuttSurfaceGridReceiver>();
        }
    }
}

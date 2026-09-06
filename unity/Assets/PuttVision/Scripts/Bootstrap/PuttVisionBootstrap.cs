using PuttVision.Simulation;
using PuttVision.Telemetry;
using UnityEngine;

namespace PuttVision.Bootstrap
{
    [DefaultExecutionOrder(-100)]
    public sealed class PuttVisionBootstrap : MonoBehaviour
    {
        public const string TelemetryReceiverObjectName = "PuttTelemetryReceiver";

        [SerializeField] private PuttTelemetryReceiver telemetryReceiver;
        [SerializeField] private PuttBallController ballController;

        private void Awake()
        {
            if (telemetryReceiver == null)
                telemetryReceiver = FindFirstObjectByType<PuttTelemetryReceiver>();

            if (telemetryReceiver == null)
            {
                var receiverObject = new GameObject(TelemetryReceiverObjectName);
                telemetryReceiver = receiverObject.AddComponent<PuttTelemetryReceiver>();
            }
            else
            {
                // UnityPlayer.UnitySendMessage targets a GameObject by name.
                telemetryReceiver.gameObject.name = TelemetryReceiverObjectName;
            }

            if (ballController == null)
                ballController = FindFirstObjectByType<PuttBallController>();
        }

        private void OnEnable()
        {
            PuttTelemetryReceiver.ShotReceived += HandleShotReceived;
        }

        private void OnDisable()
        {
            PuttTelemetryReceiver.ShotReceived -= HandleShotReceived;
        }

        private void HandleShotReceived(PuttTelemetry telemetry)
        {
            if (ballController == null)
            {
                Debug.LogError("[PuttVision] No PuttBallController is assigned.");
                return;
            }

            ballController.Launch(telemetry);
        }

#if UNITY_EDITOR
        [ContextMenu("PuttVision/Send Test Putt")]
        private void SendEditorTestPutt()
        {
            if (telemetryReceiver == null)
                return;

            var testShot = new PuttTelemetry
            {
                schemaVersion = PuttTelemetry.CurrentSchemaVersion,
                shotId = "editor-test",
                timestampNs = 0,
                ballSpeedMps = 1.8f,
                launchDirectionDeg = 0.75f,
                faceAngleDeg = 0.25f,
                pathAngleDeg = 0.5f,
                impactOffsetMm = 1.5f,
                confidence = 0.98f,
                validityFlags = PuttTelemetry.FaceAngleValid |
                                PuttTelemetry.PathAngleValid |
                                PuttTelemetry.ImpactOffsetValid |
                                PuttTelemetry.ConfidenceValid
            };

            telemetryReceiver.OnTelemetryJson(JsonUtility.ToJson(testShot));
        }
#endif
    }
}

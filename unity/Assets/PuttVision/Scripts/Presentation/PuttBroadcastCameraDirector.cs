using PuttVision.Telemetry;
using UnityEngine;

namespace PuttVision.Presentation
{
    [DisallowMultipleComponent]
    public sealed class PuttBroadcastCameraDirector : MonoBehaviour
    {
        [SerializeField] private Camera targetCamera;
        [SerializeField, Min(0.1f)] private float positionSmoothTime = 0.42f;
        [SerializeField, Min(0.1f)] private float rotationResponsiveness = 4.8f;

        private PuttTelemetry _shot;
        private PuttPhysicsFrame _frame;
        private Vector3 _positionVelocity;
        private bool _replay;

        private void Awake()
        {
            if (targetCamera == null)
                targetCamera = Camera.main;
        }

        private void OnEnable()
        {
            PuttTelemetryReceiver.ShotReceived += OnShot;
            PuttPhysicsFrameReceiver.FrameReceived += OnFrame;
            PuttPhysicsFrameReceiver.RendererReset += OnReset;
            PuttReplayController.ReplayStarted += OnReplayStarted;
            PuttReplayController.ReplayEnded += OnReplayEnded;
        }

        private void OnDisable()
        {
            PuttTelemetryReceiver.ShotReceived -= OnShot;
            PuttPhysicsFrameReceiver.FrameReceived -= OnFrame;
            PuttPhysicsFrameReceiver.RendererReset -= OnReset;
            PuttReplayController.ReplayStarted -= OnReplayStarted;
            PuttReplayController.ReplayEnded -= OnReplayEnded;
        }

        private void LateUpdate()
        {
            if (targetCamera == null)
                return;

            ResolvePose(out var desiredPosition, out var lookAt, out var fov);
            targetCamera.transform.position = Vector3.SmoothDamp(
                targetCamera.transform.position,
                desiredPosition,
                ref _positionVelocity,
                positionSmoothTime,
                Mathf.Infinity,
                Time.unscaledDeltaTime);

            var forward = lookAt - targetCamera.transform.position;
            if (forward.sqrMagnitude > 0.0001f)
            {
                var desiredRotation = Quaternion.LookRotation(forward.normalized, Vector3.up);
                var t = 1f - Mathf.Exp(-rotationResponsiveness * Time.unscaledDeltaTime);
                targetCamera.transform.rotation = Quaternion.Slerp(targetCamera.transform.rotation, desiredRotation, t);
            }

            targetCamera.fieldOfView = Mathf.Lerp(
                targetCamera.fieldOfView,
                fov,
                1f - Mathf.Exp(-3.2f * Time.unscaledDeltaTime));
        }

        private void ResolvePose(out Vector3 position, out Vector3 lookAt, out float fov)
        {
            var holeZ = _shot != null ? Mathf.Max(2f, _shot.holeDistanceM) : 5f;
            var ball = _frame != null
                ? new Vector3(_frame.xM, Mathf.Max(0f, _frame.centerZM), _frame.yM)
                : new Vector3(_shot?.startXM ?? 0f, 0.02f, _shot?.startYM ?? -3.5f);
            var hole = new Vector3(0f, 0f, holeZ);

            if (_replay)
            {
                var mid = Vector3.Lerp(ball, hole, 0.38f);
                position = new Vector3(ball.x - 1.65f, 1.15f, ball.z - 2.25f);
                lookAt = mid + Vector3.up * 0.08f;
                fov = 38f;
                return;
            }

            if (_frame != null && _frame.running)
            {
                var progress = Mathf.Clamp01(ball.z / Mathf.Max(holeZ, 0.1f));
                var lateral = Mathf.Lerp(0.5f, 1.15f, progress);
                position = new Vector3(ball.x - lateral, Mathf.Lerp(1.65f, 1.15f, progress), ball.z - Mathf.Lerp(3.3f, 2.25f, progress));
                lookAt = Vector3.Lerp(ball, hole, 0.48f) + Vector3.up * 0.07f;
                fov = Mathf.Lerp(34f, 39f, progress);
                return;
            }

            if (_frame != null && !_frame.running)
            {
                position = new Vector3(hole.x - 1.35f, 1.05f, hole.z - 2.15f);
                lookAt = Vector3.Lerp(ball, hole, 0.72f) + Vector3.up * 0.05f;
                fov = 36f;
                return;
            }

            var span = Mathf.Clamp(holeZ, 3f, 12f);
            position = new Vector3(-0.65f, Mathf.Lerp(2.15f, 3.25f, span / 12f), -Mathf.Lerp(4.8f, 6.6f, span / 12f));
            lookAt = new Vector3(0f, 0.08f, holeZ * 0.54f);
            fov = 34f;
        }

        private void OnShot(PuttTelemetry shot)
        {
            _shot = shot;
            _frame = null;
            _replay = false;
        }

        private void OnFrame(PuttPhysicsFrame frame) => _frame = frame;

        private void OnReset()
        {
            _shot = null;
            _frame = null;
            _replay = false;
        }

        private void OnReplayStarted() => _replay = true;
        private void OnReplayEnded() => _replay = false;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void Install()
        {
            if (FindFirstObjectByType<PuttBroadcastCameraDirector>() != null)
                return;
            new GameObject("PuttBroadcastCameraDirector").AddComponent<PuttBroadcastCameraDirector>();
        }
    }
}

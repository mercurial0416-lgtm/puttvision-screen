using PuttVision.Telemetry;
using UnityEngine;

namespace PuttVision.Simulation
{
    [DisallowMultipleComponent]
    public sealed class PuttAuthoritativeBallPresenter : MonoBehaviour
    {
        [SerializeField] private Transform ballVisual;
        [SerializeField] private PuttBallController localSimulationFallback;

        private Vector3 _initialPosition;
        private Quaternion _initialRotation;
        private bool _hasAuthority;

        public bool HasAuthority => _hasAuthority;

        private void Awake()
        {
            if (ballVisual == null)
                ballVisual = transform;

            if (localSimulationFallback == null)
                localSimulationFallback = GetComponent<PuttBallController>();

            _initialPosition = ballVisual.position;
            _initialRotation = ballVisual.rotation;
        }

        private void OnEnable()
        {
            PuttPhysicsFrameReceiver.FrameReceived += ApplyFrame;
            PuttPhysicsFrameReceiver.RendererReset += ResetPresentation;
        }

        private void OnDisable()
        {
            PuttPhysicsFrameReceiver.FrameReceived -= ApplyFrame;
            PuttPhysicsFrameReceiver.RendererReset -= ResetPresentation;
        }

        public void ApplyFrame(PuttPhysicsFrame frame)
        {
            if (frame == null || !frame.IsUsable || ballVisual == null)
                return;

            if (!_hasAuthority)
            {
                _hasAuthority = true;
                if (localSimulationFallback != null)
                    localSimulationFallback.enabled = false;
            }

            // Native physics basis: +X player-right, +Y target-forward, +Z up.
            // Unity basis:        +X player-right, +Z target-forward, +Y up.
            var position = new Vector3(frame.xM, frame.centerZM, frame.yM);
            var rotation = NativeQuaternionToUnity(frame);
            ballVisual.SetPositionAndRotation(position, rotation);
        }

        private void ResetPresentation()
        {
            _hasAuthority = false;

            if (ballVisual != null)
                ballVisual.SetPositionAndRotation(_initialPosition, _initialRotation);

            if (localSimulationFallback != null)
            {
                localSimulationFallback.ResetBall();
                localSimulationFallback.enabled = true;
            }
        }

        /// <summary>
        /// Converts the exact quaternion convention used by V136BallPose.kt.
        ///
        /// This is intentionally NOT a component swap. Swapping native Y/Z changes handedness;
        /// the correct Unity rotation is B * Rnative * B, where B maps (x,y,z) -> (x,z,y).
        /// </summary>
        internal static Quaternion NativeQuaternionToUnity(PuttPhysicsFrame frame)
        {
            var w = frame.orientationW;
            var x = frame.orientationX;
            var y = frame.orientationY;
            var z = frame.orientationZ;

            var magnitude = Mathf.Sqrt(w * w + x * x + y * y + z * z);
            if (!float.IsFinite(magnitude) || magnitude < 1e-6f)
                return Quaternion.identity;

            w /= magnitude;
            x /= magnitude;
            y /= magnitude;
            z /= magnitude;

            var xx = x * x;
            var yy = y * y;
            var zz = z * z;
            var xy = x * y;
            var xz = x * z;
            var yz = y * z;
            var wx = w * x;
            var wy = w * y;
            var wz = w * z;

            // Rnative, matching V136BallPose.kt.
            var r01 = 2f * (xy - wz);
            var r02 = 2f * (xz + wy);
            var r11 = 1f - 2f * (xx + zz);
            var r12 = 2f * (yz - wx);
            var r21 = 2f * (yz + wx);
            var r22 = 1f - 2f * (xx + yy);

            // Columns of Runity = B * Rnative * B needed by LookRotation.
            var unityForward = new Vector3(r01, r21, r11);
            var unityUp = new Vector3(r02, r22, r12);

            if (unityForward.sqrMagnitude < 1e-8f || unityUp.sqrMagnitude < 1e-8f)
                return Quaternion.identity;

            return Quaternion.LookRotation(unityForward.normalized, unityUp.normalized);
        }

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void InstallOnPrototypeBall()
        {
            var fallback = FindFirstObjectByType<PuttBallController>();
            if (fallback == null || fallback.GetComponent<PuttAuthoritativeBallPresenter>() != null)
                return;

            fallback.gameObject.AddComponent<PuttAuthoritativeBallPresenter>();
        }
    }
}

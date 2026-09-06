using PuttVision.Telemetry;
using UnityEngine;

namespace PuttVision.Simulation
{
    public sealed class PuttBallController : MonoBehaviour
    {
        [Header("Scene")]
        [SerializeField] private Transform ballVisual;
        [SerializeField] private GreenSurfaceSampler surfaceSampler;

        [Header("Ball")]
        [SerializeField, Min(0.001f)] private float ballRadiusM = 0.02135f;
        [SerializeField, Min(0f)] private float rollingResistanceMps2 = 0.42f;
        [SerializeField, Min(0.001f)] private float stopSpeedMps = 0.025f;
        [SerializeField, Min(0.01f)] private float maxAcceptedSpeedMps = 8f;

        private Vector3 _velocity;
        private Vector3 _startPosition;
        private bool _rolling;
        private bool _initialized;

        public bool IsRolling => _rolling;
        public float SpeedMps => _velocity.magnitude;

        private void Awake()
        {
            if (ballVisual == null)
                ballVisual = transform;

            _startPosition = ballVisual.position;
            _initialized = true;
            SnapToGreen();
        }

        public void Launch(PuttTelemetry telemetry)
        {
            if (telemetry == null || !telemetry.IsSimulationReady)
                return;

            if (!_initialized)
            {
                _startPosition = ballVisual != null ? ballVisual.position : transform.position;
                _initialized = true;
            }

            ResetBall();

            var speed = Mathf.Clamp(telemetry.ballSpeedMps, 0f, maxAcceptedSpeedMps);
            var radians = telemetry.launchDirectionDeg * Mathf.Deg2Rad;
            var direction = new Vector3(Mathf.Sin(radians), 0f, Mathf.Cos(radians)).normalized;

            var surface = surfaceSampler != null
                ? surfaceSampler.Sample(ballVisual.position)
                : new GreenSurfaceSample(false, ballVisual.position, Vector3.up);

            direction = Vector3.ProjectOnPlane(direction, surface.Normal).normalized;
            _velocity = direction * speed;
            _rolling = speed >= stopSpeedMps;
        }

        public void ResetBall()
        {
            _velocity = Vector3.zero;
            _rolling = false;

            if (ballVisual == null)
                return;

            ballVisual.position = _startPosition;
            ballVisual.rotation = Quaternion.identity;
            SnapToGreen();
        }

        private void FixedUpdate()
        {
            if (!_rolling || ballVisual == null)
                return;

            var dt = Time.fixedDeltaTime;
            var surface = surfaceSampler != null
                ? surfaceSampler.Sample(ballVisual.position)
                : new GreenSurfaceSample(false, ballVisual.position, Vector3.up);

            // Gravity component tangent to the green produces break. Rolling resistance
            // opposes motion with a tuneable constant acceleration.
            var slopeAcceleration = Vector3.ProjectOnPlane(Physics.gravity, surface.Normal);
            var resistance = _velocity.sqrMagnitude > 0f
                ? -_velocity.normalized * rollingResistanceMps2
                : Vector3.zero;

            var acceleration = slopeAcceleration + resistance;
            var nextVelocity = Vector3.ProjectOnPlane(_velocity + acceleration * dt, surface.Normal);

            // Prevent resistance from numerically reversing a nearly stopped ball. A steep
            // enough slope is still allowed to keep the ball moving.
            if (Vector3.Dot(nextVelocity, _velocity) <= 0f &&
                slopeAcceleration.magnitude <= rollingResistanceMps2)
            {
                Stop();
                return;
            }

            if (nextVelocity.magnitude < stopSpeedMps &&
                slopeAcceleration.magnitude <= rollingResistanceMps2)
            {
                Stop();
                return;
            }

            var previousPosition = ballVisual.position;
            _velocity = nextVelocity;
            var proposedPosition = previousPosition + _velocity * dt;

            var nextSurface = surfaceSampler != null
                ? surfaceSampler.Sample(proposedPosition)
                : new GreenSurfaceSample(false, proposedPosition, Vector3.up);

            ballVisual.position = nextSurface.Point + nextSurface.Normal * ballRadiusM;
            RotateBallVisual(previousPosition, ballVisual.position, nextSurface.Normal);
        }

        private void RotateBallVisual(Vector3 previousPosition, Vector3 currentPosition, Vector3 normal)
        {
            var displacement = currentPosition - previousPosition;
            var distance = displacement.magnitude;
            if (distance <= Mathf.Epsilon || ballRadiusM <= Mathf.Epsilon)
                return;

            var travelDirection = displacement / distance;
            var axis = Vector3.Cross(normal, travelDirection).normalized;
            if (axis.sqrMagnitude <= Mathf.Epsilon)
                return;

            var degrees = distance / ballRadiusM * Mathf.Rad2Deg;
            ballVisual.rotation = Quaternion.AngleAxis(degrees, axis) * ballVisual.rotation;
        }

        private void SnapToGreen()
        {
            if (ballVisual == null || surfaceSampler == null)
                return;

            var surface = surfaceSampler.Sample(ballVisual.position);
            ballVisual.position = surface.Point + surface.Normal * ballRadiusM;
        }

        private void Stop()
        {
            _velocity = Vector3.zero;
            _rolling = false;
        }
    }
}

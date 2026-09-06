using UnityEngine;

namespace PuttVision.Simulation
{
    public readonly struct GreenSurfaceSample
    {
        public GreenSurfaceSample(bool hit, Vector3 point, Vector3 normal)
        {
            Hit = hit;
            Point = point;
            Normal = normal.sqrMagnitude > 0f ? normal.normalized : Vector3.up;
        }

        public bool Hit { get; }
        public Vector3 Point { get; }
        public Vector3 Normal { get; }
    }

    public sealed class GreenSurfaceSampler : MonoBehaviour
    {
        [SerializeField] private LayerMask greenMask = ~0;
        [SerializeField, Min(0.1f)] private float castHeight = 2f;
        [SerializeField, Min(0.1f)] private float castDistance = 5f;

        public GreenSurfaceSample Sample(Vector3 worldPosition)
        {
            var origin = worldPosition + Vector3.up * castHeight;
            var distance = castHeight + castDistance;

            if (Physics.Raycast(
                    origin,
                    Vector3.down,
                    out var hit,
                    distance,
                    greenMask,
                    QueryTriggerInteraction.Ignore))
            {
                return new GreenSurfaceSample(true, hit.point, hit.normal);
            }

            // Safe fallback for scene bring-up. Production scenes should always hit a
            // collider assigned to the green layer and should treat misses as diagnostics.
            var fallbackPoint = new Vector3(worldPosition.x, 0f, worldPosition.z);
            return new GreenSurfaceSample(false, fallbackPoint, Vector3.up);
        }
    }
}

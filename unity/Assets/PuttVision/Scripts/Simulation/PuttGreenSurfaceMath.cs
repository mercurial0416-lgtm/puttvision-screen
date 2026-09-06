using PuttVision.Telemetry;
using UnityEngine;

namespace PuttVision.Simulation
{
    /// <summary>
    /// Visual-only port of GreenSurface.kt / GreenTerrain.effectiveHeightAt for the 24 built-in
    /// practice profiles. Native V135/V136 physics remains authoritative.
    ///
    /// User-authored/custom greens are intentionally not guessed here; those will use sampled
    /// native surface data so rendered geometry cannot drift from physics.
    /// </summary>
    public static class PuttGreenSurfaceMath
    {
        public static float EffectiveHeightAt(PuttTelemetry shot, float xM, float yM)
        {
            if (shot == null)
                return 0f;

            return HeightAt(shot.terrainProfileId, xM, yM, shot.holeDistanceM)
                   - 0.01f * shot.sideSlopePct * xM
                   - 0.01f * shot.longSlopePct * yM;
        }

        public static float HeightAt(int profileId, float xM, float yM, float holeDistanceM)
        {
            if (profileId < 0)
                return 0f;

            var d = Mathf.Max(holeDistanceM, 2f);
            var lateral = Mathf.Max(d * 0.24f, 0.75f);
            var t = Mathf.Clamp(yM / d, -0.25f, 1.50f);
            var u = Mathf.Clamp(xM / lateral, -2f, 2f);
            var p = Mathf.Clamp(profileId, 0, 23);

            float SidePlane(float sidePct) => -0.01f * sidePct * xM;
            float LongPlane(float longPct) => -0.01f * longPct * yM;

            float Bowl(float ampM, float cx = 0f, float cy = 0.56f, float wx = 1f, float wy = 1f)
            {
                var dx = (u - cx) / Mathf.Max(wx, 0.2f);
                var dy = (t - cy) / Mathf.Max(wy, 0.2f);
                return ampM * (dx * dx + dy * dy);
            }

            float Crown(float ampM, float cx = 0f, float cy = 0.50f, float wx = 1f, float wy = 1f) =>
                -Bowl(ampM, cx, cy, wx, wy);

            float GaussianRidge(float ampM, float centerU, float width)
            {
                var z = (u - centerU) / Mathf.Max(width, 0.15f);
                return ampM * Mathf.Exp(-z * z);
            }

            var pi = Mathf.PI;
            switch (p)
            {
                // EASY
                case 0:
                    return 0.0008f * Mathf.Sin(t * pi * 2f) + 0.0004f * Mathf.Cos(u * pi);
                case 1:
                    return SidePlane(0.34f + 0.16f * Mathf.Sin(t * pi)) + 0.0005f * Mathf.Sin(t * pi * 2f);
                case 2:
                    return SidePlane(-0.34f - 0.16f * Mathf.Sin(t * pi)) + 0.0005f * Mathf.Sin(t * pi * 2f);
                case 3:
                    return LongPlane(-0.30f) + 0.0008f * Mathf.Sin(t * pi * 2f) + 0.00035f * Mathf.Sin(u * pi);
                case 4:
                    return LongPlane(0.30f) + 0.0008f * Mathf.Sin(t * pi * 2f) - 0.00035f * Mathf.Sin(u * pi);
                case 5:
                    return Bowl(0.0036f, cy: 0.58f, wx: 1.15f, wy: 0.72f) + 0.0004f * Mathf.Sin(t * pi * 2f);

                // STANDARD
                case 6:
                    return SidePlane(0.64f + 0.26f * Mathf.Sin((t - 0.08f) * pi)) + 0.0008f * Mathf.Sin(t * pi * 2f);
                case 7:
                    return SidePlane(-0.64f - 0.26f * Mathf.Sin((t - 0.08f) * pi)) + 0.0008f * Mathf.Sin(t * pi * 2f);
                case 8:
                    return SidePlane(0.56f + 0.22f * Mathf.Sin(t * pi)) + LongPlane(-0.52f) + 0.0010f * Mathf.Sin(t * pi * 2f);
                case 9:
                    return SidePlane(-0.56f - 0.22f * Mathf.Sin(t * pi)) + LongPlane(-0.52f) + 0.0010f * Mathf.Sin(t * pi * 2f);
                case 10:
                    return SidePlane(0.58f + 0.20f * Mathf.Sin(t * pi * 1.4f)) + LongPlane(0.52f) + 0.0010f * Mathf.Sin(t * pi * 2f);
                case 11:
                    return SidePlane(-0.58f - 0.20f * Mathf.Sin(t * pi * 1.4f)) + LongPlane(0.52f) + 0.0010f * Mathf.Sin(t * pi * 2f);

                // ADVANCED
                case 12:
                    return SidePlane(0.88f * Mathf.Sin((t - 0.10f) * pi * 2f)) + 0.0021f * Mathf.Cos(t * pi * 2f) + 0.0005f * u * t;
                case 13:
                    return SidePlane(-0.88f * Mathf.Sin((t - 0.10f) * pi * 2f)) + 0.0021f * Mathf.Cos(t * pi * 2f) - 0.0005f * u * t;
                case 14:
                    return Crown(0.0060f, cy: 0.48f, wx: 1.10f, wy: 0.78f) + 0.0007f * Mathf.Sin(t * pi * 2f);
                case 15:
                    return Bowl(0.0072f, cy: 0.56f, wx: 1.05f, wy: 0.72f) + 0.0008f * Mathf.Sin(t * pi * 2f);
                case 16:
                    return SidePlane(0.14f + 1.00f * t * t) + LongPlane(-0.12f) + 0.0007f * Mathf.Cos(t * pi * 2f);
                case 17:
                    return SidePlane(-0.14f - 1.00f * t * t) + LongPlane(-0.12f) + 0.0007f * Mathf.Cos(t * pi * 2f);

                // EXPERT
                case 18:
                    return GaussianRidge(0.0062f, 0.18f * Mathf.Sin(t * pi * 3f), 0.38f) + LongPlane(-0.20f) + 0.0014f * Mathf.Cos(t * pi * 2f);
                case 19:
                    return Bowl(0.0042f, cx: 0.42f * Mathf.Sin((t + 0.08f) * pi * 2.6f), cy: t, wx: 0.48f, wy: 8f) + 0.0018f * Mathf.Cos(t * pi * 3f);
                case 20:
                    return SidePlane(1.18f + 0.38f * Mathf.Sin((t - 0.08f) * pi)) + LongPlane(-1.02f) + 0.0018f * Mathf.Cos(t * pi * 2f);
                case 21:
                    return SidePlane(-1.18f - 0.38f * Mathf.Sin((t - 0.08f) * pi)) + LongPlane(1.02f) + 0.0018f * Mathf.Cos(t * pi * 2f);
                case 22:
                    return Crown(0.0064f, cy: 0.46f, wx: 0.90f, wy: 0.82f) + SidePlane(0.64f * Mathf.Sin(t * pi * 2f)) + 0.0010f * Mathf.Sin((u + t) * pi);
                default:
                    return SidePlane(-0.48f + 1.12f * Mathf.Sin(t * pi * 2.2f)) + LongPlane(-0.42f)
                           + 0.0030f * Mathf.Cos(t * pi * 2f)
                           + 0.0014f * Mathf.Sin((u * 0.8f + t * 1.4f) * pi);
            }
        }
    }
}

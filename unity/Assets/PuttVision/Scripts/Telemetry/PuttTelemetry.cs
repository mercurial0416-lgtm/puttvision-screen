using System;

namespace PuttVision.Telemetry
{
    [Serializable]
    public sealed class PuttTelemetry
    {
        public const int CurrentSchemaVersion = 1;

        public const int FaceAngleValid = 1 << 0;
        public const int PathAngleValid = 1 << 1;
        public const int ImpactOffsetValid = 1 << 2;
        public const int ConfidenceValid = 1 << 3;

        public int schemaVersion = CurrentSchemaVersion;
        public string shotId = string.Empty;
        public long timestampNs;
        public float ballSpeedMps;
        public float launchDirectionDeg;
        public float faceAngleDeg;
        public float pathAngleDeg;
        public float impactOffsetMm;
        public float confidence;
        public int validityFlags;

        // Native shot-static context. Unity renders these values; it does not reinterpret physics.
        public float startXM;
        public float startYM;
        public float holeDistanceM = 5f;
        public float stimpMeters = 2.8f;
        public float sideSlopePct;
        public float longSlopePct;
        public int terrainProfileId = -1;
        public bool flagstickIn;
        public float grainDirectionDeg;
        public float grainStrength01;
        public float moisture01 = 0.5f;
        public float firmness01 = 0.5f;
        public float trueness01 = 1f;

        public bool HasFaceAngle => (validityFlags & FaceAngleValid) != 0;
        public bool HasPathAngle => (validityFlags & PathAngleValid) != 0;
        public bool HasImpactOffset => (validityFlags & ImpactOffsetValid) != 0;
        public bool HasConfidence => (validityFlags & ConfidenceValid) != 0;

        // Used only by the editor/local smoke simulation. Product motion follows PuttPhysicsFrame.
        public bool IsSimulationReady =>
            schemaVersion == CurrentSchemaVersion &&
            !float.IsNaN(ballSpeedMps) &&
            !float.IsInfinity(ballSpeedMps) &&
            ballSpeedMps >= 0f &&
            !float.IsNaN(launchDirectionDeg) &&
            !float.IsInfinity(launchDirectionDeg);
    }
}

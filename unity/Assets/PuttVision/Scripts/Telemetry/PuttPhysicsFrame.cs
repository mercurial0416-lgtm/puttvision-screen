using System;

namespace PuttVision.Telemetry
{
    [Serializable]
    public sealed class PuttPhysicsFrame
    {
        public const int CurrentSchemaVersion = 1;

        public int schemaVersion = CurrentSchemaVersion;
        public float elapsedSec;
        public float xM;
        public float yM;
        public float centerZM;
        public float vxMps;
        public float vyMps;
        public float vzMps;

        public float orientationW = 1f;
        public float orientationX;
        public float orientationY;
        public float orientationZ;

        public float surfaceNormalX;
        public float surfaceNormalY;
        public float surfaceNormalZ = 1f;
        public float slipSpeedMps;
        public bool airborne;

        public bool running;
        public bool holed;
        public bool lipOut;
        public string cupPhase = "NONE";
        public int cupContacts;
        public int cupWallContacts;
        public int cupBottomContacts;
        public int bridgeCount;
        public int flagstickContacts;

        // Treat the bridge as an external input boundary even though Android currently sanitizes
        // its numeric payloads. A future JNI/native transport, corrupted replay, or malformed test
        // frame must not be allowed to inject NaN/Infinity into transforms, camera tracking or HUD
        // interpolation where one bad value can poison presentation state for subsequent frames.
        public bool IsUsable =>
            schemaVersion == CurrentSchemaVersion &&
            IsFinite(elapsedSec) && elapsedSec >= 0f &&
            IsFinite(xM) &&
            IsFinite(yM) &&
            IsFinite(centerZM) &&
            IsFinite(vxMps) &&
            IsFinite(vyMps) &&
            IsFinite(vzMps) &&
            IsFinite(orientationW) &&
            IsFinite(orientationX) &&
            IsFinite(orientationY) &&
            IsFinite(orientationZ) &&
            IsFinite(surfaceNormalX) &&
            IsFinite(surfaceNormalY) &&
            IsFinite(surfaceNormalZ) &&
            IsFinite(slipSpeedMps) && slipSpeedMps >= 0f;

        private static bool IsFinite(float value) => !float.IsNaN(value) && !float.IsInfinity(value);
    }
}

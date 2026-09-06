using System;
using UnityEngine;

namespace PuttVision.Telemetry
{
    [Serializable]
    public sealed class PuttSurfaceGrid
    {
        public const int CurrentSchemaVersion = 1;

        public int schemaVersion = CurrentSchemaVersion;
        public int width;
        public int height;
        public float minXM;
        public float maxXM;
        public float minYM;
        public float maxYM;
        public string heightF32LeBase64 = string.Empty;

        [NonSerialized] private float[] _heights;

        public bool IsDecoded => _heights != null && _heights.Length == width * height;

        public bool TryDecode()
        {
            if (schemaVersion != CurrentSchemaVersion || width < 2 || height < 2 ||
                !IsFinite(minXM) || !IsFinite(maxXM) || !IsFinite(minYM) || !IsFinite(maxYM) ||
                maxXM <= minXM || maxYM <= minYM || string.IsNullOrEmpty(heightF32LeBase64))
                return false;

            byte[] bytes;
            try { bytes = Convert.FromBase64String(heightF32LeBase64); }
            catch { return false; }

            var count = width * height;
            if (bytes.Length != count * 4)
                return false;

            var values = new float[count];
            for (var i = 0; i < count; i++)
            {
                var offset = i * 4;
                var bits = bytes[offset]
                           | (bytes[offset + 1] << 8)
                           | (bytes[offset + 2] << 16)
                           | (bytes[offset + 3] << 24);
                var value = BitConverter.Int32BitsToSingle(bits);
                if (!IsFinite(value))
                    return false;
                values[i] = value;
            }

            _heights = values;
            return true;
        }

        public bool Covers(float xM, float yM) =>
            IsDecoded && xM >= minXM && xM <= maxXM && yM >= minYM && yM <= maxYM;

        public float Sample(float xM, float yM)
        {
            if (!IsDecoded)
                return 0f;

            var tx = Mathf.Clamp01((xM - minXM) / (maxXM - minXM)) * (width - 1);
            var ty = Mathf.Clamp01((yM - minYM) / (maxYM - minYM)) * (height - 1);
            var x0 = Mathf.FloorToInt(tx);
            var y0 = Mathf.FloorToInt(ty);
            var x1 = Mathf.Min(x0 + 1, width - 1);
            var y1 = Mathf.Min(y0 + 1, height - 1);
            var fx = tx - x0;
            var fy = ty - y0;

            var h00 = _heights[y0 * width + x0];
            var h10 = _heights[y0 * width + x1];
            var h01 = _heights[y1 * width + x0];
            var h11 = _heights[y1 * width + x1];
            return Mathf.Lerp(Mathf.Lerp(h00, h10, fx), Mathf.Lerp(h01, h11, fx), fy);
        }

        private static bool IsFinite(float value) => !float.IsNaN(value) && !float.IsInfinity(value);
    }
}

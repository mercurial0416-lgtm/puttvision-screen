using System.Collections.Generic;
using PuttVision.Telemetry;
using UnityEngine;

namespace PuttVision.Presentation
{
    [DisallowMultipleComponent]
    public sealed class PuttTrailPresenter : MonoBehaviour
    {
        [SerializeField, Min(32)] private int maxPoints = 2048;
        [SerializeField, Min(0.001f)] private float minimumPointSpacingM = 0.012f;
        [SerializeField, Min(0.001f)] private float lineWidthM = 0.012f;

        private readonly List<Vector3> _points = new List<Vector3>(512);
        private LineRenderer _line;
        private Material _runtimeMaterial;

        private void Awake()
        {
            _line = gameObject.AddComponent<LineRenderer>();
            _line.useWorldSpace = true;
            _line.widthMultiplier = lineWidthM;
            _line.numCapVertices = 4;
            _line.numCornerVertices = 2;
            _line.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            _line.receiveShadows = false;
            _line.textureMode = LineTextureMode.Stretch;

            var shader = Shader.Find("Universal Render Pipeline/Unlit") ?? Shader.Find("Sprites/Default");
            if (shader != null)
            {
                _runtimeMaterial = new Material(shader) { name = "PuttVision Trail Runtime" };
                if (_runtimeMaterial.HasProperty("_BaseColor"))
                    _runtimeMaterial.SetColor("_BaseColor", new Color(0.82f, 0.94f, 1f, 0.82f));
                else
                    _runtimeMaterial.color = new Color(0.82f, 0.94f, 1f, 0.82f);
                _line.sharedMaterial = _runtimeMaterial;
            }
            _line.positionCount = 0;
        }

        private void OnEnable()
        {
            PuttTelemetryReceiver.ShotReceived += OnShot;
            PuttPhysicsFrameReceiver.FrameReceived += OnFrame;
            PuttPhysicsFrameReceiver.RendererReset += Clear;
        }

        private void OnDisable()
        {
            PuttTelemetryReceiver.ShotReceived -= OnShot;
            PuttPhysicsFrameReceiver.FrameReceived -= OnFrame;
            PuttPhysicsFrameReceiver.RendererReset -= Clear;
        }

        private void OnDestroy()
        {
            if (_runtimeMaterial != null)
            {
                Destroy(_runtimeMaterial);
                _runtimeMaterial = null;
            }
        }

        private void OnShot(PuttTelemetry _) => Clear();

        private void OnFrame(PuttPhysicsFrame frame)
        {
            if (frame == null || !frame.IsUsable || _line == null) return;
            var point = new Vector3(frame.xM, Mathf.Max(0.004f, frame.centerZM * 0.22f), frame.yM);
            if (_points.Count > 0 && !ShouldAppendPoint(_points[_points.Count - 1], point, minimumPointSpacingM))
                return;

            if (_points.Count >= maxPoints)
            {
                _points.RemoveAt(0);
                _points.Add(point);
                _line.positionCount = _points.Count;
                for (var i = 0; i < _points.Count; i++) _line.SetPosition(i, _points[i]);
                return;
            }

            _points.Add(point);
            _line.positionCount = _points.Count;
            _line.SetPosition(_points.Count - 1, point);
        }

        internal static bool ShouldAppendPoint(Vector3 previous, Vector3 next, float minimumSpacingM)
        {
            var delta = next - previous;
            var threshold = Mathf.Max(0f, minimumSpacingM);
            return delta.sqrMagnitude >= threshold * threshold;
        }

        private void Clear()
        {
            _points.Clear();
            if (_line != null) _line.positionCount = 0;
        }

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void Install()
        {
            if (FindFirstObjectByType<PuttTrailPresenter>() == null)
                new GameObject("PuttTrailPresenter").AddComponent<PuttTrailPresenter>();
        }
    }
}

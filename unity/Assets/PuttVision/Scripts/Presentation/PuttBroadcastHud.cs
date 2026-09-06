using PuttVision.Telemetry;
using UnityEngine;
using UnityEngine.UI;

namespace PuttVision.Presentation
{
    [DisallowMultipleComponent]
    public sealed class PuttBroadcastHud : MonoBehaviour
    {
        private Text _headline;
        private Text _subhead;
        private Text _metrics;
        private Text _green;
        private Text _status;
        private PuttTelemetry _shot;
        private PuttPhysicsFrame _frame;

        private void Awake() => BuildUi();

        private void OnEnable()
        {
            PuttTelemetryReceiver.ShotReceived += OnShot;
            PuttPhysicsFrameReceiver.FrameReceived += OnFrame;
            PuttPhysicsFrameReceiver.RendererReset += OnReset;
        }

        private void OnDisable()
        {
            PuttTelemetryReceiver.ShotReceived -= OnShot;
            PuttPhysicsFrameReceiver.FrameReceived -= OnFrame;
            PuttPhysicsFrameReceiver.RendererReset -= OnReset;
        }

        private void OnShot(PuttTelemetry shot)
        {
            _shot = shot;
            _frame = null;
            Refresh();
        }

        private void OnFrame(PuttPhysicsFrame frame)
        {
            _frame = frame;
            Refresh();
        }

        private void OnReset()
        {
            _shot = null;
            _frame = null;
            Refresh();
        }

        private void Refresh()
        {
            if (_headline == null)
                return;

            if (_shot == null)
            {
                _headline.text = "PUTTVISION";
                _subhead.text = "READY";
                _metrics.text = "BALL  --     LAUNCH  --     FACE  --     PATH  --";
                _green.text = "GREEN  --";
                _status.text = "WAITING FOR SHOT";
                return;
            }

            var remaining = _shot.holeDistanceM;
            if (_frame != null)
            {
                var dx = _frame.xM;
                var dy = _frame.yM - _shot.holeDistanceM;
                remaining = Mathf.Sqrt(dx * dx + dy * dy);
            }

            _headline.text = $"{_shot.holeDistanceM:F1} m  PUTT";
            _subhead.text = $"REMAINING  {remaining:F2} m";
            _metrics.text =
                $"BALL  {_shot.ballSpeedMps:F2} m/s     " +
                $"LAUNCH  {Signed(_shot.launchDirectionDeg)}°     " +
                $"FACE  {Optional(_shot.HasFaceAngle, _shot.faceAngleDeg, "°")}     " +
                $"PATH  {Optional(_shot.HasPathAngle, _shot.pathAngleDeg, "°")}";
            _green.text =
                $"STIMP  {_shot.stimpMeters:F2} m     " +
                $"SIDE  {Signed(_shot.sideSlopePct)}%     " +
                $"LONG  {Signed(_shot.longSlopePct)}%     " +
                $"PROFILE  {(_shot.terrainProfileId < 0 ? "PLANE" : _shot.terrainProfileId.ToString("00"))}";

            if (_frame == null)
            {
                _status.text = "MEASURED";
            }
            else if (_frame.holed)
            {
                _status.text = "HOLED";
            }
            else if (_frame.lipOut)
            {
                _status.text = "LIP OUT";
            }
            else if (_frame.running)
            {
                _status.text = $"ROLLING  {_frame.elapsedSec:F1}s";
            }
            else
            {
                _status.text = $"STOPPED  {remaining:F2} m";
            }
        }

        private void BuildUi()
        {
            var canvasObject = new GameObject("Broadcast HUD Canvas");
            canvasObject.transform.SetParent(transform, false);
            var canvas = canvasObject.AddComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            canvas.sortingOrder = 200;

            var scaler = canvasObject.AddComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1920f, 1080f);
            scaler.matchWidthOrHeight = 0.5f;
            canvasObject.AddComponent<GraphicRaycaster>();

            var topPanel = Panel(canvasObject.transform, "Top Bar", new Vector2(0f, 0.82f), new Vector2(1f, 1f), new Color(0.015f, 0.022f, 0.028f, 0.90f));
            _headline = Label(topPanel, "Headline", 48, TextAnchor.UpperLeft, new Vector2(0.035f, 0.48f), new Vector2(0.45f, 0.95f));
            _subhead = Label(topPanel, "Remaining", 52, TextAnchor.UpperRight, new Vector2(0.48f, 0.45f), new Vector2(0.965f, 0.95f));
            _metrics = Label(topPanel, "Metrics", 28, TextAnchor.LowerLeft, new Vector2(0.035f, 0.08f), new Vector2(0.72f, 0.48f));
            _green = Label(topPanel, "Green", 25, TextAnchor.LowerRight, new Vector2(0.50f, 0.08f), new Vector2(0.965f, 0.46f));

            var statusPanel = Panel(canvasObject.transform, "Status", new Vector2(0.73f, 0.035f), new Vector2(0.965f, 0.13f), new Color(0.015f, 0.022f, 0.028f, 0.86f));
            _status = Label(statusPanel, "Status Text", 31, TextAnchor.MiddleCenter, Vector2.zero, Vector2.one);

            Refresh();
        }

        private static RectTransform Panel(Transform parent, string name, Vector2 anchorMin, Vector2 anchorMax, Color color)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            var image = go.AddComponent<Image>();
            image.color = color;
            var rect = image.rectTransform;
            rect.anchorMin = anchorMin;
            rect.anchorMax = anchorMax;
            rect.offsetMin = Vector2.zero;
            rect.offsetMax = Vector2.zero;
            return rect;
        }

        private static Text Label(Transform parent, string name, int size, TextAnchor alignment, Vector2 anchorMin, Vector2 anchorMax)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            var text = go.AddComponent<Text>();
            text.font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            text.fontSize = size;
            text.alignment = alignment;
            text.color = new Color(0.94f, 0.96f, 0.97f, 1f);
            text.horizontalOverflow = HorizontalWrapMode.Overflow;
            text.verticalOverflow = VerticalWrapMode.Overflow;
            var rect = text.rectTransform;
            rect.anchorMin = anchorMin;
            rect.anchorMax = anchorMax;
            rect.offsetMin = Vector2.zero;
            rect.offsetMax = Vector2.zero;
            return text;
        }

        private static string Signed(float value) => value >= 0f ? $"+{value:F2}" : value.ToString("F2");

        private static string Optional(bool valid, float value, string suffix) => valid ? $"{Signed(value)}{suffix}" : "--";

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void Install()
        {
            if (FindFirstObjectByType<PuttBroadcastHud>() != null)
                return;
            new GameObject("PuttBroadcastHud").AddComponent<PuttBroadcastHud>();
        }
    }
}

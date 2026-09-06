using System;
using System.Collections.Generic;
using PuttVision.Simulation;
using PuttVision.Telemetry;
using UnityEngine;

namespace PuttVision.Presentation
{
    [DisallowMultipleComponent]
    public sealed class PuttReplayController : MonoBehaviour
    {
        public static event Action ReplayStarted;
        public static event Action ReplayEnded;

        [SerializeField] private Transform ballVisual;
        [SerializeField] private bool autoReplay = true;
        [SerializeField, Range(0.25f, 2f)] private float replaySpeed = 0.72f;
        [SerializeField, Min(0f)] private float autoReplayDelaySec = 1.15f;
        [SerializeField, Min(120)] private int maxRecordedFrames = 7200;

        private readonly List<PuttPhysicsFrame> _frames = new List<PuttPhysicsFrame>(1024);
        private PuttAuthoritativeBallPresenter _livePresenter;
        private float _countdown = -1f;
        private float _replayClock;
        private int _replayIndex;
        private int _frameStart;
        private bool _replaying;

        public bool IsReplaying => _replaying;
        public int RecordedFrameCount => _frames.Count - _frameStart;

        private void Awake()
        {
            if (ballVisual == null)
            {
                var ball = GameObject.Find("Ball");
                if (ball != null) ballVisual = ball.transform;
            }
            if (ballVisual != null)
                _livePresenter = ballVisual.GetComponent<PuttAuthoritativeBallPresenter>();
        }

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
            if (_livePresenter != null) _livePresenter.enabled = true;
        }

        private void Update()
        {
            if (_countdown >= 0f && !_replaying)
            {
                _countdown -= Time.unscaledDeltaTime;
                if (_countdown <= 0f) StartReplay();
            }
            if (!_replaying || RecordedFrameCount == 0 || ballVisual == null) return;

            _replayClock += Time.unscaledDeltaTime * replaySpeed;
            var endElapsed = FrameAt(RecordedFrameCount - 1).elapsedSec;
            while (_replayIndex + 1 < RecordedFrameCount && FrameAt(_replayIndex + 1).elapsedSec <= _replayClock)
                _replayIndex++;
            Apply(FrameAt(_replayIndex));
            if (_replayClock >= endElapsed) StopReplay(true);
        }

        public void StartReplay()
        {
            if (RecordedFrameCount < 2 || ballVisual == null) return;
            _countdown = -1f;
            _replaying = true;
            if (_livePresenter != null) _livePresenter.enabled = false;
            _replayIndex = 0;
            _replayClock = FrameAt(0).elapsedSec;
            Apply(FrameAt(0));
            ReplayStarted?.Invoke();
        }

        public void StopReplay(bool restoreFinalFrame = true)
        {
            if (!_replaying) return;
            _replaying = false;
            _countdown = -1f;
            if (restoreFinalFrame && RecordedFrameCount > 0) Apply(FrameAt(RecordedFrameCount - 1));
            if (_livePresenter != null) _livePresenter.enabled = true;
            ReplayEnded?.Invoke();
        }

        private void OnShot(PuttTelemetry _)
        {
            if (_replaying) StopReplay(false);
            ClearFrames();
            _countdown = -1f;
        }

        private void OnFrame(PuttPhysicsFrame frame)
        {
            if (frame == null || !frame.IsUsable || _replaying) return;
            AppendFrame(frame);
            if (autoReplay && !frame.running && RecordedFrameCount > 1 && _countdown < 0f)
                _countdown = autoReplayDelaySec;
        }

        private void OnReset()
        {
            if (_replaying) StopReplay(false);
            ClearFrames();
            _countdown = -1f;
        }

        private void AppendFrame(PuttPhysicsFrame frame)
        {
            _frames.Add(frame);
            var capacity = Mathf.Max(1, maxRecordedFrames);
            if (RecordedFrameCount > capacity)
                _frameStart++;

            // Avoid List.RemoveAt(0) on every HFR frame once the replay buffer fills.
            // Compact stale prefixes only occasionally so trimming is amortized O(1).
            if (_frameStart >= capacity && _frameStart >= 1024)
            {
                _frames.RemoveRange(0, _frameStart);
                _frameStart = 0;
            }
        }

        private PuttPhysicsFrame FrameAt(int logicalIndex)
        {
            return _frames[_frameStart + logicalIndex];
        }

        private void ClearFrames()
        {
            _frames.Clear();
            _frameStart = 0;
        }

        private void Apply(PuttPhysicsFrame frame)
        {
            var position = new Vector3(frame.xM, frame.centerZM, frame.yM);
            ballVisual.SetPositionAndRotation(position, PuttAuthoritativeBallPresenter.NativeQuaternionToUnity(frame));
        }

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void Install()
        {
            if (FindFirstObjectByType<PuttReplayController>() == null)
                new GameObject("PuttReplayController").AddComponent<PuttReplayController>();
        }
    }
}

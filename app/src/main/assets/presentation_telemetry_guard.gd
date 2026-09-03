extends "res://replay_timeline_camera_truth.gd"

# Presentation-only boundary guard. Native Android physics, GreenTerrain and GreenReadAdvisor
# remain authoritative; this only prevents malformed bridge values from reaching Godot presentation.
const PRESENTATION_SPATIAL_KEYS := ["ballX", "ballY", "holeDistance"]

var _presentation_last_spatial := {}

func _presentation_metrics_are_finite(s: Dictionary) -> bool:
    if not s.has("readLineDeltaCm") or not s.has("paceDeltaCm"):
        return true
    return is_finite(float(s.get("readLineDeltaCm", 0.0))) and is_finite(float(s.get("paceDeltaCm", 0.0)))

func _presentation_safe_snapshot(s: Dictionary) -> Dictionary:
    var safe := s
    var copied := false

    if not _presentation_metrics_are_finite(s):
        safe = s.duplicate(false)
        copied = true
        safe.erase("readLineDeltaCm")
        safe.erase("paceDeltaCm")

    # A malformed position sample must never poison Node3D transforms, terrain sampling, replay
    # cameras or the aim line. Reuse the last finite presentation coordinate when possible so a
    # single bad bridge packet does not visibly snap the ball/cup back to an arbitrary default.
    # This cache is presentation-only and never flows back into native physics or read advice.
    for key in PRESENTATION_SPATIAL_KEYS:
        if not s.has(key):
            continue
        var value := float(s.get(key, 0.0))
        if is_finite(value):
            _presentation_last_spatial[key] = value
            continue
        if not copied:
            safe = s.duplicate(false)
            copied = true
        if _presentation_last_spatial.has(key):
            safe[key] = float(_presentation_last_spatial[key])
        else:
            safe.erase(key)

    return safe

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(_presentation_safe_snapshot(s), immediate, delta)

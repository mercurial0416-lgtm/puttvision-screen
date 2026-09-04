extends "res://replay_stop_distance_clarity.gd"

# Presentation-only boundary guard. Native Android physics, GreenTerrain and GreenReadAdvisor
# remain authoritative; this only prevents malformed bridge values from reaching Godot presentation.
const PRESENTATION_SPATIAL_KEYS := ["ballX", "ballY", "holeDistance"]
const PRESENTATION_BALL_KEYS := ["ballX", "ballY"]

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

    # Ball coordinates are a semantic pair. If either value is malformed, erase both from the
    # presentation copy instead of replacing them with cached values. The inherited replay truth
    # layer already holds the last measured transform for missing coordinates while preserving the
    # missing-position signal, so live-break telemetry can correctly report TRACKING/LAST OBS rather
    # than manufacturing a fresh sample or exact REST from a stale cached position.
    var malformed_ball_pair := false
    for key in PRESENTATION_BALL_KEYS:
        if s.has(key) and not is_finite(float(s.get(key, 0.0))):
            malformed_ball_pair = true
            break
    if malformed_ball_pair:
        if not copied:
            safe = s.duplicate(false)
            copied = true
        safe.erase("ballX")
        safe.erase("ballY")

    # Non-ball spatial values can safely reuse their last finite presentation coordinate. This cache
    # never flows back into native physics or read advice.
    for key in PRESENTATION_SPATIAL_KEYS:
        if key in PRESENTATION_BALL_KEYS:
            continue
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

extends "res://replay_stop_distance_clarity.gd"

# Presentation-only boundary guard. Native Android physics, GreenTerrain and GreenReadAdvisor
# remain authoritative; this only prevents malformed bridge values from reaching Godot presentation.
const PRESENTATION_SPATIAL_KEYS := ["ballX", "ballY", "holeDistance"]
const PRESENTATION_BALL_KEYS := ["ballX", "ballY"]

var _presentation_last_spatial := {}

func _presentation_is_finite_number(value: Variant) -> bool:
    var value_type := typeof(value)
    return (value_type == TYPE_INT or value_type == TYPE_FLOAT) and is_finite(float(value))

func _presentation_metrics_are_finite(s: Dictionary) -> bool:
    if not s.has("readLineDeltaCm") or not s.has("paceDeltaCm"):
        return true
    return _presentation_is_finite_number(s.get("readLineDeltaCm")) and _presentation_is_finite_number(s.get("paceDeltaCm"))

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
        if s.has(key) and not _presentation_is_finite_number(s.get(key)):
            malformed_ball_pair = true
            break
    if malformed_ball_pair:
        if not copied:
            safe = s.duplicate(false)
            copied = true
        safe.erase("ballX")
        safe.erase("ballY")

    # Non-ball spatial values can safely reuse their last finite presentation coordinate. This cache
    # never flows back into native physics or read advice. Reject non-numeric bridge payloads before
    # any float coercion so malformed strings cannot silently become a plausible zero coordinate.
    for key in PRESENTATION_SPATIAL_KEYS:
        if key in PRESENTATION_BALL_KEYS:
            continue
        if not s.has(key):
            continue
        var raw_value: Variant = s.get(key)
        if _presentation_is_finite_number(raw_value):
            _presentation_last_spatial[key] = float(raw_value)
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

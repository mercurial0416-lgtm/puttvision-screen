extends "res://replay_timeline_camera_truth.gd"

# Presentation-only boundary guard. Native Android physics, GreenTerrain and GreenReadAdvisor
# remain authoritative; this only prevents malformed bridge coaching values from reaching HUDs.
func _presentation_metrics_are_finite(s: Dictionary) -> bool:
    if not s.has("readLineDeltaCm") or not s.has("paceDeltaCm"):
        return true
    return is_finite(float(s.get("readLineDeltaCm", 0.0))) and is_finite(float(s.get("paceDeltaCm", 0.0)))

func _presentation_safe_snapshot(s: Dictionary) -> Dictionary:
    if _presentation_metrics_are_finite(s):
        return s

    # Keep the original snapshot untouched. Physics already happened natively; inherited TV layers
    # receive a shallow duplicate with only the invalid coaching pair removed, so result/debrief,
    # session form and dispersion all stay neutral instead of manufacturing a plausible score.
    var safe := s.duplicate(false)
    safe.erase("readLineDeltaCm")
    safe.erase("paceDeltaCm")
    return safe

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(_presentation_safe_snapshot(s), immediate, delta)

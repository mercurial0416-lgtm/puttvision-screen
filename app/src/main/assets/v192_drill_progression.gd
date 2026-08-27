extends "res://v191_practice_streak.gd"

# Presentation-only adaptive drill progression. It turns the existing pressure ladder into an
# actionable next-distance cue without changing Android physics, GreenTerrain, GreenReadAdvisor,
# aiming, scoring, shot capture, or the active putting distance.

const V192_RESET_FAILURES := 3

func _v192_trailing_failures(axis: String) -> int:
    if axis == "BUILDING" or _v179_samples.is_empty():
        return 0
    var failures := 0
    for index in range(_v179_samples.size() - 1, -1, -1):
        if _v191_sample_in_window(_v179_samples[index], axis):
            break
        failures += 1
    return failures

func _v191_copy(streak: int, axis: String) -> String:
    if axis == "BUILDING":
        return "PRESSURE LADDER  ·  BUILDING"
    if streak >= V191_ADVANCE_STREAK:
        return "ADVANCE READY  ·  +0.5 m NEXT"
    if streak == 2:
        return "PRESSURE LADDER  ·  ONE MORE  ·  2/3"
    if streak == 1:
        return "PRESSURE LADDER  ·  HOLD IT  ·  1/3"
    if _v192_trailing_failures(axis) >= V192_RESET_FAILURES:
        return "START STREAK  ·  -0.5 m EASIER"
    return "START STREAK  ·  BUILD  ·  0/3"

func _v191_refresh() -> void:
    super._v191_refresh()
    if _v191_streak_label == null:
        return
    var metric := _v189_focus_metric()
    var spec := _v190_target_spec(metric)
    var axis := str(spec.get("axis", "BUILDING"))
    var reset_ready := _v191_streak == 0 and _v192_trailing_failures(axis) >= V192_RESET_FAILURES
    if reset_ready:
        _v191_streak_label.modulate = Color("#f0a56d")
        for segment in _v191_segments:
            segment.color = Color(0.94, 0.55, 0.34, 0.20)

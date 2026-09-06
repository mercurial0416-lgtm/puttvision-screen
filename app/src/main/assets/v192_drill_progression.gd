extends "res://v191_practice_streak.gd"

# Presentation-only adaptive drill progression. It turns the existing pressure ladder into an
# actionable next-distance cue without changing Android physics, GreenTerrain, GreenReadAdvisor,
# aiming, scoring, shot capture, or the active putting distance.

const V192_RESET_FAILURES := 3

func _v192_trailing_failures(axis: String) -> int:
    if axis == "BUILDING" or not _v191_has_focus_samples():
        return 0
    var failures := 0
    # Match the pressure-ladder focus window. A LINE -> PACE switch must not reinterpret misses that
    # happened before PACE became the active next-rep objective and falsely suggest an easier drill.
    for index in range(_v179_samples.size() - 1, _v191_focus_start_index - 1, -1):
        if _v191_sample_in_window(_v179_samples[index], axis):
            break
        failures += 1
    return failures

# v192 owns the final ladder copy because it adds distance progression. Keep the active objective
# attached to that richer copy too, otherwise the v191 TV-clarity contract is silently lost once
# this subclass is active in the production scene.
func _v191_copy(streak: int, axis: String) -> String:
    if axis == "BUILDING":
        return "PRESSURE LADDER  ·  BUILDING"
    # A newly-selected objective has no reps played against it yet. Do not present that clean slate
    # as a RESET or imply the player is already progressing toward an easier distance.
    if not _v191_has_focus_samples():
        return "PRESSURE LADDER  ·  %s  ·  0/%d  ·  START" % [axis, V191_ADVANCE_STREAK]
    var progress := "%d/%d" % [clampi(streak, 0, V191_ADVANCE_STREAK), V191_ADVANCE_STREAK]
    if streak >= V191_ADVANCE_STREAK:
        return "PRESSURE LADDER  ·  %s  ·  %s  ·  READY  ·  +0.5 m NEXT" % [axis, progress]
    if streak == 2:
        return "PRESSURE LADDER  ·  %s  ·  %s  ·  ONE MORE" % [axis, progress]
    if streak == 1:
        return "PRESSURE LADDER  ·  %s  ·  %s  ·  HOLD IT" % [axis, progress]

    var correction := _v191_reset_coaching(axis)
    # The ladder already tracks consecutive misses to decide when an easier distance should be
    # suggested. Surface that real progress instead of showing 0/3 after the first and second miss.
    # Clamp only the displayed count so an extended miss run remains truthful without noisy 4/3 copy.
    var failures := mini(_v192_trailing_failures(axis), V192_RESET_FAILURES)
    if failures >= V192_RESET_FAILURES:
        return "PRESSURE LADDER  ·  %s  ·  RESET  ·  %s  ·  %d/%d  ·  -0.5 m EASIER" % [axis, correction, failures, V192_RESET_FAILURES]
    # One or two misses are still a recoverable run, not a full reset. Calling them RESET made the
    # ladder feel punitive and overstated state transitions before the actual ease-distance gate.
    return "PRESSURE LADDER  ·  %s  ·  RECOVER  ·  %s  ·  %d/%d TO RESET" % [axis, correction, failures, V192_RESET_FAILURES]

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

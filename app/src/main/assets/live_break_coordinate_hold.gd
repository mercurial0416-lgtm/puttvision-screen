extends "res://replay_timeline_camera_truth.gd"

# Presentation-only bridge-gap protection for LIVE BREAK.
# Some bridge frames can carry running/velocity without ballX/ballY. The inherited telemetry layer
# historically interpreted missing coordinates as (0, 0), which can fabricate cross-track break,
# travel distance, and trace geometry. Hold the last real presentation coordinate when one exists;
# before the first real coordinate, keep the card neutral. Authoritative physics/read/scoring state
# remains untouched.

func _live_break_neutralize_missing_position() -> void:
    _live_curve_peak_cm = 0.0
    _live_curve_peak_signed_cm = 0.0
    _live_curve_history.clear()
    _live_curve_distance_history.clear()
    _live_curve_travel_m = 0.0
    _live_curve_last_ball_pos = Vector2.ZERO
    _live_curve_has_ball_pos = false
    _live_curve_last_trace_pos = Vector2.ZERO
    _live_curve_has_trace_pos = false
    if _live_curve_trace != null:
        _live_curve_trace.clear_points()
    if _live_curve_value != null:
        _live_curve_value.text = "TRACKING"
    if _live_curve_peak_label != null:
        _live_curve_peak_label.text = "PEAK --"
    if _live_curve_pace_label != null:
        _live_curve_pace_label.text = "PACE --"

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    var running := bool(s.get("running", false))
    var missing_ball_position := running and (not s.has("ballX") or not s.has("ballY"))
    var had_real_ball_position := _live_curve_has_ball_pos
    var presentation_snapshot := s

    # Mid-roll bridge gaps should freeze presentation at the last real coordinate instead of
    # manufacturing a jump to the world origin. A duplicate keeps the incoming bridge dictionary
    # immutable for every authoritative consumer outside this presentation chain.
    if missing_ball_position and had_real_ball_position:
        presentation_snapshot = s.duplicate()
        presentation_snapshot["ballX"] = _live_curve_last_ball_pos.x
        presentation_snapshot["ballY"] = _live_curve_last_ball_pos.y

    super._apply_snapshot(presentation_snapshot, immediate, delta)

    # At launch there may be no prior real coordinate to hold. The inherited layer has already
    # established legitimate origin/launch-axis state, so clear only synthetic ball telemetry and
    # wait for the first genuine position sample.
    if missing_ball_position and not had_real_ball_position:
        _live_break_neutralize_missing_position()

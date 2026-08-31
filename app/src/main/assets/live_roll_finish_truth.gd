extends "res://replay_timeline_camera_truth.gd"

# Presentation-only roll-finish truth layer.
# The bridge's final stopped snapshot can arrive after the last `running=true` sample. Without
# consuming that terminal position, ROLL SUMMARY can display the previous frame's break/travel
# instead of the actual resting ball position. This layer finalizes telemetry from that stopped
# snapshot only; Android V135-V137, GreenTerrain and GreenReadAdvisor remain authoritative.

const LIVE_FINISH_DISTANCE_EPS_M := 0.0005

func _live_finish_readout(cross_track_cm: float) -> String:
    if absf(cross_track_cm) < 0.05:
        return "REST CENTER"
    return "REST %s %.1f cm" % ["R" if cross_track_cm > 0.0 else "L", absf(cross_track_cm)]

func _finalize_live_roll_truth(s: Dictionary) -> void:
    if not s.has("ballX") or not s.has("ballY"):
        return

    var ball_pos := Vector2(float(s.get("ballX", 0.0)), float(s.get("ballY", 0.0)))
    var launch_right := Vector2(_live_curve_forward.y, -_live_curve_forward.x)
    var cross_track_cm := (ball_pos - _live_curve_origin).dot(launch_right) * 100.0
    if absf(cross_track_cm) > _live_curve_peak_cm:
        _live_curve_peak_cm = absf(cross_track_cm)
        _live_curve_peak_signed_cm = cross_track_cm

    var previous_travel := _live_curve_travel_m
    var traveled_m := _live_trace_accumulate_travel(ball_pos)
    var terminal_segment_added := traveled_m - previous_travel > LIVE_FINISH_DISTANCE_EPS_M
    if terminal_segment_added or _live_curve_history.is_empty():
        _live_trace_push(cross_track_cm, traveled_m)

    if _live_curve_value != null:
        _live_curve_value.text = _live_finish_readout(cross_track_cm)
    if _live_curve_peak_label != null:
        _live_curve_peak_label.text = _live_peak_readout(_live_curve_peak_signed_cm)
    if _live_curve_pace_label != null:
        _live_curve_pace_label.text = _live_summary_pace_readout()

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    var was_running := _live_curve_was_running
    var running := bool(s.get("running", false))
    super._apply_snapshot(s, immediate, delta)
    if was_running and not running:
        _finalize_live_roll_truth(s)

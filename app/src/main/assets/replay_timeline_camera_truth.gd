extends "res://green_read_direction_truth.gd"

# Presentation-only replay timeline truth layer.
# The camera choreography owns the cup-focus thresholds; the timeline reads those same inherited
# values so its TRAIL / BLEND / CUP chapters cannot silently drift from the actual replay camera.
# Android V135-V137, GreenTerrain and GreenReadAdvisor remain authoritative and untouched.

const LIVE_FINISH_DISTANCE_EPS_M := 0.0005

func _focus_replay_stage(progress: float) -> String:
    var p := clampf(progress, 0.0, 1.0)
    if p < V180_FOCUS_START:
        return "TRAIL CAM"
    if p < V180_FOCUS_FULL:
        return "CAM BLEND"
    return "CUP CAM"

func _focus_update_replay_timeline() -> void:
    if _focus_replay_track == null or _focus_replay_fill == null:
        return
    var progress := _focus_replay_progress(_v171_replay_remaining, _v171_replay_duration)
    var track_width := maxf(0.0, _focus_replay_track.size.x)
    var blend_left := track_width * V180_FOCUS_START
    var blend_right := track_width * V180_FOCUS_FULL
    if _focus_replay_blend_range != null:
        _focus_replay_blend_range.position = Vector2(blend_left, 0.0)
        _focus_replay_blend_range.size = Vector2(maxf(0.0, blend_right - blend_left), REPLAY_TIMELINE_TRACK_HEIGHT)
    _focus_replay_fill.size = Vector2(track_width * progress, REPLAY_TIMELINE_TRACK_HEIGHT)
    if _focus_replay_chapter_marker != null:
        _focus_replay_chapter_marker.position = Vector2(maxf(0.0, blend_left - 1.0), -4.0)
    if _focus_replay_chapter_end_marker != null:
        _focus_replay_chapter_end_marker.position = Vector2(maxf(0.0, blend_right - 1.0), -4.0)
    if _focus_replay_stage_label != null:
        _focus_replay_stage_label.text = _focus_replay_status(progress, _v171_replay_remaining)

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

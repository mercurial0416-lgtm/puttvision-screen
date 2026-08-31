extends "res://green_read_direction_truth.gd"

# Presentation-only replay timeline truth layer.
# The camera choreography owns the cup-focus thresholds; the timeline reads those same inherited
# values so its TRAIL / BLEND / CUP chapters cannot silently drift from the actual replay camera.
# Android V135-V137, GreenTerrain and GreenReadAdvisor remain authoritative and untouched.

const LIVE_FINISH_DISTANCE_EPS_M := 0.0005
const LIVE_LAUNCH_LOCK_MIN_SPEED_MPS := 0.03
const SESSION_HISTORY_DOT_COLOR := Color("#76d7b6")
const SESSION_LATEST_DOT_COLOR := Color("#f4dda0")

var _live_launch_lock_pending := false

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

func _live_launch_velocity(s: Dictionary) -> Vector2:
    return Vector2(float(s.get("vx", 0.0)), float(s.get("vy", 0.0)))

func _live_launch_velocity_is_trustworthy(velocity: Vector2) -> bool:
    return is_finite(velocity.x) and is_finite(velocity.y) and velocity.length_squared() >= LIVE_LAUNCH_LOCK_MIN_SPEED_MPS * LIVE_LAUNCH_LOCK_MIN_SPEED_MPS

func _suppress_unlocked_live_break() -> void:
    # A bridge can publish running=true one frame before launch velocity is populated. Do not let
    # that transient frame establish Vector2.UP as the shot axis and poison left/right telemetry for
    # the entire roll. Keep the card neutral until a real launch vector arrives.
    _live_curve_peak_cm = 0.0
    _live_curve_peak_signed_cm = 0.0
    _live_curve_launch_speed = 0.0
    _live_curve_history.clear()
    _live_curve_distance_history.clear()
    _live_curve_has_trace_pos = false
    if _live_curve_trace != null:
        _live_curve_trace.clear_points()
    if _live_curve_value != null:
        _live_curve_value.text = "TRACKING"
    if _live_curve_peak_label != null:
        _live_curve_peak_label.text = "PEAK --"
    if _live_curve_pace_label != null:
        _live_curve_pace_label.text = "PACE --"

func _finalize_unlocked_live_break() -> void:
    # If a whole roll ends before any trustworthy launch vector arrives, inherited presentation
    # state may still contain the previous shot's axis/origin. Never turn that stale state into a
    # plausible-looking REST L/R result. Finish neutral instead; physics/read/scoring stay untouched.
    _live_curve_peak_cm = 0.0
    _live_curve_peak_signed_cm = 0.0
    _live_curve_launch_speed = 0.0
    _live_curve_history.clear()
    _live_curve_distance_history.clear()
    _live_curve_has_trace_pos = false
    if _live_curve_trace != null:
        _live_curve_trace.clear_points()
    if _live_curve_value != null:
        _live_curve_value.text = "REST --"
    if _live_curve_peak_label != null:
        _live_curve_peak_label.text = "PEAK --"
    if _live_curve_pace_label != null:
        _live_curve_pace_label.text = "PACE --"

func _relock_live_break_launch(s: Dictionary, velocity: Vector2) -> void:
    # Rebuild presentation telemetry from the first trustworthy launch vector while preserving the
    # already accumulated roll distance. This only repairs HUD orientation; no physics/read/scoring
    # state is modified.
    # Delayed velocity frames are not guaranteed to repeat startX/startY. Preserve the origin that
    # the inherited launch frame already established unless both coordinates are explicitly present;
    # otherwise defaulting missing values to zero would rotate a correct axis around a fake origin.
    if s.has("startX") and s.has("startY"):
        _live_curve_origin = Vector2(float(s.get("startX", 0.0)), float(s.get("startY", 0.0)))
    _live_curve_forward = velocity.normalized()
    _live_curve_launch_speed = velocity.length()
    _live_curve_peak_cm = 0.0
    _live_curve_peak_signed_cm = 0.0
    _live_curve_history.clear()
    _live_curve_distance_history.clear()
    _live_curve_has_trace_pos = false
    if _live_curve_trace != null:
        _live_curve_trace.clear_points()

    var ball_pos := Vector2(float(s.get("ballX", 0.0)), float(s.get("ballY", 0.0)))
    var launch_right := Vector2(_live_curve_forward.y, -_live_curve_forward.x)
    var cross_track_cm := (ball_pos - _live_curve_origin).dot(launch_right) * 100.0
    _live_curve_peak_cm = absf(cross_track_cm)
    _live_curve_peak_signed_cm = cross_track_cm
    _live_trace_push(cross_track_cm, _live_curve_travel_m)
    _live_curve_last_trace_pos = ball_pos
    _live_curve_has_trace_pos = true
    if _live_curve_value != null:
        _live_curve_value.text = _live_curve_readout(cross_track_cm)
    if _live_curve_peak_label != null:
        _live_curve_peak_label.text = _live_peak_readout(_live_curve_peak_signed_cm)
    if _live_curve_pace_label != null:
        _live_curve_pace_label.text = _live_pace_readout(velocity.length(), _live_curve_launch_speed)

func _finalize_live_roll_truth(s: Dictionary) -> void:
    if not s.has("ballX") or not s.has("ballY"):
        # A terminal bridge frame can legitimately omit coordinates after the running frame. Leaving
        # the last live value on screen would misrepresent an in-flight sample as the final rest.
        # Finish neutral instead of fabricating a terminal result from stale presentation state.
        _finalize_unlocked_live_break()
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

func _v179_refresh() -> void:
    super._v179_refresh()
    # The base map preallocates all five history dots and used to color only slot five gold.
    # During reps 1-4 that slot is invisible, so the current shot had no emphasis at all. Keep the
    # established production inheritance chain and recolor only the visible tail after refresh.
    var visible_count := mini(_v179_samples.size(), _v179_points.size())
    var latest_index := visible_count - 1
    for index in range(_v179_points.size()):
        var dot := _v179_points[index]
        if dot == null:
            continue
        dot.color = SESSION_LATEST_DOT_COLOR if index == latest_index and latest_index >= 0 else SESSION_HISTORY_DOT_COLOR

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    var was_running := _live_curve_was_running
    var running := bool(s.get("running", false))
    var launch_velocity := _live_launch_velocity(s)
    var launch_lock_was_pending := _live_launch_lock_pending
    super._apply_snapshot(s, immediate, delta)

    if running and not was_running:
        _live_launch_lock_pending = not _live_launch_velocity_is_trustworthy(launch_velocity)
        if _live_launch_lock_pending:
            _suppress_unlocked_live_break()
    elif running and _live_launch_lock_pending:
        if _live_launch_velocity_is_trustworthy(launch_velocity):
            _relock_live_break_launch(s, launch_velocity)
            _live_launch_lock_pending = false
        else:
            _suppress_unlocked_live_break()
    elif not running:
        _live_launch_lock_pending = false

    if was_running and not running:
        if launch_lock_was_pending:
            _finalize_unlocked_live_break()
        else:
            _finalize_live_roll_truth(s)

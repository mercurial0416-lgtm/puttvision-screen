extends "res://commercial_read_apex.gd"

# Presentation-only directional cues derived from the existing recommended read path.
# These never feed values back into Android physics, GreenTerrain, GreenReadAdvisor, aiming, or scoring.

const READ_FLOW_FRACTIONS := [0.40, 0.60, 0.78]
const READ_FLOW_TIP_LENGTH := 4.8
const READ_FLOW_WING_HALF_WIDTH := 3.4
const LIVE_TRACE_MAX_POINTS := 28
const LIVE_TRACE_SAMPLE_STEP_M := 0.02
const LIVE_TRACE_LEFT := 20.0
const LIVE_TRACE_RIGHT := 476.0
const LIVE_TRACE_CENTER_Y := 81.0
const LIVE_TRACE_AMPLITUDE := 6.0
const LIVE_SUMMARY_HOLD_SECONDS := 2.4

var _read_flow_cues: Array[Line2D] = []
var _live_curve_was_running := false
var _live_curve_origin := Vector2.ZERO
var _live_curve_forward := Vector2.UP
var _live_curve_panel: Panel
var _live_curve_title: Label
var _live_curve_value: Label
var _live_curve_peak_label: Label
var _live_curve_pace_label: Label
var _live_curve_peak_cm := 0.0
var _live_curve_peak_signed_cm := 0.0
var _live_curve_launch_speed := 0.0
var _live_curve_trace: Line2D
var _live_curve_zero: Line2D
var _live_curve_summary_timer: Timer
var _live_curve_history := PackedFloat32Array()
var _live_curve_distance_history := PackedFloat32Array()
var _live_curve_travel_m := 0.0
var _live_curve_last_ball_pos := Vector2.ZERO
var _live_curve_has_ball_pos := false
var _live_curve_last_trace_pos := Vector2.ZERO
var _live_curve_has_trace_pos := false

func _read_flow_geometry(offset_m: float, fraction: float) -> Dictionary:
    var curve := _v183_path(offset_m)
    if curve.size() < 3:
        var fallback := V183_MAP_ORIGIN + V183_MAP_SIZE * Vector2(0.5, 0.5)
        return {
            "center": fallback,
            "tip": fallback + Vector2.UP * READ_FLOW_TIP_LENGTH,
            "left": fallback + Vector2.DOWN * READ_FLOW_TIP_LENGTH + Vector2.LEFT * READ_FLOW_WING_HALF_WIDTH,
            "right": fallback + Vector2.DOWN * READ_FLOW_TIP_LENGTH + Vector2.RIGHT * READ_FLOW_WING_HALF_WIDTH,
            "tangent": Vector2.UP
        }
    var index := clampi(int(round(float(curve.size() - 1) * clampf(fraction, 0.05, 0.95))), 1, curve.size() - 2)
    var center: Vector2 = curve[index]
    var tangent := (curve[index + 1] - curve[index - 1]).normalized()
    if tangent.length_squared() < 0.5:
        tangent = Vector2.UP
    var normal := Vector2(-tangent.y, tangent.x)
    var base := center - tangent * READ_FLOW_TIP_LENGTH
    return {
        "center": center,
        "tip": center + tangent * READ_FLOW_TIP_LENGTH,
        "left": base + normal * READ_FLOW_WING_HALF_WIDTH,
        "right": base - normal * READ_FLOW_WING_HALF_WIDTH,
        "tangent": tangent
    }

func _build_hud() -> void:
    super._build_hud()
    if _v183_panel != null:
        for i in range(READ_FLOW_FRACTIONS.size()):
            var cue := Line2D.new()
            cue.name = "CommercialReadFlowCue%d" % (i + 1)
            cue.width = 1.8
            cue.default_color = Color(0.72, 0.94, 1.0, 0.74 - float(i) * 0.08)
            cue.joint_mode = Line2D.LINE_JOINT_ROUND
            cue.begin_cap_mode = Line2D.LINE_CAP_ROUND
            cue.end_cap_mode = Line2D.LINE_CAP_ROUND
            _v183_panel.add_child(cue)
            _read_flow_cues.append(cue)

    var layer := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if layer == null:
        return
    var root := layer.get_node_or_null("V174HUDRoot") as Control
    if root == null:
        return

    # Dedicated live-roll telemetry keeps the base GREEN READ card stable while the ball rolls.
    # It is derived only from bridge snapshots and is hidden outside the live shot, so Forward Mobile
    # pays no persistent drawing/animation cost and the authoritative solver remains untouched.
    _live_curve_panel = _v174_panel(root, Vector2(1392, 310), Vector2(498, 92), Color(0.014, 0.021, 0.026, 0.88), Color(0.45, 0.72, 0.82, 0.22), 13)
    _live_curve_panel.name = "LiveBreakMeter"
    _live_curve_panel.visible = false
    _v174_accent(_live_curve_panel, Vector2(0, 0), Vector2(6, 92), Color("#73c2d4"))
    _live_curve_title = _v174_text(_live_curve_panel, Vector2(20, 8), Vector2(170, 22), "LIVE BREAK", 13, Color("#bfe9f1"))
    _live_curve_title.name = "LiveBreakTitle"
    _live_curve_pace_label = _v174_text(_live_curve_panel, Vector2(190, 8), Vector2(286, 22), "PACE --", 12, Color(0.68, 0.82, 0.82, 0.92), HORIZONTAL_ALIGNMENT_RIGHT)
    _live_curve_pace_label.name = "LiveRollPace"
    _live_curve_value = _v174_text(_live_curve_panel, Vector2(20, 30), Vector2(250, 42), "CENTER", 24, Color("#f4f6f0"))
    _live_curve_value.name = "LiveBreakValue"
    _live_curve_peak_label = _v174_text(_live_curve_panel, Vector2(280, 30), Vector2(196, 42), "PEAK CENTER", 14, Color(0.74, 0.82, 0.82, 0.94), HORIZONTAL_ALIGNMENT_RIGHT)
    _live_curve_peak_label.name = "LiveBreakPeak"

    _live_curve_zero = Line2D.new()
    _live_curve_zero.name = "LiveBreakTraceZero"
    _live_curve_zero.width = 1.0
    _live_curve_zero.default_color = Color(0.48, 0.68, 0.72, 0.18)
    _live_curve_zero.points = PackedVector2Array([Vector2(LIVE_TRACE_LEFT, LIVE_TRACE_CENTER_Y), Vector2(LIVE_TRACE_RIGHT, LIVE_TRACE_CENTER_Y)])
    _live_curve_panel.add_child(_live_curve_zero)

    _live_curve_trace = Line2D.new()
    _live_curve_trace.name = "LiveBreakTrace"
    _live_curve_trace.width = 1.8
    _live_curve_trace.default_color = Color(0.45, 0.86, 0.92, 0.82)
    _live_curve_trace.joint_mode = Line2D.LINE_JOINT_ROUND
    _live_curve_trace.begin_cap_mode = Line2D.LINE_CAP_ROUND
    _live_curve_trace.end_cap_mode = Line2D.LINE_CAP_ROUND
    _live_curve_panel.add_child(_live_curve_trace)

    _live_curve_summary_timer = Timer.new()
    _live_curve_summary_timer.name = "LiveBreakSummaryHold"
    _live_curve_summary_timer.one_shot = true
    _live_curve_summary_timer.wait_time = LIVE_SUMMARY_HOLD_SECONDS
    _live_curve_summary_timer.timeout.connect(_hide_live_curve_summary)
    add_child(_live_curve_summary_timer)

func _refresh_read_flow() -> void:
    if _v183_panel == null:
        return
    var visible := _v183_panel.visible
    for i in range(_read_flow_cues.size()):
        var cue := _read_flow_cues[i]
        cue.visible = visible
        if not visible:
            continue
        var geometry := _read_flow_geometry(_v165_recommended_offset, READ_FLOW_FRACTIONS[i])
        cue.points = PackedVector2Array([geometry["left"], geometry["tip"], geometry["right"]])

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    super._v183_update(s, force_visible)
    _refresh_read_flow()

func _live_curve_readout(cross_track_cm: float) -> String:
    if absf(cross_track_cm) < 0.05:
        return "CENTER"
    return "%s %.1f cm" % ["R" if cross_track_cm > 0.0 else "L", absf(cross_track_cm)]

func _live_peak_readout(peak_signed_cm: float) -> String:
    if absf(peak_signed_cm) < 0.05:
        return "PEAK CENTER"
    return "PEAK %s %.1f cm" % ["R" if peak_signed_cm > 0.0 else "L", absf(peak_signed_cm)]

func _live_pace_readout(current_speed: float, launch_speed: float) -> String:
    if launch_speed <= 0.001:
        return "PACE --"
    var ratio := clampf(current_speed / launch_speed, 0.0, 1.0)
    var phase := "ROLLING"
    if ratio < 0.35:
        phase = "DYING"
    elif ratio < 0.72:
        phase = "SETTLING"
    return "PACE %d%% · %s" % [int(round(ratio * 100.0)), phase]

func _live_summary_pace_readout() -> String:
    return "ROLL COMPLETE · %.2f m" % _live_curve_travel_m

func _hide_live_curve_summary() -> void:
    if _live_curve_panel != null and not _live_curve_was_running:
        _live_curve_panel.visible = false

func _show_live_curve_summary() -> void:
    if _live_curve_panel == null:
        return
    _live_curve_panel.visible = true
    if _live_curve_title != null:
        _live_curve_title.text = "ROLL SUMMARY"
    if _live_curve_pace_label != null:
        _live_curve_pace_label.text = _live_summary_pace_readout()
    if _live_curve_summary_timer != null:
        _live_curve_summary_timer.start(LIVE_SUMMARY_HOLD_SECONDS)

func _live_trace_points_with_distance(history: PackedFloat32Array, distances: PackedFloat32Array) -> PackedVector2Array:
    var points := PackedVector2Array()
    if history.is_empty():
        return points
    var peak := 5.0
    for value in history:
        peak = maxf(peak, absf(value))
    var count := history.size()
    var use_distance_axis := distances.size() == count and count > 1
    var first_distance := distances[0] if use_distance_axis else 0.0
    var distance_span := maxf(LIVE_TRACE_SAMPLE_STEP_M, distances[count - 1] - first_distance) if use_distance_axis else 0.0
    for i in range(count):
        # A lone first sample represents the trace origin. Anchoring it at the left edge avoids the
        # one-frame right-edge pop that otherwise occurs before a second sample establishes a span.
        var t := 0.0 if count == 1 else float(i) / float(count - 1)
        if use_distance_axis:
            t = clampf((distances[i] - first_distance) / distance_span, 0.0, 1.0)
        var x := lerpf(LIVE_TRACE_LEFT, LIVE_TRACE_RIGHT, t)
        var y := LIVE_TRACE_CENTER_Y - clampf(history[i] / peak, -1.0, 1.0) * LIVE_TRACE_AMPLITUDE
        points.append(Vector2(x, y))
    return points

func _live_trace_points(history: PackedFloat32Array) -> PackedVector2Array:
    return _live_trace_points_with_distance(history, PackedFloat32Array())

func _live_trace_accumulate_travel(ball_pos: Vector2) -> float:
    if not _live_curve_has_ball_pos:
        _live_curve_last_ball_pos = ball_pos
        _live_curve_has_ball_pos = true
        return _live_curve_travel_m
    _live_curve_travel_m += ball_pos.distance_to(_live_curve_last_ball_pos)
    _live_curve_last_ball_pos = ball_pos
    return _live_curve_travel_m

func _live_trace_accept_sample(ball_pos: Vector2) -> bool:
    if not _live_curve_has_trace_pos:
        _live_curve_last_trace_pos = ball_pos
        _live_curve_has_trace_pos = true
        return true
    if ball_pos.distance_squared_to(_live_curve_last_trace_pos) < LIVE_TRACE_SAMPLE_STEP_M * LIVE_TRACE_SAMPLE_STEP_M:
        return false
    _live_curve_last_trace_pos = ball_pos
    return true

func _live_trace_push(cross_track_cm: float, traveled_m: float = -1.0) -> void:
    if traveled_m < 0.0:
        traveled_m = 0.0 if _live_curve_distance_history.is_empty() else _live_curve_distance_history[_live_curve_distance_history.size() - 1] + LIVE_TRACE_SAMPLE_STEP_M
    _live_curve_history.append(cross_track_cm)
    _live_curve_distance_history.append(maxf(0.0, traveled_m))
    while _live_curve_history.size() > LIVE_TRACE_MAX_POINTS:
        _live_curve_history.remove_at(0)
        _live_curve_distance_history.remove_at(0)
    if _live_curve_trace != null:
        _live_curve_trace.points = _live_trace_points_with_distance(_live_curve_history, _live_curve_distance_history)

# Make the authoritative roll response obvious without touching physics. During a live roll the
# dedicated meter reports current/peak cross-track curve, speed decay, and a bounded mini trace of
# the curve history from bridge snapshots. Trace points stay distance-gated for stable rendering,
# while their horizontal axis accumulates every bridge-frame path segment so curved travel between
# accepted samples is not collapsed into a shorter chord. Peak telemetry preserves the side of the
# largest excursion so the player can distinguish left- and right-breaking rolls at a glance.
# When the roll stops, the final telemetry remains visible briefly as a read-only summary so the
# player can actually inspect the outcome before the HUD clears. Nothing feeds back into
# GreenTerrain, GreenReadAdvisor, scoring, aim, or physics.
func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    var running := bool(s.get("running", false))
    var velocity := Vector2(float(s.get("vx", 0.0)), float(s.get("vy", 0.0)))
    if running and not _live_curve_was_running:
        if _live_curve_summary_timer != null:
            _live_curve_summary_timer.stop()
        if _live_curve_title != null:
            _live_curve_title.text = "LIVE BREAK"
        _live_curve_origin = Vector2(float(s.get("startX", 0.0)), float(s.get("startY", 0.0)))
        _live_curve_forward = velocity.normalized() if velocity.length_squared() > 0.0001 else Vector2.UP
        _live_curve_peak_cm = 0.0
        _live_curve_peak_signed_cm = 0.0
        _live_curve_launch_speed = velocity.length()
        _live_curve_history.clear()
        _live_curve_distance_history.clear()
        _live_curve_travel_m = 0.0
        _live_curve_last_ball_pos = Vector2.ZERO
        _live_curve_has_ball_pos = false
        _live_curve_has_trace_pos = false
        if _live_curve_trace != null:
            _live_curve_trace.clear_points()

    if running:
        if _live_curve_panel != null:
            _live_curve_panel.visible = true
        var ball_pos := Vector2(float(s.get("ballX", 0.0)), float(s.get("ballY", 0.0)))
        var launch_right := Vector2(_live_curve_forward.y, -_live_curve_forward.x)
        var cross_track_cm := (ball_pos - _live_curve_origin).dot(launch_right) * 100.0
        if absf(cross_track_cm) > _live_curve_peak_cm:
            _live_curve_peak_cm = absf(cross_track_cm)
            _live_curve_peak_signed_cm = cross_track_cm
        var traveled_m := _live_trace_accumulate_travel(ball_pos)
        if _live_trace_accept_sample(ball_pos):
            _live_trace_push(cross_track_cm, traveled_m)
        if _live_curve_value != null:
            _live_curve_value.text = _live_curve_readout(cross_track_cm)
        if _live_curve_peak_label != null:
            _live_curve_peak_label.text = _live_peak_readout(_live_curve_peak_signed_cm)
        if _live_curve_pace_label != null:
            _live_curve_pace_label.text = _live_pace_readout(velocity.length(), _live_curve_launch_speed)
    elif _live_curve_was_running:
        _show_live_curve_summary()

    _live_curve_was_running = running

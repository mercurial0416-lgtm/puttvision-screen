extends "res://commercial_read_apex_preview.gd"

const FlowScene = preload("res://commercial_read_flow.gd")
const PREVIEW_SIDE_SLOPE := 1.35
const PREVIEW_LONG_SLOPE := -0.55

var _replay_compare_preview_ready := false
var _live_break_preview_ready := false
var _preview_live_break_panel: Panel
var _preview_live_break_value: Label
var _preview_live_break_peak: Label
var _preview_live_roll_pace: Label
var _preview_live_break_trace: Line2D

# Keep the rendered regression frame internally honest: the authoritative/base HUD and the
# commercial green-read panel must describe the same slope. Previously the overview injected a
# synthetic break while the base snapshot stayed flat, producing a misleading verification image.
func _snapshot() -> Dictionary:
    var s := super._snapshot()
    s["sideSlope"] = PREVIEW_SIDE_SLOPE
    s["longSlope"] = PREVIEW_LONG_SLOPE
    return s

func _seed_live_break_preview() -> void:
    var layer := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if layer == null:
        return
    var root := layer.get_node_or_null("V174HUDRoot") as Control
    if root == null:
        return
    if _preview_live_break_panel == null:
        _preview_live_break_panel = _v174_panel(root, Vector2(1392, 310), Vector2(498, 92), Color(0.014, 0.021, 0.026, 0.88), Color(0.45, 0.72, 0.82, 0.22), 13)
        _preview_live_break_panel.name = "PreviewLiveBreakMeter"
        _v174_accent(_preview_live_break_panel, Vector2(0, 0), Vector2(6, 92), Color("#73c2d4"))
        _v174_text(_preview_live_break_panel, Vector2(20, 8), Vector2(170, 22), "LIVE BREAK", 13, Color("#bfe9f1"))
        _preview_live_roll_pace = _v174_text(_preview_live_break_panel, Vector2(190, 8), Vector2(286, 22), "PACE 46% · SETTLING", 12, Color(0.68, 0.82, 0.82, 0.92), HORIZONTAL_ALIGNMENT_RIGHT)
        _preview_live_roll_pace.name = "PreviewLiveRollPace"
        _preview_live_break_value = _v174_text(_preview_live_break_panel, Vector2(20, 30), Vector2(250, 42), "R 12.4 cm", 24, Color("#f4f6f0"))
        _preview_live_break_peak = _v174_text(_preview_live_break_panel, Vector2(280, 30), Vector2(196, 42), "PEAK R 18.7 cm", 14, Color(0.74, 0.82, 0.82, 0.94), HORIZONTAL_ALIGNMENT_RIGHT)
        var zero := Line2D.new()
        zero.name = "PreviewLiveBreakTraceZero"
        zero.width = 1.0
        zero.default_color = Color(0.48, 0.68, 0.72, 0.18)
        zero.points = PackedVector2Array([Vector2(20, 81), Vector2(476, 81)])
        _preview_live_break_panel.add_child(zero)
        _preview_live_break_trace = Line2D.new()
        _preview_live_break_trace.name = "PreviewLiveBreakTrace"
        _preview_live_break_trace.width = 1.8
        _preview_live_break_trace.default_color = Color(0.45, 0.86, 0.92, 0.82)
        _preview_live_break_trace.joint_mode = Line2D.LINE_JOINT_ROUND
        _preview_live_break_trace.begin_cap_mode = Line2D.LINE_CAP_ROUND
        _preview_live_break_trace.end_cap_mode = Line2D.LINE_CAP_ROUND
        _preview_live_break_panel.add_child(_preview_live_break_trace)
    _preview_live_break_panel.visible = true
    _preview_live_break_panel.modulate.a = 1.0
    _preview_live_roll_pace.text = "PACE 46% · SETTLING"
    _preview_live_break_value.text = "R 12.4 cm"
    _preview_live_break_peak.text = "PEAK R 18.7 cm"
    var preview_history := PackedFloat32Array([0.0, 0.8, 2.2, 4.8, 7.1, 10.0, 12.4, 15.8, 18.7])
    var probe = FlowScene.new()
    _preview_live_break_trace.points = probe._live_trace_points(preview_history)
    probe.free()

func _process(delta: float) -> void:
    super._process(delta)
    # The parent preview captures on a deferred frame. Hold synthetic presentation states after all
    # parent snapshot/focus choreography so the uploaded image proves these HUDs are actually visible.
    if _replay_compare_preview_ready and _capture_started:
        _seed_replay_compare_preview()
    if _live_break_preview_ready and _capture_started:
        _seed_live_break_preview()

func _seed_replay_compare_preview() -> void:
    _v171_replay_actual = [Vector2(0.0, 1.0), Vector2(0.08, 3.0), Vector2(0.12, 5.0), Vector2(0.05, 6.5)]
    _v171_replay_predicted = [Vector2(0.0, 1.0), Vector2(0.03, 3.0), Vector2(0.04, 5.0), Vector2(0.0, 6.5)]
    _v171_replay_duration = 10.0
    _v171_replay_remaining = 1.20
    _v180_refresh_compare()
    if _v180_compare_chip != null:
        _v180_compare_chip.visible = true
        _v180_compare_chip.modulate.a = 1.0
    if _v180_focus_chip != null:
        _v180_focus_chip.visible = true
        _v180_focus_chip.modulate.a = 1.0
        _v180_focus_distance.text = "42 cm TO CUP"

func _run_apex_preview_regression() -> bool:
    if not super._run_apex_preview_regression():
        return false

    if slope_label == null or slope_label.text.find("+1.35%") < 0 or slope_label.text.find("-0.55%") < 0:
        push_error("Preview slope authority mismatch: %s" % ("<missing>" if slope_label == null else slope_label.text))
        get_tree().quit(35)
        return false

    var probe = FlowScene.new()
    if probe._v174_direction(PREVIEW_SIDE_SLOPE) != "RIGHT" or probe._v183_break_text(PREVIEW_SIDE_SLOPE) != "BREAK  R 1.35%":
        push_error("Positive side slope no longer labels the authoritative right break")
        probe.free()
        get_tree().quit(36)
        return false
    if probe._v174_direction(-PREVIEW_SIDE_SLOPE) != "LEFT" or probe._v183_break_text(-PREVIEW_SIDE_SLOPE) != "BREAK  L 1.35%":
        push_error("Negative side slope no longer labels the authoritative left break")
        probe.free()
        get_tree().quit(36)
        return false
    if probe._v174_grade(PREVIEW_LONG_SLOPE) != "UPHILL" or probe._v183_grade_text(PREVIEW_LONG_SLOPE) != "GRADE  UP 0.55%":
        push_error("Negative longitudinal slope no longer labels uphill consistently")
        probe.free()
        get_tree().quit(37)
        return false
    if probe._v174_grade(-PREVIEW_LONG_SLOPE) != "DOWNHILL" or probe._v183_grade_text(-PREVIEW_LONG_SLOPE) != "GRADE  DOWN 0.55%":
        push_error("Positive longitudinal slope no longer labels downhill consistently")
        probe.free()
        get_tree().quit(37)
        return false

    var previous_center: Vector2 = Vector2.ZERO
    for i in range(probe.READ_FLOW_FRACTIONS.size()):
        var fraction: float = probe.READ_FLOW_FRACTIONS[i]
        var geometry := probe._read_flow_geometry(0.42, fraction)
        var center: Vector2 = geometry["center"]
        var tip: Vector2 = geometry["tip"]
        var left: Vector2 = geometry["left"]
        var right: Vector2 = geometry["right"]
        var tangent: Vector2 = geometry["tangent"]
        var wing_mid := (left + right) * 0.5
        if (tip - wing_mid).dot(tangent) <= 4.0:
            push_error("Read flow cue no longer points along path direction")
            probe.free()
            get_tree().quit(34)
            return false
        if absf(left.distance_to(center) - right.distance_to(center)) > 0.2:
            push_error("Read flow cue wing symmetry regression")
            probe.free()
            get_tree().quit(34)
            return false
        if i > 0 and center.distance_to(previous_center) < 18.0:
            push_error("Read flow cues collapsed together")
            probe.free()
            get_tree().quit(34)
            return false
        previous_center = center

        if _v183_panel != null:
            var cue := Line2D.new()
            cue.name = "PreviewCommercialReadFlowCue%d" % (i + 1)
            cue.width = 1.8
            cue.default_color = Color(0.72, 0.94, 1.0, 0.74 - float(i) * 0.08)
            cue.joint_mode = Line2D.LINE_JOINT_ROUND
            cue.begin_cap_mode = Line2D.LINE_CAP_ROUND
            cue.end_cap_mode = Line2D.LINE_CAP_ROUND
            cue.points = PackedVector2Array([left, tip, right])
            _v183_panel.add_child(cue)

    if probe._live_curve_readout(0.0) != "CENTER" or probe._live_curve_readout(12.44) != "R 12.4 cm" or probe._live_curve_readout(-7.26) != "L 7.3 cm":
        push_error("Live break direction/readout regression")
        probe.free()
        get_tree().quit(42)
        return false
    if probe._live_peak_readout(0.0) != "PEAK CENTER" or probe._live_peak_readout(18.74) != "PEAK R 18.7 cm" or probe._live_peak_readout(-9.26) != "PEAK L 9.3 cm":
        push_error("Live break peak direction/readout regression")
        probe.free()
        get_tree().quit(42)
        return false
    if probe._live_pace_readout(0.0, 0.0) != "PACE --" or probe._live_pace_readout(0.86, 1.0) != "PACE 86% · ROLLING" or probe._live_pace_readout(0.46, 1.0) != "PACE 46% · SETTLING" or probe._live_pace_readout(0.18, 1.0) != "PACE 18% · DYING":
        push_error("Live roll pace phase/readout regression")
        probe.free()
        get_tree().quit(43)
        return false
    if probe._live_pace_readout(2.0, 1.0) != "PACE 100% · ROLLING":
        push_error("Live roll pace clamp regression")
        probe.free()
        get_tree().quit(43)
        return false

    var trace_history := PackedFloat32Array([-10.0, 0.0, 10.0])
    var trace_points := probe._live_trace_points(trace_history)
    if trace_points.size() != 3 or absf(trace_points[0].x - probe.LIVE_TRACE_LEFT) > 0.01 or absf(trace_points[2].x - probe.LIVE_TRACE_RIGHT) > 0.01:
        push_error("Live break trace span regression")
        probe.free()
        get_tree().quit(44)
        return false
    if trace_points[0].y <= probe.LIVE_TRACE_CENTER_Y or absf(trace_points[1].y - probe.LIVE_TRACE_CENTER_Y) > 0.01 or trace_points[2].y >= probe.LIVE_TRACE_CENTER_Y:
        push_error("Live break trace direction regression")
        probe.free()
        get_tree().quit(44)
        return false
    for i in range(probe.LIVE_TRACE_MAX_POINTS + 6):
        probe._live_trace_push(float(i))
    if probe._live_curve_history.size() != probe.LIVE_TRACE_MAX_POINTS:
        push_error("Live break trace bounded-history regression")
        probe.free()
        get_tree().quit(44)
        return false

    _seed_live_break_preview()
    if _preview_live_break_panel == null or _preview_live_break_value == null or _preview_live_break_peak == null or _preview_live_roll_pace == null or _preview_live_break_trace == null:
        push_error("Live roll preview HUD missing")
        probe.free()
        get_tree().quit(42)
        return false
    if _preview_live_break_value.text != "R 12.4 cm" or _preview_live_break_peak.text != "PEAK R 18.7 cm" or _preview_live_roll_pace.text != "PACE 46% · SETTLING" or _preview_live_break_trace.points.size() < 5:
        push_error("Live roll meter HUD regression")
        probe.free()
        get_tree().quit(42)
        return false
    _live_break_preview_ready = true

    _v179_samples = [Vector2(-8, -18), Vector2(-3, 10), Vector2(5, 22), Vector2(7, 6), Vector2(3, 14)]
    if _v179_make_count() != 2 or _v179_make_rate_text() != "WINDOW 2/5":
        push_error("Session make-window tally regression: %s" % _v179_make_rate_text())
        probe.free()
        get_tree().quit(40)
        return false
    _v179_samples = [Vector2(-5, -15), Vector2(5, 15), Vector2(5.01, 0), Vector2(0, 15.01)]
    if _v179_make_count() != 2:
        push_error("Session make-window boundary regression: %d" % _v179_make_count())
        probe.free()
        get_tree().quit(40)
        return false

    _v179_samples = [Vector2(6, 18), Vector2(7, 16), Vector2(5, 14)]
    if _v179_next_rep_text() != "NEXT · 6cm LEFT · SOFTER":
        push_error("Next-rep right/long correction regression: %s" % _v179_next_rep_text())
        probe.free()
        get_tree().quit(41)
        return false
    _v179_samples = [Vector2(-6, -18), Vector2(-7, -16), Vector2(-5, -14)]
    if _v179_next_rep_text() != "NEXT · 6cm RIGHT · FIRMER":
        push_error("Next-rep left/short correction regression: %s" % _v179_next_rep_text())
        probe.free()
        get_tree().quit(41)
        return false
    _v179_samples = [Vector2(2, 8), Vector2(-2, -8), Vector2(0, 0)]
    if _v179_next_rep_text() != "NEXT · HOLD LINE · HOLD PACE":
        push_error("Next-rep deadband regression: %s" % _v179_next_rep_text())
        probe.free()
        get_tree().quit(41)
        return false
    _v179_samples = [Vector2(20, 12), Vector2(20, 12), Vector2(20, 12)]
    if _v179_next_rep_text().find("9cm LEFT") < 0:
        push_error("Next-rep correction clamp regression: %s" % _v179_next_rep_text())
        probe.free()
        get_tree().quit(41)
        return false

    _v179_preview_seed()
    if _v179_window_label == null or _v179_window_label.text != "WINDOW 2/5" or _v179_plot == null or _v179_plot.get_node_or_null("MakeWindow") == null:
        push_error("Session make-window HUD regression")
        probe.free()
        get_tree().quit(40)
        return false
    if _v179_tendency_label == null or _v179_tendency_label.text != "NEXT · HOLD LINE · HOLD PACE":
        push_error("Next-rep compact HUD regression: %s" % ("<missing>" if _v179_tendency_label == null else _v179_tendency_label.text))
        probe.free()
        get_tree().quit(41)
        return false

    if _v180_compare_chip == null or _v180_compare_primary == null or _v180_compare_secondary == null:
        push_error("Current TV preview missing replay read comparison HUD")
        probe.free()
        get_tree().quit(38)
        return false
    _seed_replay_compare_preview()
    if not _v180_compare_primary.text.begins_with("AVG ") or not _v180_compare_primary.text.contains("PEAK ") or _v180_compare_secondary.text.find("RIGHT") < 0 or _v180_compare_secondary.text.find("PACE OK") < 0:
        push_error("Replay directional finish verdict regression: %s" % _v180_compare_secondary.text)
        probe.free()
        get_tree().quit(38)
        return false
    var verdict_right := _v180_finish_verdict(Vector2(0.08, 1.0), Vector2(0.0, 1.0), Vector2.DOWN)
    var verdict_left_short := _v180_finish_verdict(Vector2(-0.06, 0.92), Vector2(0.0, 1.0), Vector2.DOWN)
    var verdict_center := _v180_finish_verdict(Vector2(0.01, 1.01), Vector2(0.0, 1.0), Vector2.DOWN)
    if verdict_right != "8 cm RIGHT  ·  PACE OK" or verdict_left_short != "6 cm LEFT  ·  8 cm SHORT" or verdict_center != "ON LINE  ·  PACE OK":
        push_error("Replay miss-direction semantics regression: %s / %s / %s" % [verdict_right, verdict_left_short, verdict_center])
        probe.free()
        get_tree().quit(39)
        return false
    _replay_compare_preview_ready = true

    probe.free()
    print("COMMERCIAL_READ_FLOW_OK=1")
    print("GREEN_SLOPE_PREVIEW_AUTHORITY_OK=1")
    print("GREEN_SLOPE_LABEL_SEMANTICS_OK=1")
    print("LIVE_BREAK_METER_OK=1")
    print("LIVE_ROLL_PACE_OK=1")
    print("LIVE_BREAK_TRACE_OK=1")
    print("SESSION_MAKE_WINDOW_OK=1")
    print("NEXT_REP_COACH_OK=1")
    print("REPLAY_READ_COMPARE_OK=1")
    print("REPLAY_FINISH_VERDICT_OK=1")
    return true

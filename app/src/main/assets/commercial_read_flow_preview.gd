extends "res://commercial_read_apex_preview.gd"

const FlowScene = preload("res://commercial_read_flow.gd")
const PREVIEW_SIDE_SLOPE := 1.35
const PREVIEW_LONG_SLOPE := -0.55

var _replay_compare_preview_ready := false

# Keep the rendered regression frame internally honest: the authoritative/base HUD and the
# commercial green-read panel must describe the same slope. Previously the overview injected a
# synthetic break while the base snapshot stayed flat, producing a misleading verification image.
func _snapshot() -> Dictionary:
    var s := super._snapshot()
    s["sideSlope"] = PREVIEW_SIDE_SLOPE
    s["longSlope"] = PREVIEW_LONG_SLOPE
    return s

func _process(delta: float) -> void:
    super._process(delta)
    # The parent preview captures on a deferred frame. Hold the synthetic replay after all parent
    # snapshot/focus choreography so the uploaded image proves this HUD is actually visible.
    if _replay_compare_preview_ready and _capture_started:
        _seed_replay_compare_preview()

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

    probe._v179_samples = [Vector2(-8, -18), Vector2(-3, 10), Vector2(5, 22), Vector2(7, 6), Vector2(3, 14)]
    if probe._v179_make_count() != 2 or probe._v179_make_rate_text() != "WINDOW 2/5":
        push_error("Session make-window tally regression: %s" % probe._v179_make_rate_text())
        probe.free()
        get_tree().quit(40)
        return false
    probe._v179_samples = [Vector2(-5, -15), Vector2(5, 15), Vector2(5.01, 0), Vector2(0, 15.01)]
    if probe._v179_make_count() != 2:
        push_error("Session make-window boundary regression: %d" % probe._v179_make_count())
        probe.free()
        get_tree().quit(40)
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
    print("SESSION_MAKE_WINDOW_OK=1")
    print("REPLAY_READ_COMPARE_OK=1")
    print("REPLAY_FINISH_VERDICT_OK=1")
    return true
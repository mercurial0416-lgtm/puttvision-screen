extends "res://commercial_read_apex_preview.gd"

const FlowScene = preload("res://commercial_read_flow.gd")
const PREVIEW_SIDE_SLOPE := 1.35
const PREVIEW_LONG_SLOPE := -0.55

# Keep the rendered regression frame internally honest: the authoritative/base HUD and the
# commercial green-read panel must describe the same slope. Previously the overview injected a
# synthetic break while the base snapshot stayed flat, producing a misleading verification image.
func _snapshot() -> Dictionary:
    var s := super._snapshot()
    s["sideSlope"] = PREVIEW_SIDE_SLOPE
    s["longSlope"] = PREVIEW_LONG_SLOPE
    return s

func _run_apex_preview_regression() -> bool:
    if not super._run_apex_preview_regression():
        return false

    if slope_label == null or slope_label.text.find("+1.35%") < 0 or slope_label.text.find("-0.55%") < 0:
        push_error("Preview slope authority mismatch: %s" % ("<missing>" if slope_label == null else slope_label.text))
        get_tree().quit(35)
        return false

    var probe = FlowScene.new()
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

    probe.free()
    print("COMMERCIAL_READ_FLOW_OK=1")
    print("GREEN_SLOPE_PREVIEW_AUTHORITY_OK=1")
    return true

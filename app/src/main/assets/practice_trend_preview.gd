extends "res://replay_playhead_preview.gd"

const PracticeTrendScene = preload("res://practice_trend_vector.gd")
var _trend_checked := false
var _trend_visual_added := false

func _preview_trend_samples() -> Array[Vector2]:
    return [Vector2(20, 42), Vector2(17, 34), Vector2(11, 21), Vector2(8, 15), Vector2(4, 7)]

func _add_trend_visual() -> void:
    if _v179_plot == null or _v179_panel == null:
        return
    _v179_samples = _preview_trend_samples()
    _v179_refresh()
    _v179_panel.visible = true

    var probe = PracticeTrendScene.new()
    var geometry := probe._practice_trend_geometry(_v179_samples)
    if bool(geometry.get("visible", false)):
        var line := Line2D.new()
        line.name = "PreviewPracticeTrendVector"
        line.width = 2.0
        line.default_color = Color(0.46, 0.85, 0.66, 0.92)
        line.begin_cap_mode = Line2D.LINE_CAP_ROUND
        line.end_cap_mode = Line2D.LINE_CAP_ROUND
        line.points = PackedVector2Array([geometry["start"], geometry["tip"]])
        _v179_plot.add_child(line)

        var head := Line2D.new()
        head.name = "PreviewPracticeTrendVectorHead"
        head.width = 2.0
        head.default_color = line.default_color
        head.joint_mode = Line2D.LINE_JOINT_ROUND
        head.points = PackedVector2Array([geometry["left"], geometry["tip"], geometry["right"]])
        _v179_plot.add_child(head)

        var label := _v174_text(_v179_panel, Vector2(24, 34), Vector2(300, 14), "TREND · TIGHTENING", 8, line.default_color)
        label.name = "PreviewPracticeTrendLabel"
    probe.free()

func _process(delta: float) -> void:
    super._process(delta)

    if not _trend_visual_added and _preview_frames >= 10:
        _trend_visual_added = true
        _add_trend_visual()
    if _trend_visual_added and _v179_panel != null:
        _v179_panel.visible = true

    if _trend_checked or _preview_frames < 16:
        return
    _trend_checked = true

    var probe = PracticeTrendScene.new()
    var result := probe._practice_trend_geometry(_preview_trend_samples())
    assert(str(result.get("state", "")) == "TIGHTENING")
    assert(bool(result.get("visible", false)))
    assert(float(result.get("recent_error", 99.0)) < float(result.get("early_error", 0.0)))

    var drifting: Array[Vector2] = [Vector2(4, 7), Vector2(8, 15), Vector2(17, 34), Vector2(20, 42)]
    assert(str(probe._practice_trend_geometry(drifting).get("state", "")) == "DRIFTING")

    var building: Array[Vector2] = [Vector2(10, 20), Vector2(8, 16), Vector2(6, 12)]
    assert(not bool(probe._practice_trend_geometry(building).get("visible", true)))
    probe.free()
    print("PRACTICE_TREND_VECTOR_OK=1")

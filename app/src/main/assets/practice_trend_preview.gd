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
    var ring := probe._practice_recent_ring_geometry(_v179_samples)
    if bool(ring.get("visible", false)):
        var ring_line := Line2D.new()
        ring_line.name = "PreviewPracticeRecentConsistencyRing"
        ring_line.width = 1.5
        ring_line.default_color = Color(0.72, 0.90, 0.96, 0.78)
        ring_line.joint_mode = Line2D.LINE_JOINT_ROUND
        ring_line.points = ring["points"]
        _v179_plot.add_child(ring_line)

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

    var ring := probe._practice_recent_ring_geometry(_preview_trend_samples())
    assert(bool(ring.get("visible", false)))
    assert(float(ring.get("radius", 0.0)) >= probe.PRACTICE_RECENT_RING_MIN_RADIUS)
    assert(float(ring.get("radius", 99.0)) <= probe.PRACTICE_RECENT_RING_MAX_RADIUS)
    var ring_points: PackedVector2Array = ring["points"]
    assert(ring_points.size() == probe.PRACTICE_RECENT_RING_SEGMENTS + 1)
    assert(ring_points[0].distance_to(ring_points[ring_points.size() - 1]) < 0.01)

    var edge_samples: Array[Vector2] = [Vector2(28, 62), Vector2(30, 68), Vector2(30, 70), Vector2(30, 70)]
    var edge_ring := probe._practice_recent_ring_geometry(edge_samples)
    assert(bool(edge_ring.get("visible", false)))
    var edge_points: PackedVector2Array = edge_ring["points"]
    assert(edge_points.size() == probe.PRACTICE_RECENT_RING_SEGMENTS + 1)
    for point in edge_points:
        assert(point.x >= probe.PRACTICE_RECENT_RING_EDGE_INSET - 0.01)
        assert(point.y >= probe.PRACTICE_RECENT_RING_EDGE_INSET - 0.01)
        assert(point.x <= probe.V179_PLOT_SIZE.x - probe.PRACTICE_RECENT_RING_EDGE_INSET + 0.01)
        assert(point.y <= probe.V179_PLOT_SIZE.y - probe.PRACTICE_RECENT_RING_EDGE_INSET + 0.01)

    var drifting: Array[Vector2] = [Vector2(4, 7), Vector2(8, 15), Vector2(17, 34), Vector2(20, 42)]
    assert(str(probe._practice_trend_geometry(drifting).get("state", "")) == "DRIFTING")

    var building: Array[Vector2] = [Vector2(10, 20), Vector2(8, 16), Vector2(6, 12)]
    assert(not bool(probe._practice_trend_geometry(building).get("visible", true)))
    assert(not bool(probe._practice_recent_ring_geometry(building).get("visible", true)))
    probe.free()
    print("PRACTICE_TREND_VECTOR_OK=1")
    print("PRACTICE_RECENT_CONSISTENCY_RING_OK=1")
    print("PRACTICE_RECENT_RING_EDGE_SAFE_OK=1")

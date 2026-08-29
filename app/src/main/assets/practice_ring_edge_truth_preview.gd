extends "res://replay_transition_cues_preview.gd"

const PracticeRingEdgeTruthScene = preload("res://practice_ring_edge_truth.gd")
var _practice_ring_edge_truth_checked := false
var _practice_ring_edge_truth_visual_added := false

func _practice_ring_edge_samples() -> Array[Vector2]:
    return [Vector2(28, 62), Vector2(30, 68), Vector2(30, 70), Vector2(30, 70)]

func _process(delta: float) -> void:
    super._process(delta)

    if not _practice_ring_edge_truth_visual_added and _trend_visual_added and _v179_plot != null:
        _practice_ring_edge_truth_visual_added = true
        var visual_probe = PracticeRingEdgeTruthScene.new()
        var visual_ring := visual_probe._practice_recent_ring_geometry(_practice_ring_edge_samples())
        if bool(visual_ring.get("visible", false)):
            var arc := Line2D.new()
            arc.name = "PreviewPracticeEdgeTruthArc"
            arc.width = 1.8
            arc.default_color = Color(0.72, 0.90, 0.96, 0.86)
            arc.joint_mode = Line2D.LINE_JOINT_ROUND
            arc.begin_cap_mode = Line2D.LINE_CAP_ROUND
            arc.end_cap_mode = Line2D.LINE_CAP_ROUND
            arc.points = visual_ring["points"]
            _v179_plot.add_child(arc)
        visual_probe.free()

    if _practice_ring_edge_truth_checked or _preview_frames < 20:
        return
    _practice_ring_edge_truth_checked = true

    var probe = PracticeRingEdgeTruthScene.new()

    var edge_ring := probe._practice_recent_ring_geometry(_practice_ring_edge_samples())
    assert(bool(edge_ring.get("visible", false)))
    assert(bool(edge_ring.get("clipped", false)))
    var edge_center: Vector2 = edge_ring["center"]
    var edge_radius := float(edge_ring.get("radius", 0.0))
    assert(edge_radius >= probe.PRACTICE_RECENT_RING_MIN_RADIUS)
    assert(edge_radius <= probe.PRACTICE_RECENT_RING_MAX_RADIUS)

    # Regression: never reduce the radius just to keep the circle inside the chart. The visible
    # arc must preserve the true requested scale and every rendered point must remain on that arc.
    var old_edge_capacity: float = minf(
        minf(edge_center.x, probe.V179_PLOT_SIZE.x - edge_center.x),
        minf(edge_center.y, probe.V179_PLOT_SIZE.y - edge_center.y)
    ) - probe.PRACTICE_RECENT_RING_EDGE_INSET
    assert(edge_radius > maxf(0.0, old_edge_capacity) + 0.5)
    var edge_points: PackedVector2Array = edge_ring["points"]
    assert(edge_points.size() >= 2)
    assert(edge_points.size() < probe.PRACTICE_RECENT_RING_SEGMENTS + 1)
    for point in edge_points:
        assert(probe._practice_ring_point_inside(point))
        assert(absf(point.distance_to(edge_center) - edge_radius) < 0.05)

    # Interior groups keep the existing complete-circle presentation and exact radius semantics.
    var interior_samples: Array[Vector2] = [Vector2(-10, -8), Vector2(6, 7), Vector2(8, 5), Vector2(9, 6)]
    var interior_ring := probe._practice_recent_ring_geometry(interior_samples)
    assert(bool(interior_ring.get("visible", false)))
    assert(not bool(interior_ring.get("clipped", true)))
    var interior_points: PackedVector2Array = interior_ring["points"]
    assert(interior_points.size() == probe.PRACTICE_RECENT_RING_SEGMENTS + 1)
    assert(interior_points[0].distance_to(interior_points[interior_points.size() - 1]) < 0.01)

    probe.free()
    print("PRACTICE_RING_EDGE_TRUE_SCALE_OK=1")
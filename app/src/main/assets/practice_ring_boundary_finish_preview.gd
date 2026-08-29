extends "res://replay_spatial_pacing_preview.gd"

const PracticeRingBoundaryFinishScene = preload("res://practice_ring_boundary_finish.gd")
var _practice_ring_boundary_finish_checked := false

func _practice_ring_edge_distance(probe, point: Vector2) -> float:
    var inset: float = probe.PRACTICE_RECENT_RING_EDGE_INSET
    return minf(
        minf(absf(point.x - inset), absf(point.x - (probe.V179_PLOT_SIZE.x - inset))),
        minf(absf(point.y - inset), absf(point.y - (probe.V179_PLOT_SIZE.y - inset)))
    )

func _process(delta: float) -> void:
    super._process(delta)
    if _practice_ring_boundary_finish_checked or _preview_frames < 20:
        return
    _practice_ring_boundary_finish_checked = true

    var probe = PracticeRingBoundaryFinishScene.new()
    var edge_ring := probe._practice_recent_ring_geometry(_practice_ring_edge_samples())
    assert(bool(edge_ring.get("visible", false)))
    assert(bool(edge_ring.get("clipped", false)))
    assert(bool(edge_ring.get("boundary_snapped", false)))

    var center: Vector2 = edge_ring["center"]
    var radius := float(edge_ring["radius"])
    var points: PackedVector2Array = edge_ring["points"]
    assert(points.size() >= 2)

    # Regression: clipped arc endpoints must now terminate at the visible plot boundary instead of
    # ending one coarse angular sample early. Keep every endpoint on the true, unshrunk circle.
    assert(_practice_ring_edge_distance(probe, points[0]) < 0.02)
    assert(_practice_ring_edge_distance(probe, points[points.size() - 1]) < 0.02)
    assert(absf(points[0].distance_to(center) - radius) < 0.01)
    assert(absf(points[points.size() - 1].distance_to(center) - radius) < 0.01)
    assert(probe._practice_ring_point_inside(points[0]))
    assert(probe._practice_ring_point_inside(points[points.size() - 1]))

    # Interior rings remain closed and untouched by the clipping finish pass.
    var interior_samples: Array[Vector2] = [Vector2(-10, -8), Vector2(6, 7), Vector2(8, 5), Vector2(9, 6)]
    var interior_ring := probe._practice_recent_ring_geometry(interior_samples)
    assert(bool(interior_ring.get("visible", false)))
    assert(not bool(interior_ring.get("clipped", true)))
    assert(not bool(interior_ring.get("boundary_snapped", false)))
    var interior_points: PackedVector2Array = interior_ring["points"]
    assert(interior_points.size() == probe.PRACTICE_RECENT_RING_SEGMENTS + 1)

    probe.free()
    print("PRACTICE_RING_BOUNDARY_FINISH_OK=1")

extends "res://practice_ring_boundary_finish_preview.gd"

const ReadLandmarkSpatialSpacingScene = preload("res://read_landmark_spatial_spacing.gd")
var _read_landmark_spatial_spacing_checked := false

func _process(delta: float) -> void:
    super._process(delta)
    if _read_landmark_spatial_spacing_checked or _preview_frames < 20:
        return
    _read_landmark_spatial_spacing_checked = true

    var probe = ReadLandmarkSpatialSpacingScene.new()

    # Regression: index midpoint would land at y=1.0. Spatial midpoint must represent half of the
    # actual nine-unit read path, keeping apex/gate/flow landmarks honest on uneven solver samples.
    var uneven := PackedVector2Array([Vector2(0, 0), Vector2(0, 1), Vector2(0, 9)])
    var halfway := probe._read_path_sample(uneven, 0.5)
    assert((halfway["point"] as Vector2).distance_to(Vector2(0, 4.5)) < 0.01)
    assert((halfway["tangent"] as Vector2).distance_to(Vector2.DOWN) < 0.01)

    # The launch cue uses the same traveled-distance sampler. At 18% of this ten-unit path its tip
    # belongs 0.8 units into the long horizontal segment; the old raw-index method picked the dense
    # early sample instead and pointed the arrow in the wrong visual direction.
    var launch_path := PackedVector2Array([Vector2(0, 0), Vector2(0, 1), Vector2(9, 1)])
    var launch_sample := probe._read_path_sample(launch_path, probe.READ_LAUNCH_FRACTION)
    assert((launch_sample["point"] as Vector2).distance_to(Vector2(0.8, 1.0)) < 0.01)
    assert((launch_sample["tangent"] as Vector2).distance_to(Vector2.RIGHT) < 0.01)

    # A bent path must choose the tangent of the segment containing the requested traveled distance,
    # rather than inheriting a neighboring dense sample's direction.
    var bent := PackedVector2Array([Vector2(0, 0), Vector2(0, 1), Vector2(8, 1)])
    var bent_half := probe._read_path_sample(bent, 0.5)
    assert((bent_half["point"] as Vector2).distance_to(Vector2(3.5, 1.0)) < 0.01)
    assert((bent_half["tangent"] as Vector2).distance_to(Vector2.RIGHT) < 0.01)

    # Duplicate samples and malformed fractions stay deterministic and never divide by zero.
    var duplicates := PackedVector2Array([Vector2(2, 3), Vector2(2, 3), Vector2(2, 7)])
    assert((probe._read_path_sample(duplicates, 0.25)["point"] as Vector2).distance_to(Vector2(2, 4)) < 0.01)
    assert((probe._read_path_sample(duplicates, NAN)["point"] as Vector2) == Vector2(2, 3))
    assert((probe._read_path_sample(duplicates, 2.0)["point"] as Vector2) == Vector2(2, 7))

    probe.free()
    print("READ_LANDMARK_SPATIAL_SPACING_OK=1")

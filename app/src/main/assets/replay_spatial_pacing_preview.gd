extends "res://practice_ring_edge_truth_preview.gd"

const ReplaySpatialPacingScene = preload("res://replay_spatial_pacing.gd")
var _replay_spatial_pacing_checked := false

func _process(delta: float) -> void:
    super._process(delta)
    if _replay_spatial_pacing_checked or _preview_frames < 20:
        return
    _replay_spatial_pacing_checked = true

    var probe = ReplaySpatialPacingScene.new()

    # Regression: index-based interpolation would sit at x=0.1 halfway through this replay.
    # Arc-length pacing must place the cinematic replay halfway through the actual traveled path.
    var uneven: Array = [Vector2(0.0, 0.0), Vector2(0.1, 0.0), Vector2(10.0, 0.0)]
    var halfway := probe._v175_trail_point(uneven, 0.5)
    assert(halfway.distance_to(Vector2(5.0, 0.0)) < 0.01)

    # Curved uneven segments must also advance by traveled distance rather than sample count.
    var curved: Array = [Vector2(0.0, 0.0), Vector2(0.0, 1.0), Vector2(3.0, 1.0)]
    var curved_half := probe._v175_trail_point(curved, 0.5)
    assert(curved_half.distance_to(Vector2(1.0, 1.0)) < 0.01)

    # Duplicate samples are common around settling frames; they must not stall or divide by zero.
    var duplicate: Array = [Vector2(0.0, 0.0), Vector2(0.0, 0.0), Vector2(4.0, 0.0)]
    assert(probe._v175_trail_point(duplicate, 0.25).distance_to(Vector2(1.0, 0.0)) < 0.01)

    # Clamp/fallback behavior stays deterministic for malformed presentation input.
    assert(probe._v175_trail_point(uneven, -2.0) == Vector2(0.0, 0.0))
    assert(probe._v175_trail_point(uneven, 2.0) == Vector2(10.0, 0.0))
    assert(probe._v175_trail_point(uneven, NAN) == Vector2(0.0, 0.0))

    probe.free()
    print("REPLAY_SPATIAL_PACING_OK=1")

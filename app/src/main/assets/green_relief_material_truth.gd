extends "res://replay_timeline_camera_truth.gd"

# Presentation-only material truth guard. The opaque Green mesh is already rebuilt from the
# authoritative terrain samples by terrain_relief_visibility.gd, including its bounded visual Y
# exaggeration. The original turf shader's side_slope/long_slope vertex warp was designed for a
# flat PlaneMesh; applying it again to the sampled mesh double-warps the visible green and can make
# crowns, bowls and cross-slopes disagree with the grid/ball/cup anchors. Keep fringe/rough legacy
# grading intact, but lock the sampled Green material to its mesh geometry after every snapshot.
# Android V135-V137, GreenTerrain, GreenReadAdvisor, solver paths and shot state remain untouched.

func _green_relief_lock_sampled_mesh_material() -> void:
    if mat_green == null:
        return
    mat_green.set_shader_parameter("side_slope", 0.0)
    mat_green.set_shader_parameter("long_slope", 0.0)

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _green_relief_lock_sampled_mesh_material()

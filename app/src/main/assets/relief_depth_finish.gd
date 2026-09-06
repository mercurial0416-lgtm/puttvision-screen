extends "res://address_relief_camera.gd"

# Presentation-only depth finish for the stationary TV green. Android V135-V137, GreenTerrain,
# GreenReadAdvisor, solver paths and shot coordinates remain authoritative. This layer only increases
# depth cues already derived from the same terrain samples and keeps the work bounded for Forward Mobile.

const RELIEF_DEPTH_CAMERA_LOWER_M := 0.035
const RELIEF_DEPTH_CLEARANCE_GUARD_M := 0.08
const RELIEF_PLANAR_GRADE_START_PCT := 0.55
const RELIEF_PLANAR_GRADE_FULL_PCT := 2.25
const RELIEF_PLANAR_CAMERA_LOWER_M := 0.050
const RELIEF_PLANAR_FOV_BOOST_DEG := 1.4
const RELIEF_AIM_WIDTH_M := 0.012
const RELIEF_AIM_CLEARANCE_M := 0.010
const RELIEF_AIM_SEGMENT_M := 0.24
const RELIEF_AIM_MAX_SEGMENTS := 96
const LONG_FRAME_START_M := 6.0
const LONG_FRAME_FULL_M := 14.0
const LONG_FRAME_TRAIL_EXTRA_M := 0.52
const LONG_FRAME_HEIGHT_EXTRA_M := 0.14
const LONG_FRAME_LOOK_EXTRA := 0.08
const LONG_FRAME_FOV_EXTRA_DEG := 1.4

func _terrain_relief_material() -> ShaderMaterial:
    var material := super._terrain_relief_material()
    if material == null or material.shader == null:
        return material

    # The base shell now carries stronger macro relief. Add only a bounded TV depth finish on top so
    # shallow crowns/bowls keep readable form without turning the green into a painted contour map.
    var code := material.shader.code
    code = code.replace("mix(0.89, 1.11, primary_hillshade * 0.5 + 0.5)", "mix(0.86, 1.14, primary_hillshade * 0.5 + 0.5)")
    code = code.replace("vec3(0.022, 0.008, -0.018) * cross_hillshade", "vec3(0.030, 0.011, -0.022) * cross_hillshade")
    code = code.replace(
        "float cross_hillshade = clamp(cross_facing * slope_signal, -1.0, 1.0);",
        "float cross_hillshade = clamp(cross_facing * slope_signal, -1.0, 1.0);\n    float form_light = clamp(primary_hillshade * 0.78 + cross_hillshade * 0.22, -1.0, 1.0);\n    float form_lobe = mix(1.0, mix(0.82, 1.18, form_light * 0.5 + 0.5), active);"
    )
    code = code.replace("relief_color *= cross_tint;", "relief_color *= cross_tint;\n    relief_color *= form_lobe;")
    code = code.replace("elevation_ribbon * active * 0.42", "elevation_ribbon * active * 0.48")
    code = code.replace("0.022 + active * (0.096 + 0.018 * abs(height_bias))", "0.026 + active * (0.108 + 0.020 * abs(height_bias))")
    material.shader.code = code
    return material

func _terrain_following_aim_mesh(distance_m: float) -> ArrayMesh:
    var vertices := PackedVector3Array()
    var indices := PackedInt32Array()
    var start_m := 0.10
    var end_m := maxf(0.30, distance_m - 0.10)
    var span_m := maxf(0.20, end_m - start_m)
    var segments := clampi(int(ceil(span_m / RELIEF_AIM_SEGMENT_M)), 2, RELIEF_AIM_MAX_SEGMENTS)
    var half_width := RELIEF_AIM_WIDTH_M * 0.5

    for i in range(segments + 1):
        var t := float(i) / float(segments)
        var forward_m := lerpf(start_m, end_m, t)
        var left_surface_m := _v166_sample(-half_width, forward_m).x
        var right_surface_m := _v166_sample(half_width, forward_m).x
        var left_height_m := _terrain_relief_visual_height(left_surface_m) + RELIEF_AIM_CLEARANCE_M
        var right_height_m := _terrain_relief_visual_height(right_surface_m) + RELIEF_AIM_CLEARANCE_M
        vertices.append(Vector3(-half_width, left_height_m, -forward_m))
        vertices.append(Vector3(half_width, right_height_m, -forward_m))

    for i in range(segments):
        var a := i * 2
        var b := a + 1
        var c := a + 2
        var d := a + 3
        indices.append_array(PackedInt32Array([a, c, b, b, c, d]))

    var arrays: Array = []
    arrays.resize(Mesh.ARRAY_MAX)
    arrays[Mesh.ARRAY_VERTEX] = vertices
    arrays[Mesh.ARRAY_INDEX] = indices
    var mesh := ArrayMesh.new()
    mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
    return mesh

func _update_aim_line(distance_m: float) -> void:
    if aim_line == null:
        return
    if not _v166_terrain_ready:
        super._update_aim_line(distance_m)
        return
    aim_line.mesh = _terrain_following_aim_mesh(distance_m)
    aim_line.position = Vector3.ZERO

func _terrain_relief_rebuild() -> void:
    super._terrain_relief_rebuild()
    if _v166_terrain_ready and aim_line != null:
        _update_aim_line(target_distance)

func _address_planar_grade_signal(ball_world: Vector3, plan: Dictionary) -> float:
    var look: Vector3 = plan.get("look", ball_world)
    var midpoint := ball_world.lerp(look, 0.62)
    var terrain := _v166_sample(midpoint.x, -midpoint.z)
    var grade_pct := Vector2(terrain.y, terrain.z).length()
    return smoothstep(RELIEF_PLANAR_GRADE_START_PCT, RELIEF_PLANAR_GRADE_FULL_PCT, grade_pct)

func _long_putt_frame_signal(distance_m: float) -> float:
    if not is_finite(distance_m) or distance_m <= LONG_FRAME_START_M:
        return 0.0
    return smoothstep(LONG_FRAME_START_M, LONG_FRAME_FULL_M, distance_m)

func _apply_long_putt_address_frame(ball_world: Vector3, distance_to_cup: float, plan: Dictionary) -> void:
    var signal := _long_putt_frame_signal(distance_to_cup)
    plan["long_frame_signal"] = signal
    if signal <= 0.0:
        return

    var cup_world := target_root.global_position if target_root != null else ball_world + Vector3(0.0, 0.0, -maxf(0.5, distance_to_cup))
    var ball_xz := Vector2(ball_world.x, ball_world.z)
    var cup_xz := Vector2(cup_world.x, cup_world.z)
    var flat_delta := cup_xz - ball_xz
    var flat_length := flat_delta.length()
    if flat_length <= 0.001:
        return

    var forward := flat_delta / flat_length
    var position: Vector3 = plan.get("position", ball_world)
    position.x -= forward.x * LONG_FRAME_TRAIL_EXTRA_M * signal
    position.z -= forward.y * LONG_FRAME_TRAIL_EXTRA_M * signal
    position.y += LONG_FRAME_HEIGHT_EXTRA_M * signal
    plan["position"] = position

    var current_fraction := clampf(float(plan.get("look_fraction", ADDRESS_LOOK_FRACTION)), 0.0, 1.0)
    var desired_fraction := clampf(current_fraction + LONG_FRAME_LOOK_EXTRA * signal, current_fraction, 0.72)
    var desired_xz := ball_xz.lerp(cup_xz, desired_fraction)
    var desired_h := _address_visual_height(_v166_sample(desired_xz.x, -desired_xz.y).x) + ADDRESS_LOOK_LIFT
    plan["look"] = Vector3(desired_xz.x, desired_h, desired_xz.y)
    plan["look_fraction"] = desired_fraction
    plan["fov"] = float(plan.get("fov", camera.fov)) + LONG_FRAME_FOV_EXTRA_DEG * signal

func _address_relief_camera_plan(ball_world: Vector3, distance_to_cup: float) -> Dictionary:
    var plan := super._address_relief_camera_plan(ball_world, distance_to_cup)
    _apply_long_putt_address_frame(ball_world, distance_to_cup, plan)
    var clearance_raise := float(plan.get("clearance_raise", 0.0))
    if clearance_raise <= RELIEF_DEPTH_CLEARANCE_GUARD_M:
        var position: Vector3 = plan["position"]
        var planar_signal := _address_planar_grade_signal(ball_world, plan)
        position.y -= RELIEF_DEPTH_CAMERA_LOWER_M
        position.y -= RELIEF_PLANAR_CAMERA_LOWER_M * planar_signal
        plan["position"] = position
        plan["fov"] = float(plan.get("fov", camera.fov)) + RELIEF_PLANAR_FOV_BOOST_DEG * planar_signal
        plan["planar_grade_signal"] = planar_signal
    else:
        plan["planar_grade_signal"] = 0.0
    return plan

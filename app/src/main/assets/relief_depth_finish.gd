extends "res://address_relief_camera.gd"

# Presentation-only depth finish for the stationary TV green. Android V135-V137, GreenTerrain,
# GreenReadAdvisor, solver paths and shot coordinates remain authoritative. This layer only increases
# depth cues already derived from the same terrain samples and keeps the work bounded for Forward Mobile.

const RELIEF_DEPTH_CAMERA_LOWER_M := 0.035
const RELIEF_DEPTH_CLEARANCE_GUARD_M := 0.08
const RELIEF_AIM_WIDTH_M := 0.012
const RELIEF_AIM_CLEARANCE_M := 0.010
const RELIEF_AIM_SEGMENT_M := 0.24
const RELIEF_AIM_MAX_SEGMENTS := 96

func _terrain_relief_material() -> ShaderMaterial:
    var material := super._terrain_relief_material()
    if material == null or material.shader == null:
        return material

    # The base relief shell deliberately stayed subtle to avoid a painted-island look. On a TV that
    # made the already-exaggerated mesh read too flat because the unshaded overlay carried only a
    # six-percent light/dark swing. Strengthen directional hillshade and physical elevation ribbons
    # without adding lights, per-frame geometry, or touching the opaque terrain coordinates.
    var code := material.shader.code
    code = code.replace("mix(0.94, 1.06, primary_hillshade * 0.5 + 0.5)", "mix(0.88, 1.12, primary_hillshade * 0.5 + 0.5)")
    code = code.replace("vec3(0.014, 0.005, -0.012) * cross_hillshade", "vec3(0.024, 0.009, -0.018) * cross_hillshade")
    # Add one broad, continuous form-light lobe from the already encoded terrain grade. Unlike the
    # contour ribbons this reads on every crown/bowl face, so a low TV camera sees actual volume
    # instead of a uniformly tinted sheet. Flats stay neutral through the active blend.
    code = code.replace(
        "float cross_hillshade = clamp(cross_facing * slope_signal, -1.0, 1.0);",
        "float cross_hillshade = clamp(cross_facing * slope_signal, -1.0, 1.0);\n    float form_light = clamp(primary_hillshade * 0.78 + cross_hillshade * 0.22, -1.0, 1.0);\n    float form_lobe = mix(1.0, mix(0.82, 1.18, form_light * 0.5 + 0.5), active);"
    )
    code = code.replace("relief_color *= cross_tint;", "relief_color *= cross_tint;\n    relief_color *= form_lobe;")
    code = code.replace("elevation_ribbon * active * 0.34", "elevation_ribbon * active * 0.44")
    code = code.replace("0.018 + active * (0.072 + 0.012 * abs(height_bias))", "0.026 + active * (0.108 + 0.016 * abs(height_bias))")
    code = code.replace("elevation_ribbon * active * 0.22", "elevation_ribbon * active * 0.28")
    code = code.replace("min(0.32, base_alpha + ribbon_alpha)", "min(0.40, base_alpha + ribbon_alpha)")
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
        var surface_m := _v166_sample(0.0, forward_m).x
        var height_m := _terrain_relief_visual_height(surface_m) + RELIEF_AIM_CLEARANCE_M
        vertices.append(Vector3(-half_width, height_m, -forward_m))
        vertices.append(Vector3(half_width, height_m, -forward_m))

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
    # The old BoxMesh only sampled one midpoint height, so on crowns/bowls it visibly cut through the
    # green or floated above it. The guide now rides the exact same presentation relief as turf/grid.
    aim_line.mesh = _terrain_following_aim_mesh(distance_m)
    aim_line.position = Vector3.ZERO

func _terrain_relief_rebuild() -> void:
    super._terrain_relief_rebuild()
    if _v166_terrain_ready and aim_line != null:
        _update_aim_line(target_distance)

func _address_relief_camera_plan(ball_world: Vector3, distance_to_cup: float) -> Dictionary:
    var plan := super._address_relief_camera_plan(ball_world, distance_to_cup)
    var clearance_raise := float(plan.get("clearance_raise", 0.0))
    if clearance_raise <= RELIEF_DEPTH_CLEARANCE_GUARD_M:
        var position: Vector3 = plan["position"]
        # A small extra grazing-angle bias exposes crown/bowl silhouette and parallax. It is disabled
        # whenever the inherited sightline guard is already working hard, so the cup cannot disappear
        # behind an exaggerated ridge.
        position.y -= RELIEF_DEPTH_CAMERA_LOWER_M
        plan["position"] = position
    return plan

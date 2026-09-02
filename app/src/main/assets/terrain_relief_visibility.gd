extends "res://practice_trend_vector.gd"

# Presentation-only macro relief. Android GreenTerrain / GreenReadAdvisor remain authoritative.
# The renderer exaggerates only Y for TV readability; X/Z, solver paths and all physics stay exact.
# Important: the opaque Green mesh itself is rebuilt on the same presentation geometry. A translucent
# shell alone cannot show bowls because the physical turf in front of it depth-occludes depressions.

const RELIEF_GREEN_SIZE := Vector2(11.8, 34.5)
const RELIEF_SUB_X := 30
const RELIEF_SUB_Z := 86
const RELIEF_VISUAL_SCALE := 7.2
const RELIEF_EXTRA_CAP_M := 0.96
const RELIEF_MINOR_CONTOUR_M := 0.05
const RELIEF_MAJOR_CONTOUR_M := 0.10
const RELIEF_TRAIL_CLEARANCE_M := 0.0075

var _terrain_relief: MeshInstance3D
var _terrain_relief_mat: ShaderMaterial

func _terrain_relief_visual_offset(terrain_height_m: float) -> float:
    return clampf(
        terrain_height_m * (RELIEF_VISUAL_SCALE - 1.0),
        -RELIEF_EXTRA_CAP_M,
        RELIEF_EXTRA_CAP_M
    )

func _terrain_relief_geometry_height(terrain_height_m: float) -> float:
    return terrain_height_m + _terrain_relief_visual_offset(terrain_height_m)

func _terrain_relief_visual_height(terrain_height_m: float) -> float:
    return _terrain_relief_geometry_height(terrain_height_m) + 0.003

func _terrain_relief_visibility_strength(slope_percent: float, terrain_height_m: float) -> float:
    var slope_signal := smoothstep(0.10, 0.70, maxf(0.0, slope_percent))
    var elevation_signal := smoothstep(0.020, 0.11, absf(terrain_height_m))
    return maxf(slope_signal, elevation_signal * 0.58)

func _terrain_relief_hillshade_contrast(slope_percent: float) -> float:
    var slope_signal := smoothstep(0.10, 0.70, maxf(0.0, slope_percent))
    return lerpf(0.0, 0.24, slope_signal)

# Build actual presentation geometry instead of relying only on a transparent vertex-displaced shell.
# This keeps bowls/crowns visible through normal depth testing and gives the turf shader real silhouette
# and parallax. The source samples remain untouched and never flow back to Android.
func _terrain_relief_surface_mesh(size: Vector2, sub_x: int, sub_z: int, world_z_origin: float, encode_read: bool) -> ArrayMesh:
    var vertices := PackedVector3Array()
    var normals := PackedVector3Array()
    var uvs := PackedVector2Array()
    var colors := PackedColorArray()
    var indices := PackedInt32Array()
    var columns: int = sub_x + 1

    for iz in range(sub_z + 1):
        var fz: float = float(iz) / float(maxi(1, sub_z))
        var local_z: float = lerpf(-size.y * 0.5, size.y * 0.5, fz)
        var physics_y: float = -(world_z_origin + local_z)
        for ix in range(sub_x + 1):
            var fx: float = float(ix) / float(maxi(1, sub_x))
            var x: float = lerpf(-size.x * 0.5, size.x * 0.5, fx)
            var terrain := _v166_sample(x, physics_y)
            var visible_height := _terrain_relief_geometry_height(terrain.x)
            vertices.append(Vector3(x, visible_height, local_z))
            # Scale the authoritative local grade only for the presentation normal. This is evaluated
            # at terrain rebuild time, not per frame, and stays cheap on Forward Mobile.
            normals.append(Vector3(
                terrain.y * 0.01 * RELIEF_VISUAL_SCALE,
                1.0,
                -terrain.z * 0.01 * RELIEF_VISUAL_SCALE
            ).normalized())
            uvs.append(Vector2(fx, fz))
            if encode_read:
                colors.append(Color(
                    clampf(terrain.x / 4.0 + 0.5, 0.0, 1.0),
                    clampf(terrain.y / 24.0 + 0.5, 0.0, 1.0),
                    clampf(terrain.z / 24.0 + 0.5, 0.0, 1.0),
                    1.0
                ))
            else:
                colors.append(Color.WHITE)

    for iz in range(sub_z):
        for ix in range(sub_x):
            var a: int = iz * columns + ix
            var b: int = a + 1
            var c: int = a + columns
            var d: int = c + 1
            indices.append_array(PackedInt32Array([a, c, b, b, c, d]))

    var arrays: Array = []
    arrays.resize(Mesh.ARRAY_MAX)
    arrays[Mesh.ARRAY_VERTEX] = vertices
    arrays[Mesh.ARRAY_NORMAL] = normals
    arrays[Mesh.ARRAY_TEX_UV] = uvs
    arrays[Mesh.ARRAY_COLOR] = colors
    arrays[Mesh.ARRAY_INDEX] = indices
    var mesh := ArrayMesh.new()
    mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
    return mesh

func _terrain_relief_material() -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled, blend_mix, depth_draw_never;

varying float terrain_height;
varying float slope_pct;
varying vec2 local_slope;

void vertex() {
    terrain_height = (COLOR.r - 0.5) * 4.0;
    local_slope = (COLOR.gb - vec2(0.5)) * 24.0;
    slope_pct = length(local_slope);
    float relief_delta = clamp(
        terrain_height * (7.2 - 1.0),
        -0.96,
        0.96
    );
    VERTEX.y = terrain_height + relief_delta + 0.0030;
}

void fragment() {
    float slope_signal = smoothstep(0.10, 0.70, slope_pct);
    float elevation_signal = smoothstep(0.020, 0.11, abs(terrain_height));
    float active = max(slope_signal, elevation_signal * 0.58);
    float height_bias = clamp(terrain_height / 0.26, -1.0, 1.0);

    vec2 downhill = slope_pct > 0.001 ? local_slope / slope_pct : vec2(0.0, 1.0);
    float facing = dot(downhill, normalize(vec2(0.72, -0.69)));
    float cross_facing = dot(downhill, normalize(vec2(0.69, 0.72)));
    float primary_hillshade = clamp(facing * slope_signal, -1.0, 1.0);
    float cross_hillshade = clamp(cross_facing * slope_signal, -1.0, 1.0);
    float hillshade_exposure = mix(0.89, 1.11, primary_hillshade * 0.5 + 0.5);
    vec3 cross_tint = vec3(1.0) + vec3(0.022, 0.008, -0.018) * cross_hillshade;

    vec3 low_green = vec3(0.108, 0.276, 0.090);
    vec3 high_green = vec3(0.196, 0.405, 0.151);
    vec3 relief_color = mix(low_green, high_green, height_bias * 0.5 + 0.5);
    relief_color *= hillshade_exposure;
    relief_color *= cross_tint;

    float minor_phase = abs(fract(terrain_height / 0.05 + 0.5) - 0.5);
    float major_phase = abs(fract(terrain_height / 0.10 + 0.5) - 0.5);
    float minor_ribbon = 1.0 - smoothstep(0.050, 0.115, minor_phase);
    float major_ribbon = 1.0 - smoothstep(0.065, 0.145, major_phase);
    float elevation_ribbon = max(minor_ribbon * 0.50, major_ribbon);
    float ribbon_strength = elevation_ribbon * active * 0.42;
    vec3 ribbon_color = relief_color * 1.38 + vec3(0.028, 0.040, 0.012);
    relief_color = mix(relief_color, ribbon_color, ribbon_strength);

    ALBEDO = relief_color;
    float base_alpha = 0.022 + active * (0.096 + 0.018 * abs(height_bias));
    float ribbon_alpha = elevation_ribbon * active * 0.28;
    ALPHA = min(0.40, base_alpha + ribbon_alpha);
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.render_priority = 0
    return material

func _build_course() -> void:
    super._build_course()
    var green := get_node_or_null("Green") as MeshInstance3D
    if green == null:
        return

    _terrain_relief_mat = _terrain_relief_material()
    _terrain_relief = MeshInstance3D.new()
    _terrain_relief.name = "TerrainReliefVisibility"
    _terrain_relief.position = green.position
    _terrain_relief.material_override = _terrain_relief_mat
    _terrain_relief.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    _terrain_relief.mesh = _v166_surface_mesh(RELIEF_GREEN_SIZE, RELIEF_SUB_X, RELIEF_SUB_Z, green.position.z, true)
    add_child(_terrain_relief)

func _terrain_relief_ground_green_blades() -> void:
    var grass_node := _v163_green_blades as MultiMeshInstance3D
    if grass_node == null or grass_node.multimesh == null:
        return
    var mm := grass_node.multimesh
    for i in range(mm.instance_count):
        var transform := mm.get_instance_transform(i)
        var world_x: float = grass_node.position.x + transform.origin.x
        var physics_y: float = -(grass_node.position.z + transform.origin.z)
        transform.origin.y = _terrain_relief_geometry_height(_v166_sample(world_x, physics_y).x) + 0.0009
        mm.set_instance_transform(i, transform)

func _terrain_relief_rebuild() -> void:
    var green := get_node_or_null("Green") as MeshInstance3D
    if green == null:
        return
    # Replace the opaque turf geometry itself. This is the critical anti-occlusion fix: depressions
    # can no longer sit behind an un-exaggerated Green mesh and disappear from the address camera.
    green.mesh = _terrain_relief_surface_mesh(RELIEF_GREEN_SIZE, RELIEF_SUB_X, RELIEF_SUB_Z, green.position.z, false)
    if _v164_grid != null:
        _v164_grid.mesh = _terrain_relief_surface_mesh(
            Vector2(V164_GREEN_WIDTH, V164_GREEN_DEPTH),
            RELIEF_SUB_X,
            RELIEF_SUB_Z,
            V164_GREEN_CENTER_Z,
            true
        )
    _terrain_relief_ground_green_blades()
    if _terrain_relief != null:
        _terrain_relief.position = green.position
        _terrain_relief.mesh = _v166_surface_mesh(RELIEF_GREEN_SIZE, RELIEF_SUB_X, RELIEF_SUB_Z, green.position.z, true)

func _terrain_relief_sync_anchors(s: Dictionary) -> void:
    # The inherited renderer has already resolved bridge offsets, cup phases and ball pose. Apply
    # only the extra visual relief delta on top of those grounded positions; never reconstruct them
    # from snapshot Z, or the 2 cm cup bridge offset and cup-entry pose are lost.
    var ball_x: float = float(s.get("ballX", 0.0))
    var ball_y: float = float(s.get("ballY", 0.0))
    var ball_surface: float = _v166_sample(ball_x, ball_y).x
    var ball_delta: float = _terrain_relief_visual_offset(ball_surface)
    if ball != null:
        ball.position.y += ball_delta

    if _v155_ball_shadow != null:
        _v155_ball_shadow.position.y += ball_delta
    if _v162_ball_shadow != null:
        _v162_ball_shadow.position.y += ball_delta
    if _v173_ball_shadow != null:
        _v173_ball_shadow.position.y += ball_delta

    var cup_y: float = clampf(float(s.get("holeDistance", target_distance)), 0.5, 30.0)
    var cup_surface: float = _v166_sample(0.0, cup_y).x
    var cup_delta: float = _terrain_relief_visual_offset(cup_surface)
    if target_root != null:
        target_root.position.y += cup_delta

    if aim_line != null and aim_line.visible:
        var mid_y: float = cup_y * 0.5
        aim_line.position.y = _terrain_relief_visual_height(_v166_sample(0.0, mid_y).x) + 0.003

# X/Z and solver truth remain authoritative; only presentation Y follows the visible relief mesh.
func _v166_ribbon_mesh(points: Array, width: float) -> ArrayMesh:
    var vertices := PackedVector3Array()
    var indices := PackedInt32Array()
    if points.size() < 2:
        return ArrayMesh.new()
    var half_width: float = width * 0.5
    for i in range(points.size() - 1):
        var a := points[i] as Vector2
        var b := points[i + 1] as Vector2
        var render_dir := Vector2(b.x - a.x, -(b.y - a.y))
        if render_dir.length_squared() < 0.0000001:
            continue
        render_dir = render_dir.normalized()
        var perp := Vector2(-render_dir.y, render_dir.x) * half_width

        # Each ribbon shoulder sits at a different physical X/Y on a cross-slope. Sampling only the
        # centerline height made one edge clip into the visible green while the other floated above it,
        # which visually weakened both the recommended read and replay trail. Keep X/Z path truth
        # untouched and ground only each presentation vertex to its matching relief sample.
        var a_left_x := a.x + perp.x
        var a_left_y := a.y - perp.y
        var a_right_x := a.x - perp.x
        var a_right_y := a.y + perp.y
        var b_left_x := b.x + perp.x
        var b_left_y := b.y - perp.y
        var b_right_x := b.x - perp.x
        var b_right_y := b.y + perp.y
        var a_left_h := _terrain_relief_visual_height(_v166_sample(a_left_x, a_left_y).x) + RELIEF_TRAIL_CLEARANCE_M
        var a_right_h := _terrain_relief_visual_height(_v166_sample(a_right_x, a_right_y).x) + RELIEF_TRAIL_CLEARANCE_M
        var b_left_h := _terrain_relief_visual_height(_v166_sample(b_left_x, b_left_y).x) + RELIEF_TRAIL_CLEARANCE_M
        var b_right_h := _terrain_relief_visual_height(_v166_sample(b_right_x, b_right_y).x) + RELIEF_TRAIL_CLEARANCE_M
        var base: int = vertices.size()
        vertices.append(Vector3(a_left_x, a_left_h, -a_left_y))
        vertices.append(Vector3(a_right_x, a_right_h, -a_right_y))
        vertices.append(Vector3(b_left_x, b_left_h, -b_left_y))
        vertices.append(Vector3(b_right_x, b_right_h, -b_right_y))
        indices.append_array(PackedInt32Array([base, base + 2, base + 1, base + 1, base + 2, base + 3]))
    var arrays: Array = []
    arrays.resize(Mesh.ARRAY_MAX)
    arrays[Mesh.ARRAY_VERTEX] = vertices
    arrays[Mesh.ARRAY_INDEX] = indices
    var mesh := ArrayMesh.new()
    if vertices.size() >= 4:
        mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
    return mesh

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _terrain_relief_sync_anchors(s)

func _v166_refresh_terrain(key: String) -> void:
    var previous_key := _v166_terrain_key
    super._v166_refresh_terrain(key)
    if _v166_terrain_ready and _v166_terrain_key != previous_key:
        _terrain_relief_rebuild()

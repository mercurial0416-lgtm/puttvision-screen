extends "res://v160_real_green_cup.gd"

# V161: premium scene pass on top of the proven V160 mobile-safe path.
# Goals: richer depth, less low-poly repetition, calmer bentgrass mowing, dimensional sky,
# more convincing trees/clubhouse, and better cup/ball readability without risky HDRI,
# alpha-card foliage, dynamic shadow maps, anisotropic sampling, or external 3D models.

var _v161_cloud_shadow: StandardMaterial3D
var _v161_interior: StandardMaterial3D
var _v161_metal: StandardMaterial3D
var _v161_paver: StandardMaterial3D
var _v161_conifer_dark: StandardMaterial3D
var _v161_conifer_light: StandardMaterial3D

func _build_materials() -> void:
    super._build_materials()

    # V160 fixed the washed-out Android result. V161 reduces the repetitive stripe contrast and
    # lets dense fibre/normal detail do more of the work, closer to a maintained bentgrass green.
    if mat_green is ShaderMaterial:
        mat_green.set_shader_parameter("base_color", Vector3(0.205, 0.385, 0.170))
        mat_green.set_shader_parameter("lane_strength", 0.038)
        mat_green.set_shader_parameter("texture_scale", 118.0)
        mat_green.set_shader_parameter("normal_depth", 0.061)
        mat_green.set_shader_parameter("roughness_base", 0.815)
        mat_green.set_shader_parameter("micro_strength", 0.035)
    if mat_fringe is ShaderMaterial:
        mat_fringe.set_shader_parameter("base_color", Vector3(0.175, 0.335, 0.155))
        mat_fringe.set_shader_parameter("lane_strength", 0.026)
        mat_fringe.set_shader_parameter("texture_scale", 71.0)
        mat_fringe.set_shader_parameter("normal_depth", 0.090)
    if mat_rough is ShaderMaterial:
        mat_rough.set_shader_parameter("base_color", Vector3(0.135, 0.270, 0.145))
        mat_rough.set_shader_parameter("lane_strength", 0.012)
        mat_rough.set_shader_parameter("texture_scale", 38.0)
        mat_rough.set_shader_parameter("normal_depth", 0.135)

    # The old bright red guide dominates the composition. Keep it useful, but let the green read.
    if _v155_guide != null:
        _v155_guide.albedo_color = Color(0.72, 0.055, 0.045, 0.18)

    _v161_cloud_shadow = _v155_mat(Color("#c5d2d3"), 1.0)
    _v161_interior = _v155_mat(Color("#b58458"), 0.68)
    _v161_metal = _v155_mat(Color("#5e6667"), 0.44, 0.18)
    _v161_paver = _v155_mat(Color("#a49d8d"), 0.94)
    _v161_conifer_dark = _v155_mat(Color("#26482c"), 0.97)
    _v161_conifer_light = _v155_mat(Color("#355d35"), 0.95)

func _build_environment() -> void:
    super._build_environment()
    _v161_build_sky_shell()

func _v161_build_sky_shell() -> void:
    # Safe mesh-based sky gradient: visually richer than a flat BG color, but avoids the
    # ProceduralSkyMaterial/HDRI path that previously crashed affected Android devices.
    var sky_mesh := MeshInstance3D.new()
    sky_mesh.name = "V161GradientSkyShell"
    var sphere := SphereMesh.new()
    sphere.radius = 78.0
    sphere.height = 156.0
    sphere.radial_segments = 64
    sphere.rings = 32
    sky_mesh.mesh = sphere

    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_front, depth_draw_never;
void fragment() {
    float h = clamp(1.0 - UV.y, 0.0, 1.0);
    vec3 horizon = vec3(0.64, 0.77, 0.82);
    vec3 mid = vec3(0.31, 0.60, 0.78);
    vec3 zenith = vec3(0.12, 0.38, 0.65);
    vec3 col = mix(horizon, mid, smoothstep(0.42, 0.70, h));
    col = mix(col, zenith, smoothstep(0.68, 0.98, h));
    float haze = 1.0 - smoothstep(0.0, 0.16, abs(UV.y - 0.50));
    col = mix(col, vec3(0.77, 0.83, 0.82), haze * 0.16);
    ALBEDO = col;
}
"""
    var mat := ShaderMaterial.new()
    mat.shader = shader
    sky_mesh.material_override = mat
    sky_mesh.position = Vector3(0.0, -2.0, -15.0)
    add_child(sky_mesh)

func _v155_build_cloud(pos: Vector3, scale_value: float) -> void:
    # Restore clouds with actual shaded meshes. V158 disabled the old cloud path while isolating
    # the alpha-card crash; these use only opaque sphere geometry and are safe on that path.
    var cloud := Node3D.new()
    cloud.name = "V161VolumetricMeshCloud"
    cloud.position = pos
    horizon_root.add_child(cloud)

    var s := scale_value
    _v155_blob(cloud, Vector3(-0.95, -0.10, 0.03), Vector3(0.78, 0.18, 0.20) * s, _v161_cloud_shadow, 24, 10)
    _v155_blob(cloud, Vector3(-0.58, 0.02, 0.00), Vector3(0.75, 0.27, 0.24) * s, _v155_cloud, 28, 12)
    _v155_blob(cloud, Vector3(-0.12, 0.20, 0.01), Vector3(0.94, 0.39, 0.30) * s, _v155_cloud, 30, 14)
    _v155_blob(cloud, Vector3(0.43, 0.13, 0.02), Vector3(0.82, 0.34, 0.27) * s, _v155_cloud, 28, 12)
    _v155_blob(cloud, Vector3(0.88, -0.02, 0.04), Vector3(0.70, 0.23, 0.22) * s, _v155_cloud, 24, 10)
    _v155_blob(cloud, Vector3(0.02, -0.07, 0.02), Vector3(1.42, 0.19, 0.24) * s, _v155_cloud, 30, 12)

func _v155_build_tree(pos: Vector3, scale_value: float) -> void:
    # Irregular branch structure + many smaller leaf masses reads much more like a mature deciduous
    # tree than V160's few large spheres, while remaining opaque geometry on the safe renderer.
    var tree := Node3D.new()
    tree.name = "V161MatureTree3D"
    tree.position = pos
    tree.rotation_degrees.y = fmod(abs(pos.x * 41.0 + pos.z * 17.0), 360.0)
    horizon_root.add_child(tree)

    var s := scale_value
    _v155_shadow(tree, Vector3(0.38 * s, 0.006, 0.34 * s), Vector2(2.45, 0.82) * s, -26.0, 0.14)
    _v155_cylinder(tree, 0.095 * s, 1.68 * s, Vector3(0.0, 0.84 * s, 0.0), _v155_bark, 22)

    var b1 := _v155_cylinder(tree, 0.035 * s, 0.92 * s, Vector3(-0.18, 1.30, 0.00) * s, _v155_bark, 14)
    b1.rotation_degrees = Vector3(18.0, 12.0, -34.0)
    var b2 := _v155_cylinder(tree, 0.033 * s, 0.86 * s, Vector3(0.19, 1.36, 0.02) * s, _v155_bark, 14)
    b2.rotation_degrees = Vector3(-12.0, 31.0, 37.0)
    var b3 := _v155_cylinder(tree, 0.027 * s, 0.66 * s, Vector3(0.02, 1.57, -0.13) * s, _v155_bark, 12)
    b3.rotation_degrees = Vector3(39.0, -18.0, 8.0)

    var canopy := [
        [Vector3(-0.63,1.60, 0.05), Vector3(0.47,0.41,0.39), _v155_leaf_b],
        [Vector3(-0.35,1.76, 0.30), Vector3(0.50,0.43,0.40), _v155_leaf_a],
        [Vector3( 0.02,1.62, 0.40), Vector3(0.50,0.42,0.40), _v155_leaf_c],
        [Vector3( 0.43,1.69, 0.26), Vector3(0.52,0.45,0.42), _v155_leaf_b],
        [Vector3( 0.66,1.84,-0.02), Vector3(0.46,0.41,0.38), _v155_leaf_a],
        [Vector3( 0.48,1.73,-0.34), Vector3(0.45,0.40,0.38), _v155_leaf_c],
        [Vector3( 0.04,1.70,-0.43), Vector3(0.54,0.44,0.40), _v155_leaf_b],
        [Vector3(-0.43,1.78,-0.31), Vector3(0.48,0.41,0.39), _v155_leaf_a],
        [Vector3(-0.66,1.94,-0.09), Vector3(0.43,0.39,0.36), _v155_leaf_c],
        [Vector3(-0.36,2.08, 0.19), Vector3(0.53,0.46,0.42), _v155_leaf_b],
        [Vector3( 0.03,2.02, 0.28), Vector3(0.58,0.49,0.44), _v155_leaf_a],
        [Vector3( 0.40,2.09, 0.13), Vector3(0.51,0.45,0.41), _v155_leaf_c],
        [Vector3( 0.50,2.16,-0.19), Vector3(0.45,0.40,0.37), _v155_leaf_b],
        [Vector3( 0.08,2.21,-0.31), Vector3(0.51,0.44,0.40), _v155_leaf_a],
        [Vector3(-0.38,2.22,-0.20), Vector3(0.46,0.41,0.37), _v155_leaf_c],
        [Vector3(-0.24,2.42, 0.04), Vector3(0.48,0.42,0.39), _v155_leaf_b],
        [Vector3( 0.18,2.48, 0.02), Vector3(0.44,0.39,0.36), _v155_leaf_a],
        [Vector3( 0.00,2.66,-0.02), Vector3(0.34,0.31,0.29), _v155_leaf_c]
    ]
    for item in canopy:
        _v155_blob(tree, item[0] * s, item[1] * s, item[2], 22, 11)

func _v155_build_clubhouse(local_pos: Vector3) -> void:
    super._v155_build_clubhouse(local_pos)
    var house := horizon_root.get_node_or_null("ReferencePuttingLabClubhouse3D") as Node3D
    if house == null:
        return

    # Roof fascia/gutter and a deeper terrace stop the building reading as simple stacked boxes.
    _v155_box(house, Vector3(7.72, 0.065, 0.075), Vector3(0.05, 1.73, 1.36), _v161_metal)
    _v155_box(house, Vector3(5.30, 0.055, 1.14), Vector3(0.92, 0.060, 1.60), _v161_paver)
    _v155_box(house, Vector3(4.70, 0.075, 0.42), Vector3(1.14, 0.040, 2.20), _v155_stone_light)

    # Warm interior panels sit behind the glass, with varied darkness so the facade gains depth.
    var dark_inside := _v155_mat(Color("#302a24"), 0.84)
    for i in range(7):
        var x := -0.18 + float(i) * 0.54
        var inside_mat: Material = _v161_interior if i == 1 or i == 4 else dark_inside
        _v155_box(house, Vector3(0.40, 0.74, 0.018), Vector3(x, 0.77, 1.045), inside_mat)

    # Slender exterior lights + planters give the eye real scale cues.
    var lamp_mat := _v155_mat(Color("#dbc28a"), 0.52)
    for x in [-0.35, 1.42, 2.96]:
        _v155_box(house, Vector3(0.045, 0.16, 0.045), Vector3(float(x), 1.25, 1.145), _v161_metal)
        _v155_blob(house, Vector3(float(x), 1.18, 1.17), Vector3(0.045, 0.055, 0.035), lamp_mat, 14, 7)

    for x in [-0.75, 0.63, 2.08]:
        _v155_box(house, Vector3(0.38, 0.28, 0.34), Vector3(float(x), 0.16, 1.88), _v155_stone_light)
        _v155_blob(house, Vector3(float(x), 0.38, 1.88), Vector3(0.29, 0.22, 0.25), _v155_leaf_b, 18, 9)

func _build_horizon() -> void:
    super._build_horizon()
    _v161_add_distant_tree_line()

func _v161_add_distant_tree_line() -> void:
    # Low-cost conifers create several depth planes between the close trees and hills.
    for i in range(14):
        var x := -14.0 + float(i) * 2.15
        var z := -8.3 - float(i % 3) * 0.65
        var s := 0.72 + float((i * 7) % 5) * 0.08
        _v161_conifer(Vector3(x, 0.0, z), s, i % 2 == 0)

func _v161_conifer(pos: Vector3, scale_value: float, light: bool) -> void:
    var root := Node3D.new()
    root.name = "V161DistantConifer"
    root.position = pos
    horizon_root.add_child(root)
    var s := scale_value
    _v155_cylinder(root, 0.055 * s, 1.20 * s, Vector3(0.0, 0.60 * s, 0.0), _v155_bark, 12)
    var mat: Material = _v161_conifer_light if light else _v161_conifer_dark
    for data in [
        [0.58, 0.70, 1.00],
        [0.46, 0.58, 1.32],
        [0.34, 0.45, 1.60],
        [0.22, 0.31, 1.82]
    ]:
        var crown := MeshInstance3D.new()
        var cone := CylinderMesh.new()
        cone.top_radius = 0.0
        cone.bottom_radius = float(data[0]) * s
        cone.height = float(data[1]) * s
        cone.radial_segments = 16
        crown.mesh = cone
        crown.material_override = mat
        crown.position.y = float(data[2]) * s
        root.add_child(crown)

func _build_target() -> void:
    super._build_target()
    # Add a subtle metal collar on the flagstick and a deeper cup-floor shadow. These are tiny but
    # materially help the close-up camera read the cup as a physical hole rather than a decal.
    var collar := _v155_cylinder(target_root, 0.0083, 0.025, Vector3(0.0, 0.020, 0.0), _v161_metal, 20)
    collar.name = "V161FlagstickFerrule"

    var floor := MeshInstance3D.new()
    floor.name = "V161CupFloorShadow"
    var cm := CylinderMesh.new()
    cm.top_radius = 0.042
    cm.bottom_radius = 0.040
    cm.height = 0.006
    cm.radial_segments = 42
    floor.mesh = cm
    floor.material_override = _v155_mat(Color("#10120e"), 1.0)
    floor.position.y = -0.048
    target_root.add_child(floor)

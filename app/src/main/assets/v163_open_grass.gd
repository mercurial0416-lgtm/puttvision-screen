extends "res://v162_final_cleanup.gd"

# V163: runtime-only putting-grass pass derived from the MIT-licensed SimpleGrassTextured
# MultiMesh + vertex-wind approach. We intentionally do NOT vendor its editor plugin, singleton,
# collision painter, textures, or interactive system: Android V135-V137 physics stay authoritative.
# Third-party notice: res://third_party/SimpleGrassTextured_MIT.txt

var _v163_green_blade_mat: ShaderMaterial
var _v163_fringe_blade_mat: ShaderMaterial
var _v163_green_blades: MultiMeshInstance3D
var _v163_fringe_blades: MultiMeshInstance3D

func _v163_hash01(i: int, salt: int) -> float:
    var v: float = sin(float(i * 127 + salt * 311) * 12.9898) * 43758.5453
    return v - floor(v)

func _v163_cluster_mesh(height_m: float, width_m: float) -> ArrayMesh:
    var vertices := PackedVector3Array()
    var normals := PackedVector3Array()
    var uvs := PackedVector2Array()
    var indices := PackedInt32Array()
    for blade_index in range(3):
        var angle: float = deg_to_rad(float(blade_index) * 60.0)
        var right := Vector3(cos(angle), 0.0, sin(angle)) * (width_m * 0.5)
        var normal := Vector3(-sin(angle), 0.06, cos(angle)).normalized()
        var base: int = vertices.size()
        vertices.append(-right)
        vertices.append(right)
        vertices.append(right + Vector3.UP * height_m)
        vertices.append(-right + Vector3.UP * height_m)
        for _j in range(4):
            normals.append(normal)
        uvs.append(Vector2(0.0, 0.0))
        uvs.append(Vector2(1.0, 0.0))
        uvs.append(Vector2(1.0, 1.0))
        uvs.append(Vector2(0.0, 1.0))
        indices.append_array(PackedInt32Array([base, base + 1, base + 2, base, base + 2, base + 3]))
    var arrays: Array = []
    arrays.resize(Mesh.ARRAY_MAX)
    arrays[Mesh.ARRAY_VERTEX] = vertices
    arrays[Mesh.ARRAY_NORMAL] = normals
    arrays[Mesh.ARRAY_TEX_UV] = uvs
    arrays[Mesh.ARRAY_INDEX] = indices
    var mesh := ArrayMesh.new()
    mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
    return mesh

func _v163_blade_material(base_color: Color, tip_color: Color, wind_strength: float, roughness_value: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx, cull_disabled, world_vertex_coords;
uniform vec3 base_color : source_color = vec3(0.08, 0.20, 0.07);
uniform vec3 tip_color : source_color = vec3(0.22, 0.38, 0.14);
uniform float wind_strength = 0.00035;
uniform float roughness_value = 0.90;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
varying float blade_height;
varying float blade_random;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }
void vertex(){
    float tip = clamp(UV.y, 0.0, 1.0);
    float h = hash21(floor(VERTEX.xz * 21.0));
    float gust = sin(TIME * 0.72 + VERTEX.x * 1.55 + VERTEX.z * 1.18 + h * 6.2831853);
    float flutter = sin(TIME * 1.83 + VERTEX.z * 3.10 + h * 11.7) * 0.35;
    float bend = (gust * 0.72 + flutter * 0.28) * wind_strength * tip * tip;
    VERTEX.x += bend * 0.72;
    VERTEX.z += bend * 0.36;
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
    blade_height = tip;
    blade_random = h;
}
void fragment(){
    float t = smoothstep(0.0, 1.0, blade_height);
    vec3 col = mix(base_color, tip_color, t * 0.78);
    float root_ao = mix(0.64, 1.0, smoothstep(0.02, 0.72, t));
    col *= root_ao * (0.94 + blade_random * 0.10);
    ALBEDO = col;
    ROUGHNESS = clamp(roughness_value - t * 0.055, 0.78, 0.97);
    SPECULAR = 0.11 + t * 0.035;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("base_color", Vector3(base_color.r, base_color.g, base_color.b))
    material.set_shader_parameter("tip_color", Vector3(tip_color.r, tip_color.g, tip_color.b))
    material.set_shader_parameter("wind_strength", wind_strength)
    material.set_shader_parameter("roughness_value", roughness_value)
    return material

func _v163_build_patch(name_value: String, mesh: Mesh, material: Material, instance_count: int, x_min: float, x_max: float, z_near: float, z_far: float, y_base: float, seed: int, edge_mode: bool = false) -> MultiMeshInstance3D:
    var mm := MultiMesh.new()
    mm.transform_format = MultiMesh.TRANSFORM_3D
    mm.mesh = mesh
    mm.instance_count = instance_count
    for i in range(instance_count):
        var rx: float = _v163_hash01(i + seed * 13, seed + 3)
        var rz: float = _v163_hash01(i + seed * 31, seed + 11)
        var rr: float = _v163_hash01(i + seed * 47, seed + 19)
        var rs: float = _v163_hash01(i + seed * 61, seed + 29)
        var x: float
        if edge_mode:
            var side_sign: float = -1.0 if (i & 1) == 0 else 1.0
            x = side_sign * lerp(abs(x_min), abs(x_max), rx)
        else:
            x = lerp(x_min, x_max, rx)
        var z: float = lerp(z_near, z_far, rz)
        var basis := Basis(Vector3.UP, rr * TAU)
        basis = basis.scaled(Vector3(0.78 + rs * 0.42, 0.78 + _v163_hash01(i + seed * 73, seed + 37) * 0.48, 0.78 + rs * 0.42))
        mm.set_instance_transform(i, Transform3D(basis, Vector3(x, y_base, z)))
    mm.custom_aabb = AABB(Vector3(-12.0, -1.8, -12.0), Vector3(24.0, 3.6, 14.0))
    var node := MultiMeshInstance3D.new()
    node.name = name_value
    node.multimesh = mm
    node.material_override = material
    node.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    add_child(node)
    return node

func _build_course() -> void:
    super._build_course()
    _v163_green_blade_mat = _v163_blade_material(Color("#17301a"), Color("#416137"), 0.00024, 0.905)
    _v163_fringe_blade_mat = _v163_blade_material(Color("#142a18"), Color("#3b5c37"), 0.00055, 0.925)
    var green_cluster := _v163_cluster_mesh(0.0062, 0.00135)
    var fringe_cluster := _v163_cluster_mesh(0.0115, 0.00185)
    _v163_green_blades = _v163_build_patch("V163SimpleGrassGreenBlades", green_cluster, _v163_green_blade_mat, 17500, -6.45, 6.45, 0.55, -8.70, 0.0011, 163)
    _v163_fringe_blades = _v163_build_patch("V163SimpleGrassFringeBlades", fringe_cluster, _v163_fringe_blade_mat, 6200, 6.65, 8.55, 0.35, -9.30, -0.0105, 311, true)

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    var side: float = float(s.get("sideSlope", 0.0)) * 0.01
    var longitudinal: float = float(s.get("longSlope", 0.0)) * 0.01
    if _v163_green_blade_mat != null:
        _v163_green_blade_mat.set_shader_parameter("side_slope", side)
        _v163_green_blade_mat.set_shader_parameter("long_slope", longitudinal)
    if _v163_fringe_blade_mat != null:
        _v163_fringe_blade_mat.set_shader_parameter("side_slope", side)
        _v163_fringe_blade_mat.set_shader_parameter("long_slope", longitudinal)

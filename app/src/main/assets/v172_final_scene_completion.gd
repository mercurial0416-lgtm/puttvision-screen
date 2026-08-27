extends "res://v171_commercial_completion.gd"

# V172 final scene pass: replace sphere-cluster foliage with very low-poly crossed leaf cards and
# add a short read-to-address camera settle. Rendering only; Android physics remains authoritative.

var _v172_read_timer := 1.6
var _v172_last_terrain_key := ""

func _v172_leaf_card_mesh() -> ArrayMesh:
    var vertices := PackedVector3Array()
    var normals := PackedVector3Array()
    var uvs := PackedVector2Array()
    var indices := PackedInt32Array()
    for plane in range(3):
        var angle: float = deg_to_rad(float(plane) * 60.0)
        var right := Vector3(cos(angle), 0.0, sin(angle)) * 0.50
        var up := Vector3.UP * 0.42
        var base: int = vertices.size()
        vertices.append(-right - up)
        vertices.append(right - up)
        vertices.append(right + up)
        vertices.append(-right + up)
        var normal := Vector3(-sin(angle), 0.08, cos(angle)).normalized()
        for _i in range(4):
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

func _v172_leaf_material(phase: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled;
uniform vec3 leaf_dark : source_color = vec3(0.12, 0.20, 0.14);
uniform vec3 leaf_light : source_color = vec3(0.30, 0.38, 0.25);
uniform float phase = 0.0;
varying float world_noise;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
void vertex(){
    float w = sin(TIME * 0.42 + VERTEX.x * 0.73 + VERTEX.z * 0.61 + phase);
    VERTEX.x += w * 0.006 * smoothstep(0.35, 1.0, UV.y);
    world_noise = hash21(floor(VERTEX.xz * 3.1) + phase);
}
void fragment(){
    vec2 p = (UV - vec2(0.5)) * 2.0;
    float ellipse = length(p * vec2(0.74, 1.0));
    float edge = 1.0 - smoothstep(0.72, 1.02, ellipse);
    float serration = hash21(floor(UV * vec2(13.0, 11.0)) + phase);
    float inner_hole = hash21(floor(UV * vec2(7.0, 6.0)) + phase * 2.7);
    if (edge < 0.32 || (inner_hole < 0.105 && ellipse > 0.25) || (serration < 0.035 && ellipse > 0.60)) discard;
    float light = clamp(0.24 + UV.y * 0.45 + world_noise * 0.24, 0.0, 1.0);
    ALBEDO = mix(leaf_dark, leaf_light, light);
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    var hue: float = fmod(abs(phase) * 0.071, 0.08)
    material.set_shader_parameter("leaf_dark", Vector3(0.105 + hue * 0.25, 0.175 + hue * 0.45, 0.120 + hue * 0.20))
    material.set_shader_parameter("leaf_light", Vector3(0.265 + hue * 0.30, 0.345 + hue * 0.35, 0.225 + hue * 0.25))
    material.set_shader_parameter("phase", phase)
    return material

# Overrides the inherited sphere-cluster canopy. Each foliage instance is now only six triangles
# (three crossed cards) instead of a small sphere, improving both silhouette and Forward Mobile cost.
func _v162_leaf_multimesh(parent: Node3D, _material: Material, s: float, count: int, phase: float, y_base: float, radius: float) -> void:
    var mesh := _v172_leaf_card_mesh()
    var mm := MultiMesh.new()
    mm.transform_format = MultiMesh.TRANSFORM_3D
    mm.mesh = mesh
    var leaf_count: int = int(round(float(count) * 1.36))
    mm.instance_count = leaf_count

    for i in range(leaf_count):
        var fi: float = float(i)
        var angle: float = deg_to_rad(fmod(fi * 137.507 + phase * 63.0, 360.0))
        var shell: float = 0.26 + float((i * 11) % 19) * 0.038
        var x: float = cos(angle) * radius * shell
        var z: float = sin(angle) * radius * shell * (0.72 + float(i % 5) * 0.035)
        var crown: float = 1.0 - min(1.0, shell * 0.88)
        var y: float = y_base + crown * 0.34 + sin(fi * 1.71 + phase) * 0.15 + float(i % 4) * 0.045
        var width: float = 0.30 + float((i * 7) % 11) * 0.018
        var height: float = 0.24 + float((i * 5) % 9) * 0.017
        var depth: float = 0.28 + float((i * 3) % 10) * 0.016
        var basis := Basis.IDENTITY
        basis = basis.rotated(Vector3.UP, angle * 0.31 + phase * 0.27)
        basis = basis.rotated(Vector3.RIGHT, sin(fi * 0.77 + phase) * 0.13)
        basis = basis.scaled(Vector3(width * s, height * s, depth * s))
        mm.set_instance_transform(i, Transform3D(basis, Vector3(x, y, z) * s))

    var instance := MultiMeshInstance3D.new()
    instance.name = "V172LeafCardScatter"
    instance.multimesh = mm
    instance.material_override = _v172_leaf_material(phase)
    instance.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    parent.add_child(instance)

func _process(delta: float) -> void:
    super._process(delta)
    if not _v171_last_running and _v171_replay_remaining <= 0.0 and _v172_read_timer > 0.0:
        _v172_read_timer = max(0.0, _v172_read_timer - delta)

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    if _v171_replay_remaining > 0.0 or running or _v172_read_timer <= 0.0:
        super._update_camera(ball_world, running, phase, distance_to_cup, immediate, delta)
        return

    # Start high enough to read the complete break, then settle into V167's lower address camera.
    var look_distance: float = min(7.2, max(3.2, target_distance * 0.70))
    var desired_look := Vector3(0.0, 0.045, -look_distance)
    var desired_pos := Vector3(0.88, 1.05, 2.38)
    var pos_alpha: float = 1.0 if immediate else 1.0 - exp(-delta * 3.8)
    var look_alpha: float = 1.0 if immediate else 1.0 - exp(-delta * 4.4)
    camera_pos = camera_pos.lerp(desired_pos, pos_alpha)
    camera_look = camera_look.lerp(desired_look, look_alpha)
    camera.position = camera_pos
    camera.fov = lerp(camera.fov, 43.5, 1.0 if immediate else min(1.0, delta * 3.5))
    camera.look_at(camera_look, Vector3.UP)

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    var incoming_key: String = str(s.get("terrainKey", ""))
    if incoming_key != "" and incoming_key != _v172_last_terrain_key:
        _v172_last_terrain_key = incoming_key
        _v172_read_timer = 1.6
    super._apply_snapshot(s, immediate, delta)
    if bool(s.get("running", false)):
        _v172_read_timer = 0.0

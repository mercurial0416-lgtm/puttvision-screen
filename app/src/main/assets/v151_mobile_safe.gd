extends "res://v143_tv.gd"

# V151: conservative Android-safe renderer after real-device V143 scene SIGSEGV.
# Keep the production snapshot/camera/HUD/ball/cup contract, but remove the render resources
# most likely to trigger a mobile-driver/native crash: external turf textures, HDR panorama,
# anisotropic texture sampling, procedural sky shaders, Sprite3D alpha impostors and shadows.

func _safe_grass(tint_color: Color) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded;
uniform vec3 tint : source_color = vec3(0.22, 0.43, 0.24);
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
void vertex(){
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
}
void fragment(){
    ALBEDO = tint;
    ROUGHNESS = 1.0;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("tint", Vector3(tint_color.r, tint_color.g, tint_color.b))
    return material

func _build_materials() -> void:
    mat_green = _safe_grass(Color("#47764a"))
    mat_fringe = _safe_grass(Color("#3a653f"))
    mat_rough = _safe_grass(Color("#2d5134"))
    mat_white = _pbr(Color("#ecece6"), 0.72, 0.0)
    mat_dark = _pbr(Color("#111818"), 0.92, 0.0)
    mat_red = _pbr(Color("#c92b32"), 0.66, 0.0)
    mat_house = _pbr(Color("#aaa494"), 0.90, 0.0)
    mat_roof = _pbr(Color("#252c30"), 0.88, 0.0)
    mat_window = _pbr(Color("#173746"), 0.38, 0.0)
    mat_stone = _pbr(Color("#5f5d57"), 0.96, 0.0)

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "MobileSafeEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("#5d8fb4")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#d7e2e6")
    env.ambient_light_energy = 0.72
    env_node.environment = env
    add_child(env_node)

    var sun := DirectionalLight3D.new()
    sun.name = "MobileSafeSun"
    sun.light_color = Color("#fff1d8")
    sun.light_energy = 0.92
    sun.shadow_enabled = false
    sun.rotation_degrees = Vector3(-46.0, -34.0, 0.0)
    add_child(sun)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 46.5
    camera.near = 0.025
    camera.far = 120.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

func _build_course() -> void:
    _course_plane("Rough", Vector2(42.0, 72.0), Vector3(0.0, -0.032, -31.0), mat_rough, 2, 8)
    _course_plane("Fringe", Vector2(13.8, 36.0), Vector3(0.0, -0.014, -15.0), mat_fringe, 2, 10)
    _course_plane("Green", Vector2(11.8, 34.5), Vector3(0.0, 0.0, -14.25), mat_green, 4, 16)

    aim_line = MeshInstance3D.new()
    aim_line.name = "AimLine"
    var aim_mat := StandardMaterial3D.new()
    aim_mat.albedo_color = Color(0.72, 0.08, 0.075, 0.72)
    aim_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    aim_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    aim_line.material_override = aim_mat
    add_child(aim_line)
    _update_aim_line(DEFAULT_DISTANCE)

func _build_horizon() -> void:
    horizon_root = Node3D.new()
    horizon_root.name = "MobileSafeHorizon"
    add_child(horizon_root)
    _build_clubhouse(Vector3(-3.8, 0.0, -2.3))
    _build_fence(-3.65)

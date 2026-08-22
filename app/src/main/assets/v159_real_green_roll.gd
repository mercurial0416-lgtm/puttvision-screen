extends "res://v158_match_safe.gd"

# V159: make the live surface read as a tightly cut putting green and make ball travel obvious.
# The renderer stays on the mobile-safe path: no HDRI, dynamic shadows, Sprite3D or anisotropic sampling.

func _v159_green_material(tile_scale: float, base_color: Color, stripe_strength: float, normal_depth: float, roughness_base: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform sampler2D turf_albedo : source_color;
uniform sampler2D turf_normal : hint_normal;
uniform sampler2D turf_roughness;
uniform vec3 base_color : source_color = vec3(0.31, 0.47, 0.24);
uniform float tile_scale = 72.0;
uniform float stripe_strength = 0.024;
uniform float normal_depth = 0.055;
uniform float roughness_base = 0.86;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise21(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void vertex() {
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
}

void fragment() {
    vec2 tex_uv = UV * tile_scale;
    vec3 source = texture(turf_albedo, tex_uv).rgb;
    float source_luma = dot(source, vec3(0.2126, 0.7152, 0.0722));

    // 12 broad mowing passes across the long green: visible like a real cut, never giant polygons.
    float mow_wave = 0.5 + 0.5 * sin(UV.y * 12.0 * 6.2831853);
    float mow = smoothstep(0.16, 0.84, mow_wave);
    float mow_light = mix(1.0 - stripe_strength, 1.0 + stripe_strength, mow);

    // Use the CC0 lawn map only as high-frequency fibre variation.  Do not reproduce its coarse leaves.
    float texture_micro = (source_luma - 0.50) * 0.050;
    float broad = (noise21(UV * vec2(22.0, 62.0)) - 0.5) * 0.018;
    float medium = (noise21(UV * vec2(160.0, 520.0)) - 0.5) * 0.018;
    float grain = (hash21(floor(UV * vec2(1800.0, 4200.0))) - 0.5) * 0.020;
    float blades = sin(UV.x * 1550.0 + UV.y * 241.0) * 0.0045;

    float brightness = mow_light + texture_micro + broad + medium + grain + blades;
    vec3 col = base_color * brightness;
    col += vec3(0.012, 0.013, 0.004) * max(0.0, medium * 18.0);
    ALBEDO = col;

    float rough_tex = texture(turf_roughness, tex_uv).r;
    ROUGHNESS = clamp(roughness_base + (rough_tex - 0.5) * 0.055 + (0.5 - mow) * 0.018, 0.79, 0.94);
    SPECULAR = 0.12;

    vec3 ntex = texture(turf_normal, tex_uv).rgb;
    NORMAL_MAP = ntex;
    NORMAL_MAP_DEPTH = normal_depth;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader

    var albedo_path := "res://v143_assets/turf/albedo.png"
    var normal_path := "res://v143_assets/turf/normal.png"
    var rough_path := "res://v143_assets/turf/roughness.png"
    if ResourceLoader.exists(albedo_path) and ResourceLoader.exists(normal_path) and ResourceLoader.exists(rough_path):
        material.set_shader_parameter("turf_albedo", load(albedo_path))
        material.set_shader_parameter("turf_normal", load(normal_path))
        material.set_shader_parameter("turf_roughness", load(rough_path))
    else:
        return _v155_grass(base_color, stripe_strength, 0.020)

    material.set_shader_parameter("base_color", Vector3(base_color.r, base_color.g, base_color.b))
    material.set_shader_parameter("tile_scale", tile_scale)
    material.set_shader_parameter("stripe_strength", stripe_strength)
    material.set_shader_parameter("normal_depth", normal_depth)
    material.set_shader_parameter("roughness_base", roughness_base)
    return material

func _build_materials() -> void:
    super._build_materials()
    # Fine bent-grass proportions: high-frequency fibre, very shallow normal, subdued olive green.
    mat_green = _v159_green_material(78.0, Color("#567d42"), 0.024, 0.050, 0.865)
    mat_fringe = _v159_green_material(38.0, Color("#4a733d"), 0.020, 0.075, 0.885)
    mat_rough = _v159_green_material(17.0, Color("#3e6839"), 0.014, 0.115, 0.905)

func _build_ball() -> void:
    super._build_ball()
    # A visible three-dot alignment mark rotates with the ball quaternion.  Translation and spin are
    # therefore both obvious even on a TV viewed several metres away.
    if ball_marker != null:
        ball_marker.scale = Vector3(1.55, 1.20, 1.55)
    for z in [-0.0070, 0.0, 0.0070]:
        var marker := MeshInstance3D.new()
        marker.name = "V159RollMark"
        var sphere := SphereMesh.new()
        sphere.radius = 0.0025
        sphere.height = 0.0048
        sphere.radial_segments = 12
        sphere.rings = 7
        marker.mesh = sphere
        marker.material_override = mat_dark
        var zz: float = float(z)
        var yy: float = sqrt(max(0.000001, BALL_RADIUS * BALL_RADIUS - zz * zz))
        marker.position = Vector3(0.0, yy + 0.0010, zz)
        ball.add_child(marker)

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    var desired_pos: Vector3
    var desired_look: Vector3
    var desired_fov: float = 42.5
    var cup_ground_y: float = last_cup_z - 0.020
    var cup_world := Vector3(0.0, cup_ground_y + 0.006, -target_distance)
    var cup_action: bool = phase == "RIM" or phase == "DROP" or phase == "SETTLED" or distance_to_cup < 0.70

    if cup_action:
        desired_pos = cup_world + Vector3(0.055, 0.285, 0.77)
        desired_look = cup_world + Vector3(0.0, 0.017, -0.012)
        desired_fov = 38.0
    elif running:
        # V158 chased the ball almost 1:1, which visually cancelled its translation.  Keep the
        # broadcast camera near the launch side and only pan gently so travel is unmistakable.
        var progress: float = clamp((-ball_world.z) / max(0.50, target_distance), 0.0, 1.0)
        desired_pos = Vector3(ball_world.x * 0.08, 0.405 + progress * 0.025, 1.50 - progress * 0.30)
        var look_z: float = lerp(-1.70, -max(2.30, target_distance - 0.70), min(1.0, progress * 0.72))
        desired_look = Vector3(ball_world.x * 0.16, 0.050, look_z)
        desired_fov = 41.5
    else:
        desired_pos = Vector3(0.0, 0.40, 1.54)
        var look_distance: float = min(6.1, max(2.70, target_distance * 0.60))
        desired_look = Vector3(0.0, 0.060, -look_distance)
        desired_fov = 42.5

    if immediate:
        camera_pos = desired_pos
        camera_look = desired_look
    else:
        var pos_alpha: float = 1.0 - exp(-delta * (7.2 if cup_action else 4.2))
        var look_alpha: float = 1.0 - exp(-delta * (8.7 if cup_action else 5.2))
        camera_pos = camera_pos.lerp(desired_pos, pos_alpha)
        camera_look = camera_look.lerp(desired_look, look_alpha)
    camera.position = camera_pos
    camera.fov = lerp(camera.fov, desired_fov, 1.0 if immediate else min(1.0, delta * 5.0))
    camera.look_at(camera_look, Vector3.UP)

extends "res://practice_trend_vector.gd"

# Presentation-only macro relief pass. The exact Android terrain mesh remains authoritative;
# this layer only makes its existing height/slope field readable on a TV without exaggerating
# geometry or feeding anything back into physics, GreenTerrain, GreenReadAdvisor, aim or scoring.

const RELIEF_GREEN_SIZE := Vector2(11.8, 34.5)
const RELIEF_SUB_X := 30
const RELIEF_SUB_Z := 86
const RELIEF_SURFACE_LIFT := 0.0016

var _terrain_relief: MeshInstance3D
var _terrain_relief_mat: ShaderMaterial
var _terrain_relief_light: DirectionalLight3D

func _terrain_relief_material() -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled, blend_mix, depth_draw_never;

varying float terrain_height;
varying float slope_pct;

void vertex() {
    terrain_height = (COLOR.r - 0.5) * 4.0;
    vec2 local_slope = (COLOR.gb - vec2(0.5)) * 24.0;
    slope_pct = length(local_slope);
    VERTEX.y += 0.0016;
}

void fragment() {
    // A soft elevation wash plus broad 10 cm contour sheen. It is intentionally subtle enough
    // to remain turf, but strong enough that a 1-2% plane no longer reads as dead flat on a TV.
    float active = smoothstep(0.18, 0.85, slope_pct);
    float height_bias = clamp(terrain_height / 0.34, -1.0, 1.0);
    float contour_wave = 0.5 + 0.5 * sin(terrain_height * 62.831853);
    float contour_soft = smoothstep(0.24, 0.78, contour_wave);

    vec3 low_green = vec3(0.055, 0.145, 0.072);
    vec3 high_green = vec3(0.205, 0.300, 0.112);
    vec3 relief_color = mix(low_green, high_green, height_bias * 0.5 + 0.5);
    relief_color *= mix(0.94, 1.07, contour_soft);

    ALBEDO = relief_color;
    ALPHA = active * (0.075 + 0.035 * abs(height_bias) + 0.040 * contour_soft);
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

func _build_environment() -> void:
    super._build_environment()
    # Shadowless grazing key: one cheap Forward-Mobile-safe directional light that makes the exact
    # macro normals from GreenTerrain legible without enabling dynamic shadows or extra geometry.
    _terrain_relief_light = DirectionalLight3D.new()
    _terrain_relief_light.name = "TerrainReliefGrazingLight"
    _terrain_relief_light.light_color = Color("#f4e6bf")
    _terrain_relief_light.light_energy = 0.24
    _terrain_relief_light.shadow_enabled = false
    _terrain_relief_light.rotation_degrees = Vector3(-21.0, 68.0, 0.0)
    add_child(_terrain_relief_light)

func _terrain_relief_rebuild() -> void:
    if _terrain_relief == null:
        return
    var green := get_node_or_null("Green") as MeshInstance3D
    if green == null:
        return
    _terrain_relief.position = green.position
    _terrain_relief.mesh = _v166_surface_mesh(RELIEF_GREEN_SIZE, RELIEF_SUB_X, RELIEF_SUB_Z, green.position.z, true)

func _v166_refresh_terrain(key: String) -> void:
    var previous_key := _v166_terrain_key
    super._v166_refresh_terrain(key)
    if _v166_terrain_ready and _v166_terrain_key != previous_key:
        _terrain_relief_rebuild()

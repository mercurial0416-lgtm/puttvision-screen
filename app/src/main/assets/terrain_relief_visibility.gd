extends "res://practice_trend_vector.gd"

# Presentation-only macro relief shell. Android GreenTerrain / GreenReadAdvisor remain authoritative.
# This shell deliberately exaggerates vertical displacement for TV readability only; it never feeds
# coordinates back into physics, aim, scoring, replay truth, or the authoritative terrain payload.

const RELIEF_GREEN_SIZE := Vector2(11.8, 34.5)
const RELIEF_SUB_X := 30
const RELIEF_SUB_Z := 86
const RELIEF_VISUAL_SCALE := 3.2
const RELIEF_EXTRA_CAP_M := 0.55

var _terrain_relief: MeshInstance3D
var _terrain_relief_mat: ShaderMaterial

func _terrain_relief_visual_height(terrain_height_m: float) -> float:
    var relief_delta := clampf(
        terrain_height_m * (RELIEF_VISUAL_SCALE - 1.0),
        -RELIEF_EXTRA_CAP_M,
        RELIEF_EXTRA_CAP_M
    )
    return terrain_height_m + relief_delta + 0.003

func _terrain_relief_visibility_strength(slope_percent: float, terrain_height_m: float) -> float:
    var slope_signal := smoothstep(0.18, 0.90, maxf(0.0, slope_percent))
    var elevation_signal := smoothstep(0.035, 0.14, absf(terrain_height_m))
    return maxf(slope_signal, elevation_signal * 0.48)

func _terrain_relief_hillshade_contrast(slope_percent: float) -> float:
    var slope_signal := smoothstep(0.18, 0.90, maxf(0.0, slope_percent))
    return lerpf(0.0, 0.22, slope_signal)

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
    // Bounded presentation-only relief: enough for a real TV silhouette without turning
    // steep/custom greens into giant floating slabs.
    float relief_delta = clamp(
        terrain_height * (3.2 - 1.0),
        -0.55,
        0.55
    );
    VERTEX.y = terrain_height + relief_delta + 0.0030;
}

void fragment() {
    float slope_signal = smoothstep(0.18, 0.90, slope_pct);
    float elevation_signal = smoothstep(0.035, 0.14, abs(terrain_height));
    float active = max(slope_signal, elevation_signal * 0.48);
    float height_bias = clamp(terrain_height / 0.34, -1.0, 1.0);

    // Keep directional shading secondary to the actual geometry. This avoids the dark painted
    // 'blob' look that made the first relief pass feel detached from the turf in replay preview.
    vec2 downhill = slope_pct > 0.001 ? local_slope / slope_pct : vec2(0.0, 1.0);
    float facing = dot(downhill, normalize(vec2(0.72, -0.69)));
    float cross_facing = dot(downhill, normalize(vec2(0.69, 0.72)));
    float primary_hillshade = clamp(facing * slope_signal, -1.0, 1.0);
    float cross_hillshade = clamp(cross_facing * slope_signal, -1.0, 1.0);
    float hillshade_exposure = mix(0.84, 1.16, primary_hillshade * 0.5 + 0.5);
    vec3 cross_tint = vec3(1.0) + vec3(0.040, 0.012, -0.035) * cross_hillshade;

    vec3 low_green = vec3(0.070, 0.165, 0.070);
    vec3 high_green = vec3(0.165, 0.275, 0.105);
    vec3 relief_color = mix(low_green, high_green, height_bias * 0.5 + 0.5);
    relief_color *= hillshade_exposure;
    relief_color *= cross_tint;

    ALBEDO = relief_color;
    // Continuous but restrained shell: geometry communicates the grade, not a giant dark mask.
    ALPHA = 0.055 + active * (0.205 + 0.055 * abs(height_bias));
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

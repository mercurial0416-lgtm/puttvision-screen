extends "res://practice_trend_vector.gd"

# Presentation-only macro relief pass. The exact Android terrain mesh remains authoritative;
# this layer only makes its existing height/slope field readable on a TV without exaggerating
# geometry or feeding anything back into physics, GreenTerrain, GreenReadAdvisor, aim or scoring.

const RELIEF_GREEN_SIZE := Vector2(11.8, 34.5)
const RELIEF_SUB_X := 30
const RELIEF_SUB_Z := 86

var _terrain_relief: MeshInstance3D
var _terrain_relief_mat: ShaderMaterial

func _terrain_relief_visibility_strength(slope_percent: float, terrain_height_m: float) -> float:
    # A crown peak or bowl floor is physically important even though its instantaneous local
    # slope approaches zero. Keep a restrained height-driven floor so macro relief does not
    # visually disappear at exactly those turning points.
    var slope_signal := smoothstep(0.18, 0.90, maxf(0.0, slope_percent))
    var elevation_signal := smoothstep(0.035, 0.14, absf(terrain_height_m))
    return maxf(slope_signal, elevation_signal * 0.48)

func _terrain_relief_hillshade_contrast(slope_percent: float) -> float:
    # Presentation-only contrast budget. A 1% plane should be unmistakable from the address
    # camera, while nearly level turf remains visually quiet. Kept bounded for TV/mobile safety.
    var slope_signal := smoothstep(0.18, 0.90, maxf(0.0, slope_percent))
    return lerpf(0.0, 0.32, slope_signal)

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
    // Tiny z-fight separation only; physical/display geometry is not vertically exaggerated.
    VERTEX.y += 0.0016;
}

void fragment() {
    // Preserve relief continuity through crown peaks and bowl floors. Local slope naturally
    // approaches zero at those turning points, but the authoritative physical elevation still
    // carries useful shape information. The height signal is intentionally capped well below
    // the slope response so flat baseline turf stays quiet instead of looking heat-mapped.
    float slope_signal = smoothstep(0.18, 0.90, slope_pct);
    float elevation_signal = smoothstep(0.035, 0.14, abs(terrain_height));
    float active = max(slope_signal, elevation_signal * 0.48);
    float height_bias = clamp(terrain_height / 0.34, -1.0, 1.0);

    // Omnidirectional TV-readable hillshade. The old single key vector had a blind axis: a real
    // slope nearly perpendicular to that key could collapse to neutral and still look flat from
    // address. Use the orthogonal axis only as a bounded fallback, preserving a signed broad-face
    // cue without contour stripes, extra lights, displaced geometry, or physics changes.
    vec2 downhill = slope_pct > 0.001 ? local_slope / slope_pct : vec2(0.0, 1.0);
    float facing = dot(downhill, normalize(vec2(0.72, -0.69)));
    float cross_facing = dot(downhill, normalize(vec2(0.69, 0.72)));
    float hillshade_sign = abs(facing) > 0.06 ? sign(facing) : sign(cross_facing);
    float hillshade_axis = max(abs(facing), abs(cross_facing) * 0.34);
    float signed_hillshade = clamp(hillshade_sign * hillshade_axis * slope_signal, -1.0, 1.0);
    float hillshade_exposure = mix(0.68, 1.32, signed_hillshade * 0.5 + 0.5);

    vec3 low_green = vec3(0.036, 0.095, 0.046);
    vec3 high_green = vec3(0.235, 0.320, 0.125);
    vec3 relief_color = mix(low_green, high_green, height_bias * 0.5 + 0.5);
    relief_color *= hillshade_exposure;

    ALBEDO = relief_color;
    // Enough opacity to make macro slope survive TV scaling while keeping underlying turf visible.
    ALPHA = active * (0.235 + 0.115 * abs(height_bias));
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

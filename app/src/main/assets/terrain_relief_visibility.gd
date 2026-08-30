extends "res://practice_trend_vector.gd"

# Presentation-only macro relief shell. Android GreenTerrain / GreenReadAdvisor remain authoritative.
# This shell deliberately exaggerates vertical displacement for TV readability only; it never feeds
# coordinates back into physics, aim, scoring, replay truth, or the authoritative terrain payload.

const RELIEF_GREEN_SIZE := Vector2(11.8, 34.5)
const RELIEF_SUB_X := 30
const RELIEF_SUB_Z := 86
# The previous 3.2x pass still read too flat from the address camera on subtle 1-2% surfaces.
# 4.6x is presentation-only and hard-capped so gentle terrain becomes legible without allowing large
# crowns/bowls to turn into cartoon geometry. The shell stays translucent so existing turf/grid/read
# cues remain visible; narrow physical-elevation ribbons carry the strongest displaced depth cue.
const RELIEF_VISUAL_SCALE := 4.6
const RELIEF_EXTRA_CAP_M := 0.72
const RELIEF_MINOR_CONTOUR_M := 0.05
const RELIEF_MAJOR_CONTOUR_M := 0.10

var _terrain_relief: MeshInstance3D
var _terrain_relief_mat: ShaderMaterial

func _terrain_relief_visual_offset(terrain_height_m: float) -> float:
    return clampf(
        terrain_height_m * (RELIEF_VISUAL_SCALE - 1.0),
        -RELIEF_EXTRA_CAP_M,
        RELIEF_EXTRA_CAP_M
    )

func _terrain_relief_visual_height(terrain_height_m: float) -> float:
    return terrain_height_m + _terrain_relief_visual_offset(terrain_height_m) + 0.003

func _terrain_relief_visibility_strength(slope_percent: float, terrain_height_m: float) -> float:
    var slope_signal := smoothstep(0.18, 0.90, maxf(0.0, slope_percent))
    var elevation_signal := smoothstep(0.035, 0.14, absf(terrain_height_m))
    return maxf(slope_signal, elevation_signal * 0.48)

func _terrain_relief_hillshade_contrast(slope_percent: float) -> float:
    var slope_signal := smoothstep(0.18, 0.90, maxf(0.0, slope_percent))
    return lerpf(0.0, 0.16, slope_signal)

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
        terrain_height * (4.6 - 1.0),
        -0.72,
        0.72
    );
    VERTEX.y = terrain_height + relief_delta + 0.0030;
}

void fragment() {
    float slope_signal = smoothstep(0.18, 0.90, slope_pct);
    float elevation_signal = smoothstep(0.035, 0.14, abs(terrain_height));
    float active = max(slope_signal, elevation_signal * 0.48);
    float height_bias = clamp(terrain_height / 0.34, -1.0, 1.0);

    vec2 downhill = slope_pct > 0.001 ? local_slope / slope_pct : vec2(0.0, 1.0);
    float facing = dot(downhill, normalize(vec2(0.72, -0.69)));
    float cross_facing = dot(downhill, normalize(vec2(0.69, 0.72)));
    float primary_hillshade = clamp(facing * slope_signal, -1.0, 1.0);
    float cross_hillshade = clamp(cross_facing * slope_signal, -1.0, 1.0);
    float hillshade_exposure = mix(0.94, 1.06, primary_hillshade * 0.5 + 0.5);
    vec3 cross_tint = vec3(1.0) + vec3(0.014, 0.005, -0.012) * cross_hillshade;

    vec3 low_green = vec3(0.120, 0.300, 0.100);
    vec3 high_green = vec3(0.180, 0.380, 0.140);
    vec3 relief_color = mix(low_green, high_green, height_bias * 0.5 + 0.5);
    relief_color *= hillshade_exposure;
    relief_color *= cross_tint;

    float minor_phase = abs(fract(terrain_height / 0.05 + 0.5) - 0.5);
    float major_phase = abs(fract(terrain_height / 0.10 + 0.5) - 0.5);
    float minor_ribbon = 1.0 - smoothstep(0.050, 0.115, minor_phase);
    float major_ribbon = 1.0 - smoothstep(0.065, 0.145, major_phase);
    float elevation_ribbon = max(minor_ribbon * 0.46, major_ribbon);
    float ribbon_strength = elevation_ribbon * active * 0.34;
    vec3 ribbon_color = relief_color * 1.34 + vec3(0.024, 0.034, 0.010);
    relief_color = mix(relief_color, ribbon_color, ribbon_strength);

    ALBEDO = relief_color;
    float base_alpha = 0.018 + active * (0.072 + 0.012 * abs(height_bias));
    float ribbon_alpha = elevation_ribbon * active * 0.22;
    ALPHA = min(0.32, base_alpha + ribbon_alpha);
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

    # All inherited contact shadows were positioned before the relief delta. Lift each active
    # presentation shadow by exactly the same amount so the ball does not appear to float.
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

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _terrain_relief_sync_anchors(s)

func _v166_refresh_terrain(key: String) -> void:
    var previous_key := _v166_terrain_key
    super._v166_refresh_terrain(key)
    if _v166_terrain_ready and _v166_terrain_key != previous_key:
        _terrain_relief_rebuild()

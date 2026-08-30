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

func _terrain_relief_sync_anchors(s: Dictionary) -> void:
    # Physics reports exact ball/cup Z against the authoritative, unexaggerated terrain. Move only
    # the presentation nodes by the same extra relief delta so they remain visually grounded.
    var ball_x: float = float(s.get("ballX", 0.0))
    var ball_y: float = float(s.get("ballY", 0.0))
    var ball_surface: float = _v166_sample(ball_x, ball_y).x
    var ball_delta: float = _terrain_relief_visual_offset(ball_surface)
    if ball != null:
        ball.position.y = float(s.get("ballZ", BALL_RADIUS)) + ball_delta

    var cup_y: float = clampf(float(s.get("holeDistance", target_distance)), 0.5, 30.0)
    var cup_surface: float = _v166_sample(0.0, cup_y).x
    var cup_delta: float = _terrain_relief_visual_offset(cup_surface)
    if target_root != null:
        target_root.position.y = float(s.get("cupZ", last_cup_z)) + cup_delta

    # The temporary pre-solver aim bar is a presentation guide; keep its center above the shell.
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

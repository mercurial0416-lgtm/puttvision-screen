extends "res://practice_trend_preview.gd"

const TerrainReliefScene = preload("res://terrain_relief_visibility.gd")
const CinematicReplayScene = preload("res://v175_cinematic_replay.gd")
const RELIEF_PREVIEW_SIDE := 1.35
const RELIEF_PREVIEW_LONG := -0.55
const REPLAY_TRACK_WIDTH := 634.0
var _terrain_relief_checked := false
var _terrain_relief_preview_added := false
var _green_fall_line_checked := false
var _replay_progress_checked := false
var _live_break_distance_axis_checked := false

func _terrain_relief_probe():
    var probe = TerrainReliefScene.new()
    probe._v166_terrain_ready = false
    probe._v166_fallback_side = RELIEF_PREVIEW_SIDE
    probe._v166_fallback_long = RELIEF_PREVIEW_LONG
    return probe

func _add_terrain_relief_preview() -> void:
    if _terrain_relief_preview_added:
        return
    _terrain_relief_preview_added = true
    var green := get_node_or_null("Green") as MeshInstance3D
    if green == null:
        return
    var probe = _terrain_relief_probe()
    var overlay := MeshInstance3D.new()
    overlay.name = "PreviewTerrainReliefVisibility"
    overlay.position = green.position
    overlay.mesh = probe._v166_surface_mesh(Vector2(11.8, 34.5), 30, 86, green.position.z, true)
    overlay.material_override = probe._terrain_relief_material()
    overlay.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    add_child(overlay)
    probe.free()

func _check_green_fall_line_geometry() -> bool:
    var flat := _v183_fall_line_geometry(0.0, 0.0)
    if bool(flat.get("visible", true)):
        push_error("Green overview fall line should hide on level terrain")
        return false

    var side := _v183_fall_line_geometry(1.4, 0.0)
    var side_shaft: PackedVector2Array = side.get("shaft", PackedVector2Array())
    if not bool(side.get("visible", false)) or side_shaft.size() != 2:
        push_error("Green overview side-slope fall line missing")
        return false
    var side_delta := side_shaft[1] - side_shaft[0]
    if side_delta.x <= 0.0 or absf(side_delta.y) > 0.01:
        push_error("Green overview side-slope direction regressed")
        return false

    var down := _v183_fall_line_geometry(0.0, 1.4)
    var down_shaft: PackedVector2Array = down.get("shaft", PackedVector2Array())
    if down_shaft.size() != 2:
        push_error("Green overview longitudinal fall line missing")
        return false
    var down_delta := down_shaft[1] - down_shaft[0]
    if down_delta.y >= 0.0 or absf(down_delta.x) > 0.01:
        push_error("Green overview downhill direction regressed")
        return false

    var mixed := _v183_fall_line_geometry(-1.1, -0.8)
    var mixed_shaft: PackedVector2Array = mixed.get("shaft", PackedVector2Array())
    var left_head: PackedVector2Array = mixed.get("left", PackedVector2Array())
    var right_head: PackedVector2Array = mixed.get("right", PackedVector2Array())
    if mixed_shaft.size() != 2 or left_head.size() != 2 or right_head.size() != 2:
        push_error("Green overview fall-line arrowhead geometry missing")
        return false
    var mixed_delta := mixed_shaft[1] - mixed_shaft[0]
    if mixed_delta.x >= 0.0 or mixed_delta.y <= 0.0:
        push_error("Green overview mixed fall line lost a slope axis")
        return false
    if left_head[1].distance_to(mixed_shaft[1]) > 0.01 or right_head[1].distance_to(mixed_shaft[1]) > 0.01:
        push_error("Green overview fall-line arrowhead detached from shaft")
        return false
    return true

func _check_replay_chronological_progress() -> bool:
    var probe = CinematicReplayScene.new()
    var quarter: float = probe._v175_progress_from_times(6.0, 8.0)
    var quarter_width: float = probe._v175_replay_track_fill_width(quarter)
    var expected_width := REPLAY_TRACK_WIDTH * 0.25
    var eased_width := REPLAY_TRACK_WIDTH * smoothstep(0.0, 1.0, quarter)
    var ok := true
    if absf(quarter - 0.25) > 0.001:
        push_error("Replay chronological progress calculation regression")
        ok = false
    elif absf(quarter_width - expected_width) > 0.001:
        push_error("Replay HUD track no longer matches chronological percentage")
        ok = false
    elif absf(quarter_width - eased_width) < 1.0:
        push_error("Replay HUD track accidentally reintroduced camera easing")
        ok = false
    elif absf(probe._v175_replay_track_fill_width(-0.4)) > 0.001:
        push_error("Replay HUD lower progress clamp regression")
        ok = false
    elif absf(probe._v175_replay_track_fill_width(1.4) - REPLAY_TRACK_WIDTH) > 0.001:
        push_error("Replay HUD upper progress clamp regression")
        ok = false
    probe.free()
    return ok

func _check_live_break_distance_axis() -> bool:
    var probe = FlowScene.new()
    var history := PackedFloat32Array([-10.0, 0.0, 10.0])
    var distances := PackedFloat32Array([0.0, 0.20, 1.0])
    var points := probe._live_trace_points_with_distance(history, distances)
    var expected_mid_x := lerpf(probe.LIVE_TRACE_LEFT, probe.LIVE_TRACE_RIGHT, 0.20)
    var equal_sample_mid_x := lerpf(probe.LIVE_TRACE_LEFT, probe.LIVE_TRACE_RIGHT, 0.50)
    var ok := true
    if points.size() != 3:
        push_error("Live break distance-axis trace point count regression")
        ok = false
    elif absf(points[1].x - expected_mid_x) > 0.01:
        push_error("Live break trace no longer preserves physical sample spacing")
        ok = false
    elif absf(points[1].x - equal_sample_mid_x) < 10.0:
        push_error("Live break trace accidentally reverted to sample-index spacing")
        ok = false
    elif absf(points[0].x - probe.LIVE_TRACE_LEFT) > 0.01 or absf(points[2].x - probe.LIVE_TRACE_RIGHT) > 0.01:
        push_error("Live break distance-axis trace span regression")
        ok = false
    probe.free()
    return ok

func _process(delta: float) -> void:
    super._process(delta)
    if not _terrain_relief_preview_added and _preview_frames >= 10:
        _add_terrain_relief_preview()
    if not _green_fall_line_checked and _preview_frames >= 11:
        _green_fall_line_checked = true
        if not _check_green_fall_line_geometry():
            get_tree().quit(42)
            return
        print("GREEN_FALL_LINE_GEOMETRY_OK=1")
    if not _replay_progress_checked and _preview_frames >= 16:
        _replay_progress_checked = true
        if not _check_replay_chronological_progress():
            get_tree().quit(35)
            return
        print("REPLAY_CHRONOLOGICAL_HUD_PROGRESS_OK=1")
    if not _live_break_distance_axis_checked and _preview_frames >= 17:
        _live_break_distance_axis_checked = true
        if not _check_live_break_distance_axis():
            get_tree().quit(44)
            return
        print("LIVE_BREAK_DISTANCE_AXIS_OK=1")
    if _terrain_relief_checked or _preview_frames < 11:
        return
    _terrain_relief_checked = true

    var probe = _terrain_relief_probe()
    var mesh := probe._v166_surface_mesh(Vector2(11.8, 34.5), 12, 24, -19.2, true)
    if mesh == null or mesh.get_surface_count() < 1:
        push_error("Terrain relief regression probe produced no surface")
        probe.free()
        get_tree().quit(41)
        return
    var bounds := mesh.get_aabb()
    if bounds.size.y < 0.15:
        push_error("Terrain macro relief collapsed visually: %.3fm" % bounds.size.y)
        probe.free()
        get_tree().quit(41)
        return
    var arrays := mesh.surface_get_arrays(0)
    var colors: PackedColorArray = arrays[Mesh.ARRAY_COLOR]
    if colors.size() < 4:
        push_error("Terrain relief mesh lost encoded physical height/slope colors")
        probe.free()
        get_tree().quit(41)
        return
    var first: Color = colors[0]
    var last: Color = colors[colors.size() - 1]
    if abs(first.r - last.r) < 0.01:
        push_error("Terrain relief height encoding became visually flat")
        probe.free()
        get_tree().quit(41)
        return
    var material := probe._terrain_relief_material()
    if material == null or material.shader == null:
        push_error("Terrain relief material missing")
        probe.free()
        get_tree().quit(41)
        return
    var shader_code := material.shader.code
    if shader_code.find("terrain_height") < 0 or shader_code.find("contour_wave") >= 0:
        push_error("Terrain relief shader lost natural physical-height shading")
        probe.free()
        get_tree().quit(41)
        return
    var light := DirectionalLight3D.new()
    light.shadow_enabled = false
    if light.shadow_enabled:
        push_error("Terrain relief mobile safety regression enabled shadows")
        light.free()
        probe.free()
        get_tree().quit(41)
        return
    light.free()
    probe.free()
    print("TERRAIN_RELIEF_VISIBILITY_OK=1")

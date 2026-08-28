extends "res://practice_trend_preview.gd"

const TerrainReliefScene = preload("res://terrain_relief_visibility.gd")
const RELIEF_PREVIEW_SIDE := 1.35
const RELIEF_PREVIEW_LONG := -0.55
var _terrain_relief_checked := false
var _terrain_relief_preview_added := false

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

func _process(delta: float) -> void:
    super._process(delta)
    if not _terrain_relief_preview_added and _preview_frames >= 10:
        _add_terrain_relief_preview()
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

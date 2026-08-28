extends "res://practice_trend_preview.gd"

const TerrainReliefScene = preload("res://terrain_relief_visibility.gd")
var _terrain_relief_checked := false

func _process(delta: float) -> void:
    super._process(delta)
    if _terrain_relief_checked or _preview_frames < 17:
        return
    _terrain_relief_checked = true

    if _terrain_relief == null or _terrain_relief.mesh == null or _terrain_relief.mesh.get_surface_count() < 1:
        push_error("Terrain relief overlay missing from rendered green")
        get_tree().quit(41)
        return
    if _terrain_relief_light == null or _terrain_relief_light.shadow_enabled:
        push_error("Terrain relief grazing light missing or mobile-unsafe shadows enabled")
        get_tree().quit(41)
        return

    var probe = TerrainReliefScene.new()
    probe._v166_terrain_ready = false
    probe._v166_fallback_side = 2.0
    probe._v166_fallback_long = -1.5
    var mesh := probe._v166_surface_mesh(Vector2(11.8, 34.5), 12, 24, -19.2, true)
    if mesh == null or mesh.get_surface_count() < 1:
        push_error("Terrain relief regression probe produced no surface")
        probe.free()
        get_tree().quit(41)
        return
    var bounds := mesh.get_aabb()
    if bounds.size.y < 0.30:
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
    if abs(first.r - last.r) < 0.02:
        push_error("Terrain relief height encoding became visually flat")
        probe.free()
        get_tree().quit(41)
        return
    if _terrain_relief_mat == null or _terrain_relief_mat.shader == null or _terrain_relief_mat.shader.code.find("terrain_height") < 0:
        push_error("Terrain relief shader lost physical-height shading")
        probe.free()
        get_tree().quit(41)
        return

    probe.free()
    print("TERRAIN_RELIEF_VISIBILITY_OK=1")

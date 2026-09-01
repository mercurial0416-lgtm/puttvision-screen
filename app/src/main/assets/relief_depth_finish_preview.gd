extends "res://terrain_relief_preview.gd"

const ReliefDepthFinishScene = preload("res://relief_depth_finish.gd")
var _relief_depth_finish_checked := false

func _terrain_relief_probe():
    var probe = ReliefDepthFinishScene.new()
    probe._v166_terrain_ready = false
    probe._v166_fallback_side = RELIEF_PREVIEW_SIDE
    probe._v166_fallback_long = RELIEF_PREVIEW_LONG
    return probe

func _process(delta: float) -> void:
    super._process(delta)
    if _relief_depth_finish_checked or _preview_frames < 12:
        return
    _relief_depth_finish_checked = true
    var probe = _terrain_relief_probe()
    var material := probe._terrain_relief_material()
    if material == null or material.shader == null:
        push_error("Relief depth finish preview material missing")
        probe.free()
        get_tree().quit(48)
        return
    var code := material.shader.code
    if code.find("mix(0.88, 1.12") < 0 or code.find("elevation_ribbon * active * 0.44") < 0:
        push_error("Relief depth finish did not reach visual preview")
        probe.free()
        get_tree().quit(48)
        return
    if code.find("min(0.40, base_alpha + ribbon_alpha)") < 0:
        push_error("Relief depth finish alpha budget regressed")
        probe.free()
        get_tree().quit(48)
        return
    probe.free()
    print("RELIEF_DEPTH_FINISH_PREVIEW_OK=1")

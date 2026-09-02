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
    if code.find("mix(0.86, 1.14") < 0 or code.find("elevation_ribbon * active * 0.48") < 0:
        push_error("Relief depth finish did not reach visual preview")
        probe.free()
        get_tree().quit(48)
        return
    if code.find("float form_lobe = mix(1.0, mix(0.82, 1.18") < 0 or code.find("relief_color *= form_lobe;") < 0:
        push_error("Continuous relief form shading did not reach visual preview")
        probe.free()
        get_tree().quit(48)
        return
    if code.find("0.026 + active * (0.108 + 0.020 * abs(height_bias))") < 0 or code.find("min(0.40, base_alpha + ribbon_alpha)") < 0:
        push_error("Relief depth finish alpha budget regressed")
        probe.free()
        get_tree().quit(48)
        return
    probe.free()
    print("RELIEF_DEPTH_FINISH_PREVIEW_OK=1")

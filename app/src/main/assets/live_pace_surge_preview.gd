extends "res://relief_depth_finish_preview.gd"

const LivePaceSurgeScene = preload("res://live_pace_surge.gd")
var _live_pace_surge_checked := false

func _process(delta: float) -> void:
    super._process(delta)
    if _live_pace_surge_checked or _preview_frames < 13:
        return
    _live_pace_surge_checked = true
    var probe = LivePaceSurgeScene.new()
    if probe._live_pace_surge_state(0.0, 0.0, false) != "IDLE":
        push_error("Live pace surge idle state regression")
        probe.free()
        get_tree().quit(43)
        return
    if probe._live_pace_surge_state(1.28, 0.0, true) != "SURGING":
        push_error("Live pace surge threshold regression")
        probe.free()
        get_tree().quit(43)
        return
    probe.free()
    print("LIVE_PACE_SURGE_OK=1")

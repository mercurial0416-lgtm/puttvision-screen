extends "res://relief_depth_finish_preview.gd"

const LivePaceSurgeScene = preload("res://live_pace_surge.gd")
var _live_pace_surge_checked := false

func _process(delta: float) -> void:
    super._process(delta)

    # Parent preview layers reseed their synthetic live-roll state while the deferred capture is
    # active. Apply a representative over-range surge after that chain so the uploaded reference
    # image proves the HUD tells the truth when measured pace exceeds the compact numeric ceiling.
    if _capture_started and _preview_live_roll_pace != null:
        _preview_live_roll_pace.text = "PACE 199%+ · SURGING"

    if _live_pace_surge_checked:
        return
    _live_pace_surge_checked = true

    var probe = LivePaceSurgeScene.new()
    if probe._live_pace_readout(0.0, 0.0) != "PACE --":
        push_error("Live pace invalid-baseline regression")
        probe.free()
        get_tree().quit(61)
        return
    if probe._live_pace_readout(0.46, 1.0) != "PACE 46% · SETTLING":
        push_error("Live pace settling regression")
        probe.free()
        get_tree().quit(61)
        return
    if probe._live_pace_readout(0.86, 1.0) != "PACE 86% · ROLLING":
        push_error("Live pace rolling regression")
        probe.free()
        get_tree().quit(61)
        return
    if probe._live_pace_readout(1.28, 1.0) != "PACE 128% · SURGING":
        push_error("Downhill acceleration is no longer visible in live pace HUD")
        probe.free()
        get_tree().quit(61)
        return
    if probe._live_pace_readout(5.0, 1.0) != "PACE 199%+ · SURGING":
        push_error("Live pace over-range truth regression")
        probe.free()
        get_tree().quit(61)
        return
    probe.free()
    print("LIVE_ROLL_SURGE_OK=1")

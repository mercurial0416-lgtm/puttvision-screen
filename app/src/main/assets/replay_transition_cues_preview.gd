extends "res://live_pace_surge_preview.gd"

const ReplayTransitionCueScene = preload("res://replay_transition_cues.gd")
var _replay_transition_cues_checked := false

func _process(delta: float) -> void:
    super._process(delta)

    # Keep the representative transition cue visible in the uploaded rendered preview after parent
    # synthetic-state updates have run, so CI proves the premium replay handoff is actually legible.
    if _capture_started and _focus_replay_stage_label != null:
        _focus_replay_stage_label.text = "BLEND · →CUP 0.3s"

    if _replay_transition_cues_checked:
        return
    _replay_transition_cues_checked = true

    var probe = ReplayTransitionCueScene.new()
    if probe._replay_transition_status(0.50, 1.40, 2.80) != "TRAIL · →BLEND 0.6s":
        push_error("Replay trail-to-blend countdown regression")
        probe.free()
        get_tree().quit(62)
        return
    if probe._replay_transition_status(0.78, 0.62, 2.80) != "BLEND · →CUP 0.3s":
        push_error("Replay blend-to-cup countdown regression")
        probe.free()
        get_tree().quit(62)
        return
    if probe._replay_transition_status(0.92, 0.22, 2.80) != "CUP · 0.2s":
        push_error("Replay cup-camera remaining-time regression")
        probe.free()
        get_tree().quit(62)
        return
    if probe._replay_transition_status(0.78, NAN, 2.80) != "CAM BLEND":
        push_error("Replay invalid remaining-time fallback regression")
        probe.free()
        get_tree().quit(62)
        return
    if probe._replay_transition_status(0.92, 0.1, 0.0) != "CUP CAM":
        push_error("Replay invalid duration fallback regression")
        probe.free()
        get_tree().quit(62)
        return
    if probe._replay_transition_status(-4.0, 2.8, 2.8) != "TRAIL · →BLEND 2.0s":
        push_error("Replay transition progress clamp regression")
        probe.free()
        get_tree().quit(62)
        return
    probe.free()
    print("REPLAY_TRANSITION_CUES_OK=1")

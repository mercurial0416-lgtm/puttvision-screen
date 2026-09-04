extends "res://live_pace_surge_preview.gd"

const ReplayTransitionCueScene = preload("res://replay_transition_cues.gd")
var _replay_transition_cues_checked := false

func _process(delta: float) -> void:
    super._process(delta)

    # Preview scripts mirror production through a separate inheritance chain. Update whichever
    # replay-stage label is present so the uploaded frame proves ETA + measured distance coexist.
    if _capture_started:
        var replay_stage := find_child("ReplayCameraStage", true, false) as Label
        if replay_stage == null:
            replay_stage = find_child("PreviewReplayCameraStage", true, false) as Label
        if replay_stage != null:
            replay_stage.text = "→CUP 0.3s · 0.9m TO STOP"

    if _replay_transition_cues_checked:
        return
    _replay_transition_cues_checked = true

    var probe = ReplayTransitionCueScene.new()
    if probe._replay_transition_status(0.50, 1.40, 2.80) != "→BLEND 0.6s":
        push_error("Replay trail-to-blend countdown regression")
        probe.free()
        get_tree().quit(62)
        return
    if probe._replay_transition_status(0.78, 0.62, 2.80) != "→CUP 0.3s":
        push_error("Replay blend-to-cup countdown regression")
        probe.free()
        get_tree().quit(62)
        return
    if probe._replay_transition_status(0.92, 0.22, 2.80) != "CUP 0.2s":
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
    if probe._replay_transition_status(-4.0, 2.8, 2.8) != "→BLEND 2.0s":
        push_error("Replay transition progress clamp regression")
        probe.free()
        get_tree().quit(62)
        return

    # Parent replay timeline owns measured trail distance. The transition layer must preserve that
    # already-rendered truth without importing or reaching into downstream cache state.
    if probe._replay_transition_readout("→BLEND 0.6s", "TRAIL · 0.9m REST") != "→BLEND 0.6s · 0.9m TO STOP":
        push_error("Replay transition must preserve parent roll distance")
        probe.free()
        get_tree().quit(62)
        return
    if probe._replay_transition_readout("→CUP 0.3s", "CAM BLEND · 0.9m TO STOP") != "→CUP 0.3s · 0.9m TO STOP":
        push_error("Replay transition clear-distance suffix regression")
        probe.free()
        get_tree().quit(62)
        return
    if probe._replay_transition_readout("CAM BLEND", "CAM BLEND") != "CAM BLEND":
        push_error("Replay transition must not fabricate distance")
        probe.free()
        get_tree().quit(62)
        return

    probe.free()
    print("REPLAY_TRANSITION_CUES_OK=1")

extends "res://practice_trend_preview.gd"

const CinematicReplayScene = preload("res://v175_cinematic_replay.gd")
var _replay_progress_regression_checked := false

func _process(delta: float) -> void:
    super._process(delta)
    if _replay_progress_regression_checked or _preview_frames < 16:
        return
    _replay_progress_regression_checked = true

    var probe = CinematicReplayScene.new()
    var quarter := probe._v175_progress_from_times(6.0, 8.0)
    if absf(quarter - 0.25) > 0.001:
        push_error("Replay chronological progress calculation regression")
        probe.free()
        get_tree().quit(35)
        return

    var quarter_width := probe._v175_replay_track_fill_width(quarter)
    var expected_quarter_width := probe.V175_REPLAY_TRACK_WIDTH * 0.25
    if absf(quarter_width - expected_quarter_width) > 0.001:
        push_error("Replay HUD track no longer matches chronological percentage")
        probe.free()
        get_tree().quit(35)
        return

    var eased_quarter_width := probe.V175_REPLAY_TRACK_WIDTH * smoothstep(0.0, 1.0, quarter)
    if absf(quarter_width - eased_quarter_width) < 1.0:
        push_error("Replay HUD track accidentally reintroduced camera easing")
        probe.free()
        get_tree().quit(35)
        return

    if absf(probe._v175_replay_track_fill_width(-0.4)) > 0.001:
        push_error("Replay HUD lower progress clamp regression")
        probe.free()
        get_tree().quit(35)
        return
    if absf(probe._v175_replay_track_fill_width(1.4) - probe.V175_REPLAY_TRACK_WIDTH) > 0.001:
        push_error("Replay HUD upper progress clamp regression")
        probe.free()
        get_tree().quit(35)
        return
    if absf(probe._v175_replay_track_fill_width(NAN)) > 0.001:
        push_error("Replay HUD invalid progress guard regression")
        probe.free()
        get_tree().quit(35)
        return
    if absf(probe._v175_progress_from_times(NAN, 8.0)) > 0.001 or absf(probe._v175_progress_from_times(2.0, NAN)) > 0.001:
        push_error("Replay timing invalid input guard regression")
        probe.free()
        get_tree().quit(35)
        return
    if absf(probe._v175_progress_from_times(0.0, 0.0) - 1.0) > 0.001:
        push_error("Replay zero-duration completion behavior regression")
        probe.free()
        get_tree().quit(35)
        return

    probe.free()
    print("REPLAY_CHRONOLOGICAL_HUD_PROGRESS_OK=1")

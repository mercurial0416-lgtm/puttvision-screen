extends "res://v181_pace_target.gd"

var _preview_frames := 0
var _capture_started := false
var _checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    _preview_frames += 1

    if not _checks_done and _preview_frames >= 10:
        _checks_done = true
        if _v169_profile_id < 0:
            push_error("Profile state missing")
            get_tree().quit(3)
            return
        print("V171_PROFILE_SWITCH_OK=1")

        if _v173_ball_shadow == null or _v173_flag_cloth == null or target_root == null or target_root.get_node_or_null("V173CupLiner") == null:
            push_error("Premium nodes missing")
            get_tree().quit(4)
            return
        print("V173_PREMIUM_NODES_OK=1")
        if _v174_remaining_label == null or _v174_surface_label == null or _v174_result_panel == null:
            push_error("Broadcast HUD missing")
            get_tree().quit(5)
            return
        print("V174_BROADCAST_HUD_OK=1")
        if _v175_replay_panel == null or _v175_replay_fill == null or _v175_replay_marker == null:
            push_error("Replay package missing")
            get_tree().quit(6)
            return
        print("V175_CINEMATIC_REPLAY_OK=1")
        if _v176_panel == null or _v176_curve == null or _v176_aim_marker == null:
            push_error("Precision read missing")
            get_tree().quit(7)
            return
        print("V176_PRECISION_READ_OK=1")
        if _v177_panel == null or _v177_grade_label == null or _v177_line_bar == null or _v177_pace_bar == null:
            push_error("Shot debrief missing")
            get_tree().quit(8)
            return
        print("V177_SHOT_DEBRIEF_OK=1")
        if _v178_panel == null or _v178_score_labels.size() != V178_HISTORY:
            push_error("Session form missing")
            get_tree().quit(9)
            return
        print("V178_SESSION_FORM_OK=1")
        if _v179_panel == null or _v179_points.size() != V179_HISTORY:
            push_error("Session dispersion package missing")
            get_tree().quit(10)
            return
        print("V179_SESSION_DISPERSION_OK=1")
        if _v180_focus_chip == null or _v180_focus_distance == null:
            push_error("Replay cup-focus package missing")
            get_tree().quit(11)
            return
        print("REPLAY_CUP_FOCUS_OK=1")

        if _v181_panel == null or _v181_target == null or _v181_actual == null or _v181_marker == null or _v181_fill == null:
            push_error("Pace target ribbon missing")
            get_tree().quit(12)
            return
        var flat := _v181_target_speed(3.0, 0.0, 3.0)
        var uphill := _v181_target_speed(3.0, 2.0, 3.0)
        var fast_green := _v181_target_speed(3.0, 0.0, 4.5)
        if uphill <= flat or fast_green >= flat:
            push_error("Pace target monotonic regression")
            get_tree().quit(12)
            return
        if _v181_match_pct(flat, flat) != 100 or _v181_match_pct(flat * 1.5, flat) >= 100:
            push_error("Pace match regression")
            get_tree().quit(12)
            return
        _v181_update({"distanceToCup": 4.2, "longSlopePct": 1.4, "greenSpeed": 3.2, "ballSpeed": 1.9, "running": false}, true)
        if not _v181_panel.visible or not _v181_target.text.begins_with("TARGET") or not _v181_actual.text.begins_with("ACTUAL"):
            push_error("Pace preview binding regression")
            get_tree().quit(12)
            return
        print("PACE_TARGET_RIBBON_OK=1")

    if not _capture_started and _preview_frames >= 14:
        _capture_started = true
        _capture_preview.call_deferred()

func _capture_preview() -> void:
    await RenderingServer.frame_post_draw
    var image := get_viewport().get_texture().get_image()
    var output := ProjectSettings.globalize_path("res://v143-preview.png")
    var error := image.save_png(output)
    if error != OK:
        push_error("Preview save failed: %s" % error)
        get_tree().quit(2)
        return
    print("V143_PREVIEW_SAVED=" + output)
    get_tree().quit()

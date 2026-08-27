extends "res://v185_pace_intent.gd"

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
        if _v182_beads.size() != V182_BEAD_COUNT:
            push_error("Break flow bead count regression")
            get_tree().quit(13)
            return
        print("BREAK_FLOW_BEADS_OK=1")
        if _v183_panel == null or _v183_path_line == null or _v183_ball == null or _v183_cup == null or _v183_distance_label == null:
            push_error("Green overview package missing")
            get_tree().quit(14)
            return
        print("GREEN_OVERVIEW_OK=1")
        if _v184_left_edge == null or _v184_right_edge == null or _v184_gate == null or _v184_window_label == null:
            push_error("Make-window package missing")
            get_tree().quit(15)
            return
        print("MAKE_WINDOW_CORRIDOR_OK=1")
        if _v185_pace_label == null or _v185_pace_track == null or _v185_pace_fill == null or _v185_pace_marker == null:
            push_error("Pace-intent package missing")
            get_tree().quit(16)
            return

        var short_downhill := _v185_intent(2.0, 1.6)
        var long_uphill := _v185_intent(7.5, -1.6)
        if short_downhill >= long_uphill or short_downhill < 0.15 or long_uphill > 0.89:
            push_error("Pace-intent difficulty regression")
            get_tree().quit(16)
            return

        _v165_recommended_offset = 0.42
        _v183_update({"distanceToCup": 5.4, "sideSlope": 1.65, "longSlope": -0.72, "running": false}, true)
        if not _v183_panel.visible or not _v185_pace_label.visible or _v185_pace_fill.points.size() != 2:
            push_error("Pace-intent live binding regression")
            get_tree().quit(16)
            return
        if _v185_pace_label.text.find("PACE") != 0:
            push_error("Pace-intent label regression")
            get_tree().quit(16)
            return
        var marker_x := _v185_pace_marker.position.x
        if marker_x < 111.0 or marker_x > 231.0:
            push_error("Pace-intent marker bounds regression")
            get_tree().quit(16)
            return
        print("PACE_INTENT_OK=1")

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

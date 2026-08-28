extends "res://v197_shot_map_make_window.gd"

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
        if _v183_panel == null or _v183_path_line == null or _v183_ball == null or _v183_cup == null:
            push_error("Green overview package missing")
            get_tree().quit(14)
            return
        if _v184_left_edge == null or _v184_right_edge == null or _v184_gate == null:
            push_error("Make-window package missing")
            get_tree().quit(15)
            return
        if _v185_pace_label == null or _v185_pace_marker == null:
            push_error("Pace-intent package missing")
            get_tree().quit(16)
            return
        if _v186_entry_ring == null or _v186_entry_label == null:
            push_error("Cup-entry package missing")
            get_tree().quit(17)
            return
        if _v187_gate == null or _v187_center_tick == null or _v187_aim_marker == null or _v187_gate_label == null:
            push_error("Start-line gate package missing")
            get_tree().quit(18)
            return
        if _v188_panel == null or _v188_ring == null or _v188_dot == null or _v188_vector == null or _v188_detail == null:
            push_error("Shot miss map package missing")
            get_tree().quit(19)
            return

        _v165_recommended_offset = 0.42
        _v183_update({"distanceToCup": 5.4, "sideSlope": 1.65, "longSlope": -0.72, "running": false}, true)
        if not _v183_panel.visible or not _v187_gate.visible or _v187_gate.points.size() != 2:
            push_error("Start-line gate live binding regression")
            get_tree().quit(18)
            return
        if _v187_gate_half_px < 4.9 or _v187_gate_half_px > 16.1 or not _v187_gate_label.text.begins_with("GATE"):
            push_error("Start-line gate tolerance regression")
            get_tree().quit(18)
            return
        var start := _v183_path_line.points[0]
        if _v187_aim_marker.position.distance_to(start) < 15.0:
            push_error("Start-line aim marker collapsed onto ball")
            get_tree().quit(18)
            return
        var easy := _v184_tolerance_cm(2.0, 0.2, 0.1) * 2.0
        var hard := _v184_tolerance_cm(7.5, 2.2, 1.4) * 2.0
        if easy <= hard:
            push_error("Start-line gate difficulty regression")
            get_tree().quit(18)
            return
        print("START_LINE_GATE_OK=1")

        var debrief := {
            "actualTrail": [Vector2(0.0, 0.0), Vector2(0.4, 4.9)],
            "readLineDeltaCm": 12.0,
            "paceDeltaCm": 28.0,
            "distanceToCup": 0.42,
            "running": false,
            "holed": false,
            "lipOut": false
        }
        _v177_update_debrief(debrief, true)
        if not _v188_panel.visible or _v188_vector.points.size() != 2:
            push_error("Shot miss map visibility regression")
            get_tree().quit(19)
            return
        var miss_point := _v188_point(12.0, 28.0)
        if miss_point.x <= V188_CENTER.x or miss_point.y >= V188_CENTER.y:
            push_error("Shot miss map direction regression")
            get_tree().quit(19)
            return
        if not _v188_detail.text.contains("RIGHT") or not _v188_detail.text.contains("LONG"):
            push_error("Shot miss map label regression")
            get_tree().quit(19)
            return
        print("SHOT_MISS_MAP_OK=1")

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

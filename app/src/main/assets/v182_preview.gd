extends "res://v182_break_flow.gd"

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
        _v182_side_pct = 1.8
        _v182_last_offset = 0.32
        if _v176_panel != null:
            _v176_panel.visible = true
        _v182_update_flow(0.12)
        var first := _v182_beads[0].position
        _v182_update_flow(0.35)
        var second := _v182_beads[0].position
        if first.distance_to(second) < 0.5 or not _v182_beads[0].visible:
            push_error("Break flow animation regression")
            get_tree().quit(13)
            return
        print("BREAK_FLOW_BEADS_OK=1")
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

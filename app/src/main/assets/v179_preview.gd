extends "res://v179_session_dispersion.gd"

var _preview_frames := 0
var _capture_started := false
var _checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    _preview_frames += 1

    if not _checks_done and _preview_frames >= 10:
        _checks_done = true
        if _v169_profile_id < 0:
            push_error("V171 profile state missing")
            get_tree().quit(3)
            return
        print("V171_PROFILE_SWITCH_OK=1")

        if _v173_ball_shadow == null or _v173_flag_cloth == null or target_root == null or target_root.get_node_or_null("V173CupLiner") == null:
            push_error("V173 premium nodes missing")
            get_tree().quit(4)
            return
        print("V173_PREMIUM_NODES_OK=1")

        if _v174_remaining_label == null or _v174_surface_label == null or _v174_result_panel == null:
            push_error("V174 broadcast HUD missing")
            get_tree().quit(5)
            return
        print("V174_BROADCAST_HUD_OK=1")

        if _v175_replay_panel == null or _v175_replay_fill == null or _v175_replay_marker == null:
            push_error("V175 replay package missing")
            get_tree().quit(6)
            return
        print("V175_CINEMATIC_REPLAY_OK=1")

        if _v176_panel == null or _v176_curve == null or _v176_aim_marker == null:
            push_error("V176 precision read missing")
            get_tree().quit(7)
            return
        print("V176_PRECISION_READ_OK=1")

        if _v177_panel == null or _v177_grade_label == null or _v177_line_bar == null or _v177_pace_bar == null:
            push_error("V177 debrief missing")
            get_tree().quit(8)
            return
        print("V177_SHOT_DEBRIEF_OK=1")

        if _v178_panel == null or _v178_score_labels.size() != V178_HISTORY:
            push_error("V178 session form missing")
            get_tree().quit(9)
            return
        print("V178_SESSION_FORM_OK=1")

        if _v179_panel == null or _v179_points.size() != V179_HISTORY:
            push_error("V179 dispersion package missing")
            get_tree().quit(10)
            return
        _v179_samples.clear()
        for sample in [Vector2(-12, -35), Vector2(-4, -10), Vector2(0, 0), Vector2(8, 24), Vector2(14, 42), Vector2(5, 15)]:
            _v179_push_sample(sample.x, sample.y)
        if _v179_samples.size() != V179_HISTORY or _v179_samples[0] != Vector2(-4, -10) or _v179_samples[4] != Vector2(5, 15):
            push_error("V179 rolling dispersion regression: %s" % _v179_samples)
            get_tree().quit(10)
            return
        var center: Vector2 = _v179_plot_position(Vector2.ZERO)
        if center.distance_to(V179_PLOT_SIZE * 0.5) > 0.001:
            push_error("V179 plot center regression: %s" % center)
            get_tree().quit(10)
            return
        if _v179_plot_position(Vector2(20, 0)).x <= center.x or _v179_plot_position(Vector2(0, 40)).y >= center.y:
            push_error("V179 plot axis direction regression")
            get_tree().quit(10)
            return
        _v179_preview_seed()
        if not _v179_panel.visible or _v179_tendency_label.text.is_empty() or not _v179_points[0].visible:
            push_error("V179 preview binding regression")
            get_tree().quit(10)
            return
        print("V179_SESSION_DISPERSION_OK=1")

    if not _capture_started and _preview_frames >= 14:
        _capture_started = true
        _capture_preview.call_deferred()

func _capture_preview() -> void:
    await RenderingServer.frame_post_draw
    var image := get_viewport().get_texture().get_image()
    var output := ProjectSettings.globalize_path("res://v143-preview.png")
    var error := image.save_png(output)
    if error != OK:
        push_error("V143 preview save failed: %s" % error)
        get_tree().quit(2)
        return
    print("V143_PREVIEW_SAVED=" + output)
    get_tree().quit()

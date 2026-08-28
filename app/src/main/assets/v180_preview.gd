extends "res://v180_replay_cup_focus.gd"

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
        _v179_samples.clear()
        for sample in [Vector2(-12, -35), Vector2(-4, -10), Vector2(0, 0), Vector2(8, 24), Vector2(14, 42), Vector2(5, 15)]:
            _v179_push_sample(sample.x, sample.y)
        if _v179_samples.size() != V179_HISTORY or _v179_samples[0] != Vector2(-4, -10) or _v179_samples[4] != Vector2(5, 15):
            push_error("Rolling dispersion regression: %s" % _v179_samples)
            get_tree().quit(10)
            return
        var center: Vector2 = _v179_plot_position(Vector2.ZERO)
        if center.distance_to(V179_PLOT_SIZE * 0.5) > 0.001:
            push_error("Plot center regression: %s" % center)
            get_tree().quit(10)
            return
        if _v179_plot_position(Vector2(20, 0)).x <= center.x or _v179_plot_position(Vector2(0, 40)).y >= center.y:
            push_error("Plot axis direction regression")
            get_tree().quit(10)
            return
        _v179_preview_seed()
        if not _v179_panel.visible or _v179_tendency_label.text.is_empty() or not _v179_points[0].visible:
            push_error("Dispersion preview binding regression")
            get_tree().quit(10)
            return
        print("V179_SESSION_DISPERSION_OK=1")

        if _v180_focus_chip == null or _v180_focus_distance == null or _v180_compare_chip == null or _v180_compare_primary == null or _v180_compare_secondary == null:
            push_error("Replay presentation package missing")
            get_tree().quit(11)
            return
        if _v180_focus_amount(0.70) > 0.001 or _v180_focus_amount(0.96) < 0.99:
            push_error("Replay cup-focus timing regression")
            get_tree().quit(11)
            return
        _v171_replay_actual = [Vector2(0.0, 1.0), Vector2(0.08, 3.0), Vector2(0.12, 5.0), Vector2(0.05, 6.5)]
        _v171_replay_predicted = [Vector2(0.0, 1.0), Vector2(0.03, 3.0), Vector2(0.04, 5.0), Vector2(0.0, 6.5)]
        # Keep the synthetic replay alive long enough for the rendered regression frame.
        _v171_replay_duration = 10.0
        _v171_replay_remaining = 1.20
        var final_point := _v180_final_point()
        if final_point.distance_to(Vector2(0.05, 6.5)) > 0.001:
            push_error("Replay final-point regression")
            get_tree().quit(11)
            return
        _v180_refresh_compare()
        if not _v180_compare_primary.text.begins_with("AVG ") or not _v180_compare_primary.text.contains("PEAK ") or not _v180_compare_secondary.text.begins_with("FINISH Δ "):
            push_error("Replay read-vs-roll comparison regression")
            get_tree().quit(11)
            return
        _v180_focus_chip.visible = true
        _v180_focus_distance.text = "42 cm TO CUP"
        _v180_compare_chip.visible = true
        print("REPLAY_CUP_FOCUS_OK=1")
        print("REPLAY_READ_COMPARE_OK=1")

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

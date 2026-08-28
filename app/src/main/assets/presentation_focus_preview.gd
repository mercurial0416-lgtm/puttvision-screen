extends "res://v197_preview.gd"

var _focus_checks_done := false
const FocusScene = preload("res://presentation_focus_choreography.gd")

func _process(delta: float) -> void:
    super._process(delta)
    if _focus_checks_done or _preview_frames < 14:
        return
    _focus_checks_done = true

    var probe = FocusScene.new()
    if probe._focus_phase_for(true, false, false) != "ROLL":
        push_error("Presentation focus roll-phase regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_phase_for(false, true, true) != "REPLAY":
        push_error("Presentation focus replay priority regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_phase_for(false, false, true) != "RESULT":
        push_error("Presentation focus result-phase regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_role_alpha("ROLL", "telemetry") <= probe._focus_role_alpha("ROLL", "break"):
        push_error("Presentation focus no longer prioritizes live ball telemetry")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_role_alpha("REPLAY", "target") >= probe._focus_role_alpha("READY", "target"):
        push_error("Presentation focus replay declutter regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_role_alpha("RESULT", "result") < 0.99:
        push_error("Presentation focus result package regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_role_alpha("REPLAY", "letterbox") < 0.60:
        push_error("Replay cinematic framing regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_role_alpha("READY", "letterbox") > 0.01 or probe._focus_role_alpha("RESULT", "letterbox") > 0.01:
        push_error("Replay letterbox leaks outside replay phase")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe.REPLAY_BAR_HEIGHT - 46.0) > 0.1:
        push_error("Replay letterbox safe-height regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_role_alpha("REPLAY", "replay_timeline") < 0.99:
        push_error("Replay timeline is not visible during replay")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_role_alpha("READY", "replay_timeline") > 0.01 or probe._focus_role_alpha("RESULT", "replay_timeline") > 0.01:
        push_error("Replay timeline leaks outside replay phase")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._focus_replay_progress(2.8, 2.8)) > 0.001:
        push_error("Replay timeline start progress regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._focus_replay_progress(1.4, 2.8) - 0.5) > 0.001:
        push_error("Replay timeline midpoint regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._focus_replay_progress(-0.2, 2.8) - 1.0) > 0.001:
        push_error("Replay timeline completion clamp regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._focus_replay_progress(1.0, 0.0)) > 0.001:
        push_error("Replay timeline invalid-duration guard regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_replay_stage(0.719) != "TRAIL CAM":
        push_error("Replay camera chapter early-switch regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_replay_stage(0.720) != "CAM BLEND":
        push_error("Replay camera blend start regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_replay_stage(0.899) != "CAM BLEND":
        push_error("Replay camera blend end regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_replay_stage(0.900) != "CUP CAM":
        push_error("Replay cup-camera full handoff regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe.REPLAY_CUP_CHAPTER_START < 0.60 or probe.REPLAY_CUP_CHAPTER_START > 0.85:
        push_error("Replay camera chapter marker moved outside safe replay window")
        probe.free()
        get_tree().quit(29)
        return
    if probe.REPLAY_CUP_CHAPTER_FULL <= probe.REPLAY_CUP_CHAPTER_START or probe.REPLAY_CUP_CHAPTER_FULL > 0.96:
        push_error("Replay camera blend range regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_replay_status(0.50, 1.26) != "TRAIL CAM · 1.3s":
        push_error("Replay remaining-time rounding regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_replay_status(0.78, 0.62) != "CAM BLEND · 0.6s":
        push_error("Replay camera blend status regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_replay_status(0.90, -0.4) != "CUP CAM · 0.0s":
        push_error("Replay remaining-time clamp regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_replay_status(0.90, NAN) != "CUP CAM":
        push_error("Replay invalid-time fallback regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe.REPLAY_TIMELINE_STAGE_WIDTH < 148.0:
        push_error("Replay status width too narrow for blend timecode")
        probe.free()
        get_tree().quit(29)
        return

    # Render replay-focused hierarchy in the CI screenshot: setup overlays recede while
    # cinematic framing, playback progress, camera blend state, and time-to-finish remain legible.
    var replay_phase := "REPLAY"
    var target_card := distance_label.get_parent() as CanvasItem if distance_label != null else null
    var telemetry_card := speed_label.get_parent() as CanvasItem if speed_label != null else null
    var break_card := _v174_break_value.get_parent() as CanvasItem if _v174_break_value != null else null
    var state_card := _v174_state_label.get_parent() as CanvasItem if _v174_state_label != null else null
    _preview_set_alpha(target_card, probe._focus_role_alpha(replay_phase, "target"))
    _preview_set_alpha(telemetry_card, probe._focus_role_alpha(replay_phase, "telemetry"))
    _preview_set_alpha(break_card, probe._focus_role_alpha(replay_phase, "break"))
    _preview_set_alpha(state_card, probe._focus_role_alpha(replay_phase, "state"))
    _preview_set_alpha(_v176_panel, probe._focus_role_alpha(replay_phase, "read"))
    _preview_set_alpha(_v183_panel, probe._focus_role_alpha(replay_phase, "read"))
    _preview_set_alpha(_v177_panel, probe._focus_role_alpha(replay_phase, "result"))
    _preview_set_alpha(_v188_panel, probe._focus_role_alpha(replay_phase, "result"))
    _preview_add_letterbox(probe.REPLAY_BAR_HEIGHT, probe._focus_role_alpha(replay_phase, "letterbox"))
    var preview_progress := 0.78
    var preview_remaining := 0.62
    _preview_add_replay_timeline(preview_progress, probe._focus_role_alpha(replay_phase, "replay_timeline"), probe.REPLAY_BAR_HEIGHT, probe.REPLAY_TIMELINE_SIDE_INSET, probe.REPLAY_TIMELINE_LABEL_WIDTH, probe.REPLAY_TIMELINE_STAGE_WIDTH, probe.REPLAY_TIMELINE_TRACK_HEIGHT, probe.REPLAY_CUP_CHAPTER_START, probe.REPLAY_CUP_CHAPTER_FULL, probe._focus_replay_status(preview_progress, preview_remaining))
    probe.free()
    print("PRESENTATION_FOCUS_CHOREOGRAPHY_OK=1")
    print("REPLAY_CINEMATIC_LETTERBOX_OK=1")
    print("REPLAY_TIMELINE_OK=1")
    print("REPLAY_CAMERA_CHAPTER_OK=1")
    print("REPLAY_CAMERA_BLEND_OK=1")
    print("REPLAY_TIMECODE_OK=1")

func _preview_add_letterbox(height: float, alpha: float) -> void:
    var top := ColorRect.new()
    top.name = "PreviewReplayLetterboxTop"
    top.mouse_filter = Control.MOUSE_FILTER_IGNORE
    top.position = Vector2.ZERO
    top.size = Vector2(1920.0, height)
    top.color = Color(0.012, 0.018, 0.024, alpha)
    top.z_index = 240
    add_child(top)

    var bottom := ColorRect.new()
    bottom.name = "PreviewReplayLetterboxBottom"
    bottom.mouse_filter = Control.MOUSE_FILTER_IGNORE
    bottom.position = Vector2(0.0, 1080.0 - height)
    bottom.size = Vector2(1920.0, height)
    bottom.color = Color(0.012, 0.018, 0.024, alpha)
    bottom.z_index = 240
    add_child(bottom)

func _preview_add_replay_timeline(progress: float, alpha: float, bar_height: float, side_inset: float, label_width: float, stage_width: float, track_height: float, chapter_start: float, chapter_full: float, stage_text: String) -> void:
    var label := Label.new()
    label.name = "PreviewReplayTimelineLabel"
    label.position = Vector2(side_inset, 1080.0 - bar_height + 12.0)
    label.size = Vector2(label_width, 24.0)
    label.text = "SHOT REPLAY"
    label.add_theme_font_size_override("font_size", 14)
    label.add_theme_color_override("font_color", Color(0.84, 0.90, 0.94, alpha))
    label.z_index = 242
    add_child(label)

    var stage := Label.new()
    stage.name = "PreviewReplayCameraStage"
    stage.position = Vector2(1920.0 - side_inset - stage_width, 1080.0 - bar_height + 12.0)
    stage.size = Vector2(stage_width, 24.0)
    stage.text = stage_text
    stage.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
    stage.add_theme_font_size_override("font_size", 13)
    stage.add_theme_color_override("font_color", Color(0.72, 0.84, 0.88, 0.94 * alpha))
    stage.z_index = 242
    add_child(stage)

    var track_left: float = side_inset + label_width
    var track_width: float = maxf(1.0, 1920.0 - track_left - side_inset - stage_width - 14.0)
    var track := ColorRect.new()
    track.name = "PreviewReplayTimelineTrack"
    track.position = Vector2(track_left, 1080.0 - bar_height + 22.0)
    track.size = Vector2(track_width, track_height)
    track.color = Color(0.28, 0.34, 0.38, 0.42 * alpha)
    track.z_index = 242
    add_child(track)

    var blend_left := track_left + track_width * clampf(chapter_start, 0.0, 1.0)
    var blend_right := track_left + track_width * clampf(chapter_full, 0.0, 1.0)
    var blend_range := ColorRect.new()
    blend_range.name = "PreviewReplayCameraBlendRange"
    blend_range.position = Vector2(blend_left, track.position.y)
    blend_range.size = Vector2(maxf(0.0, blend_right - blend_left), track_height)
    blend_range.color = Color(0.90, 0.78, 0.40, 0.26 * alpha)
    blend_range.z_index = 243
    add_child(blend_range)

    var fill := ColorRect.new()
    fill.name = "PreviewReplayTimelineFill"
    fill.position = track.position
    fill.size = Vector2(track_width * clampf(progress, 0.0, 1.0), track_height)
    fill.color = Color(0.88, 0.95, 0.98, 0.94 * alpha)
    fill.z_index = 244
    add_child(fill)

    var marker := ColorRect.new()
    marker.name = "PreviewReplayCupCameraMarker"
    marker.position = Vector2(blend_left - 1.0, 1080.0 - bar_height + 18.0)
    marker.size = Vector2(2.0, 11.0)
    marker.color = Color(0.90, 0.78, 0.40, 0.90 * alpha)
    marker.z_index = 245
    add_child(marker)

    var end_marker := ColorRect.new()
    end_marker.name = "PreviewReplayCupCameraFullMarker"
    end_marker.position = Vector2(blend_right - 1.0, 1080.0 - bar_height + 18.0)
    end_marker.size = Vector2(2.0, 11.0)
    end_marker.color = Color(0.90, 0.78, 0.40, 0.62 * alpha)
    end_marker.z_index = 245
    add_child(end_marker)

func _preview_set_alpha(item: CanvasItem, alpha: float) -> void:
    if item == null:
        return
    var c := item.modulate
    c.a = alpha
    item.modulate = c
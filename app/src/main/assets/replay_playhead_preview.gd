extends "res://commercial_read_flow_preview.gd"

const ReplayFinish = preload("res://replay_playhead_finish.gd")
var _replay_playhead_checks_done := false
var _preview_replay_playhead: ColorRect
var _preview_replay_chapter_color := Color.WHITE

func _seed_replay_finish_preview() -> void:
    var track := get_node_or_null("PreviewReplayTimelineTrack") as ColorRect
    var fill := get_node_or_null("PreviewReplayTimelineFill") as ColorRect
    var stage := get_node_or_null("PreviewReplayCameraStage") as Label
    if track == null or fill == null or stage == null:
        return
    if _focus_replay_timeline != null:
        _focus_replay_timeline.z_index = ReplayFinish.REPLAY_TIMELINE_TOP_Z
    fill.z_index = ReplayFinish.REPLAY_TIMELINE_TOP_Z
    stage.z_index = ReplayFinish.REPLAY_TIMELINE_TOP_Z
    fill.color = Color(_preview_replay_chapter_color.r, _preview_replay_chapter_color.g, _preview_replay_chapter_color.b, 0.94)
    stage.add_theme_color_override("font_color", Color(_preview_replay_chapter_color.r, _preview_replay_chapter_color.g, _preview_replay_chapter_color.b, 0.94))
    _preview_set_alpha(_v179_panel, 0.0)
    if _preview_replay_playhead != null:
        _preview_replay_playhead.visible = true
        _preview_replay_playhead.modulate.a = 1.0

func _process(delta: float) -> void:
    super._process(delta)
    if _replay_playhead_checks_done:
        if _capture_started:
            _seed_replay_finish_preview()
        return
    if not _capture_started:
        return
    _replay_playhead_checks_done = true

    var probe = ReplayFinish.new()
    if absf(probe._replay_playhead_x(-1.0, 400.0)) > 0.001:
        push_error("Replay playhead lower clamp regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._replay_playhead_x(0.50, 400.0) - 197.0) > 0.001:
        push_error("Replay playhead midpoint regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._replay_playhead_x(2.0, 400.0) - 397.0) > 0.001:
        push_error("Replay playhead upper clamp regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._replay_chapter_color(0.719) != probe.REPLAY_TRAIL_COLOR:
        push_error("Replay trail chapter color regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._replay_chapter_color(0.720) != probe.REPLAY_CUP_COLOR:
        push_error("Replay cup chapter color regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe.REPLAY_PLAYHEAD_WIDTH < 5.0 or probe.REPLAY_PLAYHEAD_HEIGHT < 9.0:
        push_error("Replay playhead is too small for TV readability")
        probe.free()
        get_tree().quit(29)
        return
    if probe.REPLAY_TIMELINE_TOP_Z < 280:
        push_error("Replay timeline layering regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._replay_session_alpha("REPLAY") > 0.01 or probe._replay_session_alpha("READY") < 0.99:
        push_error("Replay practice-panel declutter regression")
        probe.free()
        get_tree().quit(29)
        return

    var preview_progress := 0.78
    _preview_replay_chapter_color = probe._replay_chapter_color(preview_progress)
    var track := get_node_or_null("PreviewReplayTimelineTrack") as ColorRect
    var fill := get_node_or_null("PreviewReplayTimelineFill") as ColorRect
    var stage := get_node_or_null("PreviewReplayCameraStage") as Label
    if track == null or fill == null or stage == null:
        push_error("Replay preview timeline nodes missing")
        probe.free()
        get_tree().quit(29)
        return

    _preview_replay_playhead = ColorRect.new()
    _preview_replay_playhead.name = "PreviewReplayCurrentPlayhead"
    _preview_replay_playhead.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _preview_replay_playhead.position = Vector2(track.position.x + probe._replay_playhead_x(preview_progress, track.size.x), track.position.y - 4.0)
    _preview_replay_playhead.size = Vector2(probe.REPLAY_PLAYHEAD_WIDTH, probe.REPLAY_PLAYHEAD_HEIGHT)
    _preview_replay_playhead.color = _preview_replay_chapter_color
    _preview_replay_playhead.z_index = ReplayFinish.REPLAY_TIMELINE_TOP_Z + 2
    add_child(_preview_replay_playhead)
    _seed_replay_finish_preview()

    probe.free()
    print("REPLAY_PLAYHEAD_OK=1")
    print("REPLAY_CHAPTER_COLOR_OK=1")
    print("REPLAY_PRACTICE_DECLUTTER_OK=1")

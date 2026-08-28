extends "res://commercial_read_flow_preview.gd"

const ReplayFinish = preload("res://replay_playhead_finish.gd")
var _replay_playhead_checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    if _replay_playhead_checks_done or _preview_frames < 18:
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

    var preview_progress := 0.78
    var chapter_color: Color = probe._replay_chapter_color(preview_progress)
    var track := get_node_or_null("PreviewReplayTimelineTrack") as ColorRect
    var fill := get_node_or_null("PreviewReplayTimelineFill") as ColorRect
    var stage := get_node_or_null("PreviewReplayCameraStage") as Label
    if track == null or fill == null or stage == null:
        push_error("Replay preview timeline nodes missing")
        probe.free()
        get_tree().quit(29)
        return

    fill.color = Color(chapter_color.r, chapter_color.g, chapter_color.b, 0.94)
    stage.add_theme_color_override("font_color", Color(chapter_color.r, chapter_color.g, chapter_color.b, 0.94))

    var playhead := ColorRect.new()
    playhead.name = "PreviewReplayCurrentPlayhead"
    playhead.mouse_filter = Control.MOUSE_FILTER_IGNORE
    playhead.position = Vector2(track.position.x + probe._replay_playhead_x(preview_progress, track.size.x), track.position.y - 4.0)
    playhead.size = Vector2(probe.REPLAY_PLAYHEAD_WIDTH, probe.REPLAY_PLAYHEAD_HEIGHT)
    playhead.color = chapter_color
    playhead.z_index = 245
    add_child(playhead)

    probe.free()
    print("REPLAY_PLAYHEAD_OK=1")
    print("REPLAY_CHAPTER_COLOR_OK=1")

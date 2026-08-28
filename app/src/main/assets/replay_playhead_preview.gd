extends "res://commercial_read_flow_preview.gd"

const ReplayPlayheadScene = preload("res://replay_playhead_polish.gd")
var _replay_playhead_checks_done := false

func _preview_add_replay_timeline(progress: float, alpha: float, bar_height: float, side_inset: float, label_width: float, stage_width: float, track_height: float, chapter_start: float, chapter_full: float, stage_text: String) -> void:
    super._preview_add_replay_timeline(progress, alpha, bar_height, side_inset, label_width, stage_width, track_height, chapter_start, chapter_full, stage_text)

    var track_left: float = side_inset + label_width
    var track_width: float = maxf(1.0, 1920.0 - track_left - side_inset - stage_width - 14.0)
    var x := track_left + track_width * clampf(progress, 0.0, 1.0)

    var finish_width := track_width * 0.12
    var finish_zone := ColorRect.new()
    finish_zone.name = "PreviewReplayFinishZone"
    finish_zone.position = Vector2(track_left + track_width - finish_width, 1080.0 - bar_height + 22.0)
    finish_zone.size = Vector2(finish_width, track_height)
    finish_zone.color = Color(0.66, 0.92, 0.78, 0.18 * alpha)
    finish_zone.z_index = 245
    add_child(finish_zone)

    # Re-overlay the blend segment above the progress fill so the camera handoff remains legible
    # after playback crosses the first handoff marker.
    var blend_left := track_left + track_width * clampf(chapter_start, 0.0, 1.0)
    var blend_right := track_left + track_width * clampf(chapter_full, 0.0, 1.0)
    var blend_overlay := ColorRect.new()
    blend_overlay.name = "PreviewReplayCameraBlendOverlay"
    blend_overlay.position = Vector2(blend_left, 1080.0 - bar_height + 22.0)
    blend_overlay.size = Vector2(maxf(0.0, blend_right - blend_left), track_height)
    blend_overlay.color = Color(0.90, 0.78, 0.40, 0.26 * alpha)
    blend_overlay.z_index = 246
    add_child(blend_overlay)

    var playhead := ColorRect.new()
    playhead.name = "PreviewReplayTimelinePlayhead"
    playhead.mouse_filter = Control.MOUSE_FILTER_IGNORE
    playhead.size = Vector2(9.0, 9.0)
    playhead.pivot_offset = playhead.size * 0.5
    playhead.rotation_degrees = 45.0
    playhead.position = Vector2(x - 4.5, 1080.0 - bar_height + 19.0)
    playhead.color = Color(0.96, 0.99, 1.0, 0.98 * alpha)
    playhead.z_index = 247
    add_child(playhead)

func _process(delta: float) -> void:
    super._process(delta)
    if _replay_playhead_checks_done or _preview_frames < 15:
        return
    _replay_playhead_checks_done = true

    var probe = ReplayPlayheadScene.new()
    if absf(probe._replay_playhead_x(0.50, 100.0) - 50.0) > 0.001:
        push_error("Replay playhead midpoint regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._replay_playhead_x(-0.4, 100.0)) > 0.001:
        push_error("Replay playhead lower clamp regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._replay_playhead_x(1.4, 100.0) - 100.0) > 0.001:
        push_error("Replay playhead upper clamp regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._replay_playhead_x(NAN, 100.0)) > 0.001:
        push_error("Replay playhead invalid progress guard regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe.REPLAY_PLAYHEAD_SIZE < 7.0 or probe.REPLAY_PLAYHEAD_SIZE > 12.0:
        push_error("Replay playhead TV readability size regression")
        probe.free()
        get_tree().quit(29)
        return
    var finish_geometry := probe._replay_finish_zone_geometry(100.0)
    if absf(finish_geometry.x - 88.0) > 0.001 or absf(finish_geometry.y - 12.0) > 0.001:
        push_error("Replay finish-zone geometry regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._replay_finish_zone_geometry(-1.0) != Vector2.ZERO:
        push_error("Replay finish-zone invalid width guard regression")
        probe.free()
        get_tree().quit(29)
        return
    probe.free()
    print("REPLAY_PLAYHEAD_VISIBILITY_OK=1")
    print("REPLAY_BLEND_LAYERING_OK=1")
    print("REPLAY_FINISH_ZONE_OK=1")

extends "res://commercial_read_flow_preview.gd"

const ReplayPlayheadScene = preload("res://replay_playhead_polish.gd")
const ReadLaunchScene = preload("res://read_launch_vector.gd")
var _replay_playhead_checks_done := false
var _read_launch_preview_added := false

func _preview_add_replay_chapter_label(text_value: String, rect: Rect2, tint: Color, alpha: float) -> void:
    var label := Label.new()
    label.name = "PreviewReplayChapter%s" % text_value.capitalize()
    label.position = rect.position
    label.size = rect.size
    label.text = text_value
    label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    label.add_theme_font_size_override("font_size", 10)
    label.add_theme_color_override("font_color", Color(tint.r, tint.g, tint.b, tint.a * alpha))
    label.z_index = 248
    add_child(label)

func _preview_add_replay_timeline(progress: float, alpha: float, bar_height: float, side_inset: float, label_width: float, stage_width: float, track_height: float, chapter_start: float, chapter_full: float, stage_text: String) -> void:
    super._preview_add_replay_timeline(progress, alpha, bar_height, side_inset, label_width, stage_width, track_height, chapter_start, chapter_full, stage_text)

    var track_left: float = side_inset + label_width
    var track_width: float = maxf(1.0, 1920.0 - track_left - side_inset - stage_width - 14.0)
    var helper = ReplayPlayheadScene.new()
    var x := track_left + helper._replay_playhead_x(progress, track_width)
    helper.free()

    var finish_width := track_width * 0.12
    var finish_zone := ColorRect.new()
    finish_zone.name = "PreviewReplayFinishZone"
    finish_zone.position = Vector2(track_left + track_width - finish_width, 1080.0 - bar_height + 22.0)
    finish_zone.size = Vector2(finish_width, track_height)
    finish_zone.color = Color(0.66, 0.92, 0.78, 0.18 * alpha)
    finish_zone.z_index = 245
    add_child(finish_zone)

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

    var label_y := 1080.0 - bar_height + 27.0
    _preview_add_replay_chapter_label("ROLL", Rect2(Vector2(track_left, label_y), Vector2(blend_left - track_left, 14.0)), Color(0.70, 0.80, 0.84, 0.72), alpha)
    _preview_add_replay_chapter_label("BLEND", Rect2(Vector2(blend_left, label_y), Vector2(blend_right - blend_left, 14.0)), Color(0.90, 0.78, 0.40, 0.82), alpha)
    _preview_add_replay_chapter_label("CUP", Rect2(Vector2(blend_right, label_y), Vector2(track_left + track_width - blend_right, 14.0)), Color(0.70, 0.92, 0.80, 0.82), alpha)

func _preview_add_read_launch_vector() -> void:
    if _v183_panel == null:
        return
    var probe = ReadLaunchScene.new()
    var geometry := probe._read_launch_geometry(0.42)
    var start: Vector2 = geometry["start"]
    var tip: Vector2 = geometry["tip"]

    var shaft := Line2D.new()
    shaft.name = "PreviewCommercialReadLaunchShaft"
    shaft.width = 2.7
    shaft.default_color = Color(0.48, 0.91, 1.0, 0.94)
    shaft.begin_cap_mode = Line2D.LINE_CAP_ROUND
    shaft.end_cap_mode = Line2D.LINE_CAP_ROUND
    shaft.points = PackedVector2Array([start, tip])
    _v183_panel.add_child(shaft)

    var head := Line2D.new()
    head.name = "PreviewCommercialReadLaunchHead"
    head.width = 2.7
    head.default_color = Color(0.74, 0.96, 1.0, 0.98)
    head.joint_mode = Line2D.LINE_JOINT_ROUND
    head.begin_cap_mode = Line2D.LINE_CAP_ROUND
    head.end_cap_mode = Line2D.LINE_CAP_ROUND
    head.points = PackedVector2Array([geometry["left"], tip, geometry["right"]])
    _v183_panel.add_child(head)
    probe.free()

func _process(delta: float) -> void:
    if not _read_launch_preview_added and _preview_frames >= 11:
        _read_launch_preview_added = true
        _preview_add_read_launch_vector()
    super._process(delta)
    if _replay_playhead_checks_done or _preview_frames < 15:
        return
    _replay_playhead_checks_done = true

    var probe = ReplayPlayheadScene.new()
    var rotated_half_extent := probe._replay_playhead_half_extent(100.0)
    var expected_rotated_half_extent := probe.REPLAY_PLAYHEAD_SIZE / sqrt(2.0)
    if absf(rotated_half_extent - expected_rotated_half_extent) > 0.001:
        push_error("Replay rotated playhead extent regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._replay_playhead_x(0.50, 100.0) - 50.0) > 0.001:
        push_error("Replay playhead midpoint regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._replay_playhead_x(-0.4, 100.0) - expected_rotated_half_extent) > 0.001:
        push_error("Replay playhead lower rotated-edge containment regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._replay_playhead_x(1.4, 100.0) - (100.0 - expected_rotated_half_extent)) > 0.001:
        push_error("Replay playhead upper rotated-edge containment regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._replay_playhead_x(0.5, 6.0) - 3.0) > 0.001:
        push_error("Replay playhead narrow-track containment regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._replay_playhead_half_extent(6.0) - 3.0) > 0.001:
        push_error("Replay playhead narrow-track rotated extent regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(probe._replay_playhead_x(NAN, 100.0)) > 0.001 or absf(probe._replay_playhead_half_extent(NAN)) > 0.001:
        push_error("Replay playhead invalid input guard regression")
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
    var chapters := probe._replay_chapter_geometry(100.0)
    var roll: Vector2 = chapters["roll"]
    var blend: Vector2 = chapters["blend"]
    var cup: Vector2 = chapters["cup"]
    if absf(roll.x) > 0.001 or absf(roll.y - 72.0) > 0.001:
        push_error("Replay roll chapter geometry regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(blend.x - 72.0) > 0.001 or absf(blend.y - 18.0) > 0.001:
        push_error("Replay blend chapter geometry regression")
        probe.free()
        get_tree().quit(29)
        return
    if absf(cup.x - 90.0) > 0.001 or absf(cup.y - 10.0) > 0.001:
        push_error("Replay cup chapter geometry regression")
        probe.free()
        get_tree().quit(29)
        return
    var invalid_chapters := probe._replay_chapter_geometry(NAN)
    if invalid_chapters["roll"] != Vector2.ZERO or invalid_chapters["blend"] != Vector2.ZERO or invalid_chapters["cup"] != Vector2.ZERO:
        push_error("Replay chapter invalid width guard regression")
        probe.free()
        get_tree().quit(29)
        return
    probe.free()

    var launch_probe = ReadLaunchScene.new()
    var right_launch := launch_probe._read_launch_geometry(0.42)
    var left_launch := launch_probe._read_launch_geometry(-0.42)
    var right_start: Vector2 = right_launch["start"]
    var right_tip: Vector2 = right_launch["tip"]
    var left_start: Vector2 = left_launch["start"]
    var left_tip: Vector2 = left_launch["tip"]
    if right_tip.x <= right_start.x or left_tip.x >= left_start.x:
        push_error("Read launch-vector side-direction regression")
        launch_probe.free()
        get_tree().quit(34)
        return
    if right_start.distance_to(right_tip) < 18.0:
        push_error("Read launch-vector visibility length regression")
        launch_probe.free()
        get_tree().quit(34)
        return
    var right_left: Vector2 = right_launch["left"]
    var right_right: Vector2 = right_launch["right"]
    if absf(right_left.distance_to(right_right) - 9.2) > 0.35:
        push_error("Read launch-vector arrowhead width regression")
        launch_probe.free()
        get_tree().quit(34)
        return
    var tangent: Vector2 = right_launch["tangent"]
    if absf(tangent.length() - 1.0) > 0.01:
        push_error("Read launch-vector tangent regression")
        launch_probe.free()
        get_tree().quit(34)
        return
    launch_probe.free()

    print("REPLAY_PLAYHEAD_VISIBILITY_OK=1")
    print("REPLAY_PLAYHEAD_ROTATED_EDGE_CONTAINMENT_OK=1")
    print("REPLAY_BLEND_LAYERING_OK=1")
    print("REPLAY_FINISH_ZONE_OK=1")
    print("REPLAY_CHAPTER_CLARITY_OK=1")
    print("COMMERCIAL_READ_LAUNCH_VECTOR_OK=1")

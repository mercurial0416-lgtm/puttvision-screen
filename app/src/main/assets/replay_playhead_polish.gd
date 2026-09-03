extends "res://commercial_read_flow.gd"

# Presentation-only replay timeline polish. This layer never mutates shot state, Android physics,
# GreenTerrain, GreenReadAdvisor, aiming, scoring, or camera timing.
const REPLAY_PLAYHEAD_SIZE := 9.0
const REPLAY_PLAYHEAD_ROTATION_DEG := 45.0
const REPLAY_PLAYHEAD_ALPHA := 0.98
const REPLAY_FINISH_ZONE_FRACTION := 0.12
const REPLAY_FINISH_ZONE_ALPHA := 0.18
const REPLAY_CHAPTER_FONT_SIZE := 10
const REPLAY_CHAPTER_LABEL_Y := 27.0
const REPLAY_CHAPTER_ABBREV_MIN_WIDTH := 16.0
const REPLAY_CHAPTER_TEXT_CHAR_WIDTH := 6.4
const REPLAY_CHAPTER_TEXT_PADDING := 8.0

var _replay_playhead: ColorRect
var _replay_finish_zone: ColorRect
var _replay_roll_label: Label
var _replay_blend_label: Label
var _replay_cup_label: Label

func _build_hud() -> void:
    super._build_hud()
    if _focus_replay_track == null:
        return

    # Keep the camera-blend band readable even after the progress fill has crossed into it.
    # Previously the opaque fill could visually bury the beginning of the blend interval.
    if _focus_replay_fill != null:
        _focus_replay_fill.z_index = 0
    if _focus_replay_blend_range != null:
        _focus_replay_blend_range.z_index = 1
    if _focus_replay_chapter_marker != null:
        _focus_replay_chapter_marker.z_index = 2
    if _focus_replay_chapter_end_marker != null:
        _focus_replay_chapter_end_marker.z_index = 2

    # A subtle final-window band makes the replay read like a broadcast timeline: the viewer can
    # anticipate the cup-focus payoff without adding labels or touching replay timing.
    _replay_finish_zone = ColorRect.new()
    _replay_finish_zone.name = "ReplayFinishZone"
    _replay_finish_zone.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _replay_finish_zone.color = Color(0.66, 0.92, 0.78, REPLAY_FINISH_ZONE_ALPHA)
    _replay_finish_zone.z_index = 1
    _focus_replay_track.add_child(_replay_finish_zone)

    _replay_playhead = ColorRect.new()
    _replay_playhead.name = "ReplayTimelinePlayhead"
    _replay_playhead.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _replay_playhead.size = Vector2(REPLAY_PLAYHEAD_SIZE, REPLAY_PLAYHEAD_SIZE)
    _replay_playhead.pivot_offset = _replay_playhead.size * 0.5
    _replay_playhead.rotation_degrees = REPLAY_PLAYHEAD_ROTATION_DEG
    _replay_playhead.color = Color(0.96, 0.99, 1.0, REPLAY_PLAYHEAD_ALPHA)
    _replay_playhead.z_index = 3
    _focus_replay_track.add_child(_replay_playhead)

    # Commercial replay systems benefit from chapter context, not just a moving playhead. These
    # labels explain the visual handoff at a glance while adapting cleanly to narrow mobile tracks.
    _replay_roll_label = _replay_build_chapter_label("ROLL", Color(0.70, 0.80, 0.84, 0.72))
    _replay_blend_label = _replay_build_chapter_label("BLEND", Color(0.90, 0.78, 0.40, 0.82))
    _replay_cup_label = _replay_build_chapter_label("CUP", Color(0.70, 0.92, 0.80, 0.82))

func _replay_build_chapter_label(text_value: String, tint: Color) -> Label:
    var label := Label.new()
    label.mouse_filter = Control.MOUSE_FILTER_IGNORE
    label.text = text_value
    label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    label.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
    label.add_theme_font_size_override("font_size", REPLAY_CHAPTER_FONT_SIZE)
    label.add_theme_color_override("font_color", tint)
    label.z_index = 4
    _focus_replay_timeline.add_child(label)
    return label

func _replay_playhead_rotated_extent() -> float:
    var radians := deg_to_rad(REPLAY_PLAYHEAD_ROTATION_DEG)
    return REPLAY_PLAYHEAD_SIZE * (absf(cos(radians)) + absf(sin(radians)))

func _replay_track_has_playhead_room(track_width: float) -> bool:
    if not is_finite(track_width) or track_width <= 0.0:
        return false
    # A 45-degree square is wider than its unrotated Control bounds. On very narrow Forward Mobile
    # layouts the anchored replay track can collapse while the child playhead would otherwise remain
    # visible over the neighboring labels. Suppress only the ornament until its full rotated footprint fits.
    return track_width >= _replay_playhead_rotated_extent()

func _replay_playhead_half_extent(track_width: float) -> float:
    if not is_finite(track_width) or track_width <= 0.0:
        return 0.0
    var rotated_half_extent := _replay_playhead_rotated_extent() * 0.5
    return minf(rotated_half_extent, track_width * 0.5)

func _replay_playhead_x(progress: float, track_width: float) -> float:
    if not is_finite(progress) or not is_finite(track_width) or track_width <= 0.0:
        return 0.0
    # Keep the playhead spatially truthful to the same progress coordinate used by the fill and
    # camera chapter markers. The previous implementation remapped the *entire* 0..1 interval into
    # the inset-safe width, so a 72% camera handoff rendered the diamond several pixels before the
    # actual 72% marker. Clamp only when the diamond would physically clip at either endpoint.
    var half_extent := _replay_playhead_half_extent(track_width)
    var true_x := clampf(progress, 0.0, 1.0) * track_width
    return clampf(true_x, half_extent, maxf(half_extent, track_width - half_extent))

func _replay_finish_zone_geometry(track_width: float) -> Vector2:
    if not is_finite(track_width) or track_width <= 0.0:
        return Vector2.ZERO
    var width := track_width * clampf(REPLAY_FINISH_ZONE_FRACTION, 0.0, 0.5)
    return Vector2(track_width - width, width)

func _replay_chapter_geometry(track_width: float) -> Dictionary:
    if not is_finite(track_width) or track_width <= 0.0:
        return {"roll": Vector2.ZERO, "blend": Vector2.ZERO, "cup": Vector2.ZERO}
    var roll_end := track_width * clampf(REPLAY_CUP_CHAPTER_START, 0.0, 1.0)
    var cup_start := track_width * clampf(REPLAY_CUP_CHAPTER_FULL, 0.0, 1.0)
    return {
        "roll": Vector2(0.0, roll_end),
        "blend": Vector2(roll_end, maxf(0.0, cup_start - roll_end)),
        "cup": Vector2(cup_start, maxf(0.0, track_width - cup_start))
    }

func _replay_chapter_label_text(full_text: String, segment_width: float) -> String:
    if not is_finite(segment_width) or segment_width < REPLAY_CHAPTER_ABBREV_MIN_WIDTH:
        return ""
    var full_min_width := float(full_text.length()) * REPLAY_CHAPTER_TEXT_CHAR_WIDTH + REPLAY_CHAPTER_TEXT_PADDING
    if segment_width >= full_min_width:
        return full_text
    return full_text.substr(0, 1)

func _replay_place_chapter_label(label: Label, segment: Vector2, full_text: String) -> void:
    if label == null or _focus_replay_track == null:
        return
    var track_global_x := _focus_replay_track.position.x
    var display_text := _replay_chapter_label_text(full_text, segment.y)
    label.visible = not display_text.is_empty()
    label.text = display_text
    label.position = Vector2(track_global_x + segment.x, REPLAY_CHAPTER_LABEL_Y)
    label.size = Vector2(segment.y, 14.0)

func _focus_update_replay_timeline() -> void:
    super._focus_update_replay_timeline()
    if _focus_replay_track == null:
        return
    var progress := _focus_replay_progress(_v171_replay_remaining, _v171_replay_duration)
    var track_width := maxf(0.0, _focus_replay_track.size.x)

    if _replay_finish_zone != null:
        var finish_geometry := _replay_finish_zone_geometry(track_width)
        _replay_finish_zone.position = Vector2(finish_geometry.x, 0.0)
        _replay_finish_zone.size = Vector2(finish_geometry.y, REPLAY_TIMELINE_TRACK_HEIGHT)

    var chapters := _replay_chapter_geometry(track_width)
    _replay_place_chapter_label(_replay_roll_label, chapters["roll"], "ROLL")
    _replay_place_chapter_label(_replay_blend_label, chapters["blend"], "BLEND")
    _replay_place_chapter_label(_replay_cup_label, chapters["cup"], "CUP")

    if _replay_playhead == null:
        return
    _replay_playhead.visible = _replay_track_has_playhead_room(track_width)
    if not _replay_playhead.visible:
        return
    var x := _replay_playhead_x(progress, track_width)
    _replay_playhead.position = Vector2(
        x - REPLAY_PLAYHEAD_SIZE * 0.5,
        (REPLAY_TIMELINE_TRACK_HEIGHT - REPLAY_PLAYHEAD_SIZE) * 0.5
    )

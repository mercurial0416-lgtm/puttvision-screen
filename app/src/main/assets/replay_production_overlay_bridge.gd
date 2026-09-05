extends "res://replay_timeline_camera_truth.gd"

# Production bridge for presentation-only replay ornaments. The production camera-truth layer owns
# timeline fill/chapter positions and intentionally overrides the inherited updater; this bridge
# advances the premium replay overlays after that authoritative presentation pass without changing
# replay timing, camera choreography, Android physics, GreenTerrain, GreenReadAdvisor, or scoring.

const REPLAY_HANDOFF_MARKER_WIDTH := 1.5
const REPLAY_HANDOFF_MARKER_MIN_TRACK_WIDTH := 132.0

var _replay_roll_blend_handoff: ColorRect
var _replay_blend_cup_handoff: ColorRect

func _build_hud() -> void:
    super._build_hud()
    if _focus_replay_track == null:
        return

    # Thin camera handoff markers make the replay grammar readable at a glance without adding more
    # text. They are presentation-only, consume the same production camera boundaries, and disappear
    # on narrow Forward Mobile layouts where two extra vertical marks would become visual noise.
    _replay_roll_blend_handoff = ColorRect.new()
    _replay_roll_blend_handoff.name = "ReplayRollBlendHandoff"
    _replay_roll_blend_handoff.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _replay_roll_blend_handoff.color = Color(0.84, 0.91, 0.94, 0.46)
    _replay_roll_blend_handoff.z_index = 2
    _focus_replay_track.add_child(_replay_roll_blend_handoff)

    _replay_blend_cup_handoff = ColorRect.new()
    _replay_blend_cup_handoff.name = "ReplayBlendCupHandoff"
    _replay_blend_cup_handoff.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _replay_blend_cup_handoff.color = Color(0.88, 0.96, 0.90, 0.56)
    _replay_blend_cup_handoff.z_index = 2
    _focus_replay_track.add_child(_replay_blend_cup_handoff)

func _production_replay_chapters(track_width: float) -> Dictionary:
    if not is_finite(track_width) or track_width <= 0.0:
        return {"roll": Vector2.ZERO, "blend": Vector2.ZERO, "cup": Vector2.ZERO}
    var roll_end := track_width * clampf(V180_FOCUS_START, 0.0, 1.0)
    var cup_start := track_width * clampf(V180_FOCUS_FULL, 0.0, 1.0)
    return {
        "roll": Vector2(0.0, roll_end),
        "blend": Vector2(roll_end, maxf(0.0, cup_start - roll_end)),
        "cup": Vector2(cup_start, maxf(0.0, track_width - cup_start))
    }

func _production_replay_active_chapter(progress: float) -> int:
    if not is_finite(progress):
        return -1
    var safe_progress := clampf(progress, 0.0, 1.0)
    if safe_progress < V180_FOCUS_START:
        return 0
    if safe_progress < V180_FOCUS_FULL:
        return 1
    return 2

func _production_replay_local_progress(progress: float) -> float:
    if not is_finite(progress):
        return -1.0
    var safe_progress := clampf(progress, 0.0, 1.0)
    var chapter_start := 0.0
    var chapter_end := V180_FOCUS_START
    if safe_progress >= V180_FOCUS_FULL:
        chapter_start = V180_FOCUS_FULL
        chapter_end = 1.0
    elif safe_progress >= V180_FOCUS_START:
        chapter_start = V180_FOCUS_START
        chapter_end = V180_FOCUS_FULL
    var span := chapter_end - chapter_start
    if not is_finite(span) or span <= 0.0:
        return -1.0
    return clampf((safe_progress - chapter_start) / span, 0.0, 1.0)

func _production_replay_apply_emphasis(progress: float, timing_valid: bool) -> void:
    var active := _production_replay_active_chapter(progress) if timing_valid else -1
    var labels: Array[Label] = [_replay_roll_label, _replay_blend_label, _replay_cup_label]
    var inactive_colors := [
        Color(0.70, 0.80, 0.84, REPLAY_CHAPTER_INACTIVE_ALPHA),
        Color(0.90, 0.78, 0.40, REPLAY_CHAPTER_INACTIVE_ALPHA),
        Color(0.70, 0.92, 0.80, REPLAY_CHAPTER_INACTIVE_ALPHA)
    ]
    var active_colors := [
        Color(0.91, 0.97, 1.0, REPLAY_CHAPTER_ACTIVE_ALPHA),
        Color(1.0, 0.88, 0.50, REPLAY_CHAPTER_ACTIVE_ALPHA),
        Color(0.80, 1.0, 0.88, REPLAY_CHAPTER_ACTIVE_ALPHA)
    ]
    for index in range(labels.size()):
        var label := labels[index]
        if label == null:
            continue
        var is_active := index == active
        label.add_theme_font_size_override("font_size", REPLAY_CHAPTER_ACTIVE_FONT_SIZE if is_active else REPLAY_CHAPTER_FONT_SIZE)
        label.add_theme_color_override("font_color", active_colors[index] if is_active else inactive_colors[index])

func _production_replay_update_progress(progress: float, chapters: Dictionary, timing_valid: bool) -> void:
    if _replay_chapter_progress == null:
        return
    var active := _production_replay_active_chapter(progress)
    var local_progress := _production_replay_local_progress(progress)
    if not timing_valid or active < 0 or local_progress < 0.0:
        _replay_chapter_progress.visible = false
        return
    var keys := ["roll", "blend", "cup"]
    var segment: Vector2 = chapters.get(keys[active], Vector2.ZERO)
    _replay_chapter_progress.visible = segment.y >= REPLAY_CHAPTER_PROGRESS_MIN_WIDTH
    if not _replay_chapter_progress.visible:
        return
    _replay_chapter_progress.position = Vector2(segment.x, REPLAY_TIMELINE_TRACK_HEIGHT - REPLAY_CHAPTER_PROGRESS_HEIGHT)
    _replay_chapter_progress.size = Vector2(segment.y * local_progress, REPLAY_CHAPTER_PROGRESS_HEIGHT)

func _production_replay_update_handoff_markers(track_width: float, chapters: Dictionary) -> void:
    if _replay_roll_blend_handoff == null or _replay_blend_cup_handoff == null:
        return
    var visible := is_finite(track_width) and track_width >= REPLAY_HANDOFF_MARKER_MIN_TRACK_WIDTH
    _replay_roll_blend_handoff.visible = visible
    _replay_blend_cup_handoff.visible = visible
    if not visible:
        return

    var roll: Vector2 = chapters.get("roll", Vector2.ZERO)
    var cup: Vector2 = chapters.get("cup", Vector2.ZERO)
    var roll_blend_x := clampf(roll.x + roll.y, 0.0, track_width)
    var blend_cup_x := clampf(cup.x, 0.0, track_width)
    _replay_roll_blend_handoff.position = Vector2(roll_blend_x - REPLAY_HANDOFF_MARKER_WIDTH * 0.5, 0.0)
    _replay_roll_blend_handoff.size = Vector2(REPLAY_HANDOFF_MARKER_WIDTH, REPLAY_TIMELINE_TRACK_HEIGHT)
    _replay_blend_cup_handoff.position = Vector2(blend_cup_x - REPLAY_HANDOFF_MARKER_WIDTH * 0.5, 0.0)
    _replay_blend_cup_handoff.size = Vector2(REPLAY_HANDOFF_MARKER_WIDTH, REPLAY_TIMELINE_TRACK_HEIGHT)

func _focus_update_replay_timeline() -> void:
    super._focus_update_replay_timeline()
    if _focus_replay_track == null:
        return

    var timing_valid := _replay_raw_timing_valid(_v171_replay_remaining, _v171_replay_duration)
    var progress := _focus_replay_progress(_v171_replay_remaining, _v171_replay_duration)
    var track_width := maxf(0.0, _focus_replay_track.size.x)
    var chapters := _production_replay_chapters(track_width)

    if _replay_finish_zone != null:
        var finish_geometry := _replay_finish_zone_geometry(track_width)
        _replay_finish_zone.position = Vector2(finish_geometry.x, 0.0)
        _replay_finish_zone.size = Vector2(finish_geometry.y, REPLAY_TIMELINE_TRACK_HEIGHT)

    _replay_place_chapter_label(_replay_roll_label, chapters["roll"], "ROLL")
    _replay_place_chapter_label(_replay_blend_label, chapters["blend"], "BLEND")
    _replay_place_chapter_label(_replay_cup_label, chapters["cup"], "CUP")
    _production_replay_apply_emphasis(progress, timing_valid)
    _production_replay_update_progress(progress, chapters, timing_valid)
    _production_replay_update_handoff_markers(track_width, chapters)

    if _replay_playhead == null:
        return
    _replay_playhead.visible = timing_valid and _replay_track_has_playhead_room(track_width)
    if not _replay_playhead.visible:
        return
    var x := _replay_playhead_x(progress, track_width)
    _replay_playhead.position = Vector2(
        x - REPLAY_PLAYHEAD_SIZE * 0.5,
        (REPLAY_TIMELINE_TRACK_HEIGHT - REPLAY_PLAYHEAD_SIZE) * 0.5
    )

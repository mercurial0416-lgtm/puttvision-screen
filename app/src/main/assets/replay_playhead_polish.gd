extends "res://commercial_read_flow.gd"

# Presentation-only replay timeline polish. This layer never mutates shot state, Android physics,
# GreenTerrain, GreenReadAdvisor, aiming, scoring, or camera timing.
const REPLAY_PLAYHEAD_SIZE := 9.0
const REPLAY_PLAYHEAD_ALPHA := 0.98

var _replay_playhead: ColorRect

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

    _replay_playhead = ColorRect.new()
    _replay_playhead.name = "ReplayTimelinePlayhead"
    _replay_playhead.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _replay_playhead.size = Vector2(REPLAY_PLAYHEAD_SIZE, REPLAY_PLAYHEAD_SIZE)
    _replay_playhead.pivot_offset = _replay_playhead.size * 0.5
    _replay_playhead.rotation_degrees = 45.0
    _replay_playhead.color = Color(0.96, 0.99, 1.0, REPLAY_PLAYHEAD_ALPHA)
    _replay_playhead.z_index = 3
    _focus_replay_track.add_child(_replay_playhead)

func _replay_playhead_x(progress: float, track_width: float) -> float:
    if not is_finite(progress) or not is_finite(track_width) or track_width <= 0.0:
        return 0.0
    return clampf(progress, 0.0, 1.0) * track_width

func _focus_update_replay_timeline() -> void:
    super._focus_update_replay_timeline()
    if _replay_playhead == null or _focus_replay_track == null:
        return
    var progress := _focus_replay_progress(_v171_replay_remaining, _v171_replay_duration)
    var track_width := maxf(0.0, _focus_replay_track.size.x)
    var x := _replay_playhead_x(progress, track_width)
    _replay_playhead.position = Vector2(
        x - REPLAY_PLAYHEAD_SIZE * 0.5,
        (REPLAY_TIMELINE_TRACK_HEIGHT - REPLAY_PLAYHEAD_SIZE) * 0.5
    )

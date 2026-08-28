extends "res://commercial_read_flow.gd"

# Presentation-only replay legibility upgrade. This does not alter shot physics, terrain,
# GreenReadAdvisor output, camera timing, scoring, or bridge data.
const REPLAY_PLAYHEAD_WIDTH := 6.0
const REPLAY_PLAYHEAD_HEIGHT := 11.0
const REPLAY_TIMELINE_TOP_Z := 300
const REPLAY_TRAIL_COLOR := Color(0.78, 0.91, 0.98, 0.96)
const REPLAY_CUP_COLOR := Color(0.96, 0.79, 0.38, 0.98)

var _replay_playhead: ColorRect

func _replay_chapter_color(progress: float) -> Color:
    return REPLAY_CUP_COLOR if clampf(progress, 0.0, 1.0) >= REPLAY_CUP_CHAPTER_START else REPLAY_TRAIL_COLOR

func _replay_playhead_x(progress: float, track_width: float) -> float:
    return maxf(0.0, maxf(0.0, track_width) * clampf(progress, 0.0, 1.0) - REPLAY_PLAYHEAD_WIDTH * 0.5)

func _replay_session_alpha(phase: String) -> float:
    return 0.0 if phase == PHASE_REPLAY else 1.0

func _focus_build_replay_timeline() -> void:
    super._focus_build_replay_timeline()
    if _focus_replay_timeline != null:
        _focus_replay_timeline.z_index = REPLAY_TIMELINE_TOP_Z
    if _focus_replay_track == null:
        return
    _replay_playhead = ColorRect.new()
    _replay_playhead.name = "ReplayCurrentPlayhead"
    _replay_playhead.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _replay_playhead.position = Vector2.ZERO
    _replay_playhead.size = Vector2(REPLAY_PLAYHEAD_WIDTH, REPLAY_PLAYHEAD_HEIGHT)
    _replay_playhead.color = REPLAY_TRAIL_COLOR
    _replay_playhead.z_index = 3
    _focus_replay_track.add_child(_replay_playhead)

func _focus_update_replay_timeline() -> void:
    super._focus_update_replay_timeline()
    if _focus_replay_track == null:
        return
    var progress := _focus_replay_progress(_v171_replay_remaining, _v171_replay_duration)
    var chapter_color := _replay_chapter_color(progress)
    if _focus_replay_fill != null:
        _focus_replay_fill.color = chapter_color
    if _focus_replay_stage_label != null:
        var stage_color := chapter_color
        stage_color.a = 0.94
        _focus_replay_stage_label.add_theme_color_override("font_color", stage_color)
    if _replay_playhead != null:
        _replay_playhead.position = Vector2(_replay_playhead_x(progress, _focus_replay_track.size.x), -4.0)
        _replay_playhead.color = chapter_color

func _focus_apply_phase(phase: String, immediate: bool = false, delta: float = 0.0) -> void:
    super._focus_apply_phase(phase, immediate, delta)
    _focus_set_alpha(_v179_panel, _replay_session_alpha(phase), immediate, delta)

extends "res://v197_shot_map_make_window.gd"

# Presentation-only focus choreography for commercial TV readability.
# Authoritative Android physics, GreenTerrain and GreenReadAdvisor remain untouched.
# Secondary HUD packages fade by shot phase so attention follows the ball/replay/result
# instead of giving every panel equal visual weight at all times.

const FOCUS_FADE_SPEED := 5.4
const FOCUS_MAX_FADE_DELTA_S := 0.10
const PHASE_READY := "READY"
const PHASE_ROLL := "ROLL"
const PHASE_REPLAY := "REPLAY"
const PHASE_RESULT := "RESULT"
const REPLAY_BAR_HEIGHT := 46.0
const REPLAY_TIMELINE_SIDE_INSET := 28.0
const REPLAY_TIMELINE_LABEL_WIDTH := 122.0
const REPLAY_TIMELINE_STAGE_WIDTH := 154.0
const REPLAY_TIMELINE_TRACK_HEIGHT := 3.0
# Matches the existing cinematic cup-focus handoff without changing camera or shot logic.
const REPLAY_CUP_CHAPTER_START := 0.72
const REPLAY_CUP_CHAPTER_FULL := 0.90

var _focus_phase := PHASE_READY
var _focus_running := false
var _focus_target_card: CanvasItem
var _focus_telemetry_card: CanvasItem
var _focus_break_card: CanvasItem
var _focus_state_card: CanvasItem
var _focus_letterbox_top: ColorRect
var _focus_letterbox_bottom: ColorRect
var _focus_replay_timeline: Control
var _focus_replay_track: ColorRect
var _focus_replay_blend_range: ColorRect
var _focus_replay_fill: ColorRect
var _focus_replay_label: Label
var _focus_replay_stage_label: Label
var _focus_replay_chapter_marker: ColorRect
var _focus_replay_chapter_end_marker: ColorRect

func _build_hud() -> void:
    super._build_hud()
    _focus_target_card = distance_label.get_parent() as CanvasItem if distance_label != null else null
    _focus_telemetry_card = speed_label.get_parent() as CanvasItem if speed_label != null else null
    _focus_break_card = _v174_break_value.get_parent() as CanvasItem if _v174_break_value != null else null
    _focus_state_card = _v174_state_label.get_parent() as CanvasItem if _v174_state_label != null else null
    _focus_build_letterbox()
    _focus_build_replay_timeline()
    _focus_apply_phase(PHASE_READY, true)

func _focus_build_letterbox() -> void:
    _focus_letterbox_top = ColorRect.new()
    _focus_letterbox_top.name = "ReplayLetterboxTop"
    _focus_letterbox_top.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _focus_letterbox_top.set_anchors_preset(Control.PRESET_TOP_WIDE)
    _focus_letterbox_top.offset_bottom = REPLAY_BAR_HEIGHT
    _focus_letterbox_top.color = Color(0.012, 0.018, 0.024, 1.0)
    _focus_letterbox_top.z_index = 240
    add_child(_focus_letterbox_top)

    _focus_letterbox_bottom = ColorRect.new()
    _focus_letterbox_bottom.name = "ReplayLetterboxBottom"
    _focus_letterbox_bottom.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _focus_letterbox_bottom.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
    _focus_letterbox_bottom.offset_top = -REPLAY_BAR_HEIGHT
    _focus_letterbox_bottom.color = Color(0.012, 0.018, 0.024, 1.0)
    _focus_letterbox_bottom.z_index = 240
    add_child(_focus_letterbox_bottom)

func _focus_build_replay_timeline() -> void:
    _focus_replay_timeline = Control.new()
    _focus_replay_timeline.name = "ReplayTimeline"
    _focus_replay_timeline.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _focus_replay_timeline.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
    _focus_replay_timeline.offset_top = -REPLAY_BAR_HEIGHT
    _focus_replay_timeline.z_index = 242
    add_child(_focus_replay_timeline)

    _focus_replay_label = Label.new()
    _focus_replay_label.name = "ReplayTimelineLabel"
    _focus_replay_label.position = Vector2(REPLAY_TIMELINE_SIDE_INSET, 12.0)
    _focus_replay_label.size = Vector2(REPLAY_TIMELINE_LABEL_WIDTH, 24.0)
    _focus_replay_label.text = "SHOT REPLAY"
    _focus_replay_label.add_theme_font_size_override("font_size", 14)
    _focus_replay_label.add_theme_color_override("font_color", Color(0.84, 0.90, 0.94, 0.96))
    _focus_replay_timeline.add_child(_focus_replay_label)

    _focus_replay_stage_label = Label.new()
    _focus_replay_stage_label.name = "ReplayCameraStage"
    _focus_replay_stage_label.anchor_left = 1.0
    _focus_replay_stage_label.anchor_right = 1.0
    _focus_replay_stage_label.offset_left = -REPLAY_TIMELINE_SIDE_INSET - REPLAY_TIMELINE_STAGE_WIDTH
    _focus_replay_stage_label.offset_right = -REPLAY_TIMELINE_SIDE_INSET
    _focus_replay_stage_label.offset_top = 12.0
    _focus_replay_stage_label.offset_bottom = 36.0
    _focus_replay_stage_label.text = "TRAIL CAM · 0.0s"
    _focus_replay_stage_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
    _focus_replay_stage_label.add_theme_font_size_override("font_size", 13)
    _focus_replay_stage_label.add_theme_color_override("font_color", Color(0.72, 0.84, 0.88, 0.94))
    _focus_replay_timeline.add_child(_focus_replay_stage_label)

    _focus_replay_track = ColorRect.new()
    _focus_replay_track.name = "ReplayTimelineTrack"
    _focus_replay_track.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _focus_replay_track.anchor_right = 1.0
    _focus_replay_track.offset_left = REPLAY_TIMELINE_SIDE_INSET + REPLAY_TIMELINE_LABEL_WIDTH
    _focus_replay_track.offset_right = -REPLAY_TIMELINE_SIDE_INSET - REPLAY_TIMELINE_STAGE_WIDTH - 14.0
    _focus_replay_track.offset_top = 22.0
    _focus_replay_track.offset_bottom = 22.0 + REPLAY_TIMELINE_TRACK_HEIGHT
    _focus_replay_track.color = Color(0.28, 0.34, 0.38, 0.42)
    _focus_replay_timeline.add_child(_focus_replay_track)

    _focus_replay_blend_range = ColorRect.new()
    _focus_replay_blend_range.name = "ReplayCameraBlendRange"
    _focus_replay_blend_range.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _focus_replay_blend_range.position = Vector2.ZERO
    _focus_replay_blend_range.size = Vector2.ZERO
    _focus_replay_blend_range.color = Color(0.90, 0.78, 0.40, 0.26)
    _focus_replay_track.add_child(_focus_replay_blend_range)

    _focus_replay_fill = ColorRect.new()
    _focus_replay_fill.name = "ReplayTimelineFill"
    _focus_replay_fill.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _focus_replay_fill.position = Vector2.ZERO
    _focus_replay_fill.size = Vector2(0.0, REPLAY_TIMELINE_TRACK_HEIGHT)
    _focus_replay_fill.color = Color(0.88, 0.95, 0.98, 0.94)
    _focus_replay_track.add_child(_focus_replay_fill)

    _focus_replay_chapter_marker = ColorRect.new()
    _focus_replay_chapter_marker.name = "ReplayCupCameraMarker"
    _focus_replay_chapter_marker.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _focus_replay_chapter_marker.position = Vector2.ZERO
    _focus_replay_chapter_marker.size = Vector2(2.0, 11.0)
    _focus_replay_chapter_marker.color = Color(0.90, 0.78, 0.40, 0.90)
    _focus_replay_track.add_child(_focus_replay_chapter_marker)

    _focus_replay_chapter_end_marker = ColorRect.new()
    _focus_replay_chapter_end_marker.name = "ReplayCupCameraFullMarker"
    _focus_replay_chapter_end_marker.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _focus_replay_chapter_end_marker.position = Vector2.ZERO
    _focus_replay_chapter_end_marker.size = Vector2(2.0, 11.0)
    _focus_replay_chapter_end_marker.color = Color(0.90, 0.78, 0.40, 0.62)
    _focus_replay_track.add_child(_focus_replay_chapter_end_marker)

func _focus_phase_for(running: bool, replaying: bool, showing_result: bool) -> String:
    if running:
        return PHASE_ROLL
    if replaying:
        return PHASE_REPLAY
    if showing_result:
        return PHASE_RESULT
    return PHASE_READY

func _focus_role_alpha(phase: String, role: String) -> float:
    match phase:
        PHASE_ROLL:
            match role:
                "target": return 0.72
                "telemetry": return 1.0
                "break": return 0.16
                "state": return 1.0
                "read": return 0.0
                "result": return 0.0
                "practice": return 0.42
                "letterbox": return 0.10
                "replay_timeline": return 0.0
        PHASE_REPLAY:
            match role:
                "target": return 0.40
                "telemetry": return 0.28
                "break": return 0.10
                "state": return 1.0
                "read": return 0.0
                "result": return 0.0
                "practice": return 0.0
                "letterbox": return 0.68
                "replay_timeline": return 1.0
        PHASE_RESULT:
            match role:
                "target": return 0.52
                "telemetry": return 0.40
                "break": return 0.30
                "state": return 0.78
                "read": return 0.0
                "result": return 1.0
                "practice": return 1.0
                "letterbox": return 0.0
                "replay_timeline": return 0.0
        _:
            match role:
                "target": return 1.0
                "telemetry": return 0.92
                "break": return 1.0
                "state": return 1.0
                "read": return 1.0
                "result": return 1.0
                "practice": return 1.0
                "letterbox": return 0.0
                "replay_timeline": return 0.0
    return 1.0

func _focus_replay_progress(remaining: float, duration: float) -> float:
    if not is_finite(remaining) or not is_finite(duration) or duration <= 0.001:
        return 0.0
    return clamp(1.0 - max(0.0, remaining) / duration, 0.0, 1.0)

func _focus_replay_stage(progress: float) -> String:
    var p := clampf(progress, 0.0, 1.0)
    if p < REPLAY_CUP_CHAPTER_START:
        return "TRAIL CAM"
    if p < REPLAY_CUP_CHAPTER_FULL:
        return "CAM BLEND"
    return "CUP CAM"

func _focus_replay_status(progress: float, remaining: float) -> String:
    var stage := _focus_replay_stage(progress)
    if not is_finite(remaining):
        return stage
    return "%s · %.1fs" % [stage, maxf(0.0, remaining)]

func _focus_update_replay_timeline() -> void:
    if _focus_replay_track == null or _focus_replay_fill == null:
        return
    var progress := _focus_replay_progress(_v171_replay_remaining, _v171_replay_duration)
    var track_width := maxf(0.0, _focus_replay_track.size.x)
    var blend_left := track_width * REPLAY_CUP_CHAPTER_START
    var blend_right := track_width * REPLAY_CUP_CHAPTER_FULL
    if _focus_replay_blend_range != null:
        _focus_replay_blend_range.position = Vector2(blend_left, 0.0)
        _focus_replay_blend_range.size = Vector2(maxf(0.0, blend_right - blend_left), REPLAY_TIMELINE_TRACK_HEIGHT)
    _focus_replay_fill.size = Vector2(track_width * progress, REPLAY_TIMELINE_TRACK_HEIGHT)
    if _focus_replay_chapter_marker != null:
        _focus_replay_chapter_marker.position = Vector2(maxf(0.0, blend_left - 1.0), -4.0)
    if _focus_replay_chapter_end_marker != null:
        _focus_replay_chapter_end_marker.position = Vector2(maxf(0.0, blend_right - 1.0), -4.0)
    if _focus_replay_stage_label != null:
        _focus_replay_stage_label.text = _focus_replay_status(progress, _v171_replay_remaining)

func _focus_safe_fade_delta(delta: float) -> float:
    # Focus fades are presentation state, so a suspended/resumed Forward Mobile frame or debugger
    # hitch must not instantly slam every HUD package to its target opacity. Freeze invalid/backward
    # time and cap a single visual step to 100 ms, matching the replay camera's existing hitch guard.
    if not is_finite(delta) or delta <= 0.0:
        return 0.0
    return minf(delta, FOCUS_MAX_FADE_DELTA_S)

func _focus_set_alpha(item: CanvasItem, target: float, immediate: bool, delta: float = 0.0) -> void:
    if item == null:
        return
    var c := item.modulate
    var safe_delta := _focus_safe_fade_delta(delta)
    c.a = target if immediate else move_toward(c.a, target, FOCUS_FADE_SPEED * safe_delta)
    item.modulate = c

func _focus_apply_phase(phase: String, immediate: bool = false, delta: float = 0.0) -> void:
    _focus_phase = phase
    _focus_set_alpha(_focus_target_card, _focus_role_alpha(phase, "target"), immediate, delta)
    _focus_set_alpha(_focus_telemetry_card, _focus_role_alpha(phase, "telemetry"), immediate, delta)
    _focus_set_alpha(_focus_break_card, _focus_role_alpha(phase, "break"), immediate, delta)
    _focus_set_alpha(_focus_state_card, _focus_role_alpha(phase, "state"), immediate, delta)
    _focus_set_alpha(_v176_panel, _focus_role_alpha(phase, "read"), immediate, delta)
    _focus_set_alpha(_v183_panel, _focus_role_alpha(phase, "read"), immediate, delta)
    _focus_set_alpha(_v177_panel, _focus_role_alpha(phase, "result"), immediate, delta)
    _focus_set_alpha(_v188_panel, _focus_role_alpha(phase, "result"), immediate, delta)
    _focus_set_alpha(_v179_panel, _focus_role_alpha(phase, "practice"), immediate, delta)
    _focus_set_alpha(_v191_bar, _focus_role_alpha(phase, "practice"), immediate, delta)
    _focus_set_alpha(_focus_letterbox_top, _focus_role_alpha(phase, "letterbox"), immediate, delta)
    _focus_set_alpha(_focus_letterbox_bottom, _focus_role_alpha(phase, "letterbox"), immediate, delta)
    _focus_set_alpha(_focus_replay_timeline, _focus_role_alpha(phase, "replay_timeline"), immediate, delta)

func _focus_current_phase() -> String:
    var replaying := _v171_replay_remaining > 0.0
    var showing_result := _v177_panel != null and _v177_panel.visible
    return _focus_phase_for(_focus_running, replaying, showing_result)

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _focus_running = bool(s.get("running", false))

func _process(delta: float) -> void:
    super._process(delta)
    _focus_update_replay_timeline()
    _focus_apply_phase(_focus_current_phase(), false, delta)

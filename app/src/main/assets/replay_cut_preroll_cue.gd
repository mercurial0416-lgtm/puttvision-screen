extends Node

# Presentation-only camera handoff anticipation for the replay timeline. The inherited cinematic
# camera thresholds remain authoritative; this helper only makes the NEXT cut legible at TV distance.
# No shot state, camera timing, physics, terrain, aim, scoring, or replay sampling is modified.

const TRACK_NAME := "ReplayTimelineTrack"
const BLEND_MARKER_NAME := "ReplayCupCameraMarker"
const CUP_MARKER_NAME := "ReplayCupCameraFullMarker"
const CUE_NAME := "ReplayNextCutCue"
const LABEL_NAME := "ReplayNextCutLabel"
const CHAPTER_BLEND := 0.72
const CHAPTER_CUP := 0.90
const PREROLL_FRACTION := 0.105
const CUE_WIDTH_PX := 24.0
const CUE_HEIGHT_PX := 15.0
const LABEL_WIDTH_PX := 58.0

var _root: Node
var _track: Control
var _blend_marker: Control
var _cup_marker: Control
var _cue: ColorRect
var _label: Label

func _ready() -> void:
    process_priority = 146
    call_deferred("_bind_preroll")

func _finite_number(value: Variant) -> float:
    if value is int or value is float:
        var number := float(value)
        return number if is_finite(number) else NAN
    return NAN

func _bind_preroll() -> void:
    _root = get_tree().current_scene
    if _root == null:
        set_process(false)
        return
    _track = _root.find_child(TRACK_NAME, true, false) as Control
    _blend_marker = _root.find_child(BLEND_MARKER_NAME, true, false) as Control
    _cup_marker = _root.find_child(CUP_MARKER_NAME, true, false) as Control
    if _track == null or _blend_marker == null or _cup_marker == null:
        set_process(false)
        return

    _cue = ColorRect.new()
    _cue.name = CUE_NAME
    _cue.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _cue.size = Vector2(CUE_WIDTH_PX, CUE_HEIGHT_PX)
    _cue.color = Color(0.98, 0.82, 0.40, 0.0)
    _cue.z_index = 3
    _track.add_child(_cue)

    _label = Label.new()
    _label.name = LABEL_NAME
    _label.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _label.size = Vector2(LABEL_WIDTH_PX, 14.0)
    _label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    _label.add_theme_font_size_override("font_size", 9)
    _label.add_theme_color_override("font_color", Color(1.0, 0.90, 0.58, 0.0))
    _label.z_index = 5
    _track.add_child(_label)

func _preroll_state(progress: float) -> Dictionary:
    if not is_finite(progress):
        return {"visible": false}
    var p := clampf(progress, 0.0, 1.0)
    var boundary := -1.0
    var text := ""
    if p < CHAPTER_BLEND:
        boundary = CHAPTER_BLEND
        text = "NEXT · BLEND"
    elif p < CHAPTER_CUP:
        boundary = CHAPTER_CUP
        text = "NEXT · CUP"
    else:
        return {"visible": false}
    var distance := boundary - p
    if distance > PREROLL_FRACTION:
        return {"visible": false}
    var intensity := clampf(1.0 - distance / PREROLL_FRACTION, 0.0, 1.0)
    return {
        "visible": true,
        "boundary": boundary,
        "text": text,
        "intensity": intensity
    }

func _hide_preroll() -> void:
    if _cue != null:
        _cue.visible = false
    if _label != null:
        _label.visible = false

func _process(_delta: float) -> void:
    if _root == null or _track == null or _cue == null or _label == null:
        return
    var remaining := _finite_number(_root.get("_v171_replay_remaining"))
    var duration := _finite_number(_root.get("_v171_replay_duration"))
    if not is_finite(remaining) or not is_finite(duration) or remaining <= 0.0 or duration <= 0.05:
        _hide_preroll()
        return
    var progress := clampf(1.0 - remaining / duration, 0.0, 1.0)
    var state := _preroll_state(progress)
    if not bool(state.get("visible", false)):
        _hide_preroll()
        return

    var boundary := float(state.get("boundary", 0.0))
    var intensity := float(state.get("intensity", 0.0))
    var x := _track.size.x * boundary
    _cue.visible = true
    _cue.position = Vector2(x - CUE_WIDTH_PX * 0.5, -6.0)
    _cue.color = Color(0.98, 0.82, 0.40, lerpf(0.055, 0.20, intensity))

    _label.visible = _track.size.x >= 180.0
    _label.text = str(state.get("text", ""))
    _label.position = Vector2(clampf(x - LABEL_WIDTH_PX * 0.5, 0.0, maxf(0.0, _track.size.x - LABEL_WIDTH_PX)), -21.0)
    _label.add_theme_color_override("font_color", Color(1.0, 0.90, 0.58, lerpf(0.52, 0.96, intensity)))

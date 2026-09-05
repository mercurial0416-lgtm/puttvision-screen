extends Node

# Presentation layout/wording guard for replay roll distance. Production values are owned by
# replay_timeline_camera_truth.gd; this helper only keeps the longer status inside its TV column and
# spells the remaining roll as TO STOP so it cannot be confused with the ball's final resting offset.
# The preview values are layout-only; runtime distance and replay time always come from existing
# presentation telemetry and never feed the replay clock, camera choreography, trail, or shot physics.

const STATUS_WIDTH := 248.0
const SIDE_INSET := 28.0
const TRACK_GAP := 14.0
const PREVIEW_SAMPLE_DISTANCE := "0.9m TO STOP"
const PREVIEW_SAMPLE_TIME := "1.2s"
const LEGACY_REMAINING_SUFFIX := " REST"
const CLEAR_REMAINING_SUFFIX := " TO STOP"
const STATUS_SEPARATOR := " · "
const STATUS_FONT_SIZE := 17
const STATUS_OUTLINE_SIZE := 2
const STATUS_TEXT_COLOR := Color(0.94, 0.98, 0.96, 0.98)
const STATUS_OUTLINE_COLOR := Color(0.02, 0.05, 0.05, 0.92)

var _layout_done := false
var _cached_stage: Label = null
var _cached_track: Control = null
var _cached_preview_stage: Label = null
var _last_source_text := ""
var _last_presented_text := ""

func _ready() -> void:
    process_priority = 120

func _process(_delta: float) -> void:
    var root := get_parent()
    if root == null:
        return

    # Resolve scene references once and only rebind after a node is replaced/freed. This helper runs
    # every frame during replay, so recursive tree walks here used to be needless TV/mobile frame cost.
    if not is_instance_valid(_cached_stage) or not is_instance_valid(_cached_track):
        _bind_nodes(root)

    var stage := _cached_stage
    var track := _cached_track
    if stage == null or track == null:
        return

    _present_stage_text(stage, root)

    if _layout_done:
        return

    if stage.anchor_left >= 0.99:
        stage.offset_left = -SIDE_INSET - STATUS_WIDTH
        stage.offset_right = -SIDE_INSET
    else:
        stage.position.x = 1920.0 - SIDE_INSET - STATUS_WIDTH
        stage.size.x = STATUS_WIDTH

    if track.anchor_right >= 0.99:
        track.offset_right = -SIDE_INSET - STATUS_WIDTH - TRACK_GAP
    else:
        var track_left := track.position.x
        track.size.x = maxf(1.0, 1920.0 - track_left - SIDE_INSET - STATUS_WIDTH - TRACK_GAP)

    _apply_stage_hierarchy(stage)
    _layout_done = true

func _bind_nodes(root: Node) -> void:
    _cached_stage = null
    _cached_track = null
    _cached_preview_stage = root.find_child("PreviewReplayCameraStage", true, false) as Label

    if root.has_method("get"):
        _cached_stage = root.get("_focus_replay_stage_label") as Label
        _cached_track = root.get("_focus_replay_track") as Control

    if _cached_stage == null and _cached_preview_stage != null:
        _cached_stage = _cached_preview_stage
    if _cached_track == null:
        _cached_track = root.find_child("PreviewReplayTimelineTrack", true, false) as Control

    _layout_done = false
    _last_source_text = ""
    _last_presented_text = ""

func _apply_stage_hierarchy(stage: Label) -> void:
    # Broadcast-style readout: distance remains the dominant terminal cue, aligned to the edge of the
    # timeline so eyes travel naturally from replay progress into remaining roll. Styling is applied
    # only on bind/layout, never per frame, and never touches replay timing, trails, camera or physics.
    stage.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
    stage.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    stage.clip_text = true
    stage.add_theme_font_size_override("font_size", STATUS_FONT_SIZE)
    stage.add_theme_constant_override("outline_size", STATUS_OUTLINE_SIZE)
    stage.add_theme_color_override("font_color", STATUS_TEXT_COLOR)
    stage.add_theme_color_override("font_outline_color", STATUS_OUTLINE_COLOR)

func _replay_clock_readout(root: Node) -> String:
    # Read the already-running presentation clock without mutating it. Reject malformed values instead
    # of formatting them into a plausible countdown; the HUD should go quiet rather than lie.
    var remaining_value: Variant = root.get("_v171_replay_remaining")
    var value_type := typeof(remaining_value)
    if value_type != TYPE_INT and value_type != TYPE_FLOAT:
        return ""
    var remaining := float(remaining_value)
    if not is_finite(remaining) or remaining <= 0.0:
        return ""
    return "%.1fs" % remaining

func _inject_replay_clock(source_text: String, clock_text: String) -> String:
    if clock_text.is_empty():
        return source_text
    # Keep the measured roll distance at the terminal edge where it is easiest to scan. Insert the
    # replay countdown immediately before that distance, or append it when no distance is available.
    var distance_separator := source_text.rfind(STATUS_SEPARATOR)
    if distance_separator < 0:
        return "%s%s%s" % [source_text, STATUS_SEPARATOR, clock_text]
    return "%s%s%s%s" % [
        source_text.substr(0, distance_separator),
        STATUS_SEPARATOR,
        clock_text,
        source_text.substr(distance_separator)
    ]

func _present_stage_text(stage: Label, root: Node) -> void:
    var source_text := stage.text
    # If our own previous presentation text is still on screen, nothing upstream changed and there is
    # no reason to allocate/replace strings again. The timeline remains free to publish a new value.
    if source_text == _last_presented_text:
        return

    var presented_text := source_text
    var previewing := _cached_preview_stage != null and stage == _cached_preview_stage
    if previewing and not presented_text.contains(CLEAR_REMAINING_SUFFIX):
        presented_text = "%s%s%s" % [presented_text, STATUS_SEPARATOR, PREVIEW_SAMPLE_DISTANCE]

    # The underlying timeline owns the measured value and may refresh every frame. Rewrite only the
    # presentation suffix after that update; no replay clock, trail point, camera or physics data changes.
    if presented_text.contains(LEGACY_REMAINING_SUFFIX):
        presented_text = presented_text.replace(LEGACY_REMAINING_SUFFIX, CLEAR_REMAINING_SUFFIX)

    var clock_text := PREVIEW_SAMPLE_TIME if previewing else _replay_clock_readout(root)
    presented_text = _inject_replay_clock(presented_text, clock_text)

    _last_source_text = source_text
    _last_presented_text = presented_text
    if presented_text != source_text:
        stage.text = presented_text

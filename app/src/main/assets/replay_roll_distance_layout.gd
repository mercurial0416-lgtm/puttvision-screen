extends Node

# Presentation layout/wording guard for replay roll distance. Production values are owned by
# replay_timeline_camera_truth.gd; this helper only keeps the longer status inside its TV column and
# spells the remaining roll as TO STOP so it cannot be confused with the ball's final resting offset.
# The preview number is layout-only; runtime distance always comes from the recorded actual trail.

const STATUS_WIDTH := 248.0
const SIDE_INSET := 28.0
const TRACK_GAP := 14.0
const PREVIEW_SAMPLE_DISTANCE := "0.9m TO STOP"
const LEGACY_REMAINING_SUFFIX := " REST"
const CLEAR_REMAINING_SUFFIX := " TO STOP"

var _layout_done := false

func _ready() -> void:
    process_priority = 120

func _process(_delta: float) -> void:
    var root := get_parent()
    if root == null:
        return

    var stage: Label = null
    var track: Control = null
    if root.has_method("get"):
        stage = root.get("_focus_replay_stage_label") as Label
        track = root.get("_focus_replay_track") as Control

    var preview_stage := root.find_child("PreviewReplayCameraStage", true, false) as Label
    if stage == null and preview_stage != null:
        stage = preview_stage
    if track == null:
        track = root.find_child("PreviewReplayTimelineTrack", true, false) as Control

    if stage == null or track == null:
        return

    if preview_stage != null and stage == preview_stage and not stage.text.contains("TO STOP"):
        stage.text = "%s · %s" % [stage.text, PREVIEW_SAMPLE_DISTANCE]

    # The underlying timeline owns the measured value and may refresh every frame. Rewrite only the
    # presentation suffix after that update; no replay clock, trail point, camera or physics data changes.
    if stage.text.contains(LEGACY_REMAINING_SUFFIX):
        stage.text = stage.text.replace(LEGACY_REMAINING_SUFFIX, CLEAR_REMAINING_SUFFIX)

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

    _layout_done = true

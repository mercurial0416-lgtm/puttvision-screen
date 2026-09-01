extends Node

# One-shot presentation layout guard for the longer replay status. Production text is owned by
# replay_timeline_camera_truth.gd; the preview fixture gets an equivalent sample suffix so CI can
# visually prove the commercial layout without fabricating any runtime shot/physics state.
# The preview number is layout-only; runtime distance always comes from the recorded actual trail.

const STATUS_WIDTH := 248.0
const SIDE_INSET := 28.0
const TRACK_GAP := 14.0
const PREVIEW_SAMPLE_REST := "0.9m REST"

var _done := false

func _ready() -> void:
    process_priority = 120

func _process(_delta: float) -> void:
    if _done:
        return
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

    if preview_stage != null and stage == preview_stage and not stage.text.contains("REST"):
        stage.text = "%s · %s" % [stage.text, PREVIEW_SAMPLE_REST]

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

    _done = true
    set_process(false)

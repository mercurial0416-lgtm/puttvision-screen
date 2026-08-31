extends "res://green_read_direction_truth.gd"

# Presentation-only replay timeline truth layer.
# The camera choreography owns the cup-focus thresholds; the timeline reads those same inherited
# values so its TRAIL / BLEND / CUP chapters cannot silently drift from the actual replay camera.
# Android V135-V137, GreenTerrain and GreenReadAdvisor remain authoritative and untouched.

func _focus_replay_stage(progress: float) -> String:
    var p := clampf(progress, 0.0, 1.0)
    if p < V180_FOCUS_START:
        return "TRAIL CAM"
    if p < V180_FOCUS_FULL:
        return "CAM BLEND"
    return "CUP CAM"

func _focus_update_replay_timeline() -> void:
    if _focus_replay_track == null or _focus_replay_fill == null:
        return
    var progress := _focus_replay_progress(_v171_replay_remaining, _v171_replay_duration)
    var track_width := maxf(0.0, _focus_replay_track.size.x)
    var blend_left := track_width * V180_FOCUS_START
    var blend_right := track_width * V180_FOCUS_FULL
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

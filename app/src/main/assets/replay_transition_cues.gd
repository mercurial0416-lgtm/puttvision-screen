extends "res://live_pace_surge.gd"

# Presentation-only replay cueing. Camera timing and authoritative shot/terrain physics are untouched;
# this layer only tells the viewer how long remains until the next cinematic camera handoff.
const REPLAY_TRANSITION_LABEL_MIN_DURATION := 0.05

func _replay_transition_status(progress: float, remaining: float, duration: float) -> String:
    var p := clampf(progress, 0.0, 1.0) if is_finite(progress) else 0.0
    if not is_finite(remaining) or not is_finite(duration) or duration <= REPLAY_TRANSITION_LABEL_MIN_DURATION:
        return _focus_replay_stage(p)
    var safe_remaining := maxf(0.0, remaining)
    if p < REPLAY_CUP_CHAPTER_START:
        var eta_blend := maxf(0.0, duration * (REPLAY_CUP_CHAPTER_START - p))
        return "TRAIL · →BLEND %.1fs" % eta_blend
    if p < REPLAY_CUP_CHAPTER_FULL:
        var eta_cup := maxf(0.0, duration * (REPLAY_CUP_CHAPTER_FULL - p))
        return "BLEND · →CUP %.1fs" % eta_cup
    return "CUP · %.1fs" % safe_remaining

func _focus_update_replay_timeline() -> void:
    super._focus_update_replay_timeline()
    if _focus_replay_stage_label == null:
        return
    var progress := _focus_replay_progress(_v171_replay_remaining, _v171_replay_duration)
    _focus_replay_stage_label.text = _replay_transition_status(progress, _v171_replay_remaining, _v171_replay_duration)

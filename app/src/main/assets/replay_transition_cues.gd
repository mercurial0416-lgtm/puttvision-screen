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

func _replay_transition_is_active(remaining: float, actual_sample_count: int) -> bool:
    return is_finite(remaining) and remaining > 0.0 and actual_sample_count >= 2

func _replay_focus_is_active(remaining: float, duration: float, actual_sample_count: int) -> bool:
    # The cinematic phase must represent a replay that can actually be rendered. A corrupt/infinite
    # clock, missing duration, or empty trail previously left the commercial letterbox/timeline at
    # full opacity even though no replay camera could advance. Keep that telemetry failure local to
    # presentation state instead of letting it masquerade as active playback.
    return (
        is_finite(remaining)
        and remaining > 0.0
        and is_finite(duration)
        and duration > REPLAY_TRANSITION_LABEL_MIN_DURATION
        and actual_sample_count >= 2
    )

func _focus_current_phase() -> String:
    var replaying := _replay_focus_is_active(
        _v171_replay_remaining,
        _v171_replay_duration,
        _v171_replay_actual.size()
    )
    var showing_result := _v177_panel != null and _v177_panel.visible
    return _focus_phase_for(_focus_running, replaying, showing_result)

func _focus_update_replay_timeline() -> void:
    super._focus_update_replay_timeline()
    if _focus_replay_stage_label == null:
        return
    # The parent timeline owns the idle/completed label. Do not replace it with a stale camera cue
    # after the replay clock reaches zero or when there is no valid trail to replay; otherwise the
    # HUD can sit on "CUP · 0.0s" between shots. This is presentation-only and leaves replay timing,
    # camera choreography, physics, GreenTerrain and GreenReadAdvisor untouched.
    if not _replay_transition_is_active(_v171_replay_remaining, _v171_replay_actual.size()):
        return
    var progress := _focus_replay_progress(_v171_replay_remaining, _v171_replay_duration)
    _focus_replay_stage_label.text = _replay_transition_status(progress, _v171_replay_remaining, _v171_replay_duration)

extends "res://terrain_relief_visibility.gd"

# Presentation-only live-roll pace semantics. Authoritative ball velocity still comes from the
# existing Android/bridge physics; this layer only stops the HUD from hiding downhill acceleration.
# A small hysteresis band keeps the semantic phase stable when sensor/bridge speed samples hover
# around the surge threshold; the numeric pace value still follows every authoritative sample.
const LIVE_PACE_SURGE_ENTER_RATIO := 1.07
const LIVE_PACE_SURGE_EXIT_RATIO := 1.03
const LIVE_PACE_MAX_DISPLAY_RATIO := 1.99

var _live_pace_surging := false

func _live_pace_phase(ratio: float) -> String:
    if _live_pace_surging:
        if ratio < LIVE_PACE_SURGE_EXIT_RATIO:
            _live_pace_surging = false
    elif ratio > LIVE_PACE_SURGE_ENTER_RATIO:
        _live_pace_surging = true

    if _live_pace_surging:
        return "SURGING"
    if ratio < 0.35:
        return "DYING"
    if ratio < 0.72:
        return "SETTLING"
    return "ROLLING"

func _live_pace_readout(current_speed: float, launch_speed: float) -> String:
    if not is_finite(current_speed) or not is_finite(launch_speed) or launch_speed <= 0.001:
        return "PACE --"
    var raw_ratio := maxf(0.0, current_speed / launch_speed)
    var display_ratio := minf(raw_ratio, LIVE_PACE_MAX_DISPLAY_RATIO)
    var pct_text := "%d%%" % int(round(display_ratio * 100.0))
    if raw_ratio > LIVE_PACE_MAX_DISPLAY_RATIO:
        pct_text += "+"
    return "PACE %s · %s" % [pct_text, _live_pace_phase(raw_ratio)]

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    if not bool(s.get("running", false)):
        _live_pace_surging = false

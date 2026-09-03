extends "res://terrain_relief_visibility.gd"

# Presentation-only live-roll pace semantics. Authoritative ball velocity still comes from the
# existing Android/bridge physics; this layer only stops the HUD from hiding downhill acceleration.
# A small hysteresis band keeps the semantic phase stable when sensor/bridge speed samples hover
# around the surge threshold; the numeric pace value still follows every authoritative sample.
const LIVE_PACE_SURGE_ENTER_RATIO := 1.07
const LIVE_PACE_SURGE_EXIT_RATIO := 1.03
const LIVE_PACE_MAX_DISPLAY_RATIO := 1.99
const LIVE_PACE_MAX_DISPLAY_SPEED_MPS := 9.99

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

func _live_pace_speed_text(current_speed: float) -> String:
    if not is_finite(current_speed):
        return "-- m/s"
    var speed := clampf(current_speed, 0.0, LIVE_PACE_MAX_DISPLAY_SPEED_MPS)
    var suffix := "+" if current_speed > LIVE_PACE_MAX_DISPLAY_SPEED_MPS else ""
    return "%.2f%s m/s" % [speed, suffix]

func _live_pace_readout(current_speed: float, launch_speed: float) -> String:
    if not is_finite(current_speed) or not is_finite(launch_speed) or launch_speed <= 0.001:
        # Invalid telemetry must also clear the presentation-only hysteresis latch. Otherwise a
        # single malformed bridge sample can leave the HUD stuck in SURGING when the next valid
        # ratio lands inside the neutral 1.03-1.07 hysteresis band.
        _live_pace_surging = false
        return "PACE --"
    var raw_ratio := maxf(0.0, current_speed / launch_speed)
    var display_ratio := minf(raw_ratio, LIVE_PACE_MAX_DISPLAY_RATIO)
    var pct_text := "%d%%" % int(round(display_ratio * 100.0))
    if raw_ratio > LIVE_PACE_MAX_DISPLAY_RATIO:
        pct_text += "+"
    # Keep the ratio for fast at-a-glance decay/surge reading, but also expose the authoritative
    # instantaneous speed. This removes the mental conversion users previously had to do from a
    # launch-relative percentage while preserving the existing compact TV HUD footprint.
    return "PACE %s · %s · %s" % [pct_text, _live_pace_speed_text(current_speed), _live_pace_phase(raw_ratio)]

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    if not bool(s.get("running", false)):
        _live_pace_surging = false

extends "res://terrain_relief_visibility.gd"

# Presentation-only live-roll pace semantics. Authoritative ball velocity still comes from the
# existing Android/bridge physics; this layer only stops the HUD from hiding downhill acceleration.
const LIVE_PACE_SURGE_THRESHOLD := 1.05
const LIVE_PACE_MAX_DISPLAY_RATIO := 1.99

func _live_pace_readout(current_speed: float, launch_speed: float) -> String:
    if not is_finite(current_speed) or not is_finite(launch_speed) or launch_speed <= 0.001:
        return "PACE --"
    var ratio := clampf(current_speed / launch_speed, 0.0, LIVE_PACE_MAX_DISPLAY_RATIO)
    var phase := "ROLLING"
    if ratio > LIVE_PACE_SURGE_THRESHOLD:
        phase = "SURGING"
    elif ratio < 0.35:
        phase = "DYING"
    elif ratio < 0.72:
        phase = "SETTLING"
    return "PACE %d%% · %s" % [int(round(ratio * 100.0)), phase]

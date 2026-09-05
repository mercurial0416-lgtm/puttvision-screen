extends "res://replay_production_overlay_bridge.gd"

# Presentation-only wording layer. The inherited replay timeline owns the recorded-trail distance;
# this layer only makes the on-screen unit semantically explicit. Android physics, GreenTerrain and
# GreenReadAdvisor remain authoritative and untouched.
func _focus_replay_roll_distance(progress: float) -> String:
    var remaining_m := maxf(0.0, _focus_replay_roll_total_m * (1.0 - clampf(progress, 0.0, 1.0)))
    if remaining_m < 1.0:
        return "%dcm TO STOP" % int(round(remaining_m * 100.0))
    return "%.1fm TO STOP" % remaining_m

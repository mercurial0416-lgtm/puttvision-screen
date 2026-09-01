extends "res://replay_timeline_camera_truth.gd"

# Final presentation-only language pass for live roll telemetry. The preview and the rest of the TV
# HUD already use full RIGHT/LEFT words; terminal live-roll states must use the same vocabulary so a
# player never has to reinterpret R/L abbreviations at couch distance. Authoritative Android physics,
# GreenTerrain, GreenReadAdvisor, scoring and shot data remain untouched.

func _live_finish_readout(cross_track_cm: float) -> String:
    if absf(cross_track_cm) < 0.05:
        return "REST CENTER"
    return "REST %s %.1f cm" % ["RIGHT" if cross_track_cm > 0.0 else "LEFT", absf(cross_track_cm)]

func _live_last_observed_readout(cross_track_cm: float) -> String:
    if absf(cross_track_cm) < 0.05:
        return "LAST OBS CENTER"
    return "LAST OBS %s %.1f cm" % ["RIGHT" if cross_track_cm > 0.0 else "LEFT", absf(cross_track_cm)]

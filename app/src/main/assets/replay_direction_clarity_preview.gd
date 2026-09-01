extends "res://session_dispersion_readability_preview.gd"

# Preview-only mirror of replay_direction_clarity.gd so the rendered artifact validates the exact
# user-facing direction wording while retaining the existing seeded commercial HUD scene.

func _expand_live_direction(text: String) -> String:
    if text.begins_with("REST R "):
        return text.replace("REST R ", "REST RIGHT ")
    if text.begins_with("REST L "):
        return text.replace("REST L ", "REST LEFT ")
    if text.begins_with("LAST OBS R "):
        return text.replace("LAST OBS R ", "LAST OBS RIGHT ")
    if text.begins_with("LAST OBS L "):
        return text.replace("LAST OBS L ", "LAST OBS LEFT ")
    if text.begins_with("PEAK R "):
        return text.replace("PEAK R ", "PEAK RIGHT ")
    if text.begins_with("PEAK L "):
        return text.replace("PEAK L ", "PEAK LEFT ")
    if text.begins_with("R "):
        return "RIGHT " + text.substr(2)
    if text.begins_with("L "):
        return "LEFT " + text.substr(2)
    return text

func _live_curve_readout(cross_track_cm: float) -> String:
    return _expand_live_direction(super._live_curve_readout(cross_track_cm))

func _live_peak_readout(peak_signed_cm: float) -> String:
    return _expand_live_direction(super._live_peak_readout(peak_signed_cm))

func _live_finish_readout(cross_track_cm: float) -> String:
    return _expand_live_direction(super._live_finish_readout(cross_track_cm))

func _live_last_observed_readout(cross_track_cm: float) -> String:
    return _expand_live_direction(super._live_last_observed_readout(cross_track_cm))

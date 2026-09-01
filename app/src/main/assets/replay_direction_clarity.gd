extends "res://replay_timeline_camera_truth.gd"

# Presentation-only semantic layer. Preserve every value and state from the established replay/live
# truth chain, expanding only terse R/L direction tokens for TV-distance readability.

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

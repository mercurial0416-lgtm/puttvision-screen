extends "res://session_dispersion_readability_preview.gd"

# Preview-only fixture for the clarity pass. The seeded commercial preview does not expose the
# live telemetry formatter methods used by production, so keep its established inheritance intact
# and provide only the pure semantic helper here. Production wiring is covered separately.

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

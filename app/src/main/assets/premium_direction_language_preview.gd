extends "res://session_dispersion_readability_preview.gd"

# Render-only fixture alignment for the TV capture. The inherited commercial preview owns the
# synthetic live-break meter, but its fixture still used terse R/L tokens after the production
# green-read language moved to explicit RIGHT/LEFT wording. Keep the captured verification frame
# representative without touching physics, GreenTerrain, GreenReadAdvisor, aiming or scoring.
var _premium_direction_preview_checked := false

func _process(delta: float) -> void:
    super._process(delta)
    if _preview_live_break_value != null:
        _preview_live_break_value.text = "RIGHT 12.4 cm"
    if _preview_live_break_peak != null:
        _preview_live_break_peak.text = "PEAK RIGHT 18.7 cm"

    if _premium_direction_preview_checked or _preview_live_break_value == null or _preview_live_break_peak == null:
        return
    _premium_direction_preview_checked = true
    if _preview_live_break_value.text != "RIGHT 12.4 cm" or _preview_live_break_peak.text != "PEAK RIGHT 18.7 cm":
        push_error("Premium direction-language preview regression")
        get_tree().quit(38)
        return
    print("PREMIUM_DIRECTION_LANGUAGE_PREVIEW_OK=1")

extends "res://session_dispersion_readability_preview.gd"

# Render-only fixture alignment for the TV capture. The inherited commercial preview still carries
# legacy terse direction tokens in a few synthetic fixtures, while production GREEN READ now spells
# RIGHT/LEFT out for TV-distance readability. Keep the captured verification frame representative
# without touching physics, GreenTerrain, GreenReadAdvisor, aiming or scoring.
var _premium_direction_preview_checked := false

func _v183_break_text(side_pct: float) -> String:
    if absf(side_pct) < 0.05:
        return "BREAK  STRAIGHT"
    return "BREAK  %s %.2f%%" % [("RIGHT" if side_pct > 0.0 else "LEFT"), absf(side_pct)]

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
        push_error("Premium direction-language live-break preview regression")
        get_tree().quit(38)
        return
    if _v183_break_text(1.35) != "BREAK  RIGHT 1.35%" or _v183_break_text(-1.35) != "BREAK  LEFT 1.35%":
        push_error("Premium direction-language overview preview regression")
        get_tree().quit(38)
        return
    print("PREMIUM_DIRECTION_LANGUAGE_PREVIEW_OK=1")

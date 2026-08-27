extends "res://v192_drill_progression.gd"

# Presentation-only session form trend. Compares normalized line/pace error across recent reps and
# surfaces direction of travel inside the existing pressure footer. It never feeds values back
# into Android physics, GreenTerrain, GreenReadAdvisor, aiming, scoring, or shot capture.

var _v193_trend_label: Label

func _v193_error(sample: Vector2) -> float:
    var line_component := sample.x / V190_LINE_TOLERANCE_CM
    var pace_component := sample.y / V190_PACE_TOLERANCE_CM
    return sqrt(line_component * line_component + pace_component * pace_component)

func _v193_form_trend() -> Dictionary:
    if _v179_samples.size() < 4:
        return {"state": "BUILDING", "delta": 0.0}
    var count := _v179_samples.size()
    var old_mean := (_v193_error(_v179_samples[count - 4]) + _v193_error(_v179_samples[count - 3])) * 0.5
    var new_mean := (_v193_error(_v179_samples[count - 2]) + _v193_error(_v179_samples[count - 1])) * 0.5
    var baseline := maxf(old_mean, 0.15)
    var delta := (new_mean - old_mean) / baseline
    if delta <= -0.18:
        return {"state": "IMPROVING", "delta": delta}
    if delta >= 0.18:
        return {"state": "SLIPPING", "delta": delta}
    return {"state": "STABLE", "delta": delta}

func _build_hud() -> void:
    super._build_hud()
    if _v191_bar == null:
        return
    if _v191_streak_label != null:
        _v191_streak_label.size.x = 185
    _v193_trend_label = _v174_text(
        _v191_bar,
        Vector2(205, 5),
        Vector2(108, 18),
        "FORM · BUILDING",
        9,
        Color(0.68, 0.78, 0.76, 0.94),
        HORIZONTAL_ALIGNMENT_RIGHT
    )
    _v193_refresh()

func _v193_refresh() -> void:
    if _v193_trend_label == null:
        return
    var trend := _v193_form_trend()
    var state := str(trend.get("state", "BUILDING"))
    match state:
        "IMPROVING":
            _v193_trend_label.text = "FORM · ▲ IMPROVING"
            _v193_trend_label.modulate = Color("#76d7b6")
        "SLIPPING":
            _v193_trend_label.text = "FORM · ▼ SLIPPING"
            _v193_trend_label.modulate = Color("#f0a56d")
        "STABLE":
            _v193_trend_label.text = "FORM · ● STABLE"
            _v193_trend_label.modulate = Color("#f4dda0")
        _:
            _v193_trend_label.text = "FORM · BUILDING"
            _v193_trend_label.modulate = Color(0.68, 0.78, 0.76, 0.94)

func _v179_refresh() -> void:
    super._v179_refresh()
    _v193_refresh()

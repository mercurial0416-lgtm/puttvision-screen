extends "res://address_relief_camera.gd"

# Presentation-only practice result legibility. Existing post-shot line/pace telemetry stays
# authoritative; this layer only gives the newest rep a stable visual hierarchy and turns signed
# averages into semantic LEFT/RIGHT/LONG/SHORT language that reads cleanly from a TV.
const SESSION_DISPERSION_RECENT_SIZE := Vector2(13.0, 13.0)
const SESSION_DISPERSION_HISTORY_SIZE := Vector2(9.0, 9.0)
const SESSION_DISPERSION_RECENT_COLOR := Color("#f4dda0")
const SESSION_DISPERSION_HISTORY_COLOR := Color("#76d7b6")
const SESSION_DISPERSION_ZERO_EPSILON_CM := 0.5

func _session_line_average_text(value_cm: float) -> String:
    if absf(value_cm) < SESSION_DISPERSION_ZERO_EPSILON_CM:
        return "CENTER 0 cm"
    return "R %.0f cm" % absf(value_cm) if value_cm > 0.0 else "L %.0f cm" % absf(value_cm)

func _session_pace_average_text(value_cm: float) -> String:
    if absf(value_cm) < SESSION_DISPERSION_ZERO_EPSILON_CM:
        return "CUP 0 cm"
    return "LONG %.0f cm" % absf(value_cm) if value_cm > 0.0 else "SHORT %.0f cm" % absf(value_cm)

func _session_apply_rep_hierarchy() -> void:
    var active_count := mini(_v179_samples.size(), _v179_points.size())
    for index in range(_v179_points.size()):
        var dot := _v179_points[index]
        if index >= active_count:
            continue
        var latest := index == active_count - 1
        dot.size = SESSION_DISPERSION_RECENT_SIZE if latest else SESSION_DISPERSION_HISTORY_SIZE
        dot.color = SESSION_DISPERSION_RECENT_COLOR if latest else SESSION_DISPERSION_HISTORY_COLOR
        dot.modulate.a = 1.0 if latest else 0.48 + 0.30 * float(index + 1) / float(maxi(1, active_count))
        dot.position = _v179_plot_position(_v179_samples[index]) - dot.size * 0.5

func _v179_refresh() -> void:
    super._v179_refresh()
    if _v179_panel == null:
        return
    if _v179_line_mean_label != null:
        _v179_line_mean_label.text = _session_line_average_text(_v179_mean(0))
    if _v179_pace_mean_label != null:
        _v179_pace_mean_label.text = _session_pace_average_text(_v179_mean(1))
    _session_apply_rep_hierarchy()

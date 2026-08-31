extends "res://replay_cup_camera_side_preview.gd"

const SESSION_DISPERSION_RECENT_SIZE := Vector2(13.0, 13.0)
const SESSION_DISPERSION_HISTORY_SIZE := Vector2(9.0, 9.0)
const SESSION_DISPERSION_RECENT_COLOR := Color("#f4dda0")
const SESSION_DISPERSION_HISTORY_COLOR := Color("#76d7b6")
const SESSION_DISPERSION_ZERO_EPSILON_CM := 0.5
var _session_dispersion_readability_checked := false

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

func _process(delta: float) -> void:
    super._process(delta)
    if _session_dispersion_readability_checked or _preview_frames < 24:
        return
    _session_dispersion_readability_checked = true

    _v179_samples = [Vector2(-7.0, -18.0), Vector2(3.0, 8.0), Vector2(8.0, 22.0)]
    _v179_preview_force_visible = true
    _v179_refresh()
    if _v179_panel != null:
        _v179_panel.visible = true

    if _session_line_average_text(6.0) != "R 6 cm" or _session_line_average_text(-6.0) != "L 6 cm":
        push_error("Session dispersion line semantics regression")
        get_tree().quit(31)
        return
    if _session_pace_average_text(18.0) != "LONG 18 cm" or _session_pace_average_text(-18.0) != "SHORT 18 cm":
        push_error("Session dispersion pace semantics regression")
        get_tree().quit(31)
        return
    if _v179_points.size() < 3 or _v179_points[2].color != SESSION_DISPERSION_RECENT_COLOR or _v179_points[2].size != SESSION_DISPERSION_RECENT_SIZE:
        push_error("Newest practice rep emphasis regression")
        get_tree().quit(31)
        return
    if _v179_points[1].color != SESSION_DISPERSION_HISTORY_COLOR or _v179_points[1].size != SESSION_DISPERSION_HISTORY_SIZE:
        push_error("Practice rep history hierarchy regression")
        get_tree().quit(31)
        return

    print("SESSION_DISPERSION_READABILITY_OK=1")
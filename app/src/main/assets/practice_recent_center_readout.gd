extends "res://practice_recent_center_reticle.gd"

# Presentation-only numeric companion for the production recent-three centroid reticle. The plot
# already shows where the active-focus centroid sits; this makes the same truthful point readable
# from TV distance without asking the player to estimate centimeters from the grid. Android physics,
# GreenTerrain, GreenReadAdvisor, aiming, scoring and shot capture remain authoritative and untouched.

const PRACTICE_RECENT_READOUT_LINE_DEADBAND_CM := 2.5
const PRACTICE_RECENT_READOUT_PACE_DEADBAND_CM := 8.0

var _practice_recent_center_readout: Label

func _practice_recent_center_axis_text(value_cm: float, negative: String, positive: String, deadband_cm: float, hold_text: String) -> String:
    if not is_finite(value_cm):
        return "--"
    if absf(value_cm) <= deadband_cm:
        return hold_text
    return "%s %.0f cm" % [positive if value_cm > 0.0 else negative, absf(value_cm)]

func _practice_recent_center_readout_text(sample: Vector2) -> String:
    if not is_finite(sample.x) or not is_finite(sample.y):
        return "RECENT 3 CENTER\nDATA UNAVAILABLE"
    var line_text := _practice_recent_center_axis_text(
        sample.x,
        "LEFT",
        "RIGHT",
        PRACTICE_RECENT_READOUT_LINE_DEADBAND_CM,
        "LINE OK"
    )
    var pace_text := _practice_recent_center_axis_text(
        sample.y,
        "SHORT",
        "LONG",
        PRACTICE_RECENT_READOUT_PACE_DEADBAND_CM,
        "PACE OK"
    )
    return "RECENT 3 CENTER\n%s  ·  %s" % [line_text, pace_text]

func _build_hud() -> void:
    super._build_hud()
    if _v179_panel == null:
        return
    # The right rail has a deliberate gap below the make-window detail and above the bottom AVG row.
    # Keep this readout there instead of covering shot dots inside the plot.
    _practice_recent_center_readout = _v174_text(
        _v179_panel,
        Vector2(344, 124),
        Vector2(186, 38),
        "RECENT 3 CENTER\n--",
        9,
        Color(0.76, 0.91, 0.86, 0.94),
        HORIZONTAL_ALIGNMENT_RIGHT
    )
    _practice_recent_center_readout.name = "PracticeRecentCenterReadout"
    _practice_recent_center_readout.visible = false
    _practice_recent_center_readout.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _practice_recent_center_readout_refresh()

func _practice_recent_center_readout_refresh() -> void:
    if _practice_recent_center_readout == null:
        return
    var geometry := _practice_recent_center_geometry(_practice_recent_center_focus_samples())
    var visible := bool(geometry.get("visible", false))
    _practice_recent_center_readout.visible = visible
    if not visible:
        _practice_recent_center_readout.text = "RECENT 3 CENTER\n--"
        return
    var sample_variant: Variant = geometry.get("sample", null)
    if not (sample_variant is Vector2):
        _practice_recent_center_readout.visible = false
        _practice_recent_center_readout.text = "RECENT 3 CENTER\n--"
        return
    _practice_recent_center_readout.text = _practice_recent_center_readout_text(sample_variant as Vector2)

func _v179_refresh() -> void:
    super._v179_refresh()
    _practice_recent_center_readout_refresh()

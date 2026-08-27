extends "res://v184_make_window.gd"

# Presentation-only pace intent cue. Uses the live distance/long-slope snapshot to express a
# readable speed intention without feeding values back into Android physics or GreenReadAdvisor.

var _v185_pace_label: Label
var _v185_pace_track: Line2D
var _v185_pace_fill: Line2D
var _v185_pace_marker: Polygon2D
var _v185_last_intent: float = 0.5

func _v185_intent(distance_m: float, long_pct: float) -> float:
    var distance_term := clampf((distance_m - 1.5) / 7.0, 0.0, 1.0)
    var slope_term := clampf(-long_pct / 3.0, -0.28, 0.28)
    return clampf(0.36 + distance_term * 0.34 + slope_term, 0.16, 0.88)

func _v185_intent_text(intent: float) -> String:
    if intent < 0.38:
        return "PACE  SOFT"
    if intent > 0.68:
        return "PACE  FIRM"
    return "PACE  CONTROLLED"

func _build_hud() -> void:
    super._build_hud()
    if _v183_panel == null:
        return

    _v185_pace_label = _v174_text(
        _v183_panel,
        Vector2(92, 32),
        Vector2(158, 14),
        "PACE  CONTROLLED",
        9,
        Color(0.76, 0.90, 0.84, 0.92),
        HORIZONTAL_ALIGNMENT_CENTER
    )

    _v185_pace_track = Line2D.new()
    _v185_pace_track.name = "PaceIntentTrack"
    _v185_pace_track.width = 3.0
    _v185_pace_track.default_color = Color(0.60, 0.74, 0.68, 0.18)
    _v185_pace_track.points = PackedVector2Array([Vector2(112, 45), Vector2(230, 45)])
    _v183_panel.add_child(_v185_pace_track)

    _v185_pace_fill = Line2D.new()
    _v185_pace_fill.name = "PaceIntentFill"
    _v185_pace_fill.width = 3.0
    _v185_pace_fill.default_color = Color(0.47, 0.84, 0.66, 0.88)
    _v183_panel.add_child(_v185_pace_fill)

    _v185_pace_marker = Polygon2D.new()
    _v185_pace_marker.name = "PaceIntentMarker"
    _v185_pace_marker.polygon = PackedVector2Array([Vector2(0, -4), Vector2(4, 3), Vector2(-4, 3)])
    _v185_pace_marker.color = Color(0.95, 0.80, 0.32, 0.98)
    _v183_panel.add_child(_v185_pace_marker)

func _v185_refresh_pace(distance_m: float, long_pct: float) -> void:
    if _v185_pace_label == null:
        return
    _v185_last_intent = _v185_intent(distance_m, long_pct)
    var x0 := 112.0
    var x1 := 230.0
    var x := lerpf(x0, x1, _v185_last_intent)
    _v185_pace_fill.points = PackedVector2Array([Vector2(x0, 45), Vector2(x, 45)])
    _v185_pace_marker.position = Vector2(x, 45)
    _v185_pace_label.text = _v185_intent_text(_v185_last_intent)
    var visible := _v183_panel.visible
    _v185_pace_label.visible = visible
    _v185_pace_track.visible = visible
    _v185_pace_fill.visible = visible
    _v185_pace_marker.visible = visible

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    super._v183_update(s, force_visible)
    if _v183_panel == null or not _v183_panel.visible:
        if _v185_pace_label != null:
            _v185_pace_label.visible = false
            _v185_pace_track.visible = false
            _v185_pace_fill.visible = false
            _v185_pace_marker.visible = false
        return
    _v185_refresh_pace(
        maxf(0.0, float(s.get("distanceToCup", 0.0))),
        float(s.get("longSlope", 0.0))
    )

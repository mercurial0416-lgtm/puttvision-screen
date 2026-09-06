extends "res://v184_make_window.gd"

# Presentation-only pace status. The previous bar invented a SOFT/CONTROLLED/FIRM intent from
# distance and longitudinal grade even when the Android inverse solver had not supplied a pace
# recommendation. That made a heuristic look like physics truth beside the authoritative read path.
# Keep the compact HUD slot, but report solver readiness only; never synthesize a speed instruction.

var _v185_pace_label: Label
# Kept for compatibility with later presentation layers that may inspect these members.
var _v185_pace_track: Line2D
var _v185_pace_fill: Line2D
var _v185_pace_marker: Polygon2D
var _v185_last_intent: float = 0.5

func _v185_solver_ready() -> bool:
    var ready_variant: Variant = get("_v166_solver_ready")
    return ready_variant is bool and bool(ready_variant)

func _build_hud() -> void:
    super._build_hud()
    if _v183_panel == null:
        return

    _v185_pace_label = _v174_text(
        _v183_panel,
        Vector2(92, 32),
        Vector2(158, 14),
        "PACE  SOLVING",
        9,
        Color(0.76, 0.90, 0.84, 0.92),
        HORIZONTAL_ALIGNMENT_CENTER
    )

func _v185_refresh_pace(_distance_m: float, _long_pct: float) -> void:
    if _v185_pace_label == null:
        return

    var panel_visible := _v183_panel != null and _v183_panel.visible
    _v185_pace_label.visible = panel_visible
    _v185_pace_label.text = "PACE  PHYSICS READ" if _v185_solver_ready() else "PACE  SOLVING"

    # Fail closed for any legacy nodes left alive by hot reload or an older packed scene. They carry
    # no authoritative pace meaning and must not reappear beside the exact Android solver trail.
    if _v185_pace_track != null:
        _v185_pace_track.visible = false
        _v185_pace_track.points = PackedVector2Array()
    if _v185_pace_fill != null:
        _v185_pace_fill.visible = false
        _v185_pace_fill.points = PackedVector2Array()
    if _v185_pace_marker != null:
        _v185_pace_marker.visible = false

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    super._v183_update(s, force_visible)
    if _v183_panel == null or not _v183_panel.visible:
        if _v185_pace_label != null:
            _v185_pace_label.visible = false
        return
    _v185_refresh_pace(
        maxf(0.0, float(s.get("distanceToCup", 0.0))),
        float(s.get("longSlope", 0.0))
    )

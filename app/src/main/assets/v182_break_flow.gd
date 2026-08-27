extends "res://v181_pace_target.gd"

# Presentation-only animated break-flow beads layered on the existing authoritative read curve.
# No physics/advisor values are modified; this only makes break direction and strength easier to read.

var _v182_beads: Array[ColorRect] = []
var _v182_phase := 0.0
var _v182_side_pct := 0.0
var _v182_last_offset := 0.0

const V182_BEAD_COUNT := 8

func _build_hud() -> void:
    super._build_hud()
    if _v176_panel == null:
        return
    for i in range(V182_BEAD_COUNT):
        var bead := ColorRect.new()
        bead.name = "BreakFlowBead%02d" % i
        bead.size = Vector2(5, 5)
        bead.color = Color(0.35, 0.98, 0.82, 0.95)
        bead.mouse_filter = Control.MOUSE_FILTER_IGNORE
        bead.visible = false
        _v176_panel.add_child(bead)
        _v182_beads.append(bead)

func _v182_curve_point(points: PackedVector2Array, t: float) -> Vector2:
    if points.size() < 2:
        return Vector2.ZERO
    var scaled := clampf(t, 0.0, 0.9999) * float(points.size() - 1)
    var idx := int(floor(scaled))
    var frac := scaled - float(idx)
    return points[idx].lerp(points[min(idx + 1, points.size() - 1)], frac)

func _v182_update_flow(delta: float) -> void:
    if _v182_beads.is_empty() or _v176_panel == null:
        return
    var active := _v176_panel.visible and _v165_enhanced_enabled and _v164_grid_enabled
    var strength := clampf(abs(_v182_side_pct) / 3.0, 0.0, 1.0)
    var speed := 0.20 + strength * 0.78
    _v182_phase = fposmod(_v182_phase + delta * speed, 1.0)
    var curve := _v176_read_curve(_v182_last_offset)
    var reverse := _v182_side_pct < 0.0
    for i in range(_v182_beads.size()):
        var bead := _v182_beads[i]
        bead.visible = active
        if not active:
            continue
        var lane := fposmod(_v182_phase + float(i) / float(V182_BEAD_COUNT), 1.0)
        var t := 1.0 - lane if reverse else lane
        var p := _v182_curve_point(curve, t)
        bead.position = p - bead.size * 0.5
        bead.modulate.a = 0.34 + 0.66 * (0.35 + strength * 0.65)
        var scale_value := 0.78 + strength * 0.42
        bead.scale = Vector2(scale_value, scale_value)

func _process(delta: float) -> void:
    super._process(delta)
    _v182_update_flow(delta)

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _v182_side_pct = float(s.get("sideSlope", 0.0))
    _v182_last_offset = _v165_recommended_offset

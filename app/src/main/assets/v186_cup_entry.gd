extends "res://v185_pace_intent.gd"

# Presentation-only cup-entry window. Visualizes a practical terminal-speed target and capture
# ring from the existing distance/grade snapshot. It never feeds values back into Android
# physics, GreenTerrain or GreenReadAdvisor.

var _v186_entry_ring: Line2D
var _v186_entry_label: Label
var _v186_entry_low := 0.0
var _v186_entry_high := 0.0

func _v186_entry_target(distance_m: float, long_pct: float) -> float:
    var distance_term := clampf((distance_m - 1.0) / 8.0, 0.0, 1.0) * 0.16
    var grade_term := clampf(-long_pct * 0.035, -0.10, 0.10)
    return clampf(0.58 + distance_term + grade_term, 0.45, 0.92)

func _v186_entry_band(distance_m: float, long_pct: float) -> Vector2:
    var target := _v186_entry_target(distance_m, long_pct)
    var half := clampf(0.15 - distance_m * 0.006 - absf(long_pct) * 0.012, 0.07, 0.14)
    return Vector2(maxf(0.35, target - half), minf(1.05, target + half))

func _v186_ring_points(center: Vector2, radius: float, segments: int = 32) -> PackedVector2Array:
    var out := PackedVector2Array()
    for i in range(segments + 1):
        var a := TAU * float(i) / float(segments)
        out.append(center + Vector2(cos(a), sin(a)) * radius)
    return out

func _build_hud() -> void:
    super._build_hud()
    if _v183_panel == null:
        return

    _v186_entry_ring = Line2D.new()
    _v186_entry_ring.name = "CupEntryWindow"
    _v186_entry_ring.width = 2.2
    _v186_entry_ring.default_color = Color(0.95, 0.80, 0.32, 0.82)
    _v183_panel.add_child(_v186_entry_ring)

    _v186_entry_label = _v174_text(
        _v183_panel,
        Vector2(18, 32),
        Vector2(84, 16),
        "ENTRY 0.6–0.8",
        9,
        Color(0.95, 0.84, 0.52, 0.96)
    )

func _v186_refresh_entry(distance_m: float, long_pct: float) -> void:
    if _v186_entry_ring == null or _v183_path_line == null:
        return
    var curve := _v183_path_line.points
    if curve.size() < 2:
        return

    var band := _v186_entry_band(distance_m, long_pct)
    _v186_entry_low = band.x
    _v186_entry_high = band.y
    var center: Vector2 = curve[curve.size() - 1]
    var window_width := maxf(0.02, _v186_entry_high - _v186_entry_low)
    var radius := clampf(7.0 + window_width * 42.0, 10.0, 18.0)
    _v186_entry_ring.points = _v186_ring_points(center, radius)
    _v186_entry_label.text = "ENTRY %.1f–%.1f" % [_v186_entry_low, _v186_entry_high]

    var visible := _v183_panel.visible
    _v186_entry_ring.visible = visible
    _v186_entry_label.visible = visible

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    super._v183_update(s, force_visible)
    if _v183_panel == null or not _v183_panel.visible:
        if _v186_entry_ring != null:
            _v186_entry_ring.visible = false
            _v186_entry_label.visible = false
        return
    _v186_refresh_entry(
        maxf(0.0, float(s.get("distanceToCup", 0.0))),
        float(s.get("longSlope", 0.0))
    )

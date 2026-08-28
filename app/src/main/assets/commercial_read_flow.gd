extends "res://commercial_read_apex.gd"

# Presentation-only directional cues derived from the existing recommended read path.
# These never feed values back into Android physics, GreenTerrain, GreenReadAdvisor, aiming, or scoring.

const READ_FLOW_FRACTIONS := [0.40, 0.60, 0.78]
const READ_FLOW_TIP_LENGTH := 4.8
const READ_FLOW_WING_HALF_WIDTH := 3.4

var _read_flow_cues: Array[Line2D] = []
var _live_curve_was_running := false
var _live_curve_origin := Vector2.ZERO
var _live_curve_forward := Vector2.UP

func _read_flow_geometry(offset_m: float, fraction: float) -> Dictionary:
    var curve := _v183_path(offset_m)
    if curve.size() < 3:
        var fallback := V183_MAP_ORIGIN + V183_MAP_SIZE * Vector2(0.5, 0.5)
        return {
            "center": fallback,
            "tip": fallback + Vector2.UP * READ_FLOW_TIP_LENGTH,
            "left": fallback + Vector2.DOWN * READ_FLOW_TIP_LENGTH + Vector2.LEFT * READ_FLOW_WING_HALF_WIDTH,
            "right": fallback + Vector2.DOWN * READ_FLOW_TIP_LENGTH + Vector2.RIGHT * READ_FLOW_WING_HALF_WIDTH,
            "tangent": Vector2.UP
        }
    var index := clampi(int(round(float(curve.size() - 1) * clampf(fraction, 0.05, 0.95))), 1, curve.size() - 2)
    var center: Vector2 = curve[index]
    var tangent := (curve[index + 1] - curve[index - 1]).normalized()
    if tangent.length_squared() < 0.5:
        tangent = Vector2.UP
    var normal := Vector2(-tangent.y, tangent.x)
    var base := center - tangent * READ_FLOW_TIP_LENGTH
    return {
        "center": center,
        "tip": center + tangent * READ_FLOW_TIP_LENGTH,
        "left": base + normal * READ_FLOW_WING_HALF_WIDTH,
        "right": base - normal * READ_FLOW_WING_HALF_WIDTH,
        "tangent": tangent
    }

func _build_hud() -> void:
    super._build_hud()
    if _v183_panel == null:
        return
    for i in range(READ_FLOW_FRACTIONS.size()):
        var cue := Line2D.new()
        cue.name = "CommercialReadFlowCue%d" % (i + 1)
        cue.width = 1.8
        cue.default_color = Color(0.72, 0.94, 1.0, 0.74 - float(i) * 0.08)
        cue.joint_mode = Line2D.LINE_JOINT_ROUND
        cue.begin_cap_mode = Line2D.LINE_CAP_ROUND
        cue.end_cap_mode = Line2D.LINE_CAP_ROUND
        _v183_panel.add_child(cue)
        _read_flow_cues.append(cue)

func _refresh_read_flow() -> void:
    if _v183_panel == null:
        return
    var visible := _v183_panel.visible
    for i in range(_read_flow_cues.size()):
        var cue := _read_flow_cues[i]
        cue.visible = visible
        if not visible:
            continue
        var geometry := _read_flow_geometry(_v165_recommended_offset, READ_FLOW_FRACTIONS[i])
        cue.points = PackedVector2Array([geometry["left"], geometry["tip"], geometry["right"]])

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    super._v183_update(s, force_visible)
    _refresh_read_flow()

# Make the authoritative roll response obvious without touching physics. During a live roll the
# existing slope HUD also reports cross-track curve from the measured launch line. This is derived
# only from bridge x/y/vx/vy snapshots, so a user can immediately see that a sloped green is bending
# the real ball path instead of mistaking a subtle camera perspective for a flat simulation.
func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    var running := bool(s.get("running", false))
    if running and not _live_curve_was_running:
        _live_curve_origin = Vector2(float(s.get("startX", 0.0)), float(s.get("startY", 0.0)))
        var velocity := Vector2(float(s.get("vx", 0.0)), float(s.get("vy", 0.0)))
        _live_curve_forward = velocity.normalized() if velocity.length_squared() > 0.0001 else Vector2.UP
    if running and slope_label != null:
        var ball_pos := Vector2(float(s.get("ballX", 0.0)), float(s.get("ballY", 0.0)))
        var launch_right := Vector2(_live_curve_forward.y, -_live_curve_forward.x)
        var cross_track_cm := (ball_pos - _live_curve_origin).dot(launch_right) * 100.0
        var curve_text := "CENTER"
        if abs(cross_track_cm) >= 0.05:
            curve_text = "%s %.1fcm" % ["R" if cross_track_cm > 0.0 else "L", abs(cross_track_cm)]
        slope_label.text += "   ·   LIVE CURVE %s" % curve_text
    _live_curve_was_running = running

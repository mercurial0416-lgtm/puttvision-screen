extends "res://commercial_read_apex.gd"

# Presentation-only directional cues derived from the existing recommended read path.
# These never feed values back into Android physics, GreenTerrain, GreenReadAdvisor, aiming, or scoring.

const READ_FLOW_FRACTIONS := [0.40, 0.60, 0.78]
const READ_FLOW_TIP_LENGTH := 4.8
const READ_FLOW_WING_HALF_WIDTH := 3.4

var _read_flow_cues: Array[Line2D] = []

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

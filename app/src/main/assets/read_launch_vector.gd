extends "res://replay_playhead_polish.gd"

# Presentation-only launch-vector cue derived from the existing recommended read path.
# It never feeds values back into Android physics, GreenTerrain, GreenReadAdvisor, aiming, or scoring.
const READ_LAUNCH_FRACTION := 0.18
const READ_LAUNCH_WING_LENGTH := 7.0
const READ_LAUNCH_WING_HALF_WIDTH := 4.6

var _read_launch_shaft: Line2D
var _read_launch_head: Line2D

func _read_launch_geometry(offset_m: float) -> Dictionary:
    var curve := _v183_path(offset_m)
    if curve.size() < 3:
        var fallback := V183_MAP_ORIGIN + V183_MAP_SIZE * Vector2(0.5, 0.72)
        return {
            "start": fallback,
            "tip": fallback + Vector2.UP * 20.0,
            "left": fallback + Vector2.DOWN * READ_LAUNCH_WING_LENGTH + Vector2.LEFT * READ_LAUNCH_WING_HALF_WIDTH,
            "right": fallback + Vector2.DOWN * READ_LAUNCH_WING_LENGTH + Vector2.RIGHT * READ_LAUNCH_WING_HALF_WIDTH,
            "tangent": Vector2.UP
        }

    var index := clampi(int(round(float(curve.size() - 1) * READ_LAUNCH_FRACTION)), 2, curve.size() - 1)
    var start: Vector2 = curve[0]
    var tip: Vector2 = curve[index]
    var tangent := (curve[2] - curve[0]).normalized()
    if tangent.length_squared() < 0.5:
        tangent = (tip - start).normalized()
    if tangent.length_squared() < 0.5:
        tangent = Vector2.UP
    var normal := Vector2(-tangent.y, tangent.x)
    var base := tip - tangent * READ_LAUNCH_WING_LENGTH
    return {
        "start": start,
        "tip": tip,
        "left": base + normal * READ_LAUNCH_WING_HALF_WIDTH,
        "right": base - normal * READ_LAUNCH_WING_HALF_WIDTH,
        "tangent": tangent
    }

func _build_hud() -> void:
    super._build_hud()
    if _v183_panel == null:
        return

    _read_launch_shaft = Line2D.new()
    _read_launch_shaft.name = "CommercialReadLaunchShaft"
    _read_launch_shaft.width = 2.7
    _read_launch_shaft.default_color = Color(0.48, 0.91, 1.0, 0.94)
    _read_launch_shaft.begin_cap_mode = Line2D.LINE_CAP_ROUND
    _read_launch_shaft.end_cap_mode = Line2D.LINE_CAP_ROUND
    _v183_panel.add_child(_read_launch_shaft)

    _read_launch_head = Line2D.new()
    _read_launch_head.name = "CommercialReadLaunchHead"
    _read_launch_head.width = 2.7
    _read_launch_head.default_color = Color(0.74, 0.96, 1.0, 0.98)
    _read_launch_head.joint_mode = Line2D.LINE_JOINT_ROUND
    _read_launch_head.begin_cap_mode = Line2D.LINE_CAP_ROUND
    _read_launch_head.end_cap_mode = Line2D.LINE_CAP_ROUND
    _v183_panel.add_child(_read_launch_head)

func _refresh_read_launch_vector() -> void:
    if _v183_panel == null or _read_launch_shaft == null or _read_launch_head == null:
        return
    var visible := _v183_panel.visible
    _read_launch_shaft.visible = visible
    _read_launch_head.visible = visible
    if not visible:
        return

    var geometry := _read_launch_geometry(_v165_recommended_offset)
    var start: Vector2 = geometry["start"]
    var tip: Vector2 = geometry["tip"]
    _read_launch_shaft.points = PackedVector2Array([start, tip])
    _read_launch_head.points = PackedVector2Array([geometry["left"], tip, geometry["right"]])

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    super._v183_update(s, force_visible)
    _refresh_read_launch_vector()

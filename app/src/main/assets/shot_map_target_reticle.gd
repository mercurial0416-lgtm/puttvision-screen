extends Node

# Presentation-only focus treatment for the event-driven SHOT MAP correction landing target.
# It becomes a child of the existing target marker, so position/visibility follow automatically
# without polling and without touching putting physics, terrain, read advice, aim, or scoring.

const RETICLE_HALF_PX := 3.0
const RETICLE_GAP_PX := 1.4
const RETICLE_WIDTH_PX := 1.35
const RETICLE_COLOR := Color(0.82, 1.00, 0.90, 0.96)
const FOCUS_DISC_RADIUS_PX := 4.1
const FOCUS_DISC_SEGMENTS := 16
const FOCUS_DISC_COLOR := Color(0.58, 1.00, 0.78, 0.18)

func _ready() -> void:
    call_deferred("_attach_reticle")

func _focus_disc_points() -> PackedVector2Array:
    var points := PackedVector2Array()
    for i in range(FOCUS_DISC_SEGMENTS):
        var angle := TAU * float(i) / float(FOCUS_DISC_SEGMENTS)
        points.append(Vector2(cos(angle), sin(angle)) * FOCUS_DISC_RADIUS_PX)
    return points

func _add_reticle_segment(target: Line2D, name: String, start: Vector2, finish: Vector2) -> void:
    var segment := Line2D.new()
    segment.name = name
    segment.width = RETICLE_WIDTH_PX
    segment.default_color = RETICLE_COLOR
    segment.points = PackedVector2Array([start, finish])
    segment.z_index = 1
    target.add_child(segment)

func _attach_reticle() -> void:
    var target := get_tree().current_scene.find_child("ShotMapCorrectionTarget", true, false)
    if target == null or not (target is Line2D):
        return
    if target.find_child("TargetReticleLeft", false, false) != null:
        return

    var focus_disc := Polygon2D.new()
    focus_disc.name = "TargetFocusDisc"
    focus_disc.polygon = _focus_disc_points()
    focus_disc.color = FOCUS_DISC_COLOR
    focus_disc.z_index = 0
    target.add_child(focus_disc)

    _add_reticle_segment(target, "TargetReticleLeft", Vector2(-RETICLE_HALF_PX, 0.0), Vector2(-RETICLE_GAP_PX, 0.0))
    _add_reticle_segment(target, "TargetReticleRight", Vector2(RETICLE_GAP_PX, 0.0), Vector2(RETICLE_HALF_PX, 0.0))
    _add_reticle_segment(target, "TargetReticleTop", Vector2(0.0, -RETICLE_HALF_PX), Vector2(0.0, -RETICLE_GAP_PX))
    _add_reticle_segment(target, "TargetReticleBottom", Vector2(0.0, RETICLE_GAP_PX), Vector2(0.0, RETICLE_HALF_PX))

extends Node

# Presentation-only center reticle for the event-driven SHOT MAP correction landing target.
# It becomes a child of the existing target marker, so position/visibility follow automatically
# without polling and without touching putting physics, terrain, read advice, aim, or scoring.

const RETICLE_HALF_PX := 3.0
const RETICLE_GAP_PX := 1.4
const RETICLE_WIDTH_PX := 1.35
const RETICLE_COLOR := Color(0.82, 1.00, 0.90, 0.96)

func _ready() -> void:
    call_deferred("_attach_reticle")

func _segment(name: String, points: PackedVector2Array) -> Line2D:
    var line := Line2D.new()
    line.name = name
    line.width = RETICLE_WIDTH_PX
    line.default_color = RETICLE_COLOR
    line.points = points
    line.z_index = 1
    line.mouse_filter = Control.MOUSE_FILTER_IGNORE if line is Control else 0
    return line

func _attach_reticle() -> void:
    var target := get_tree().current_scene.find_child("ShotMapCorrectionTarget", true, false)
    if target == null or not (target is Line2D):
        return
    if target.find_child("TargetReticleHorizontal", false, false) != null:
        return

    var horizontal := Line2D.new()
    horizontal.name = "TargetReticleHorizontal"
    horizontal.width = RETICLE_WIDTH_PX
    horizontal.default_color = RETICLE_COLOR
    horizontal.points = PackedVector2Array([
        Vector2(-RETICLE_HALF_PX, 0.0), Vector2(-RETICLE_GAP_PX, 0.0),
        Vector2(RETICLE_GAP_PX, 0.0), Vector2(RETICLE_HALF_PX, 0.0)
    ])
    horizontal.z_index = 1
    target.add_child(horizontal)

    var vertical := Line2D.new()
    vertical.name = "TargetReticleVertical"
    vertical.width = RETICLE_WIDTH_PX
    vertical.default_color = RETICLE_COLOR
    vertical.points = PackedVector2Array([
        Vector2(0.0, -RETICLE_HALF_PX), Vector2(0.0, -RETICLE_GAP_PX),
        Vector2(0.0, RETICLE_GAP_PX), Vector2(0.0, RETICLE_HALF_PX)
    ])
    vertical.z_index = 1
    target.add_child(vertical)

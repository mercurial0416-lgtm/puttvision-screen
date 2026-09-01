extends Node

# Presentation-only correction destination cue. It observes the existing SHOT MAP correction vector
# and adds a compact landing ring at its truthful in-map endpoint. Off-scale misses keep the existing
# bounded direction-only cue, so radial clipping can never imply a fake destination.
# Android putting physics, GreenTerrain, GreenReadAdvisor, aiming and scoring remain untouched.

var _target_ring: Line2D
var _correction_vector: Line2D
var _overflow_tick: Line2D
var _refresh_timer: Timer

const TARGET_RADIUS_PX := 5.0
const TARGET_SEGMENTS := 14
const TARGET_COLOR := Color(0.58, 1.00, 0.78, 0.96)
const TARGET_Z_INDEX := 1
const REFRESH_INTERVAL_S := 0.10

func _target_points(radius: float = TARGET_RADIUS_PX, segments: int = TARGET_SEGMENTS) -> PackedVector2Array:
    var safe_segments := maxi(8, segments)
    var out := PackedVector2Array()
    for i in range(safe_segments + 1):
        var angle := TAU * float(i) / float(safe_segments)
        out.append(Vector2(cos(angle), sin(angle)) * radius)
    return out

func _ready() -> void:
    _refresh_timer = Timer.new()
    _refresh_timer.name = "ShotMapCorrectionTargetRefresh"
    _refresh_timer.wait_time = REFRESH_INTERVAL_S
    _refresh_timer.one_shot = false
    _refresh_timer.autostart = true
    _refresh_timer.timeout.connect(_refresh_target)
    add_child(_refresh_timer)
    call_deferred("_bind_shot_map")

func _bind_shot_map() -> void:
    var root := get_parent()
    if root == null:
        return
    _correction_vector = root.find_child("ShotMapCorrectionVector", true, false) as Line2D
    _overflow_tick = root.find_child("ShotMissOverflowTick", true, false) as Line2D
    if _correction_vector == null:
        return
    var panel := _correction_vector.get_parent()
    if panel == null:
        return
    _target_ring = Line2D.new()
    _target_ring.name = "ShotMapCorrectionTarget"
    _target_ring.width = 2.0
    _target_ring.default_color = TARGET_COLOR
    _target_ring.points = _target_points()
    _target_ring.visible = false
    _target_ring.z_index = TARGET_Z_INDEX
    panel.add_child(_target_ring)
    _refresh_target()

func _refresh_target() -> void:
    if _target_ring == null:
        if _correction_vector == null:
            _bind_shot_map()
        return
    _target_ring.visible = false
    if _correction_vector == null or not _correction_vector.visible:
        return
    if _overflow_tick != null and _overflow_tick.visible:
        return
    if _correction_vector.points.size() < 2:
        return
    _target_ring.position = _correction_vector.points[_correction_vector.points.size() - 1]
    _target_ring.visible = true

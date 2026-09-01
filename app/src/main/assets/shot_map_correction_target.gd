extends "res://replay_timeline_camera_truth.gd"

# Presentation-only correction destination cue. The existing SHOT MAP correction vector already
# tells the player which direction to move a miss; this layer adds a compact landing ring at the
# buffered success-window destination when that destination can be shown truthfully on-map.
# Android putting physics, GreenTerrain, GreenReadAdvisor, aiming and scoring remain untouched.

var _shot_map_correction_target: Line2D

const SHOT_MAP_TARGET_RADIUS_PX := 5.0
const SHOT_MAP_TARGET_SEGMENTS := 14
const SHOT_MAP_TARGET_COLOR := Color(0.58, 1.00, 0.78, 0.96)
const SHOT_MAP_TARGET_Z_INDEX := 1

func _shot_map_target_ring(radius: float = SHOT_MAP_TARGET_RADIUS_PX, segments: int = SHOT_MAP_TARGET_SEGMENTS) -> PackedVector2Array:
    var safe_segments := maxi(8, segments)
    var out := PackedVector2Array()
    for i in range(safe_segments + 1):
        var angle := TAU * float(i) / float(safe_segments)
        out.append(Vector2(cos(angle), sin(angle)) * radius)
    return out

func _build_hud() -> void:
    super._build_hud()
    if _v188_panel == null:
        return

    _shot_map_correction_target = Line2D.new()
    _shot_map_correction_target.name = "ShotMapCorrectionTarget"
    _shot_map_correction_target.width = 2.0
    _shot_map_correction_target.default_color = SHOT_MAP_TARGET_COLOR
    _shot_map_correction_target.points = _shot_map_target_ring()
    _shot_map_correction_target.visible = false
    _shot_map_correction_target.z_index = SHOT_MAP_TARGET_Z_INDEX
    _v188_panel.add_child(_shot_map_correction_target)
    _v197_promote_shot_indicators()

func _v188_refresh(line_delta_cm: float, pace_delta_cm: float, visible: bool) -> void:
    super._v188_refresh(line_delta_cm, pace_delta_cm, visible)
    if _shot_map_correction_target == null:
        return

    _shot_map_correction_target.visible = false
    if not visible or _v197_inside_make_window(line_delta_cm, pace_delta_cm):
        return

    # Off-scale misses use the inherited short directional cue because radial clipping can make a
    # disconnected mapped destination visually dishonest. Keep that behavior and only show the
    # landing ring when the actual result and destination share the same truthful chart space.
    if _v188_normalized_miss(line_delta_cm, pace_delta_cm).length() > 1.0:
        return

    var target_delta := _v197_correction_target(line_delta_cm, pace_delta_cm)
    _shot_map_correction_target.position = _v188_point(target_delta.x, target_delta.y)
    _shot_map_correction_target.visible = true

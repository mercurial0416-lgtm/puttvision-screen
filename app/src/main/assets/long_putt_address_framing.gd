extends "res://address_relief_camera.gd"

# Presentation-only long-putt framing. Android physics, GreenTerrain, GreenReadAdvisor, aim and scoring
# remain authoritative. This only opens the stationary address composition on long putts so the player
# can read the whole break corridor without the camera feeling glued to the ball.
const LONG_FRAME_START_M := 6.0
const LONG_FRAME_FULL_M := 14.0
const LONG_FRAME_TRAIL_EXTRA_M := 0.52
const LONG_FRAME_HEIGHT_EXTRA_M := 0.14
const LONG_FRAME_LOOK_EXTRA := 0.08
const LONG_FRAME_FOV_EXTRA_DEG := 1.4

func _long_putt_frame_signal(distance_m: float) -> float:
    if not is_finite(distance_m) or distance_m <= LONG_FRAME_START_M:
        return 0.0
    return smoothstep(LONG_FRAME_START_M, LONG_FRAME_FULL_M, distance_m)

func _address_relief_camera_plan(ball_world: Vector3, distance_to_cup: float) -> Dictionary:
    var plan := super._address_relief_camera_plan(ball_world, distance_to_cup)
    var signal := _long_putt_frame_signal(distance_to_cup)
    plan["long_frame_signal"] = signal
    if signal <= 0.0:
        return plan

    var cup_world := target_root.global_position if target_root != null else ball_world + Vector3(0.0, 0.0, -maxf(0.5, distance_to_cup))
    var ball_xz := Vector2(ball_world.x, ball_world.z)
    var cup_xz := Vector2(cup_world.x, cup_world.z)
    var flat_delta := cup_xz - ball_xz
    var flat_length := flat_delta.length()
    if flat_length <= 0.001:
        return plan

    var forward := flat_delta / flat_length
    var position: Vector3 = plan.get("position", ball_world)
    position.x -= forward.x * LONG_FRAME_TRAIL_EXTRA_M * signal
    position.z -= forward.y * LONG_FRAME_TRAIL_EXTRA_M * signal
    position.y += LONG_FRAME_HEIGHT_EXTRA_M * signal
    plan["position"] = position

    # Preserve the inherited relief/cup look target, but let long putts see slightly farther down the
    # corridor. This is bounded well below the short-putt cup framing fraction and never changes aim.
    var look: Vector3 = plan.get("look", cup_world)
    var current_fraction := clampf(float(plan.get("look_fraction", ADDRESS_LOOK_FRACTION)), 0.0, 1.0)
    var desired_fraction := clampf(current_fraction + LONG_FRAME_LOOK_EXTRA * signal, current_fraction, 0.72)
    var desired_xz := ball_xz.lerp(cup_xz, desired_fraction)
    var desired_h := _address_visual_height(_v166_sample(desired_xz.x, -desired_xz.y).x) + ADDRESS_LOOK_LIFT
    look = Vector3(desired_xz.x, desired_h, desired_xz.y)
    plan["look"] = look
    plan["look_fraction"] = desired_fraction
    plan["fov"] = float(plan.get("fov", camera.fov)) + LONG_FRAME_FOV_EXTRA_DEG * signal
    return plan

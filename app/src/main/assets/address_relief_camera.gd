extends "res://read_landmark_spatial_spacing.gd"

# Presentation-only address camera. Android V135-V137, GreenTerrain and GreenReadAdvisor remain
# authoritative. The camera is lowered only while the ball is stationary so real terrain relief
# reads as silhouette/parallax instead of a top-down flat plate. Rolling and replay choreography
# remain owned by the inherited commercial camera stack.
const ADDRESS_CAMERA_HEIGHT := 0.30
const ADDRESS_CAMERA_TRAIL := 1.08
const ADDRESS_CAMERA_SIDE := 0.16
const ADDRESS_LOOK_FRACTION := 0.46
const ADDRESS_LOOK_LIFT := 0.065
const ADDRESS_FOV_NEAR := 42.5
const ADDRESS_FOV_FAR := 38.0

func _address_relief_camera_plan(ball_world: Vector3, distance_to_cup: float) -> Dictionary:
    var cup_world := target_root.global_position if target_root != null else ball_world + Vector3(0.0, 0.0, -maxf(0.5, distance_to_cup))
    var flat_delta := Vector2(cup_world.x - ball_world.x, cup_world.z - ball_world.z)
    var flat_length := flat_delta.length()
    var forward := flat_delta / flat_length if flat_length > 0.001 else Vector2(0.0, -1.0)
    var right := Vector2(-forward.y, forward.x)

    var look_fraction := clampf(ADDRESS_LOOK_FRACTION + flat_length * 0.006, 0.46, 0.56)
    var look_xz := Vector2(ball_world.x, ball_world.z).lerp(Vector2(cup_world.x, cup_world.z), look_fraction)
    var camera_xz := Vector2(ball_world.x, ball_world.z) - forward * ADDRESS_CAMERA_TRAIL + right * ADDRESS_CAMERA_SIDE

    var camera_terrain := _v166_sample(camera_xz.x, -camera_xz.y).x
    var look_terrain := _v166_sample(look_xz.x, -look_xz.y).x
    var desired_pos := Vector3(camera_xz.x, camera_terrain + ADDRESS_CAMERA_HEIGHT, camera_xz.y)
    var desired_look := Vector3(look_xz.x, look_terrain + ADDRESS_LOOK_LIFT, look_xz.y)
    var distance_mix := clampf((flat_length - 2.0) / 8.0, 0.0, 1.0)
    var desired_fov := lerpf(ADDRESS_FOV_NEAR, ADDRESS_FOV_FAR, distance_mix)
    return {"position": desired_pos, "look": desired_look, "fov": desired_fov}

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    # Never fight inherited rolling/cup/replay cameras.
    if running or phase != "NONE" or (_v171_replay_remaining > 0.0 and _v171_replay_actual.size() >= 2):
        super._update_camera(ball_world, running, phase, distance_to_cup, immediate, delta)
        return

    var plan := _address_relief_camera_plan(ball_world, distance_to_cup)
    var desired_pos: Vector3 = plan["position"]
    var desired_look: Vector3 = plan["look"]
    var desired_fov: float = float(plan["fov"])
    var pos_alpha := 1.0 if immediate else 1.0 - exp(-delta * 5.8)
    var look_alpha := 1.0 if immediate else 1.0 - exp(-delta * 6.6)
    camera_pos = camera_pos.lerp(desired_pos, pos_alpha)
    camera_look = camera_look.lerp(desired_look, look_alpha)
    camera.position = camera_pos
    camera.fov = lerpf(camera.fov, desired_fov, 1.0 if immediate else minf(1.0, delta * 5.0))
    camera.look_at(camera_look, Vector3.UP)

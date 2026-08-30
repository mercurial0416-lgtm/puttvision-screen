extends "res://read_landmark_spatial_spacing.gd"

# Presentation-only address camera. Android V135-V137, GreenTerrain and GreenReadAdvisor remain
# authoritative. The camera is lowered only while the ball is stationary so real terrain relief
# reads as silhouette/parallax instead of a top-down flat plate. Rolling and replay choreography
# remain owned by the inherited commercial camera stack.
const ADDRESS_CAMERA_HEIGHT := 0.30
const ADDRESS_CAMERA_TRAIL := 1.08
const ADDRESS_CAMERA_SIDE := 0.16
const ADDRESS_CAMERA_SIDE_ADAPT := 0.18
const ADDRESS_LOOK_FRACTION := 0.46
const ADDRESS_LOOK_LIFT := 0.065
const ADDRESS_FOV_NEAR := 42.5
const ADDRESS_FOV_FAR := 38.0
const ADDRESS_RELIEF_SAMPLES := 5
const ADDRESS_RELIEF_FOCUS_BLEND := 0.58

func _address_relief_focus_fraction(ball_xz: Vector2, cup_xz: Vector2) -> float:
    var span := cup_xz - ball_xz
    var span_len := span.length()
    if span_len < 0.25:
        return ADDRESS_LOOK_FRACTION

    var ball_h := _v166_sample(ball_xz.x, -ball_xz.y).x
    var cup_h := _v166_sample(cup_xz.x, -cup_xz.y).x
    var best_fraction := ADDRESS_LOOK_FRACTION
    var best_relief := 0.0
    for i in range(1, ADDRESS_RELIEF_SAMPLES):
        var fraction := float(i) / float(ADDRESS_RELIEF_SAMPLES)
        var point := ball_xz.lerp(cup_xz, fraction)
        var terrain_h := _v166_sample(point.x, -point.y).x
        var chord_h := lerpf(ball_h, cup_h, fraction)
        var relief := absf(terrain_h - chord_h)
        if relief > best_relief:
            best_relief = relief
            best_fraction = fraction

    var adaptive_weight := smoothstep(0.018, 0.090, best_relief) * ADDRESS_RELIEF_FOCUS_BLEND
    return lerpf(ADDRESS_LOOK_FRACTION, clampf(best_fraction, 0.30, 0.72), adaptive_weight)

func _address_adaptive_side_offset(look_xz: Vector2, right: Vector2) -> float:
    var terrain := _v166_sample(look_xz.x, -look_xz.y)
    # Android slope components map to Godot X/-Z. Move the eye slightly toward the uphill side of
    # the cross-slope so the downhill face exposes more silhouette/parallax instead of aligning
    # edge-on with the fixed camera axis. Bounded to avoid a noticeable orbit or motion sickness.
    var downhill := Vector2(terrain.y, -terrain.z)
    var cross_component := downhill.dot(right)
    var cross_signal := smoothstep(0.35, 2.20, absf(cross_component))
    var adaptive := -signf(cross_component) * ADDRESS_CAMERA_SIDE_ADAPT * cross_signal
    return ADDRESS_CAMERA_SIDE + adaptive

func _address_relief_camera_plan(ball_world: Vector3, distance_to_cup: float) -> Dictionary:
    var cup_world := target_root.global_position if target_root != null else ball_world + Vector3(0.0, 0.0, -maxf(0.5, distance_to_cup))
    var ball_xz := Vector2(ball_world.x, ball_world.z)
    var cup_xz := Vector2(cup_world.x, cup_world.z)
    var flat_delta := cup_xz - ball_xz
    var flat_length := flat_delta.length()
    var forward := flat_delta / flat_length if flat_length > 0.001 else Vector2(0.0, -1.0)
    var right := Vector2(-forward.y, forward.x)

    var baseline_fraction := clampf(ADDRESS_LOOK_FRACTION + flat_length * 0.006, 0.46, 0.56)
    var relief_fraction := _address_relief_focus_fraction(ball_xz, cup_xz)
    var look_fraction := clampf(lerpf(baseline_fraction, relief_fraction, 0.72), 0.34, 0.68)
    var look_xz := ball_xz.lerp(cup_xz, look_fraction)
    var side_offset := _address_adaptive_side_offset(look_xz, right)
    var camera_xz := ball_xz - forward * ADDRESS_CAMERA_TRAIL + right * side_offset

    var camera_terrain := _v166_sample(camera_xz.x, -camera_xz.y).x
    var look_terrain := _v166_sample(look_xz.x, -look_xz.y).x
    var desired_pos := Vector3(camera_xz.x, camera_terrain + ADDRESS_CAMERA_HEIGHT, camera_xz.y)
    var desired_look := Vector3(look_xz.x, look_terrain + ADDRESS_LOOK_LIFT, look_xz.y)
    var distance_mix := clampf((flat_length - 2.0) / 8.0, 0.0, 1.0)
    var desired_fov := lerpf(ADDRESS_FOV_NEAR, ADDRESS_FOV_FAR, distance_mix)
    return {"position": desired_pos, "look": desired_look, "fov": desired_fov, "look_fraction": look_fraction, "side_offset": side_offset}

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

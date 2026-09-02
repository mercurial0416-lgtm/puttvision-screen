extends "res://v174_broadcast_hud.gd"

# V175 cinematic shot replay. Presentation-only: Android V135-V137 / GreenTerrain /
# GreenReadAdvisor stay authoritative. This layer turns the existing V171 trail replay into a
# broadcast-style moving camera with explicit replay progress and shot-trace labeling.

const V175_REPLAY_TRACK_WIDTH := 634.0
const V175_HEADING_SAMPLE_M := 0.18
const V175_HEADING_WIDE_SAMPLE_M := 0.42
const V175_FOV_RESPONSE := 4.8

var _v175_replay_panel: Panel
var _v175_replay_title: Label
var _v175_replay_detail: Label
var _v175_replay_track: ColorRect
var _v175_replay_fill: ColorRect
var _v175_replay_marker: ColorRect

func _build_hud() -> void:
    super._build_hud()

    var layer := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if layer == null:
        return
    var root := layer.get_node_or_null("V174HUDRoot") as Control
    if root == null:
        return

    _v175_replay_panel = _v174_panel(root, Vector2(620, 30), Vector2(680, 90), Color(0.018, 0.025, 0.029, 0.82), Color(0.90, 0.78, 0.40, 0.22), 14)
    _v175_replay_panel.name = "V175ReplayPackage"
    _v175_replay_panel.visible = false
    _v174_accent(_v175_replay_panel, Vector2(0, 0), Vector2(6, 90), Color("#d6b85c"))
    _v175_replay_title = _v174_text(_v175_replay_panel, Vector2(22, 8), Vector2(240, 28), "SHOT TRACE REPLAY", 15, Color("#f4dda0"))
    _v175_replay_detail = _v174_text(_v175_replay_panel, Vector2(420, 8), Vector2(236, 28), "ACTUAL BALL LINE", 13, Color(0.78, 0.82, 0.78, 0.94), HORIZONTAL_ALIGNMENT_RIGHT)

    _v175_replay_track = ColorRect.new()
    _v175_replay_track.position = Vector2(22, 52)
    _v175_replay_track.size = Vector2(V175_REPLAY_TRACK_WIDTH, 6)
    _v175_replay_track.color = Color(0.82, 0.85, 0.80, 0.16)
    _v175_replay_track.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v175_replay_panel.add_child(_v175_replay_track)

    _v175_replay_fill = ColorRect.new()
    _v175_replay_fill.position = Vector2(22, 52)
    _v175_replay_fill.size = Vector2(0, 6)
    _v175_replay_fill.color = Color("#d6b85c")
    _v175_replay_fill.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v175_replay_panel.add_child(_v175_replay_fill)

    _v175_replay_marker = ColorRect.new()
    _v175_replay_marker.position = Vector2(22, 47)
    _v175_replay_marker.size = Vector2(3, 16)
    _v175_replay_marker.color = Color("#fff0b8")
    _v175_replay_marker.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v175_replay_panel.add_child(_v175_replay_marker)

    _v174_text(_v175_replay_panel, Vector2(22, 62), Vector2(120, 18), "START", 11, Color(0.70, 0.74, 0.71, 0.86))
    _v174_text(_v175_replay_panel, Vector2(536, 62), Vector2(120, 18), "FINISH", 11, Color(0.70, 0.74, 0.71, 0.86), HORIZONTAL_ALIGNMENT_RIGHT)

func _v175_progress_from_times(remaining: float, duration: float) -> float:
    if not is_finite(remaining) or not is_finite(duration):
        return 0.0
    if duration <= 0.001:
        return 1.0
    return clampf(1.0 - maxf(0.0, remaining) / duration, 0.0, 1.0)

func _v175_replay_progress() -> float:
    return _v175_progress_from_times(_v171_replay_remaining, _v171_replay_duration)

func _v175_replay_track_fill_width(progress: float) -> float:
    if not is_finite(progress):
        return 0.0
    return V175_REPLAY_TRACK_WIDTH * clampf(progress, 0.0, 1.0)

func _v175_trail_total_length(points: Array) -> float:
    if points.size() < 2:
        return 0.0
    var total := 0.0
    for i in range(1, points.size()):
        total += (points[i - 1] as Vector2).distance_to(points[i] as Vector2)
    return total

func _v175_trail_point(points: Array, progress: float) -> Vector2:
    if points.is_empty():
        return Vector2.ZERO
    if points.size() == 1:
        return points[0] as Vector2

    # Android trail samples are not guaranteed to be evenly spaced in world distance. Walking the
    # polyline by sample index made a constant replay clock visibly surge through sparse sections
    # and crawl through dense ones. Map replay progress to accumulated physical trail distance so
    # camera motion remains broadcast-smooth without changing any authoritative shot coordinates.
    var total_length := _v175_trail_total_length(points)
    if total_length <= 0.000001:
        return points[0] as Vector2
    var target_distance := clampf(progress, 0.0, 1.0) * total_length
    var walked := 0.0
    for i in range(1, points.size()):
        var a := points[i - 1] as Vector2
        var b := points[i] as Vector2
        var segment_length := a.distance_to(b)
        if segment_length <= 0.000001:
            continue
        if target_distance <= walked + segment_length or i == points.size() - 1:
            var local_t := clampf((target_distance - walked) / segment_length, 0.0, 1.0)
            return a.lerp(b, local_t)
        walked += segment_length
    return points[points.size() - 1] as Vector2

func _v175_heading_vector(points: Array, progress: float, sample_m: float, total_length: float) -> Vector2:
    var sample_progress := minf(0.22, sample_m / total_length)
    var ahead := _v175_trail_point(points, min(1.0, progress + sample_progress))
    var behind := _v175_trail_point(points, max(0.0, progress - sample_progress))
    return ahead - behind

func _v175_trail_heading(points: Array, progress: float) -> Vector2:
    if points.size() < 2:
        return Vector2(0.0, 1.0)
    var total_length := _v175_trail_total_length(points)
    if total_length <= 0.000001:
        return Vector2(0.0, 1.0)

    # A single short tangent reacts too aggressively to tiny polygon corners in the recorded trail,
    # which can make the replay rig visibly snap its yaw even though the ball path itself is smooth.
    # Blend a local tangent with a wider physical-distance tangent so the camera anticipates curvature
    # without rewriting or simplifying any authoritative Android trail samples.
    var near_heading := _v175_heading_vector(points, progress, V175_HEADING_SAMPLE_M, total_length)
    var wide_heading := _v175_heading_vector(points, progress, V175_HEADING_WIDE_SAMPLE_M, total_length)
    var heading := near_heading * 0.68 + wide_heading * 0.32
    if heading.length_squared() < 0.000001:
        heading = wide_heading
    if heading.length_squared() < 0.000001:
        heading = (points[points.size() - 1] as Vector2) - (points[0] as Vector2)
    return heading.normalized() if heading.length_squared() > 0.000001 else Vector2(0.0, 1.0)

func _process(delta: float) -> void:
    super._process(delta)
    if _v175_replay_panel == null:
        return

    var active: bool = _v171_replay_remaining > 0.0 and _v171_replay_actual.size() >= 2
    _v175_replay_panel.visible = active
    if not active:
        return

    var progress: float = _v175_replay_progress()
    # Keep the HUD on the real replay clock. Camera easing below is intentional choreography,
    # but easing the progress bar made its position disagree with the numeric percentage.
    var width: float = _v175_replay_track_fill_width(progress)
    _v175_replay_fill.size.x = width
    _v175_replay_marker.position.x = 22.0 + maxf(0.0, width - 1.5)
    _v175_replay_detail.text = "%3d%%  •  ACTUAL BALL LINE" % int(round(progress * 100.0))

func _v175_fov_damping_alpha(delta: float) -> float:
    if not is_finite(delta) or delta <= 0.0:
        return 0.0
    return 1.0 - exp(-delta * V175_FOV_RESPONSE)

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    if _v171_replay_remaining <= 0.0 or _v171_replay_actual.size() < 2:
        super._update_camera(ball_world, running, phase, distance_to_cup, immediate, delta)
        return

    # Track the trail on the exact replay clock. Applying smoothstep to path progress made the
    # camera lag the actual replay early, rush through the middle and lead it late. Keep cinematic
    # easing only on rig distance/FOV so the shot trace and camera remain temporally locked.
    var progress: float = _v175_replay_progress()
    var choreography: float = smoothstep(0.0, 1.0, progress)
    var current := _v175_trail_point(_v171_replay_actual, progress)
    var heading := _v175_trail_heading(_v171_replay_actual, progress)
    var side := Vector2(-heading.y, heading.x)

    # Stay slightly behind and outside the rolling line so the cup/trace remains readable.
    var look2 := current + heading * (0.34 + 0.28 * choreography)
    var cam2 := current - heading * (1.55 + 0.55 * choreography) + side * 0.58
    var look_y: float = _v166_sample(look2.x, look2.y).x + 0.055
    var cam_y: float = _v166_sample(cam2.x, cam2.y).x + 0.72 + 0.42 * choreography
    var desired_look := Vector3(look2.x, look_y, -look2.y)
    var desired_pos := Vector3(cam2.x, cam_y, -cam2.y)

    # Blend harder than live chase, but never teleport between sparse Android trail samples.
    var pos_alpha: float = 1.0 if immediate else 1.0 - exp(-delta * 5.4)
    var look_alpha: float = 1.0 if immediate else 1.0 - exp(-delta * 6.4)
    camera_pos = camera_pos.lerp(desired_pos, pos_alpha)
    camera_look = camera_look.lerp(desired_look, look_alpha)
    camera.position = camera_pos
    var target_fov: float = lerp(41.0, 35.5, choreography)
    camera.fov = lerp(camera.fov, target_fov, 1.0 if immediate else _v175_fov_damping_alpha(delta))
    camera.look_at(camera_look, Vector3.UP)

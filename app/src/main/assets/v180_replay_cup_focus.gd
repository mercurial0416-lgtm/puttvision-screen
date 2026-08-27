extends "res://v179_session_dispersion.gd"

# Presentation-only replay finish. Android physics / terrain / advisor remain authoritative.
# The final replay beat eases from trail chase into a low cup-side camera so near-misses and
# makes read like a commercial broadcast without altering shot state.

var _v180_focus_chip: Panel
var _v180_focus_title: Label
var _v180_focus_distance: Label

const V180_FOCUS_START := 0.72
const V180_FOCUS_FULL := 0.90

func _build_hud() -> void:
    super._build_hud()
    var layer := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if layer == null:
        return
    var root := layer.get_node_or_null("V174HUDRoot") as Control
    if root == null:
        return

    _v180_focus_chip = _v174_panel(root, Vector2(1320, 138), Vector2(270, 72), Color(0.014, 0.020, 0.024, 0.86), Color(0.90, 0.78, 0.40, 0.22), 12)
    _v180_focus_chip.name = "ReplayCupFocus"
    _v180_focus_chip.visible = false
    _v174_accent(_v180_focus_chip, Vector2(0, 0), Vector2(5, 72), Color("#d6b85c"))
    _v180_focus_title = _v174_text(_v180_focus_chip, Vector2(18, 8), Vector2(232, 22), "CUP FOCUS", 13, Color("#f4dda0"))
    _v180_focus_distance = _v174_text(_v180_focus_chip, Vector2(18, 34), Vector2(232, 24), "FINAL APPROACH", 14, Color(0.82, 0.87, 0.84, 0.96), HORIZONTAL_ALIGNMENT_RIGHT)

func _v180_focus_amount(progress: float) -> float:
    return smoothstep(V180_FOCUS_START, V180_FOCUS_FULL, clampf(progress, 0.0, 1.0))

func _v180_final_point() -> Vector2:
    if _v171_replay_actual.is_empty():
        return Vector2.ZERO
    return _v171_replay_actual[_v171_replay_actual.size() - 1] as Vector2

func _v180_cup_point() -> Vector2:
    if target_root != null:
        var p: Vector3 = target_root.global_position
        return Vector2(p.x, -p.z)
    return _v180_final_point()

func _v180_distance_to_cup_cm(progress: float) -> float:
    if _v171_replay_actual.size() < 2:
        return 0.0
    var current := _v175_trail_point(_v171_replay_actual, smoothstep(0.0, 1.0, progress))
    return current.distance_to(_v180_cup_point()) * 100.0

func _process(delta: float) -> void:
    super._process(delta)
    if _v180_focus_chip == null:
        return
    var active := _v171_replay_remaining > 0.0 and _v171_replay_actual.size() >= 2
    if not active:
        _v180_focus_chip.visible = false
        return
    var progress := _v175_replay_progress()
    var focus := _v180_focus_amount(progress)
    _v180_focus_chip.visible = focus > 0.02
    if _v180_focus_chip.visible:
        var cm := _v180_distance_to_cup_cm(progress)
        _v180_focus_distance.text = "%d cm TO CUP" % int(round(cm)) if cm < 100.0 else "%.1f m TO CUP" % (cm / 100.0)
        _v180_focus_chip.modulate.a = 0.55 + 0.45 * focus

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    if _v171_replay_remaining <= 0.0 or _v171_replay_actual.size() < 2:
        super._update_camera(ball_world, running, phase, distance_to_cup, immediate, delta)
        return

    # First obtain the proven trail-follow camera target from the previous layer.
    super._update_camera(ball_world, running, phase, distance_to_cup, immediate, delta)

    var progress := _v175_replay_progress()
    var focus := _v180_focus_amount(progress)
    if focus <= 0.001:
        return

    var cup2 := _v180_cup_point()
    var final_heading := _v175_trail_heading(_v171_replay_actual, 0.965)
    var side := Vector2(-final_heading.y, final_heading.x)
    var approach_side := side * 0.82
    var cup_cam2 := cup2 + final_heading * 0.76 + approach_side
    var cup_h := _v166_sample(cup2.x, cup2.y).x
    var cam_h := _v166_sample(cup_cam2.x, cup_cam2.y).x
    var cup_look := Vector3(cup2.x, cup_h + 0.045, -cup2.y)
    var cup_pos := Vector3(cup_cam2.x, cam_h + 0.48, -cup_cam2.y)

    # Smoothly hand off to the cup camera; no teleport and no effect on shot physics.
    var blend := focus * focus * (3.0 - 2.0 * focus)
    var desired_pos := camera_pos.lerp(cup_pos, blend)
    var desired_look := camera_look.lerp(cup_look, blend)
    var alpha := 1.0 if immediate else 1.0 - exp(-delta * 7.2)
    camera_pos = camera_pos.lerp(desired_pos, alpha)
    camera_look = camera_look.lerp(desired_look, alpha)
    camera.position = camera_pos
    camera.fov = lerp(camera.fov, 30.5, (1.0 if immediate else min(1.0, delta * 5.5)) * blend)
    camera.look_at(camera_look, Vector3.UP)

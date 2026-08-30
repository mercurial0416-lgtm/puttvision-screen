extends "res://v179_session_dispersion.gd"

# Presentation-only replay finish. Android physics / terrain / advisor remain authoritative.
# The final replay beat eases from trail chase into a low cup-side camera so near-misses and
# makes read like a commercial broadcast without altering shot state.
# Replay also summarizes actual-vs-advisor path deviation once per replay, making misses easier
# to diagnose without adding per-frame geometry analysis or changing the authoritative solver.

var _v180_focus_chip: Panel
var _v180_focus_title: Label
var _v180_focus_distance: Label
var _v180_compare_chip: Panel
var _v180_compare_title: Label
var _v180_compare_primary: Label
var _v180_compare_secondary: Label
var _v180_compare_was_active := false

const V180_FOCUS_START := 0.72
const V180_FOCUS_FULL := 0.90
const V180_COMPARE_SAMPLES := 20
const V180_FINISH_DEADBAND_CM := 2.0
const V180_CAMERA_SIDE_DEADBAND_M := 0.04

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

    _v180_compare_chip = _v174_panel(root, Vector2(1260, 222), Vector2(330, 112), Color(0.014, 0.020, 0.024, 0.90), Color(0.54, 0.78, 0.86, 0.22), 12)
    _v180_compare_chip.name = "ReplayReadCompare"
    _v180_compare_chip.visible = false
    _v174_accent(_v180_compare_chip, Vector2(0, 0), Vector2(5, 112), Color("#73c2d4"))
    _v180_compare_title = _v174_text(_v180_compare_chip, Vector2(18, 9), Vector2(292, 20), "READ vs ROLL", 13, Color("#bfe9f1"))
    _v180_compare_primary = _v174_text(_v180_compare_chip, Vector2(18, 38), Vector2(292, 26), "PATH DEV --", 18, Color(0.94, 0.96, 0.95, 0.98), HORIZONTAL_ALIGNMENT_RIGHT)
    _v180_compare_secondary = _v174_text(_v180_compare_chip, Vector2(18, 72), Vector2(292, 20), "FINISH VERDICT --", 12, Color(0.65, 0.72, 0.70, 0.92), HORIZONTAL_ALIGNMENT_RIGHT)

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
    # The distance readout describes the replay ball, so sample the same exact replay clock used by
    # the shot trace and trail-follow camera. Easing belongs to camera choreography, not telemetry.
    var current := _v175_trail_point(_v171_replay_actual, clampf(progress, 0.0, 1.0))
    return current.distance_to(_v180_cup_point()) * 100.0

func _v180_cup_camera_side_sign(final_point: Vector2, cup_point: Vector2, final_heading: Vector2) -> float:
    var heading := final_heading.normalized()
    if heading.length_squared() < 0.000001:
        heading = Vector2.UP
    var side := Vector2(-heading.y, heading.x)
    var lateral_m := (final_point - cup_point).dot(side)
    if absf(lateral_m) <= V180_CAMERA_SIDE_DEADBAND_M:
        return 1.0
    # Put the lens on the opposite side of a meaningful final miss. That preserves visual separation
    # between ball, cup and finishing line instead of letting the ball collapse behind the flag/cup.
    return -1.0 if lateral_m > 0.0 else 1.0

func _v180_finish_verdict(actual: Vector2, predicted: Vector2, predicted_heading: Vector2) -> String:
    var heading := predicted_heading.normalized()
    if heading.length_squared() < 0.001:
        heading = Vector2.UP
    var right := Vector2(heading.y, -heading.x)
    var miss := actual - predicted
    var lateral_cm := miss.dot(right) * 100.0
    var pace_cm := miss.dot(heading) * 100.0

    var lateral := "ON LINE"
    if absf(lateral_cm) >= V180_FINISH_DEADBAND_CM:
        lateral = "%d cm %s" % [int(round(absf(lateral_cm))), "RIGHT" if lateral_cm > 0.0 else "LEFT"]

    var pace := "PACE OK"
    if absf(pace_cm) >= V180_FINISH_DEADBAND_CM:
        pace = "%d cm %s" % [int(round(absf(pace_cm))), "LONG" if pace_cm > 0.0 else "SHORT"]
    return "%s  ·  %s" % [lateral, pace]

func _v180_refresh_compare() -> void:
    if _v180_compare_primary == null or _v180_compare_secondary == null:
        return
    if _v171_replay_actual.size() < 2 or _v171_replay_predicted.size() < 2:
        _v180_compare_primary.text = "ACTUAL ROLL"
        _v180_compare_secondary.text = "READ LINE UNAVAILABLE"
        return

    var total_dev := 0.0
    var peak_dev := 0.0
    for i in range(V180_COMPARE_SAMPLES + 1):
        var t := float(i) / float(V180_COMPARE_SAMPLES)
        var actual := _v175_trail_point(_v171_replay_actual, t)
        var predicted := _v175_trail_point(_v171_replay_predicted, t)
        var deviation := actual.distance_to(predicted)
        total_dev += deviation
        peak_dev = max(peak_dev, deviation)
    var avg_cm := total_dev / float(V180_COMPARE_SAMPLES + 1) * 100.0
    var peak_cm := peak_dev * 100.0
    var actual_finish := _v171_replay_actual[_v171_replay_actual.size() - 1] as Vector2
    var predicted_finish := _v171_replay_predicted[_v171_replay_predicted.size() - 1] as Vector2
    var predicted_heading := _v175_trail_heading(_v171_replay_predicted, 0.965)

    _v180_compare_primary.text = "AVG %d cm  ·  PEAK %d cm" % [int(round(avg_cm)), int(round(peak_cm))]
    _v180_compare_secondary.text = _v180_finish_verdict(actual_finish, predicted_finish, predicted_heading)

func _process(delta: float) -> void:
    super._process(delta)
    if _v180_focus_chip == null:
        return
    var active := _v171_replay_remaining > 0.0 and _v171_replay_actual.size() >= 2
    if active and not _v180_compare_was_active:
        _v180_refresh_compare()
    _v180_compare_was_active = active
    if _v180_compare_chip != null:
        _v180_compare_chip.visible = active
        if active:
            var reveal := smoothstep(0.04, 0.18, _v175_replay_progress())
            _v180_compare_chip.modulate.a = 0.38 + 0.62 * reveal
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
    var side_sign := _v180_cup_camera_side_sign(_v180_final_point(), cup2, final_heading)
    var approach_side := side * 0.82 * side_sign
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

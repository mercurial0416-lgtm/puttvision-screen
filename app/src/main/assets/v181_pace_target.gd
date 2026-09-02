extends "res://v180_replay_cup_focus.gd"

# Presentation-only practice pace cue. Android physics / GreenTerrain / GreenReadAdvisor remain authoritative.
# The cue never feeds values back into shot motion; it converts the existing distance/grade/green-speed
# snapshot into a stable address-phase reference and compares it with measured launch telemetry.

var _v181_panel: Panel
var _v181_title: Label
var _v181_target: Label
var _v181_actual: Label
var _v181_grade: Label
var _v181_fill: ColorRect
var _v181_marker: ColorRect
var _v181_preview_force_visible := false
var _v181_launch_speed := 0.0
var _v181_was_running := false

const V181_BAR_W := 238.0
const V181_LAUNCH_LOCK_MIN_MPS := 0.03
const V181_ON_PACE_LOW_RATIO := 0.92
const V181_ON_PACE_HIGH_RATIO := 1.08

func _v181_target_speed(distance_m: float, grade_pct: float, green_speed: float) -> float:
    var d: float = clampf(distance_m, 0.3, 15.0)
    var stimp: float = clampf(green_speed, 1.5, 5.5)
    var base: float = 0.58 + sqrt(d) * 0.66
    var grade_factor: float = clampf(1.0 + grade_pct * 0.035, 0.72, 1.35)
    var green_factor: float = clampf(3.0 / stimp, 0.68, 1.42)
    return clampf(base * grade_factor * green_factor, 0.55, 4.8)

func _v181_grade_text(grade_pct: float) -> String:
    if abs(grade_pct) < 0.08:
        return "LEVEL"
    return "%s %.1f%%" % [("UP" if grade_pct > 0.0 else "DOWN"), abs(grade_pct)]

func _v181_match_pct(actual: float, target: float) -> int:
    if target <= 0.01 or actual <= 0.01:
        return 0
    return int(round(clampf(100.0 - abs(actual - target) / target * 100.0, 0.0, 100.0)))

func _v181_pace_grade(actual: float, target: float) -> String:
    if target <= 0.01 or actual <= 0.01:
        return ""
    var ratio := actual / target
    if ratio < V181_ON_PACE_LOW_RATIO:
        return "SOFT"
    if ratio > V181_ON_PACE_HIGH_RATIO:
        return "FIRM"
    return "ON PACE"

func _v181_capture_launch(running: bool, ball_speed: float) -> void:
    # A stop packet normally reports the settled speed (zero), so the old HUD erased the measured
    # launch exactly when the address-phase panel became visible. Lock the first trustworthy in-roll
    # speed instead and retain it until the next shot begins. Presentation only: no bridge/physics state
    # is mutated, and a delayed zero-speed running packet cannot poison the stored launch value.
    if running and not _v181_was_running:
        _v181_launch_speed = 0.0
    if running and _v181_launch_speed <= V181_LAUNCH_LOCK_MIN_MPS and is_finite(ball_speed) and ball_speed > V181_LAUNCH_LOCK_MIN_MPS:
        _v181_launch_speed = ball_speed
    _v181_was_running = running

func _build_hud() -> void:
    super._build_hud()
    var layer := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if layer == null:
        return
    var root := layer.get_node_or_null("V174HUDRoot") as Control
    if root == null:
        return

    _v181_panel = _v174_panel(root, Vector2(1128, 878), Vector2(470, 142), Color(0.014, 0.020, 0.024, 0.90), Color(0.90, 0.78, 0.40, 0.20), 14)
    _v181_panel.name = "PaceTargetRibbon"
    _v174_accent(_v181_panel, Vector2(0, 0), Vector2(6, 142), Color("#d6b85c"))
    _v181_title = _v174_text(_v181_panel, Vector2(20, 10), Vector2(220, 22), "PACE TARGET", 13, Color("#f4dda0"))
    _v181_grade = _v174_text(_v181_panel, Vector2(248, 10), Vector2(196, 22), "LEVEL", 12, Color(0.70, 0.79, 0.75, 0.95), HORIZONTAL_ALIGNMENT_RIGHT)
    _v181_target = _v174_text(_v181_panel, Vector2(20, 38), Vector2(210, 30), "TARGET 1.8 m/s", 18, Color("#f1f4ef"))
    _v181_actual = _v174_text(_v181_panel, Vector2(222, 40), Vector2(222, 26), "ADDRESS", 13, Color(0.72, 0.82, 0.78, 0.95), HORIZONTAL_ALIGNMENT_RIGHT)

    var track := ColorRect.new()
    track.position = Vector2(20, 84)
    track.size = Vector2(V181_BAR_W, 8)
    track.color = Color(0.78, 0.83, 0.79, 0.13)
    track.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v181_panel.add_child(track)

    _v181_fill = ColorRect.new()
    _v181_fill.position = Vector2(20, 84)
    _v181_fill.size = Vector2(0, 8)
    _v181_fill.color = Color("#76d7b6")
    _v181_fill.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v181_panel.add_child(_v181_fill)

    _v181_marker = ColorRect.new()
    _v181_marker.position = Vector2(20, 78)
    _v181_marker.size = Vector2(3, 20)
    _v181_marker.color = Color("#f4dda0")
    _v181_marker.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v181_panel.add_child(_v181_marker)

    _v174_text(_v181_panel, Vector2(20, 104), Vector2(424, 22), "gold = target  •  green = measured launch", 11, Color(0.53, 0.62, 0.59, 0.90))

func _v181_update(s: Dictionary, force_visible: bool = false) -> void:
    if _v181_panel == null:
        return
    if force_visible:
        _v181_preview_force_visible = true

    var running: bool = bool(s.get("running", false))
    var sampled_speed: float = float(s.get("ballSpeed", 0.0))
    _v181_capture_launch(running, sampled_speed)

    var replaying: bool = _v171_replay_remaining > 0.0
    var show: bool = _v181_preview_force_visible or (not replaying and not running)
    _v181_panel.visible = show
    if not show:
        return

    var distance_m: float = maxf(0.3, float(s.get("distanceToCup", 3.0)))
    var grade_pct: float = float(s.get("longSlopePct", s.get("slopeLongPct", 0.0)))
    var green_speed: float = float(s.get("greenSpeed", 3.0))
    var target: float = _v181_target_speed(distance_m, grade_pct, green_speed)

    _v181_target.text = "TARGET %.1f m/s" % target
    _v181_grade.text = _v181_grade_text(grade_pct)
    var target_x: float = 20.0 + V181_BAR_W * clampf(target / 5.0, 0.0, 1.0)
    _v181_marker.position.x = target_x
    _v181_fill.size.x = V181_BAR_W * clampf(_v181_launch_speed / 5.0, 0.0, 1.0)
    if _v181_launch_speed > V181_LAUNCH_LOCK_MIN_MPS:
        _v181_actual.text = "%.1f m/s · %d%% %s" % [_v181_launch_speed, _v181_match_pct(_v181_launch_speed, target), _v181_pace_grade(_v181_launch_speed, target)]
    else:
        _v181_actual.text = "ADDRESS"

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _v181_update(s)

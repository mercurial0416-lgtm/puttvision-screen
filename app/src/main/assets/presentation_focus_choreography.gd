extends "res://v197_shot_map_make_window.gd"

# Presentation-only focus choreography for commercial TV readability.
# Authoritative Android physics, GreenTerrain and GreenReadAdvisor remain untouched.
# Secondary HUD packages fade by shot phase so attention follows the ball/replay/result
# instead of giving every panel equal visual weight at all times.

const FOCUS_FADE_SPEED := 5.4
const PHASE_READY := "READY"
const PHASE_ROLL := "ROLL"
const PHASE_REPLAY := "REPLAY"
const PHASE_RESULT := "RESULT"

var _focus_phase := PHASE_READY
var _focus_running := false
var _focus_target_card: CanvasItem
var _focus_telemetry_card: CanvasItem
var _focus_break_card: CanvasItem
var _focus_state_card: CanvasItem

func _build_hud() -> void:
    super._build_hud()
    _focus_target_card = distance_label.get_parent() as CanvasItem if distance_label != null else null
    _focus_telemetry_card = speed_label.get_parent() as CanvasItem if speed_label != null else null
    _focus_break_card = _v174_break_value.get_parent() as CanvasItem if _v174_break_value != null else null
    _focus_state_card = _v174_state_label.get_parent() as CanvasItem if _v174_state_label != null else null
    _focus_apply_phase(PHASE_READY, true)

func _focus_phase_for(running: bool, replaying: bool, showing_result: bool) -> String:
    if running:
        return PHASE_ROLL
    if replaying:
        return PHASE_REPLAY
    if showing_result:
        return PHASE_RESULT
    return PHASE_READY

func _focus_role_alpha(phase: String, role: String) -> float:
    match phase:
        PHASE_ROLL:
            match role:
                "target": return 0.72
                "telemetry": return 1.0
                "break": return 0.16
                "state": return 1.0
                "read": return 0.0
                "result": return 0.0
        PHASE_REPLAY:
            match role:
                "target": return 0.40
                "telemetry": return 0.28
                "break": return 0.10
                "state": return 1.0
                "read": return 0.0
                "result": return 0.0
        PHASE_RESULT:
            match role:
                "target": return 0.52
                "telemetry": return 0.40
                "break": return 0.30
                "state": return 0.78
                "read": return 0.0
                "result": return 1.0
        _:
            match role:
                "target": return 1.0
                "telemetry": return 0.92
                "break": return 1.0
                "state": return 1.0
                "read": return 1.0
                "result": return 1.0
    return 1.0

func _focus_set_alpha(item: CanvasItem, target: float, immediate: bool, delta: float = 0.0) -> void:
    if item == null:
        return
    var c := item.modulate
    c.a = target if immediate else move_toward(c.a, target, FOCUS_FADE_SPEED * delta)
    item.modulate = c

func _focus_apply_phase(phase: String, immediate: bool = false, delta: float = 0.0) -> void:
    _focus_phase = phase
    _focus_set_alpha(_focus_target_card, _focus_role_alpha(phase, "target"), immediate, delta)
    _focus_set_alpha(_focus_telemetry_card, _focus_role_alpha(phase, "telemetry"), immediate, delta)
    _focus_set_alpha(_focus_break_card, _focus_role_alpha(phase, "break"), immediate, delta)
    _focus_set_alpha(_focus_state_card, _focus_role_alpha(phase, "state"), immediate, delta)
    _focus_set_alpha(_v176_panel, _focus_role_alpha(phase, "read"), immediate, delta)
    _focus_set_alpha(_v183_panel, _focus_role_alpha(phase, "read"), immediate, delta)
    _focus_set_alpha(_v177_panel, _focus_role_alpha(phase, "result"), immediate, delta)
    _focus_set_alpha(_v188_panel, _focus_role_alpha(phase, "result"), immediate, delta)

func _focus_current_phase() -> String:
    var replaying := _v171_replay_remaining > 0.0
    var showing_result := _v177_panel != null and _v177_panel.visible
    return _focus_phase_for(_focus_running, replaying, showing_result)

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _focus_running = bool(s.get("running", false))

func _process(delta: float) -> void:
    super._process(delta)
    _focus_apply_phase(_focus_current_phase(), false, delta)

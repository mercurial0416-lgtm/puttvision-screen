extends "res://v197_preview.gd"

var _focus_checks_done := false
const FocusScene = preload("res://presentation_focus_choreography.gd")

func _process(delta: float) -> void:
    super._process(delta)
    if _focus_checks_done or _preview_frames < 14:
        return
    _focus_checks_done = true

    var probe = FocusScene.new()
    if probe._focus_phase_for(true, false, false) != "ROLL":
        push_error("Presentation focus roll-phase regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_phase_for(false, true, true) != "REPLAY":
        push_error("Presentation focus replay priority regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_phase_for(false, false, true) != "RESULT":
        push_error("Presentation focus result-phase regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_role_alpha("ROLL", "telemetry") <= probe._focus_role_alpha("ROLL", "break"):
        push_error("Presentation focus no longer prioritizes live ball telemetry")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_role_alpha("REPLAY", "target") >= probe._focus_role_alpha("READY", "target"):
        push_error("Presentation focus replay declutter regression")
        probe.free()
        get_tree().quit(29)
        return
    if probe._focus_role_alpha("RESULT", "result") < 0.99:
        push_error("Presentation focus result package regression")
        probe.free()
        get_tree().quit(29)
        return

    # Render the result-focused hierarchy in the CI screenshot: coaching remains full-strength
    # while setup telemetry recedes. This makes the behavior visually reviewable, not node-only.
    var result_phase := "RESULT"
    var target_card := distance_label.get_parent() as CanvasItem if distance_label != null else null
    var telemetry_card := speed_label.get_parent() as CanvasItem if speed_label != null else null
    var break_card := _v174_break_value.get_parent() as CanvasItem if _v174_break_value != null else null
    var state_card := _v174_state_label.get_parent() as CanvasItem if _v174_state_label != null else null
    _preview_set_alpha(target_card, probe._focus_role_alpha(result_phase, "target"))
    _preview_set_alpha(telemetry_card, probe._focus_role_alpha(result_phase, "telemetry"))
    _preview_set_alpha(break_card, probe._focus_role_alpha(result_phase, "break"))
    _preview_set_alpha(state_card, probe._focus_role_alpha(result_phase, "state"))
    _preview_set_alpha(_v176_panel, probe._focus_role_alpha(result_phase, "read"))
    _preview_set_alpha(_v183_panel, probe._focus_role_alpha(result_phase, "read"))
    _preview_set_alpha(_v177_panel, probe._focus_role_alpha(result_phase, "result"))
    _preview_set_alpha(_v188_panel, probe._focus_role_alpha(result_phase, "result"))
    probe.free()
    print("PRESENTATION_FOCUS_CHOREOGRAPHY_OK=1")

func _preview_set_alpha(item: CanvasItem, alpha: float) -> void:
    if item == null:
        return
    var c := item.modulate
    c.a = alpha
    item.modulate = c

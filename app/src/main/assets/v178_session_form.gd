extends "res://v177_shot_debrief.gd"

# Session-form practice overlay. Presentation-only: authoritative Android physics,
# GreenTerrain and GreenReadAdvisor remain unchanged. This layer retains a tiny local
# rolling history of completed putts so practice quality is readable at a glance.

var _v178_panel: Panel
var _v178_average_label: Label
var _v178_consistency_label: Label
var _v178_attempt_label: Label
var _v178_score_labels: Array[Label] = []
var _v178_scores: Array[int] = []
var _v178_last_signature := ""
var _v178_completion_armed := false
var _v178_completed_shot_serial := 0
var _v178_preview_force_visible := false

const V178_HISTORY := 5

func _v178_average(values: Array[int]) -> float:
    if values.is_empty():
        return 0.0
    var total := 0.0
    for value in values:
        total += float(value)
    return total / float(values.size())

func _v178_consistency(values: Array[int]) -> int:
    if values.size() < 2:
        return 100
    var avg := _v178_average(values)
    var variance := 0.0
    for value in values:
        var delta := float(value) - avg
        variance += delta * delta
    variance /= float(values.size())
    var spread := sqrt(variance)
    return int(round(clamp(100.0 - spread * 2.2, 0.0, 100.0)))

func _v178_signature(s: Dictionary) -> String:
    var trail_variant: Variant = s.get("actualTrail", [])
    var trail_size := 0
    if trail_variant is Array:
        trail_size = (trail_variant as Array).size()
    return "%d|%.2f|%.2f|%.3f|%s|%s" % [
        trail_size,
        float(s.get("readLineDeltaCm", 0.0)),
        float(s.get("paceDeltaCm", 0.0)),
        float(s.get("distanceToCup", 0.0)),
        str(bool(s.get("holed", false))),
        str(bool(s.get("lipOut", false)))
    ]

func _v178_push_score(score: int) -> void:
    _v178_scores.append(clamp(score, 0, 100))
    while _v178_scores.size() > V178_HISTORY:
        _v178_scores.pop_front()

func _build_hud() -> void:
    super._build_hud()

    var layer := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if layer == null:
        return
    var root := layer.get_node_or_null("V174HUDRoot") as Control
    if root == null:
        return

    _v178_panel = _v174_panel(root, Vector2(44, 846), Vector2(560, 170), Color(0.015, 0.022, 0.026, 0.89), Color(0.47, 0.72, 0.64, 0.20), 14)
    _v178_panel.name = "V178SessionForm"
    _v178_panel.visible = false
    _v174_accent(_v178_panel, Vector2(0, 0), Vector2(7, 170), Color("#76d7b6"))

    _v174_text(_v178_panel, Vector2(24, 12), Vector2(230, 24), "SESSION FORM", 14, Color(0.77, 0.84, 0.81, 0.96))
    _v178_attempt_label = _v174_text(_v178_panel, Vector2(340, 10), Vector2(190, 26), "0 PUTTS", 13, Color(0.62, 0.70, 0.67, 0.92), HORIZONTAL_ALIGNMENT_RIGHT)

    _v174_text(_v178_panel, Vector2(24, 48), Vector2(120, 18), "LAST 5", 11, Color(0.56, 0.65, 0.62, 0.92))
    for index in range(V178_HISTORY):
        var label := _v174_text(_v178_panel, Vector2(24 + index * 70, 70), Vector2(58, 44), "--", 21, Color("#e8eee9"), HORIZONTAL_ALIGNMENT_CENTER)
        _v178_score_labels.append(label)

    _v174_text(_v178_panel, Vector2(390, 46), Vector2(140, 18), "AVG", 11, Color(0.56, 0.65, 0.62, 0.92), HORIZONTAL_ALIGNMENT_RIGHT)
    _v178_average_label = _v174_text(_v178_panel, Vector2(390, 64), Vector2(140, 38), "--", 25, Color("#f4dda0"), HORIZONTAL_ALIGNMENT_RIGHT)

    var divider := ColorRect.new()
    divider.position = Vector2(24, 124)
    divider.size = Vector2(506, 1)
    divider.color = Color(0.75, 0.82, 0.78, 0.14)
    divider.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v178_panel.add_child(divider)

    _v174_text(_v178_panel, Vector2(24, 136), Vector2(150, 18), "CONSISTENCY", 11, Color(0.56, 0.65, 0.62, 0.92))
    _v178_consistency_label = _v174_text(_v178_panel, Vector2(350, 130), Vector2(180, 28), "100%", 18, Color("#b9dda6"), HORIZONTAL_ALIGNMENT_RIGHT)

func _v178_refresh() -> void:
    if _v178_panel == null:
        return
    _v178_attempt_label.text = "%d PUTT%s" % [_v178_scores.size(), "" if _v178_scores.size() == 1 else "S"]
    for index in range(V178_HISTORY):
        var label := _v178_score_labels[index]
        if index < _v178_scores.size():
            label.text = "%02d" % _v178_scores[index]
            label.modulate = Color.WHITE
        else:
            label.text = "--"
            label.modulate = Color(0.55, 0.60, 0.58, 0.55)
    if _v178_scores.is_empty():
        _v178_average_label.text = "--"
        _v178_consistency_label.text = "--"
    else:
        _v178_average_label.text = "%02d" % int(round(_v178_average(_v178_scores)))
        _v178_consistency_label.text = "%d%%" % _v178_consistency(_v178_scores)

func _v178_capture_completed_shot(s: Dictionary) -> void:
    var running := bool(s.get("running", false))
    if running:
        # Arm on the live roll rather than relying only on the result payload. Two genuinely
        # separate putts can finish with byte-for-byte identical metrics and must both count.
        _v178_completion_armed = true
        return

    var trail_variant: Variant = s.get("actualTrail", [])
    var has_shot := trail_variant is Array and (trail_variant as Array).size() >= 2
    var has_metrics := s.has("readLineDeltaCm") and s.has("paceDeltaCm")
    if not has_shot or not has_metrics or _v171_replay_remaining > 0.0:
        return

    var signature := _v178_signature(s)
    # Signature dedupe still protects reconnect/static snapshots when no live-roll edge was seen.
    # An armed completion always wins, allowing consecutive identical putts to remain distinct.
    if not _v178_completion_armed and signature == _v178_last_signature:
        return
    _v178_completion_armed = false
    _v178_last_signature = signature
    _v178_completed_shot_serial += 1

    var score := _v177_metric_score(
        float(s.get("readLineDeltaCm", 0.0)),
        float(s.get("paceDeltaCm", 0.0)),
        bool(s.get("holed", false))
    )
    _v178_push_score(score)
    _v178_refresh()

func _v178_update_visibility(s: Dictionary) -> void:
    if _v178_panel == null:
        return
    var running := bool(s.get("running", false))
    var result_phase := _v177_panel != null and _v177_panel.visible
    _v178_panel.visible = _v178_preview_force_visible or (not _v178_scores.is_empty() and not running and _v171_replay_remaining <= 0.0 and result_phase)

func _v178_preview_seed() -> void:
    _v178_scores = [91, 84, 95, 88, 93]
    _v178_preview_force_visible = true
    _v178_refresh()
    if _v178_panel != null:
        _v178_panel.visible = true

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _v178_capture_completed_shot(s)
    _v178_update_visibility(s)

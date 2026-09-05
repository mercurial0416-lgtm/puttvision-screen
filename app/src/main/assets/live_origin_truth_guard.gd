extends "res://practice_recent_center_reticle.gd"

# Presentation-only launch-origin guard. Native Android physics, GreenTerrain and GreenReadAdvisor
# remain authoritative. A malformed or omitted start coordinate must never rotate a valid live roll
# around world origin and fabricate a huge break value in the commercial HUD.
var _live_origin_pending := false

func _live_pair_is_finite(s: Dictionary, x_key: String, y_key: String) -> bool:
    return s.has(x_key) and s.has(y_key) \
        and _presentation_is_finite_number(s.get(x_key)) \
        and _presentation_is_finite_number(s.get(y_key))

func _live_ball_position(s: Dictionary) -> Vector2:
    return Vector2(float(s.get("ballX", 0.0)), float(s.get("ballY", 0.0)))

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    var running := bool(s.get("running", false))
    var starting_roll := running and not _live_curve_was_running

    if starting_roll:
        _live_origin_pending = not _live_pair_is_finite(s, "startX", "startY")

    # When startX/startY are missing, non-numeric, or non-finite, the first real measured ball
    # position is the only truthful presentation origin available. Patch only the Godot copy and
    # update the inherited HUD origin before the base live-break code sees the snapshot. If the first
    # frame has no valid ball sample either, keep TRACKING and resolve on the first finite numeric
    # sample instead of coercing malformed bridge payloads to (0, 0). This never feeds Android
    # physics, aiming, scoring, or read advice.
    if running and _live_origin_pending and _live_pair_is_finite(s, "ballX", "ballY"):
        var ball_pos := _live_ball_position(s)
        _live_curve_origin = ball_pos
        var presentation_snapshot := s.duplicate(false)
        presentation_snapshot["startX"] = ball_pos.x
        presentation_snapshot["startY"] = ball_pos.y
        _live_origin_pending = false
        super._apply_snapshot(presentation_snapshot, immediate, delta)
        return

    super._apply_snapshot(s, immediate, delta)

    if not running:
        _live_origin_pending = false

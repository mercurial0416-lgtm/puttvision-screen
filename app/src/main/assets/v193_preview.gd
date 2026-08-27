extends "res://v192_preview.gd"

var _v193_checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    if _v193_checks_done or _preview_frames < 14:
        return
    _v193_checks_done = true

    if _v193_ghost == null or _v193_ghost_label == null:
        push_error("Best-prior ghost package missing")
        get_tree().quit(24)
        return

    _v179_preview_force_visible = true
    _v179_samples = [
        Vector2(18.0, 40.0),
        Vector2(4.0, 8.0),
        Vector2(12.0, 28.0)
    ]
    _v188_refresh(12.0, 28.0, true)
    var expected := _v188_point(4.0, 8.0)
    if not _v193_ghost.visible or not _v193_ghost_label.visible:
        push_error("Best-prior ghost visibility regression")
        get_tree().quit(24)
        return
    if _v193_ghost.position.distance_to(expected) > 0.2:
        push_error("Best-prior ghost selection regression")
        get_tree().quit(24)
        return
    if _v193_ghost.position.distance_to(_v188_dot.position) < 2.0:
        push_error("Best-prior ghost collapsed onto current rep")
        get_tree().quit(24)
        return

    _v179_samples = [Vector2(3.0, 4.0)]
    _v188_refresh(3.0, 4.0, true)
    if _v193_ghost.visible or _v193_ghost_label.visible:
        push_error("Best-prior ghost single-sample regression")
        get_tree().quit(24)
        return

    _v179_samples = [
        Vector2(18.0, 40.0),
        Vector2(4.0, 8.0),
        Vector2(12.0, 28.0)
    ]
    _v188_refresh(12.0, 28.0, true)
    print("PRACTICE_BEST_REP_GHOST_OK=1")

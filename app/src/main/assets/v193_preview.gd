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

    # Pressure-ladder copy must remain self-contained at TV distance: the active objective and
    # progress counter travel with every success state rather than relying on player memory. v192
    # owns the final production copy, so completion also preserves its next-distance instruction.
    if _v191_copy(1, "LINE") != "PRESSURE LADDER  ·  LINE  ·  1/3  ·  HOLD IT":
        push_error("Pressure ladder LINE progress copy regression")
        get_tree().quit(24)
        return
    if _v191_copy(2, "PACE") != "PRESSURE LADDER  ·  PACE  ·  2/3  ·  ONE MORE":
        push_error("Pressure ladder PACE progress copy regression")
        get_tree().quit(24)
        return
    if _v191_copy(3, "BOTH") != "PRESSURE LADDER  ·  BOTH  ·  3/3  ·  READY  ·  +0.5 m NEXT":
        push_error("Pressure ladder completion copy regression")
        get_tree().quit(24)
        return

    _v179_preview_force_visible = true
    _v191_focus_start_index = 0
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

    _v191_focus_start_index = 0
    _v179_samples = [Vector2(3.0, 4.0)]
    _v188_refresh(3.0, 4.0, true)
    if _v193_ghost.visible or _v193_ghost_label.visible:
        push_error("Best-prior ghost single-sample regression")
        get_tree().quit(24)
        return

    # A rep from a retired focus objective can be numerically closer to center but is not a valid
    # benchmark for the active cue. Only samples captured after the current focus window began may
    # become the ghost, while the newest rep still belongs to the solid SHOT MAP dot.
    _v179_samples = [
        Vector2(1.0, 1.0),
        Vector2(6.0, 12.0),
        Vector2(13.0, 30.0)
    ]
    _v191_focus_start_index = 1
    _v188_refresh(13.0, 30.0, true)
    expected = _v188_point(6.0, 12.0)
    if not _v193_ghost.visible or _v193_ghost.position.distance_to(expected) > 0.2:
        push_error("Best-prior ghost leaked a retired focus sample")
        get_tree().quit(24)
        return

    # Immediately after a focus switch there is no prior rep against the new objective yet. Do not
    # resurrect an old-objective sample merely to keep the ghost visible.
    _v179_samples = [Vector2(1.0, 1.0), Vector2(8.0, 16.0)]
    _v191_focus_start_index = 1
    _v188_refresh(8.0, 16.0, true)
    if _v193_ghost.visible or _v193_ghost_label.visible:
        push_error("Best-prior ghost appeared before a comparable focus rep existed")
        get_tree().quit(24)
        return

    # Full session history must rebase the focus boundary when the rolling buffer evicts old reps.
    # The first rep under the new focus is current-only, so the ghost stays hidden; after the second
    # rep, that first comparable rep becomes BEST PRIOR and retired-focus samples remain excluded.
    _v179_samples = [
        Vector2(1.0, 1.0),
        Vector2(2.0, 2.0),
        Vector2(3.0, 3.0),
        Vector2(4.0, 4.0),
        Vector2(5.0, 5.0)
    ]
    _v191_focus_start_index = V179_HISTORY
    if not _v179_push_sample(16.0, 32.0):
        push_error("Best-prior rollover rejected valid focus sample")
        get_tree().quit(24)
        return
    _v188_refresh(16.0, 32.0, true)
    if _v191_focus_start_index != V179_HISTORY - 1 or not _v191_has_focus_samples():
        push_error("Practice focus boundary did not rebase after history eviction")
        get_tree().quit(24)
        return
    if _v193_ghost.visible or _v193_ghost_label.visible:
        push_error("Best-prior ghost appeared before rollover focus had a prior rep")
        get_tree().quit(24)
        return

    if not _v179_push_sample(7.0, 14.0):
        push_error("Best-prior rollover rejected second focus sample")
        get_tree().quit(24)
        return
    _v188_refresh(7.0, 14.0, true)
    expected = _v188_point(16.0, 32.0)
    if _v191_focus_start_index != V179_HISTORY - 2:
        push_error("Practice focus boundary drifted across repeated history eviction")
        get_tree().quit(24)
        return
    if not _v193_ghost.visible or _v193_ghost.position.distance_to(expected) > 0.2:
        push_error("Best-prior ghost stayed hidden after rollover focus gained a prior rep")
        get_tree().quit(24)
        return

    _v191_focus_start_index = 0
    _v179_samples = [
        Vector2(18.0, 40.0),
        Vector2(4.0, 8.0),
        Vector2(12.0, 28.0)
    ]
    _v188_refresh(12.0, 28.0, true)
    print("PRACTICE_BEST_REP_GHOST_OK=1")

extends "res://v180_replay_cup_focus.gd"

var _preview_frames := 0
var _capture_started := false
var _checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    _preview_frames += 1

    if not _checks_done and _preview_frames >= 10:
        _checks_done = true
        if _v179_panel == null or _v179_points.size() != V179_HISTORY:
            push_error("Session dispersion package missing")
            get_tree().quit(10)
            return
        if _v180_focus_chip == null or _v180_focus_distance == null:
            push_error("Replay cup-focus package missing")
            get_tree().quit(11)
            return
        if _v180_focus_amount(0.70) > 0.001 or _v180_focus_amount(0.96) < 0.99:
            push_error("Replay cup-focus timing regression")
            get_tree().quit(11)
            return

        _v171_replay_actual = [Vector2(0.0, 1.0), Vector2(0.08, 3.0), Vector2(0.12, 5.0), Vector2(0.05, 6.5)]
        _v171_replay_duration = 2.8
        _v171_replay_remaining = 0.20
        var final_point := _v180_final_point()
        if final_point.distance_to(Vector2(0.05, 6.5)) > 0.001:
            push_error("Replay final-point regression")
            get_tree().quit(11)
            return
        _v180_focus_chip.visible = true
        _v180_focus_distance.text = "42 cm TO CUP"
        print("REPLAY_CUP_FOCUS_OK=1")

    if not _capture_started and _preview_frames >= 14:
        _capture_started = true
        _capture_preview.call_deferred()

func _capture_preview() -> void:
    await RenderingServer.frame_post_draw
    var image := get_viewport().get_texture().get_image()
    var output := ProjectSettings.globalize_path("res://v143-preview.png")
    var error := image.save_png(output)
    if error != OK:
        push_error("Preview save failed: %s" % error)
        get_tree().quit(2)
        return
    print("V143_PREVIEW_SAVED=" + output)
    get_tree().quit()

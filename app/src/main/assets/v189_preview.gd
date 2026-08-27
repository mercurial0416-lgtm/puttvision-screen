extends "res://v189_practice_focus.gd"

var _preview_frames := 0
var _capture_started := false
var _checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    _preview_frames += 1
    if not _checks_done and _preview_frames >= 10:
        _checks_done = true

        if _v189_focus_title == null or _v189_focus_value == null or _v189_focus_cue == null or _v189_focus_meter == null:
            push_error("Practice focus coach package missing")
            get_tree().quit(20)
            return

        _v179_samples = [
            Vector2(14.0, 6.0),
            Vector2(12.0, 8.0),
            Vector2(11.0, 4.0),
            Vector2(13.0, 7.0),
            Vector2(10.0, 5.0)
        ]
        _v179_preview_force_visible = true
        _v179_refresh()
        if _v189_focus_title.text != "START LINE" or not _v189_focus_value.text.contains("RIGHT BIAS"):
            push_error("Practice focus line-priority regression")
            get_tree().quit(20)
            return
        if not _v189_focus_cue.text.contains("REMOVE RIGHT BIAS") or _v189_focus_meter.size.x <= 0.0:
            push_error("Practice focus correction cue regression")
            get_tree().quit(20)
            return

        _v179_samples = [
            Vector2(1.0, -36.0),
            Vector2(-1.0, -34.0),
            Vector2(2.0, -31.0),
            Vector2(0.0, -39.0),
            Vector2(1.0, -35.0)
        ]
        _v179_refresh()
        if _v189_focus_title.text != "PACE" or not _v189_focus_value.text.contains("SHORT") or not _v189_focus_cue.text.contains("ADD PACE"):
            push_error("Practice focus pace-priority regression")
            get_tree().quit(20)
            return

        _v179_samples = [
            Vector2(-8.0, -18.0),
            Vector2(-3.0, 10.0),
            Vector2(5.0, 22.0),
            Vector2(7.0, 6.0),
            Vector2(3.0, 14.0)
        ]
        _v179_refresh()
        if _v179_panel != null:
            _v179_panel.visible = true
        print("PRACTICE_FOCUS_COACH_OK=1")

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

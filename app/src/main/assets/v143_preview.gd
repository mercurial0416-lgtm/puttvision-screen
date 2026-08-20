extends "res://v143_tv.gd"

var _preview_frames := 0
var _capture_started := false

func _process(delta: float) -> void:
    super._process(delta)
    _preview_frames += 1
    if !_capture_started and _preview_frames >= 12:
        _capture_started = true
        _capture_preview.call_deferred()

func _capture_preview() -> void:
    await RenderingServer.frame_post_draw
    var image := get_viewport().get_texture().get_image()
    var output := ProjectSettings.globalize_path("res://v143-preview.png")
    var error := image.save_png(output)
    if error != OK:
        push_error("V143 preview save failed: %s" % error)
        get_tree().quit(2)
        return
    print("V143_PREVIEW_SAVED=" + output)
    get_tree().quit()

extends "res://v186_cup_entry.gd"

var _preview_frames := 0
var _capture_started := false
var _checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    _preview_frames += 1
    if not _checks_done and _preview_frames >= 10:
        _checks_done = true
        if _v185_pace_label == null or _v185_pace_marker == null:
            push_error("Pace-intent package missing")
            get_tree().quit(16)
            return
        if _v186_entry_ring == null or _v186_entry_label == null:
            push_error("Cup-entry package missing")
            get_tree().quit(17)
            return

        var short_flat := _v186_entry_band(1.5, 0.0)
        var long_uphill := _v186_entry_band(8.0, -1.8)
        if long_uphill.x <= short_flat.x or long_uphill.y <= short_flat.y:
            push_error("Cup-entry target monotonic regression")
            get_tree().quit(17)
            return
        if short_flat.x < 0.34 or long_uphill.y > 1.06:
            push_error("Cup-entry band bounds regression")
            get_tree().quit(17)
            return

        _v165_recommended_offset = 0.42
        _v183_update({"distanceToCup": 5.4, "sideSlope": 1.65, "longSlope": -0.72, "running": false}, true)
        if not _v183_panel.visible or not _v186_entry_ring.visible or _v186_entry_ring.points.size() != 33:
            push_error("Cup-entry live binding regression")
            get_tree().quit(17)
            return
        if not _v186_entry_label.text.begins_with("ENTRY") or _v186_entry_low >= _v186_entry_high:
            push_error("Cup-entry label regression")
            get_tree().quit(17)
            return
        var cup_center := _v183_path_line.points[_v183_path_line.points.size() - 1]
        var radial := _v186_entry_ring.points[0].distance_to(cup_center)
        if radial < 9.5 or radial > 18.5:
            push_error("Cup-entry ring geometry regression")
            get_tree().quit(17)
            return
        print("CUP_ENTRY_WINDOW_OK=1")

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

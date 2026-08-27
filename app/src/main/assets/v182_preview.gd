extends "res://v182_break_flow.gd"

var _preview_frames := 0
var _capture_started := false
var _checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    _preview_frames += 1
    if not _checks_done and _preview_frames >= 10:
        _checks_done = true
        if _v182_beads.size() != V182_BEAD_COUNT:
            push_error("Break flow bead count regression")
            get_tree().quit(13)
            return
        _v182_side_pct = 1.8
        _v182_last_offset = 0.32
        if _v176_panel != null:
            _v176_panel.visible = true
        _v182_update_flow(0.12)
        var first := _v182_beads[0].position
        _v182_update_flow(0.35)
        var second := _v182_beads[0].position
        if first.distance_to(second) < 0.5 or not _v182_beads[0].visible:
            push_error("Break flow animation regression")
            get_tree().quit(13)
            return
        print("BREAK_FLOW_BEADS_OK=1")
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

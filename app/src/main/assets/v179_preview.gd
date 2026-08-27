extends "res://v143_preview.gd"

var _v179_checked := false

func _process(delta: float) -> void:
    super._process(delta)
    if not _v179_checked and _preview_frames >= 10:
        _v179_checked = true
        if _v179_panel == null or _v179_points.size() != V179_HISTORY:
            push_error("V179 dispersion package missing")
            get_tree().quit(10)
            return
        _v179_samples.clear()
        for sample in [Vector2(-12, -35), Vector2(-4, -10), Vector2(0, 0), Vector2(8, 24), Vector2(14, 42), Vector2(5, 15)]:
            _v179_push_sample(sample.x, sample.y)
        if _v179_samples.size() != V179_HISTORY or _v179_samples[0] != Vector2(-4, -10) or _v179_samples[4] != Vector2(5, 15):
            push_error("V179 rolling dispersion regression: %s" % _v179_samples)
            get_tree().quit(10)
            return
        var center := _v179_plot_position(Vector2.ZERO)
        if center.distance_to(V179_PLOT_SIZE * 0.5) > 0.001:
            push_error("V179 plot center regression: %s" % center)
            get_tree().quit(10)
            return
        if _v179_plot_position(Vector2(20, 0)).x <= center.x or _v179_plot_position(Vector2(0, 40)).y >= center.y:
            push_error("V179 plot axis direction regression")
            get_tree().quit(10)
            return
        _v179_preview_seed()
        if not _v179_panel.visible or _v179_tendency_label.text.is_empty() or not _v179_points[0].visible:
            push_error("V179 preview binding regression")
            get_tree().quit(10)
            return
        print("V179_SESSION_DISPERSION_OK=1")

extends "res://v193_preview.gd"

var _v194_checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    if _v194_checks_done or _preview_frames < 15:
        return
    _v194_checks_done = true

    if _v194_envelope == null or _v194_centroid == null or _v194_bias_line == null or _v194_bias_arrow == null or _v194_spread_label == null:
        push_error("Session grouping presentation package missing")
        get_tree().quit(25)
        return

    _v179_preview_force_visible = true
    _v179_samples = [
        Vector2(-8.0, -18.0),
        Vector2(-3.0, 10.0),
        Vector2(5.0, 22.0),
        Vector2(7.0, 6.0),
        Vector2(3.0, 14.0)
    ]
    _v188_refresh(3.0, 14.0, true)
    _v179_refresh()

    var mean := _v194_mean_sample()
    var expected := _v188_point(mean.x, mean.y)
    if not _v194_envelope.visible or not _v194_centroid.visible or not _v194_spread_label.visible:
        push_error("Session grouping envelope visibility regression")
        get_tree().quit(25)
        return
    if _v194_envelope.points.size() != V194_ENVELOPE_SEGMENTS + 1:
        push_error("Session grouping envelope geometry regression")
        get_tree().quit(25)
        return
    if _v194_envelope.position.distance_to(expected) > 0.2 or _v194_centroid.position.distance_to(expected) > 0.2:
        push_error("Session grouping centroid regression")
        get_tree().quit(25)
        return
    if not _v194_spread_label.text.begins_with("GROUP ±"):
        push_error("Session grouping label regression")
        get_tree().quit(25)
        return

    # A repeatable miss must read as target-to-group bias, not merely as an offset ellipse. The cue
    # uses the already-clamped shot-map centroid and stays presentation-only.
    _v179_samples = [
        Vector2(12.0, 30.0),
        Vector2(14.0, 34.0),
        Vector2(16.0, 38.0),
        Vector2(18.0, 42.0)
    ]
    _v188_refresh(18.0, 42.0, true)
    _v179_refresh()
    var bias_mean := _v194_mean_sample()
    var bias_center := _v188_point(bias_mean.x, bias_mean.y)
    if not _v194_bias_line.visible or not _v194_bias_arrow.visible:
        push_error("Session bias vector visibility regression")
        get_tree().quit(25)
        return
    if _v194_bias_line.points.size() != 2 or _v194_bias_arrow.points.size() != 3:
        push_error("Session bias vector geometry regression")
        get_tree().quit(25)
        return
    if _v194_bias_line.points[0].distance_to(V188_CENTER) > 0.1 or _v194_bias_line.points[1].distance_to(bias_center) > 0.2:
        push_error("Session bias vector endpoint regression")
        get_tree().quit(25)
        return

    # Near-neutral groups should not get a noisy coaching arrow.
    _v179_samples = [Vector2(-1.0, -2.0), Vector2(0.0, 1.0), Vector2(1.0, 2.0)]
    _v188_refresh(1.0, 2.0, true)
    _v179_refresh()
    if _v194_bias_line.visible or _v194_bias_arrow.visible:
        push_error("Session bias vector deadzone regression")
        get_tree().quit(25)
        return

    # Correlated misses must rotate the grouping envelope instead of flattening their directional
    # pattern into an axis-aligned oval.
    _v179_samples = [
        Vector2(-12.0, -28.0),
        Vector2(-6.0, -14.0),
        Vector2(0.0, 0.0),
        Vector2(6.0, 14.0),
        Vector2(12.0, 28.0)
    ]
    _v188_refresh(12.0, 28.0, true)
    _v179_refresh()
    var diagonal_mean := _v194_mean_sample()
    var diagonal_geometry := _v194_envelope_geometry(diagonal_mean, _v194_stddev(diagonal_mean))
    var diagonal_angle := absf(float(diagonal_geometry["angle"]))
    if diagonal_angle < 0.20 or diagonal_angle > 1.35:
        push_error("Session grouping covariance orientation regression")
        get_tree().quit(25)
        return
    if float(diagonal_geometry["major"]) <= float(diagonal_geometry["minor"]) + 2.0:
        push_error("Session grouping principal-axis regression")
        get_tree().quit(25)
        return

    _v179_samples = [
        Vector2(20.0, 44.0),
        Vector2(24.0, 54.0),
        Vector2(28.0, 62.0),
        Vector2(30.0, 68.0)
    ]
    _v188_refresh(30.0, 68.0, true)
    _v179_refresh()
    var edge_mean := _v194_mean_sample()
    var edge_center := _v188_point(edge_mean.x, edge_mean.y)
    if _v194_centroid.position.distance_to(edge_center) > 0.2:
        push_error("Session grouping edge guard moved the statistical centroid")
        get_tree().quit(25)
        return
    if not _v194_envelope.visible or _v194_envelope.points.size() != V194_ENVELOPE_SEGMENTS + 1:
        push_error("Session grouping edge envelope unexpectedly hidden")
        get_tree().quit(25)
        return
    var plot_min := V188_CENTER - Vector2(V188_RADIUS, V188_RADIUS) + Vector2(V194_EDGE_INSET, V194_EDGE_INSET)
    var plot_max := V188_CENTER + Vector2(V188_RADIUS, V188_RADIUS) - Vector2(V194_EDGE_INSET, V194_EDGE_INSET)
    for local_point in _v194_envelope.points:
        var point := _v194_envelope.position + local_point
        if point.x < plot_min.x - 0.02 or point.y < plot_min.y - 0.02 or point.x > plot_max.x + 0.02 or point.y > plot_max.y + 0.02:
            push_error("Session grouping envelope escaped shot-map bounds")
            get_tree().quit(25)
            return

    _v179_samples = [Vector2(3.0, 4.0), Vector2(4.0, 6.0)]
    _v188_refresh(4.0, 6.0, true)
    _v179_refresh()
    if _v194_envelope.visible or _v194_centroid.visible or _v194_bias_line.visible or _v194_bias_arrow.visible or _v194_spread_label.visible:
        push_error("Session grouping minimum sample regression")
        get_tree().quit(25)
        return

    _v179_samples = [
        Vector2(-8.0, -18.0),
        Vector2(-3.0, 10.0),
        Vector2(5.0, 22.0),
        Vector2(7.0, 6.0),
        Vector2(3.0, 14.0)
    ]
    _v188_refresh(3.0, 14.0, true)
    _v179_refresh()
    print("PRACTICE_DISPERSION_ENVELOPE_OK=1")
    print("PRACTICE_DISPERSION_ORIENTATION_OK=1")
    print("PRACTICE_DISPERSION_ENVELOPE_EDGE_SAFE_OK=1")
    print("PRACTICE_BIAS_VECTOR_OK=1")

extends "res://v193_best_rep_ghost.gd"

# Presentation-only session grouping envelope for practice. It summarizes recent post-shot
# line/pace deltas already captured by the session dispersion layer. It never feeds back into
# Android physics, GreenTerrain, GreenReadAdvisor, scoring, aiming, or shot capture.

var _v194_envelope: Line2D
var _v194_centroid: Line2D
var _v194_bias_line: Line2D
var _v194_bias_arrow: Line2D
var _v194_bias_label: Label
var _v194_spread_label: Label

const V194_MIN_SAMPLES := 3
const V194_ENVELOPE_SEGMENTS := 28
const V194_EDGE_INSET := 1.0
const V194_SIGMA_SCALE := 1.35
const V194_MIN_AXIS_PX := 5.0
const V194_MAX_AXIS_PX := 34.0
const V194_COVARIANCE_EPSILON := 0.0001
const V194_BIAS_DEADZONE_PX := 6.0
const V194_BIAS_ARROW_LENGTH_PX := 5.0
const V194_BIAS_ARROW_HALF_WIDTH_PX := 3.0
const V194_BIAS_CENTER_DEADZONE_CM := 1.5
const V194_FIT_CLIPPED_THRESHOLD := 0.9999

func _v194_mean_sample() -> Vector2:
    if _v179_samples.is_empty():
        return Vector2.ZERO
    var total := Vector2.ZERO
    for sample in _v179_samples:
        total += sample
    return total / float(_v179_samples.size())

func _v194_stddev(mean: Vector2) -> Vector2:
    if _v179_samples.size() < 2:
        return Vector2.ZERO
    var sum_sq := Vector2.ZERO
    for sample in _v179_samples:
        var delta := sample - mean
        sum_sq += Vector2(delta.x * delta.x, delta.y * delta.y)
    var divisor := float(_v179_samples.size())
    return Vector2(sqrt(sum_sq.x / divisor), sqrt(sum_sq.y / divisor))

func _v194_covariance_pixels(mean: Vector2) -> Vector3:
    # Work in the shot-map's normalized pixel space so line and pace contribute in the same visual
    # units. The old axis-aligned ellipse discarded covariance, hiding diagonal patterns such as a
    # repeatable push-long or pull-short miss even though the individual dots clearly showed it.
    if _v179_samples.size() < 2:
        return Vector3.ZERO
    var xx := 0.0
    var yy := 0.0
    var xy := 0.0
    for sample in _v179_samples:
        var delta := sample - mean
        var px := delta.x / V188_LINE_WINDOW_CM * V188_RADIUS
        var py := -delta.y / V188_PACE_WINDOW_CM * V188_RADIUS
        xx += px * px
        yy += py * py
        xy += px * py
    var divisor := float(_v179_samples.size())
    return Vector3(xx / divisor, yy / divisor, xy / divisor)

func _v194_principal_axes(covariance: Vector3) -> Dictionary:
    var xx := maxf(0.0, covariance.x)
    var yy := maxf(0.0, covariance.y)
    var xy := covariance.z
    var discriminant := sqrt(maxf(0.0, (xx - yy) * (xx - yy) + 4.0 * xy * xy))
    var major_variance := maxf(0.0, 0.5 * (xx + yy + discriminant))
    var minor_variance := maxf(0.0, 0.5 * (xx + yy - discriminant))
    var angle := 0.5 * atan2(2.0 * xy, xx - yy) if discriminant > V194_COVARIANCE_EPSILON else 0.0
    return {
        "major": clampf(sqrt(major_variance) * V194_SIGMA_SCALE, V194_MIN_AXIS_PX, V194_MAX_AXIS_PX),
        "minor": clampf(sqrt(minor_variance) * V194_SIGMA_SCALE, V194_MIN_AXIS_PX, V194_MAX_AXIS_PX),
        "angle": angle
    }

func _v194_oriented_ellipse(major: float, minor: float, angle: float) -> PackedVector2Array:
    var points := PackedVector2Array()
    var major_axis := Vector2(cos(angle), sin(angle))
    var minor_axis := Vector2(-major_axis.y, major_axis.x)
    for index in range(V194_ENVELOPE_SEGMENTS + 1):
        var phase := TAU * float(index) / float(V194_ENVELOPE_SEGMENTS)
        points.append(major_axis * cos(phase) * major + minor_axis * sin(phase) * minor)
    return points

func _v194_fit_envelope_to_plot(center: Vector2, points: PackedVector2Array) -> Dictionary:
    # Off-scale session centroids legitimately sit on the compact plot rim. Fit only the drawn
    # envelope to the available rectangle, but report that presentation compression explicitly.
    # The caller keeps the true cm spread and centroid, so a large miss group can never masquerade
    # as a tighter one merely because the HUD has finite space.
    var plot_min := V188_CENTER - Vector2(V188_RADIUS, V188_RADIUS) + Vector2(V194_EDGE_INSET, V194_EDGE_INSET)
    var plot_max := V188_CENTER + Vector2(V188_RADIUS, V188_RADIUS) - Vector2(V194_EDGE_INSET, V194_EDGE_INSET)
    var scale := 1.0
    for local_point in points:
        if local_point.x > V194_COVARIANCE_EPSILON:
            scale = minf(scale, maxf(0.0, (plot_max.x - center.x) / local_point.x))
        elif local_point.x < -V194_COVARIANCE_EPSILON:
            scale = minf(scale, maxf(0.0, (plot_min.x - center.x) / local_point.x))
        if local_point.y > V194_COVARIANCE_EPSILON:
            scale = minf(scale, maxf(0.0, (plot_max.y - center.y) / local_point.y))
        elif local_point.y < -V194_COVARIANCE_EPSILON:
            scale = minf(scale, maxf(0.0, (plot_min.y - center.y) / local_point.y))

    var fitted := points
    var view_clipped := scale < V194_FIT_CLIPPED_THRESHOLD
    if view_clipped:
        fitted = PackedVector2Array()
        for local_point in points:
            fitted.append(local_point * scale)
    return {
        "points": fitted,
        "viewClipped": view_clipped,
        "presentationScale": scale
    }

func _v194_cross(radius: float) -> PackedVector2Array:
    return PackedVector2Array([
        Vector2(-radius, 0.0), Vector2(radius, 0.0),
        Vector2.ZERO,
        Vector2(0.0, -radius), Vector2(0.0, radius)
    ])

func _v194_bias_geometry(center: Vector2) -> Dictionary:
    # Turn statistical bias into an immediately readable coaching cue without inventing any new shot
    # metric. Both endpoints live in the same already-clamped shot-map space used by the dots.
    var delta := center - V188_CENTER
    if delta.length() < V194_BIAS_DEADZONE_PX:
        return {"visible": false, "line": PackedVector2Array(), "arrow": PackedVector2Array()}
    var direction := delta.normalized()
    var perpendicular := Vector2(-direction.y, direction.x)
    var arrow_base := center - direction * V194_BIAS_ARROW_LENGTH_PX
    return {
        "visible": true,
        "line": PackedVector2Array([V188_CENTER, center]),
        "arrow": PackedVector2Array([
            arrow_base + perpendicular * V194_BIAS_ARROW_HALF_WIDTH_PX,
            center,
            arrow_base - perpendicular * V194_BIAS_ARROW_HALF_WIDTH_PX
        ])
    }

func _v194_bias_readout(mean: Vector2) -> String:
    # The vector is quick to scan, but on a TV the player should not have to mentally decode its
    # direction or infer a unit. Reuse the same mean line/pace deltas and state both axes explicitly.
    var line_text := "LINE OK" if absf(mean.x) < V194_BIAS_CENTER_DEADZONE_CM else "%s %.0f CM" % ["RIGHT" if mean.x > 0.0 else "LEFT", absf(mean.x)]
    var pace_text := "PACE OK" if absf(mean.y) < V194_BIAS_CENTER_DEADZONE_CM else "%s %.0f CM" % ["LONG" if mean.y > 0.0 else "SHORT", absf(mean.y)]
    return "BIAS %s  ·  %s" % [line_text, pace_text]

func _v194_spread_readout(spread: Vector2, view_clipped: bool) -> String:
    # Never imply that a display-fitted ellipse is the full statistical footprint. The measured cm
    # remain authoritative; VIEW CLIPPED describes only the finite HUD viewport.
    var suffix := " · VIEW CLIPPED" if view_clipped else ""
    return "GROUP ±%.0f / ±%.0f CM%s" % [spread.x, spread.y, suffix]

# Preserve the true session centroid while shrinking only the presentation envelope to the visible
# shot-map plot. Covariance rotates the ellipse to match the actual miss pattern; edge fitting then
# scales it uniformly without flattening that directional signal.
func _v194_envelope_geometry(mean: Vector2, spread: Vector2) -> Dictionary:
    var center := _v188_point(mean.x, mean.y)
    var axes := _v194_principal_axes(_v194_covariance_pixels(mean))
    var raw_points := _v194_oriented_ellipse(float(axes["major"]), float(axes["minor"]), float(axes["angle"]))
    var fit := _v194_fit_envelope_to_plot(center, raw_points)
    var points: PackedVector2Array = fit["points"]
    var max_radius := 0.0
    for point in points:
        max_radius = maxf(max_radius, point.length())
    return {
        "center": center,
        "points": points,
        "angle": float(axes["angle"]),
        "major": float(axes["major"]),
        "minor": float(axes["minor"]),
        "visible": points.size() == V194_ENVELOPE_SEGMENTS + 1 and max_radius > 0.5,
        "viewClipped": bool(fit["viewClipped"]),
        "presentationScale": float(fit["presentationScale"]),
        "spread": spread
    }

func _build_hud() -> void:
    super._build_hud()
    if _v188_panel == null:
        return

    _v194_envelope = Line2D.new()
    _v194_envelope.name = "SessionGroupingEnvelope"
    _v194_envelope.width = 1.6
    _v194_envelope.default_color = Color(0.46, 0.78, 0.92, 0.62)
    _v194_envelope.visible = false
    _v188_panel.add_child(_v194_envelope)
    _v188_panel.move_child(_v194_envelope, 5)

    _v194_bias_line = Line2D.new()
    _v194_bias_line.name = "SessionBiasVector"
    _v194_bias_line.width = 1.15
    _v194_bias_line.default_color = Color(0.91, 0.72, 0.30, 0.72)
    _v194_bias_line.visible = false
    _v188_panel.add_child(_v194_bias_line)
    _v188_panel.move_child(_v194_bias_line, 6)

    _v194_bias_arrow = Line2D.new()
    _v194_bias_arrow.name = "SessionBiasArrow"
    _v194_bias_arrow.width = 1.35
    _v194_bias_arrow.default_color = Color(0.98, 0.82, 0.42, 0.90)
    _v194_bias_arrow.visible = false
    _v188_panel.add_child(_v194_bias_arrow)
    _v188_panel.move_child(_v194_bias_arrow, 7)

    _v194_centroid = Line2D.new()
    _v194_centroid.name = "SessionGroupingCentroid"
    _v194_centroid.width = 1.4
    _v194_centroid.default_color = Color(0.60, 0.87, 0.96, 0.82)
    _v194_centroid.points = _v194_cross(4.0)
    _v194_centroid.visible = false
    _v188_panel.add_child(_v194_centroid)

    _v194_bias_label = _v174_text(
        _v188_panel,
        Vector2(8, 116),
        Vector2(134, 10),
        "BIAS —",
        7,
        Color(0.96, 0.81, 0.45, 0.94),
        HORIZONTAL_ALIGNMENT_CENTER
    )
    _v194_bias_label.visible = false

    _v194_spread_label = _v174_text(
        _v188_panel,
        Vector2(8, 127),
        Vector2(134, 10),
        "GROUP —",
        7,
        Color(0.55, 0.78, 0.88, 0.90),
        HORIZONTAL_ALIGNMENT_CENTER
    )
    _v194_spread_label.visible = false
    _v194_refresh_envelope()

func _v194_refresh_envelope() -> void:
    if _v194_envelope == null or _v194_centroid == null or _v194_bias_line == null or _v194_bias_arrow == null or _v194_bias_label == null or _v194_spread_label == null:
        return
    var show := _v188_panel != null and _v188_panel.visible and _v179_samples.size() >= V194_MIN_SAMPLES
    _v194_envelope.visible = show
    _v194_centroid.visible = show
    _v194_bias_line.visible = false
    _v194_bias_arrow.visible = false
    _v194_bias_label.visible = show
    _v194_spread_label.visible = show
    if not show:
        return

    var mean := _v194_mean_sample()
    var spread := _v194_stddev(mean)
    var geometry := _v194_envelope_geometry(mean, spread)
    var center: Vector2 = geometry["center"]
    _v194_envelope.visible = bool(geometry["visible"])
    _v194_envelope.position = center
    if _v194_envelope.visible:
        _v194_envelope.points = geometry["points"]
    else:
        _v194_envelope.points = PackedVector2Array()
    _v194_centroid.position = center

    var bias := _v194_bias_geometry(center)
    var bias_visible := bool(bias["visible"])
    _v194_bias_line.visible = bias_visible
    _v194_bias_arrow.visible = bias_visible
    if bias_visible:
        _v194_bias_line.points = bias["line"]
        _v194_bias_arrow.points = bias["arrow"]
    else:
        _v194_bias_line.points = PackedVector2Array()
        _v194_bias_arrow.points = PackedVector2Array()

    _v194_bias_label.text = _v194_bias_readout(mean)
    _v194_spread_label.text = _v194_spread_readout(spread, bool(geometry["viewClipped"]))

func _v188_refresh(line_delta_cm: float, pace_delta_cm: float, visible: bool) -> void:
    super._v188_refresh(line_delta_cm, pace_delta_cm, visible)
    _v194_refresh_envelope()

func _v179_refresh() -> void:
    super._v179_refresh()
    _v194_refresh_envelope()

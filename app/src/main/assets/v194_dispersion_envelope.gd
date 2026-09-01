extends "res://v193_best_rep_ghost.gd"

# Presentation-only session grouping envelope for practice. It summarizes recent post-shot
# line/pace deltas already captured by the session dispersion layer. It never feeds back into
# Android physics, GreenTerrain, GreenReadAdvisor, scoring, aiming, or shot capture.

var _v194_envelope: Line2D
var _v194_centroid: Line2D
var _v194_spread_label: Label

const V194_MIN_SAMPLES := 3
const V194_ENVELOPE_SEGMENTS := 28
const V194_EDGE_INSET := 1.0
const V194_SIGMA_SCALE := 1.35
const V194_MIN_AXIS_PX := 5.0
const V194_MAX_AXIS_PX := 34.0
const V194_COVARIANCE_EPSILON := 0.0001

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

func _v194_fit_envelope_to_ring(center: Vector2, points: PackedVector2Array) -> PackedVector2Array:
    # The shot map is circular. Uniformly shrink the statistical ellipse only when a rendered vertex
    # would leave that ring; never move the true centroid or rotate the pattern back to screen axes.
    var ring_radius := V188_RADIUS - V194_EDGE_INSET
    var center_offset := center - V188_CENTER
    var scale := 1.0
    for local_point in points:
        var a := local_point.length_squared()
        if a <= V194_COVARIANCE_EPSILON:
            continue
        var b := 2.0 * center_offset.dot(local_point)
        var c := center_offset.length_squared() - ring_radius * ring_radius
        var disc := b * b - 4.0 * a * c
        if disc < 0.0:
            scale = 0.0
            break
        var limit := (-b + sqrt(disc)) / (2.0 * a)
        scale = minf(scale, maxf(0.0, limit))
    if scale >= 0.9999:
        return points
    var fitted := PackedVector2Array()
    for local_point in points:
        fitted.append(local_point * scale)
    return fitted

func _v194_cross(radius: float) -> PackedVector2Array:
    return PackedVector2Array([
        Vector2(-radius, 0.0), Vector2(radius, 0.0),
        Vector2.ZERO,
        Vector2(0.0, -radius), Vector2(0.0, radius)
    ])

# Preserve the true session centroid while shrinking only the presentation envelope to the visible
# circular shot-map plot. Covariance rotates the ellipse to match the actual miss pattern; the ring
# fit then scales it uniformly when that oriented shape approaches the edge.
func _v194_envelope_geometry(mean: Vector2, spread: Vector2) -> Dictionary:
    var center := _v188_point(mean.x, mean.y)
    var axes := _v194_principal_axes(_v194_covariance_pixels(mean))
    var raw_points := _v194_oriented_ellipse(float(axes["major"]), float(axes["minor"]), float(axes["angle"]))
    var points := _v194_fit_envelope_to_ring(center, raw_points)
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

    _v194_centroid = Line2D.new()
    _v194_centroid.name = "SessionGroupingCentroid"
    _v194_centroid.width = 1.4
    _v194_centroid.default_color = Color(0.60, 0.87, 0.96, 0.82)
    _v194_centroid.points = _v194_cross(4.0)
    _v194_centroid.visible = false
    _v188_panel.add_child(_v194_centroid)

    _v194_spread_label = _v174_text(
        _v188_panel,
        Vector2(14, 127),
        Vector2(122, 10),
        "GROUP —",
        7,
        Color(0.55, 0.78, 0.88, 0.90),
        HORIZONTAL_ALIGNMENT_CENTER
    )
    _v194_spread_label.visible = false
    _v194_refresh_envelope()

func _v194_refresh_envelope() -> void:
    if _v194_envelope == null or _v194_centroid == null or _v194_spread_label == null:
        return
    var show := _v188_panel != null and _v188_panel.visible and _v179_samples.size() >= V194_MIN_SAMPLES
    _v194_envelope.visible = show
    _v194_centroid.visible = show
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
    _v194_spread_label.text = "GROUP ±%.0f / ±%.0f CM" % [spread.x, spread.y]

func _v188_refresh(line_delta_cm: float, pace_delta_cm: float, visible: bool) -> void:
    super._v188_refresh(line_delta_cm, pace_delta_cm, visible)
    _v194_refresh_envelope()

func _v179_refresh() -> void:
    super._v179_refresh()
    _v194_refresh_envelope()

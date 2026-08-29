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

func _v194_ellipse(rx: float, ry: float) -> PackedVector2Array:
    var points := PackedVector2Array()
    for index in range(V194_ENVELOPE_SEGMENTS + 1):
        var angle := TAU * float(index) / float(V194_ENVELOPE_SEGMENTS)
        points.append(Vector2(cos(angle) * rx, sin(angle) * ry))
    return points

func _v194_cross(radius: float) -> PackedVector2Array:
    return PackedVector2Array([
        Vector2(-radius, 0.0), Vector2(radius, 0.0),
        Vector2.ZERO,
        Vector2(0.0, -radius), Vector2(0.0, radius)
    ])

# Preserve the true session centroid while shrinking only the presentation envelope to the visible
# shot-map plot. Without this guard, a biased group near an extreme line/pace edge could draw the
# ellipse outside the target plot (and even nick the panel edge on the right).
func _v194_envelope_geometry(mean: Vector2, spread: Vector2) -> Dictionary:
    var center := _v188_point(mean.x, mean.y)
    var desired_rx: float = clampf(spread.x / 30.0 * V188_RADIUS * 1.35, 5.0, 34.0)
    var desired_ry: float = clampf(spread.y / 70.0 * V188_RADIUS * 1.35, 5.0, 34.0)
    var plot_min := V188_CENTER - Vector2(V188_RADIUS, V188_RADIUS) + Vector2(V194_EDGE_INSET, V194_EDGE_INSET)
    var plot_max := V188_CENTER + Vector2(V188_RADIUS, V188_RADIUS) - Vector2(V194_EDGE_INSET, V194_EDGE_INSET)
    var available_x := maxf(0.0, minf(center.x - plot_min.x, plot_max.x - center.x))
    var available_y := maxf(0.0, minf(center.y - plot_min.y, plot_max.y - center.y))
    var radii := Vector2(minf(desired_rx, available_x), minf(desired_ry, available_y))
    return {
        "center": center,
        "radii": radii,
        "visible": radii.x > 0.5 and radii.y > 0.5
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
    var radii: Vector2 = geometry["radii"]
    _v194_envelope.visible = bool(geometry["visible"])
    _v194_envelope.position = center
    if _v194_envelope.visible:
        _v194_envelope.points = _v194_ellipse(radii.x, radii.y)
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

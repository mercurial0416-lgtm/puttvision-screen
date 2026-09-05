extends "res://presentation_telemetry_guard.gd"

# Presentation-only recent-centroid reticle for SESSION DISPERSION. The existing practice map already
# shows individual samples and a recent consistency envelope; this layer marks the actual center of
# the latest three valid reps from the active LINE/PACE/BOTH focus window and connects target center
# to that centroid so players can read current directional bias at TV distance without mentally
# averaging dots or mixing reps from an obsolete practice cue. Android physics, GreenTerrain,
# GreenReadAdvisor, aiming, scoring and capture remain authoritative and untouched.

const PRACTICE_RECENT_CENTER_MIN_SAMPLES := 3
const PRACTICE_RECENT_CENTER_GROUP_SIZE := 3
const PRACTICE_RECENT_CENTER_HALF_SPAN := 7.0
const PRACTICE_RECENT_CENTER_RING_RADIUS := 3.5
const PRACTICE_RECENT_CENTER_RING_SEGMENTS := 12
const PRACTICE_RECENT_CENTER_LINE_DEADBAND_CM := 2.5
const PRACTICE_RECENT_CENTER_PACE_DEADBAND_CM := 8.0

var _practice_recent_center_bias: Line2D
var _practice_recent_center_h: Line2D
var _practice_recent_center_v: Line2D
var _practice_recent_center_ring: Line2D
var _practice_recent_center_readout: Label

func _practice_recent_center_geometry(samples: Array[Vector2]) -> Dictionary:
    if samples.size() < PRACTICE_RECENT_CENTER_MIN_SAMPLES:
        return {"visible": false}

    var count := mini(PRACTICE_RECENT_CENTER_GROUP_SIZE, samples.size())
    var start := samples.size() - count
    var total := Vector2.ZERO
    for index in range(start, samples.size()):
        var sample := samples[index]
        if not is_finite(sample.x) or not is_finite(sample.y):
            return {"visible": false}
        total += sample

    var centroid := total / float(count)
    # Do not clamp an off-map centroid onto the edge: that would present a false center. Individual
    # clipped misses remain visible through the established edge-truth treatment.
    if absf(centroid.x) > V179_LINE_SCALE_CM or absf(centroid.y) > V179_PACE_SCALE_CM:
        return {"visible": false, "clipped": true}

    var target_center := _v179_plot_position(Vector2.ZERO)
    var center := _v179_plot_position(centroid)
    var ring_points := PackedVector2Array()
    for step in range(PRACTICE_RECENT_CENTER_RING_SEGMENTS + 1):
        var angle := TAU * float(step) / float(PRACTICE_RECENT_CENTER_RING_SEGMENTS)
        ring_points.append(center + Vector2(cos(angle), sin(angle)) * PRACTICE_RECENT_CENTER_RING_RADIUS)

    return {
        "visible": true,
        "sample": centroid,
        "center": center,
        "bias": PackedVector2Array([target_center, center]),
        "horizontal": PackedVector2Array([
            center + Vector2(-PRACTICE_RECENT_CENTER_HALF_SPAN, 0.0),
            center + Vector2(PRACTICE_RECENT_CENTER_HALF_SPAN, 0.0)
        ]),
        "vertical": PackedVector2Array([
            center + Vector2(0.0, -PRACTICE_RECENT_CENTER_HALF_SPAN),
            center + Vector2(0.0, PRACTICE_RECENT_CENTER_HALF_SPAN)
        ]),
        "ring": ring_points
    }

func _practice_recent_center_focus_samples() -> Array[Vector2]:
    # Pressure-ladder focus is prospective: the rep that selected a new objective was not played
    # against that objective. Reuse its rollover-safe boundary so the recent-center marker cannot
    # blend old LINE reps into a new PACE cue (or vice versa). If fewer than three reps have been
    # played in the current focus, fail closed and leave the marker hidden rather than manufacture a
    # misleading center from incomparable history.
    var first_eligible := clampi(_v191_focus_start_index, 0, _v179_samples.size())
    var focused: Array[Vector2] = []
    for index in range(first_eligible, _v179_samples.size()):
        focused.append(_v179_samples[index])
    return focused

func _practice_recent_center_line(name: String, width: float, color := Color(0.94, 0.98, 0.96, 0.96)) -> Line2D:
    var line := Line2D.new()
    line.name = name
    line.width = width
    line.default_color = color
    line.begin_cap_mode = Line2D.LINE_CAP_ROUND
    line.end_cap_mode = Line2D.LINE_CAP_ROUND
    line.visible = false
    return line

func _practice_recent_center_axis_text(value_cm: float, negative: String, positive: String, deadband_cm: float, hold_text: String) -> String:
    if not is_finite(value_cm):
        return "--"
    if absf(value_cm) <= deadband_cm:
        return hold_text
    return "%s %.0fcm" % [positive if value_cm > 0.0 else negative, absf(value_cm)]

func _practice_recent_center_readout_text(sample: Vector2) -> String:
    if not is_finite(sample.x) or not is_finite(sample.y):
        return "RECENT 3 · DATA UNAVAILABLE"
    var line_text := _practice_recent_center_axis_text(
        sample.x,
        "LEFT",
        "RIGHT",
        PRACTICE_RECENT_CENTER_LINE_DEADBAND_CM,
        "LINE OK"
    )
    var pace_text := _practice_recent_center_axis_text(
        sample.y,
        "SHORT",
        "LONG",
        PRACTICE_RECENT_CENTER_PACE_DEADBAND_CM,
        "PACE OK"
    )
    return "RECENT 3 · %s · %s" % [line_text, pace_text]

func _build_hud() -> void:
    super._build_hud()
    if _v179_plot == null:
        return

    # Keep the bias vector beneath the centroid reticle so it reads as a relationship cue rather than
    # another sample trace. It only updates when the existing dispersion HUD refreshes.
    _practice_recent_center_bias = _practice_recent_center_line(
        "PracticeRecentCenterBias",
        1.2,
        Color(0.78, 0.94, 0.88, 0.44)
    )
    _practice_recent_center_h = _practice_recent_center_line("PracticeRecentCenterH", 1.7)
    _practice_recent_center_v = _practice_recent_center_line("PracticeRecentCenterV", 1.7)
    _practice_recent_center_ring = _practice_recent_center_line("PracticeRecentCenterRing", 1.4)
    _v179_plot.add_child(_practice_recent_center_bias)
    _v179_plot.add_child(_practice_recent_center_h)
    _v179_plot.add_child(_practice_recent_center_v)
    _v179_plot.add_child(_practice_recent_center_ring)

    if _v179_panel != null:
        # Use the narrow strip between the header row and the plot. The right rail below is occupied by
        # practice focus/target controls, so putting another label there creates unreadable overlap.
        _practice_recent_center_readout = _v174_text(
            _v179_panel,
            Vector2(24, 38),
            Vector2(300, 12),
            "RECENT 3 · --",
            8,
            Color(0.76, 0.91, 0.86, 0.94),
            HORIZONTAL_ALIGNMENT_LEFT
        )
        _practice_recent_center_readout.name = "PracticeRecentCenterReadout"
        _practice_recent_center_readout.visible = false
        _practice_recent_center_readout.mouse_filter = Control.MOUSE_FILTER_IGNORE

    _practice_recent_center_refresh()

func _practice_recent_center_refresh() -> void:
    if _practice_recent_center_bias == null or _practice_recent_center_h == null or _practice_recent_center_v == null or _practice_recent_center_ring == null:
        return
    var geometry := _practice_recent_center_geometry(_practice_recent_center_focus_samples())
    var visible := bool(geometry.get("visible", false))
    _practice_recent_center_bias.visible = visible
    _practice_recent_center_h.visible = visible
    _practice_recent_center_v.visible = visible
    _practice_recent_center_ring.visible = visible
    if _practice_recent_center_readout != null:
        _practice_recent_center_readout.visible = visible
    if not visible:
        if _practice_recent_center_readout != null:
            _practice_recent_center_readout.text = "RECENT 3 · --"
        return
    _practice_recent_center_bias.points = geometry["bias"]
    _practice_recent_center_h.points = geometry["horizontal"]
    _practice_recent_center_v.points = geometry["vertical"]
    _practice_recent_center_ring.points = geometry["ring"]
    if _practice_recent_center_readout != null:
        var sample_variant: Variant = geometry.get("sample", null)
        if sample_variant is Vector2:
            _practice_recent_center_readout.text = _practice_recent_center_readout_text(sample_variant as Vector2)
        else:
            _practice_recent_center_readout.visible = false
            _practice_recent_center_readout.text = "RECENT 3 · --"

func _v179_refresh() -> void:
    super._v179_refresh()
    _practice_recent_center_refresh()

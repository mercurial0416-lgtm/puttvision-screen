extends "res://read_launch_vector.gd"

# Presentation-only session trend vector. It compares early and recent centroids from the
# existing local practice dispersion history so players can see both pattern movement and
# whether the grouping itself is tightening or widening. It never feeds values back into Android
# physics, GreenTerrain, GreenReadAdvisor, aiming, scoring, shot capture, or active putting distance.

const PRACTICE_TREND_MIN_SAMPLES := 4
const PRACTICE_TREND_GROUP_SIZE := 2
const PRACTICE_TREND_STABLE_GROUP_SIZE := 3
# Session dispersion intentionally keeps five reps. Stabilization must become reachable when that
# bounded history fills; waiting for a sixth rep made this branch dead and left late-session trend
# coaching unnecessarily sensitive to one miss.
const PRACTICE_TREND_STABLE_MIN_SAMPLES := 5
const PRACTICE_TREND_MIN_PIXELS := 5.0
const PRACTICE_TREND_STATE_DEADBAND := 0.05
const PRACTICE_TREND_MAX_CHANGE_PERCENT := 999
const PRACTICE_TREND_WING_LENGTH := 6.5
const PRACTICE_TREND_WING_HALF_WIDTH := 4.0
const PRACTICE_RECENT_GROUP_SIZE := 3
const PRACTICE_RECENT_RING_MIN_RADIUS := 8.0
const PRACTICE_RECENT_RING_MAX_RADIUS := 22.0
const PRACTICE_RECENT_RING_PADDING := 5.0
const PRACTICE_RECENT_RING_SEGMENTS := 20
const PRACTICE_RECENT_RING_EDGE_INSET := 1.0

var _practice_trend_line: Line2D
var _practice_trend_head: Line2D
var _practice_trend_label: Label
var _practice_recent_ring: Line2D

func _practice_trend_mean(samples: Array[Vector2], from_index: int, count: int) -> Vector2:
    if samples.is_empty() or count <= 0:
        return Vector2.ZERO
    var start := clampi(from_index, 0, samples.size())
    var finish := clampi(start + count, start, samples.size())
    if finish <= start:
        return Vector2.ZERO
    var total := Vector2.ZERO
    for index in range(start, finish):
        total += samples[index]
    return total / float(finish - start)

func _practice_trend_error(sample: Vector2) -> float:
    return Vector2(sample.x / V179_LINE_SCALE_CM, sample.y / V179_PACE_SCALE_CM).length()

func _practice_group_spread(samples: Array[Vector2], from_index: int, count: int) -> float:
    if samples.is_empty() or count <= 0:
        return 0.0
    var start := clampi(from_index, 0, samples.size())
    var finish := clampi(start + count, start, samples.size())
    if finish <= start:
        return 0.0
    var center := _practice_trend_mean(samples, start, finish - start)
    var total := 0.0
    for index in range(start, finish):
        var delta := samples[index] - center
        total += Vector2(delta.x / V179_LINE_SCALE_CM, delta.y / V179_PACE_SCALE_CM).length()
    return total / float(finish - start)

func _practice_trend_group_size(samples: Array[Vector2]) -> int:
    # Two-shot windows keep the first useful result responsive at four samples. Once the bounded
    # five-rep session history is full, three-shot windows damp a single miss from flipping the
    # coaching state while staying bounded and cheap on Forward Mobile.
    return PRACTICE_TREND_STABLE_GROUP_SIZE if samples.size() >= PRACTICE_TREND_STABLE_MIN_SAMPLES else PRACTICE_TREND_GROUP_SIZE

func _practice_trend_change_percent(early_spread: float, recent_spread: float) -> int:
    if not is_finite(early_spread) or not is_finite(recent_spread):
        return 0
    var baseline := maxf(absf(early_spread), PRACTICE_TREND_STATE_DEADBAND)
    var magnitude := absf(recent_spread - early_spread) / baseline * 100.0
    return clampi(int(round(magnitude)), 0, PRACTICE_TREND_MAX_CHANGE_PERCENT)

func _practice_trend_label_text(state: String, change_percent: int) -> String:
    if state == "TIGHTENING" or state == "WIDENING":
        return "TREND · %s · %d%%" % [state, clampi(change_percent, 0, PRACTICE_TREND_MAX_CHANGE_PERCENT)]
    return "TREND · %s" % state

func _practice_trend_geometry(samples: Array[Vector2]) -> Dictionary:
    if samples.size() < PRACTICE_TREND_MIN_SAMPLES:
        return {"visible": false, "state": "BUILDING", "change_percent": 0}

    var group_size := _practice_trend_group_size(samples)
    var early_from := 0
    var recent_from := samples.size() - group_size
    var early := _practice_trend_mean(samples, early_from, group_size)
    var recent := _practice_trend_mean(samples, recent_from, group_size)
    var start := _v179_plot_position(early)
    var tip := _v179_plot_position(recent)
    var delta := tip - start
    var early_error := _practice_trend_error(early)
    var recent_error := _practice_trend_error(recent)
    var early_spread := _practice_group_spread(samples, early_from, group_size)
    var recent_spread := _practice_group_spread(samples, recent_from, group_size)
    var spread_improvement := early_spread - recent_spread

    # The arrow already communicates centroid drift. The text describes consistency and now also
    # exposes the bounded relative magnitude so players can tell a marginal improvement from a
    # substantial one without changing any coaching or physics decision.
    var state := "STEADY"
    if spread_improvement > PRACTICE_TREND_STATE_DEADBAND:
        state = "TIGHTENING"
    elif spread_improvement < -PRACTICE_TREND_STATE_DEADBAND:
        state = "WIDENING"
    var change_percent := _practice_trend_change_percent(early_spread, recent_spread)

    var result := {
        "visible": delta.length() >= PRACTICE_TREND_MIN_PIXELS,
        "state": state,
        "change_percent": change_percent,
        "group_size": group_size,
        "early_error": early_error,
        "recent_error": recent_error,
        "early_spread": early_spread,
        "recent_spread": recent_spread,
        "start": start,
        "tip": tip
    }
    if not bool(result["visible"]):
        return result

    var tangent := delta.normalized()
    var normal := Vector2(-tangent.y, tangent.x)
    var base := tip - tangent * PRACTICE_TREND_WING_LENGTH
    result["left"] = base + normal * PRACTICE_TREND_WING_HALF_WIDTH
    result["right"] = base - normal * PRACTICE_TREND_WING_HALF_WIDTH
    return result

func _practice_recent_ring_geometry(samples: Array[Vector2]) -> Dictionary:
    if samples.size() < PRACTICE_TREND_MIN_SAMPLES:
        return {"visible": false}
    var count := mini(PRACTICE_RECENT_GROUP_SIZE, samples.size())
    var from_index := samples.size() - count
    var recent := _practice_trend_mean(samples, from_index, count)
    var center := _v179_plot_position(recent)
    var max_distance := 0.0
    for index in range(from_index, samples.size()):
        max_distance = maxf(max_distance, _v179_plot_position(samples[index]).distance_to(center))
    var desired_radius := clampf(max_distance + PRACTICE_RECENT_RING_PADDING, PRACTICE_RECENT_RING_MIN_RADIUS, PRACTICE_RECENT_RING_MAX_RADIUS)
    var edge_radius := minf(
        minf(center.x, V179_PLOT_SIZE.x - center.x),
        minf(center.y, V179_PLOT_SIZE.y - center.y)
    ) - PRACTICE_RECENT_RING_EDGE_INSET
    var radius := minf(desired_radius, maxf(0.0, edge_radius))
    if radius <= 0.0:
        return {"visible": false}
    var points := PackedVector2Array()
    for step in range(PRACTICE_RECENT_RING_SEGMENTS + 1):
        var angle := TAU * float(step) / float(PRACTICE_RECENT_RING_SEGMENTS)
        points.append(center + Vector2(cos(angle), sin(angle)) * radius)
    return {"visible": true, "center": center, "radius": radius, "points": points}

func _build_hud() -> void:
    super._build_hud()
    if _v179_plot == null or _v179_panel == null:
        return

    _practice_recent_ring = Line2D.new()
    _practice_recent_ring.name = "PracticeRecentConsistencyRing"
    _practice_recent_ring.width = 1.5
    _practice_recent_ring.default_color = Color(0.72, 0.90, 0.96, 0.78)
    _practice_recent_ring.joint_mode = Line2D.LINE_JOINT_ROUND
    _practice_recent_ring.begin_cap_mode = Line2D.LINE_CAP_ROUND
    _practice_recent_ring.end_cap_mode = Line2D.LINE_CAP_ROUND
    _practice_recent_ring.visible = false
    _v179_plot.add_child(_practice_recent_ring)

    _practice_trend_line = Line2D.new()
    _practice_trend_line.name = "PracticeTrendVector"
    _practice_trend_line.width = 2.0
    _practice_trend_line.begin_cap_mode = Line2D.LINE_CAP_ROUND
    _practice_trend_line.end_cap_mode = Line2D.LINE_CAP_ROUND
    _practice_trend_line.visible = false
    _v179_plot.add_child(_practice_trend_line)

    _practice_trend_head = Line2D.new()
    _practice_trend_head.name = "PracticeTrendVectorHead"
    _practice_trend_head.width = 2.0
    _practice_trend_head.joint_mode = Line2D.LINE_JOINT_ROUND
    _practice_trend_head.begin_cap_mode = Line2D.LINE_CAP_ROUND
    _practice_trend_head.end_cap_mode = Line2D.LINE_CAP_ROUND
    _practice_trend_head.visible = false
    _v179_plot.add_child(_practice_trend_head)

    _practice_trend_label = _v174_text(
        _v179_panel,
        Vector2(24, 34),
        Vector2(300, 14),
        "TREND · BUILDING",
        8,
        Color(0.55, 0.72, 0.78, 0.92)
    )
    _practice_trend_label.visible = false
    _practice_trend_refresh()

func _practice_trend_refresh() -> void:
    if _practice_trend_line == null or _practice_trend_head == null or _practice_trend_label == null or _practice_recent_ring == null:
        return
    var geometry := _practice_trend_geometry(_v179_samples)
    var recent_ring := _practice_recent_ring_geometry(_v179_samples)
    var state := str(geometry.get("state", "BUILDING"))
    var change_percent := int(geometry.get("change_percent", 0))
    var visible := bool(geometry.get("visible", false))
    _practice_trend_line.visible = visible
    _practice_trend_head.visible = visible
    _practice_trend_label.visible = _v179_samples.size() >= PRACTICE_TREND_MIN_SAMPLES
    _practice_recent_ring.visible = bool(recent_ring.get("visible", false))
    if _practice_recent_ring.visible:
        _practice_recent_ring.points = recent_ring["points"]

    var color := Color(0.48, 0.82, 0.92, 0.86)
    if state == "TIGHTENING":
        color = Color(0.46, 0.85, 0.66, 0.92)
    elif state == "WIDENING":
        color = Color(0.95, 0.67, 0.40, 0.92)
    _practice_trend_line.default_color = color
    _practice_trend_head.default_color = color
    _practice_trend_label.modulate = color
    _practice_trend_label.text = _practice_trend_label_text(state, change_percent)

    if not visible:
        return
    var start: Vector2 = geometry["start"]
    var tip: Vector2 = geometry["tip"]
    _practice_trend_line.points = PackedVector2Array([start, tip])
    _practice_trend_head.points = PackedVector2Array([geometry["left"], tip, geometry["right"]])

func _v179_refresh() -> void:
    super._v179_refresh()
    _practice_trend_refresh()

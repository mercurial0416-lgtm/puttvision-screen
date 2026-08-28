extends "res://read_launch_vector.gd"

# Presentation-only session trend vector. It compares early and recent centroids from the
# existing local practice dispersion history so players can see whether their pattern is
# tightening or drifting. It never feeds values back into Android physics, GreenTerrain,
# GreenReadAdvisor, aiming, scoring, shot capture, or active putting distance.

const PRACTICE_TREND_MIN_SAMPLES := 4
const PRACTICE_TREND_GROUP_SIZE := 2
const PRACTICE_TREND_MIN_PIXELS := 5.0
const PRACTICE_TREND_STATE_DEADBAND := 0.05
const PRACTICE_TREND_WING_LENGTH := 6.5
const PRACTICE_TREND_WING_HALF_WIDTH := 4.0
const PRACTICE_RECENT_GROUP_SIZE := 3
const PRACTICE_RECENT_RING_MIN_RADIUS := 8.0
const PRACTICE_RECENT_RING_MAX_RADIUS := 22.0
const PRACTICE_RECENT_RING_PADDING := 5.0
const PRACTICE_RECENT_RING_SEGMENTS := 20

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

func _practice_trend_geometry(samples: Array[Vector2]) -> Dictionary:
    if samples.size() < PRACTICE_TREND_MIN_SAMPLES:
        return {"visible": false, "state": "BUILDING"}

    var early := _practice_trend_mean(samples, 0, PRACTICE_TREND_GROUP_SIZE)
    var recent := _practice_trend_mean(samples, samples.size() - PRACTICE_TREND_GROUP_SIZE, PRACTICE_TREND_GROUP_SIZE)
    var start := _v179_plot_position(early)
    var tip := _v179_plot_position(recent)
    var delta := tip - start
    var early_error := _practice_trend_error(early)
    var recent_error := _practice_trend_error(recent)
    var improvement := early_error - recent_error

    var state := "STEADY"
    if improvement > PRACTICE_TREND_STATE_DEADBAND:
        state = "TIGHTENING"
    elif improvement < -PRACTICE_TREND_STATE_DEADBAND:
        state = "DRIFTING"

    if delta.length() < PRACTICE_TREND_MIN_PIXELS:
        return {
            "visible": false,
            "state": state,
            "early_error": early_error,
            "recent_error": recent_error,
            "start": start,
            "tip": tip
        }

    var tangent := delta.normalized()
    var normal := Vector2(-tangent.y, tangent.x)
    var base := tip - tangent * PRACTICE_TREND_WING_LENGTH
    return {
        "visible": true,
        "state": state,
        "early_error": early_error,
        "recent_error": recent_error,
        "start": start,
        "tip": tip,
        "left": base + normal * PRACTICE_TREND_WING_HALF_WIDTH,
        "right": base - normal * PRACTICE_TREND_WING_HALF_WIDTH
    }

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
    var radius := clampf(max_distance + PRACTICE_RECENT_RING_PADDING, PRACTICE_RECENT_RING_MIN_RADIUS, PRACTICE_RECENT_RING_MAX_RADIUS)
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
    elif state == "DRIFTING":
        color = Color(0.95, 0.67, 0.40, 0.92)
    _practice_trend_line.default_color = color
    _practice_trend_head.default_color = color
    _practice_trend_label.modulate = color
    _practice_trend_label.text = "TREND · %s" % state

    if not visible:
        return
    var start: Vector2 = geometry["start"]
    var tip: Vector2 = geometry["tip"]
    _practice_trend_line.points = PackedVector2Array([start, tip])
    _practice_trend_head.points = PackedVector2Array([geometry["left"], tip, geometry["right"]])

func _v179_refresh() -> void:
    super._v179_refresh()
    _practice_trend_refresh()

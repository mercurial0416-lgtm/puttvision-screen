extends "res://practice_ring_edge_truth.gd"

# Presentation-only replay pacing. Android V135-V137 / GreenTerrain / GreenReadAdvisor remain
# authoritative. Replay samples can arrive with uneven spacing; sampling by array index made the
# cinematic camera crawl through dense clusters then jump across sparse ones. Normalize replay
# interpolation by traveled arc length so the fixed replay clock produces smooth spatial motion.
const REPLAY_SPATIAL_EPSILON := 0.0001
const REPLAY_HEADING_SAMPLE_FRACTION := 0.006

func _replay_spatial_valid_points(points: Array) -> Array[Vector2]:
    var valid: Array[Vector2] = []
    var epsilon_sq := REPLAY_SPATIAL_EPSILON * REPLAY_SPATIAL_EPSILON
    for value in points:
        if typeof(value) != TYPE_VECTOR2:
            continue
        var point := value as Vector2
        if not point.is_finite():
            continue
        if not valid.is_empty() and valid[valid.size() - 1].distance_squared_to(point) <= epsilon_sq:
            continue
        valid.append(point)
    return valid

func _v175_trail_total_length(points: Array) -> float:
    # Keep every replay consumer on the same sanitized polyline. The camera already filtered invalid
    # and duplicate samples before interpolation, but the inherited total-length helper still walked
    # the raw array. A single non-finite bridge sample could therefore poison HUD distance telemetry
    # even while the replay camera itself looked healthy. Presentation only; shot physics stay intact.
    var valid_points := _replay_spatial_valid_points(points)
    if valid_points.size() < 2:
        return 0.0
    var total_length := 0.0
    for index in range(1, valid_points.size()):
        var segment := valid_points[index - 1].distance_to(valid_points[index])
        if is_finite(segment) and segment > REPLAY_SPATIAL_EPSILON:
            total_length += segment
    return total_length

func _v175_trail_heading(points: Array, progress: float) -> Vector2:
    # Heading must be sampled in the same arc-length domain as replay position. Delegating to the
    # inherited index-based helper made the camera look in the wrong direction whenever capture
    # density changed sharply along the roll, producing small but obvious yaw snaps on premium TV.
    var valid_points := _replay_spatial_valid_points(points)
    if valid_points.size() < 2:
        return Vector2.UP
    var p := clampf(progress, 0.0, 1.0) if is_finite(progress) else 0.0
    var before_p := maxf(0.0, p - REPLAY_HEADING_SAMPLE_FRACTION)
    var after_p := minf(1.0, p + REPLAY_HEADING_SAMPLE_FRACTION)
    if is_equal_approx(before_p, after_p):
        return (valid_points[valid_points.size() - 1] - valid_points[0]).normalized()
    var before := _v175_trail_point(valid_points, before_p)
    var after := _v175_trail_point(valid_points, after_p)
    var heading := after - before
    if not heading.is_finite() or heading.length_squared() <= REPLAY_SPATIAL_EPSILON * REPLAY_SPATIAL_EPSILON:
        return (valid_points[valid_points.size() - 1] - valid_points[0]).normalized()
    return heading.normalized()

func _v175_trail_point(points: Array, progress: float) -> Vector2:
    var valid_points := _replay_spatial_valid_points(points)
    if valid_points.is_empty():
        return Vector2.ZERO
    if valid_points.size() == 1:
        return valid_points[0]

    var p := clampf(progress, 0.0, 1.0) if is_finite(progress) else 0.0
    var first := valid_points[0]
    var last := valid_points[valid_points.size() - 1]
    if p <= 0.0:
        return first
    if p >= 1.0:
        return last

    var total_length := _v175_trail_total_length(valid_points)
    if total_length <= REPLAY_SPATIAL_EPSILON:
        return first

    var target_length := total_length * p
    var traversed := 0.0
    for index in range(1, valid_points.size()):
        var a := valid_points[index - 1]
        var b := valid_points[index]
        var segment := a.distance_to(b)
        if not is_finite(segment) or segment <= REPLAY_SPATIAL_EPSILON:
            continue
        if traversed + segment >= target_length:
            var local_t := clampf((target_length - traversed) / segment, 0.0, 1.0)
            return a.lerp(b, local_t)
        traversed += segment

    return last

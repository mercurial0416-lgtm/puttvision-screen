extends "res://practice_ring_edge_truth.gd"

# Presentation-only replay pacing. Android V135-V137 / GreenTerrain / GreenReadAdvisor remain
# authoritative. Replay samples can arrive with uneven spacing; sampling by array index made the
# cinematic camera crawl through dense clusters then jump across sparse ones. Normalize replay
# interpolation by traveled arc length so the fixed replay clock produces smooth spatial motion.
const REPLAY_SPATIAL_EPSILON := 0.0001

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

func _replay_spatial_total_length_valid(valid_points: Array[Vector2]) -> float:
    if valid_points.size() < 2:
        return 0.0
    var total_length := 0.0
    for index in range(1, valid_points.size()):
        var segment := valid_points[index - 1].distance_to(valid_points[index])
        if is_finite(segment) and segment > REPLAY_SPATIAL_EPSILON:
            total_length += segment
    return total_length

func _replay_spatial_point_valid(valid_points: Array[Vector2], progress: float, total_length: float) -> Vector2:
    if valid_points.is_empty():
        return Vector2.ZERO
    if valid_points.size() == 1:
        return valid_points[0]

    var p := clampf(progress, 0.0, 1.0) if is_finite(progress) else 0.0
    var first := valid_points[0]
    var last := valid_points[valid_points.size() - 1]
    if p <= 0.0 or total_length <= REPLAY_SPATIAL_EPSILON:
        return first
    if p >= 1.0:
        return last

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

func _v175_trail_total_length(points: Array) -> float:
    # Keep every replay consumer on the same sanitized polyline. Use the allocation-free valid-point
    # helper after the one sanitation pass so camera heading can reuse the same geometry each frame.
    return _replay_spatial_total_length_valid(_replay_spatial_valid_points(points))

func _v175_trail_heading(points: Array, progress: float) -> Vector2:
    # Preserve the cinematic camera's proven 0.18 m / 0.42 m physical-distance tangent blend, but
    # sanitize once and reuse that trail for both windows. Previously the inherited helper called the
    # overridden point/length helpers repeatedly, rebuilding identical arrays several times per frame.
    var valid_points := _replay_spatial_valid_points(points)
    if valid_points.size() < 2:
        return Vector2.UP
    var total_length := _replay_spatial_total_length_valid(valid_points)
    if total_length <= REPLAY_SPATIAL_EPSILON:
        return Vector2.UP

    var p := clampf(progress, 0.0, 1.0) if is_finite(progress) else 0.0
    var near_progress := minf(0.22, V175_HEADING_SAMPLE_M / total_length)
    var wide_progress := minf(0.22, V175_HEADING_WIDE_SAMPLE_M / total_length)
    var near_heading := _replay_spatial_point_valid(valid_points, minf(1.0, p + near_progress), total_length) \
        - _replay_spatial_point_valid(valid_points, maxf(0.0, p - near_progress), total_length)
    var wide_heading := _replay_spatial_point_valid(valid_points, minf(1.0, p + wide_progress), total_length) \
        - _replay_spatial_point_valid(valid_points, maxf(0.0, p - wide_progress), total_length)
    var heading := near_heading * 0.68 + wide_heading * 0.32
    if not heading.is_finite() or heading.length_squared() <= REPLAY_SPATIAL_EPSILON * REPLAY_SPATIAL_EPSILON:
        heading = wide_heading
    if not heading.is_finite() or heading.length_squared() <= REPLAY_SPATIAL_EPSILON * REPLAY_SPATIAL_EPSILON:
        heading = valid_points[valid_points.size() - 1] - valid_points[0]
    return heading.normalized() if heading.is_finite() and heading.length_squared() > REPLAY_SPATIAL_EPSILON * REPLAY_SPATIAL_EPSILON else Vector2.UP

func _v175_trail_point(points: Array, progress: float) -> Vector2:
    var valid_points := _replay_spatial_valid_points(points)
    var total_length := _replay_spatial_total_length_valid(valid_points)
    return _replay_spatial_point_valid(valid_points, progress, total_length)

extends "res://practice_ring_edge_truth.gd"

# Presentation-only replay pacing. Android V135-V137 / GreenTerrain / GreenReadAdvisor remain
# authoritative. Replay samples can arrive with uneven spacing; sampling by array index made the
# cinematic camera crawl through dense clusters then jump across sparse ones. Normalize replay
# interpolation by traveled arc length so the fixed replay clock produces smooth spatial motion.
const REPLAY_SPATIAL_EPSILON := 0.0001

func _replay_spatial_valid_points(points: Array) -> Array[Vector2]:
    var valid: Array[Vector2] = []
    for value in points:
        if typeof(value) != TYPE_VECTOR2:
            continue
        var point := value as Vector2
        if point.is_finite():
            valid.append(point)
    return valid

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

    var total_length := 0.0
    for index in range(1, valid_points.size()):
        var a := valid_points[index - 1]
        var b := valid_points[index]
        var segment := a.distance_to(b)
        if is_finite(segment):
            total_length += segment

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

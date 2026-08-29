extends "res://practice_ring_edge_truth.gd"

# Presentation-only replay pacing. Android V135-V137 / GreenTerrain / GreenReadAdvisor remain
# authoritative. Replay samples can arrive with uneven spacing; sampling by array index made the
# cinematic camera crawl through dense clusters then jump across sparse ones. Normalize replay
# interpolation by traveled arc length so the fixed replay clock produces smooth spatial motion.
const REPLAY_SPATIAL_EPSILON := 0.0001

func _v175_trail_point(points: Array, progress: float) -> Vector2:
    if points.is_empty():
        return Vector2.ZERO
    if points.size() == 1:
        return points[0] as Vector2

    var p := clampf(progress, 0.0, 1.0) if is_finite(progress) else 0.0
    var first := points[0] as Vector2
    var last := points[points.size() - 1] as Vector2
    if p <= 0.0:
        return first
    if p >= 1.0:
        return last

    var total_length := 0.0
    for index in range(1, points.size()):
        var a := points[index - 1] as Vector2
        var b := points[index] as Vector2
        var segment := a.distance_to(b)
        if is_finite(segment):
            total_length += segment

    if total_length <= REPLAY_SPATIAL_EPSILON:
        return first

    var target_length := total_length * p
    var traversed := 0.0
    for index in range(1, points.size()):
        var a := points[index - 1] as Vector2
        var b := points[index] as Vector2
        var segment := a.distance_to(b)
        if not is_finite(segment) or segment <= REPLAY_SPATIAL_EPSILON:
            continue
        if traversed + segment >= target_length:
            var local_t := clampf((target_length - traversed) / segment, 0.0, 1.0)
            return a.lerp(b, local_t)
        traversed += segment

    return last

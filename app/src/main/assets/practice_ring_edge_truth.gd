extends "res://replay_transition_cues.gd"

# Presentation-only production guard for the practice consistency envelope. Keep this production
# override aligned with practice_trend_vector.gd: if the recent group cannot be represented at its
# truthful bounded radius inside the plot, hide the envelope rather than shrinking/capping it into
# a misleadingly tight group. Physics, GreenTerrain and GreenReadAdvisor remain untouched.

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

    var raw_radius := max_distance + PRACTICE_RECENT_RING_PADDING
    var desired_radius := clampf(raw_radius, PRACTICE_RECENT_RING_MIN_RADIUS, PRACTICE_RECENT_RING_MAX_RADIUS)
    var edge_radius := minf(
        minf(center.x, V179_PLOT_SIZE.x - center.x),
        minf(center.y, V179_PLOT_SIZE.y - center.y)
    ) - PRACTICE_RECENT_RING_EDGE_INSET

    if raw_radius > PRACTICE_RECENT_RING_MAX_RADIUS + PRACTICE_RECENT_RING_FIT_EPSILON:
        return {"visible": false, "clipped": true}
    if edge_radius + PRACTICE_RECENT_RING_FIT_EPSILON < desired_radius:
        return {"visible": false, "clipped": true}

    var radius := desired_radius
    if radius <= 0.0:
        return {"visible": false}
    var points := PackedVector2Array()
    for step in range(PRACTICE_RECENT_RING_SEGMENTS + 1):
        var angle := TAU * float(step) / float(PRACTICE_RECENT_RING_SEGMENTS)
        points.append(center + Vector2(cos(angle), sin(angle)) * radius)
    return {"visible": true, "center": center, "radius": radius, "points": points, "clipped": false}

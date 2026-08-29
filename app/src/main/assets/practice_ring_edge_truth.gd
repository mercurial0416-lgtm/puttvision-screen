extends "res://replay_transition_cues.gd"

# Presentation-only correction for practice dispersion at plot boundaries. The previous ring
# implementation shrank its radius to keep a complete circle inside the chart, which made edge
# groups look artificially tighter. Preserve the true scale/location and render only the visible
# arc when the circle crosses the plot boundary. Physics, GreenTerrain and GreenReadAdvisor are
# untouched.

func _practice_ring_point_inside(point: Vector2) -> bool:
    return point.x >= PRACTICE_RECENT_RING_EDGE_INSET \
        and point.y >= PRACTICE_RECENT_RING_EDGE_INSET \
        and point.x <= V179_PLOT_SIZE.x - PRACTICE_RECENT_RING_EDGE_INSET \
        and point.y <= V179_PLOT_SIZE.y - PRACTICE_RECENT_RING_EDGE_INSET

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
    var radius := clampf(
        max_distance + PRACTICE_RECENT_RING_PADDING,
        PRACTICE_RECENT_RING_MIN_RADIUS,
        PRACTICE_RECENT_RING_MAX_RADIUS
    )

    var ring_points: Array[Vector2] = []
    var inside: Array[bool] = []
    for step in range(PRACTICE_RECENT_RING_SEGMENTS):
        var angle := TAU * float(step) / float(PRACTICE_RECENT_RING_SEGMENTS)
        var point := center + Vector2(cos(angle), sin(angle)) * radius
        ring_points.append(point)
        inside.append(_practice_ring_point_inside(point))

    var all_inside := true
    for value in inside:
        if not value:
            all_inside = false
            break
    if all_inside:
        var closed := PackedVector2Array()
        for point in ring_points:
            closed.append(point)
        closed.append(ring_points[0])
        return {"visible": true, "center": center, "radius": radius, "points": closed, "clipped": false}

    # Find the longest contiguous visible arc on the circular sample set. Doubling the index walk
    # handles an arc that crosses the 0/2PI seam without changing scale or moving the centroid.
    var best_len := 0
    var best_end := -1
    var current_len := 0
    for walk in range(PRACTICE_RECENT_RING_SEGMENTS * 2):
        var idx := walk % PRACTICE_RECENT_RING_SEGMENTS
        if inside[idx]:
            current_len = mini(current_len + 1, PRACTICE_RECENT_RING_SEGMENTS)
            if current_len > best_len:
                best_len = current_len
                best_end = walk
        else:
            current_len = 0

    if best_len < 2 or best_end < 0:
        return {"visible": false, "center": center, "radius": radius, "clipped": true}

    var visible_arc := PackedVector2Array()
    var start_walk := best_end - best_len + 1
    for walk in range(start_walk, best_end + 1):
        visible_arc.append(ring_points[walk % PRACTICE_RECENT_RING_SEGMENTS])
    return {"visible": true, "center": center, "radius": radius, "points": visible_arc, "clipped": true}

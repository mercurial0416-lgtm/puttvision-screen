extends "res://practice_ring_boundary_finish.gd"

# Presentation-only green-read landmark spacing. Recommended-read samples are authoritative input;
# this layer only positions the existing apex, start gate, launch vector and directional flow cues by
# traveled path length rather than raw sample index. That keeps the read visually truthful when curve
# samples are unevenly spaced without touching Android V135-V137, GreenTerrain, GreenReadAdvisor,
# aim or scoring.
const READ_SPATIAL_EPSILON := 0.0001
const READ_APEX_MIN_DEVIATION_PX := 0.5

func _read_path_sample(curve: PackedVector2Array, fraction: float) -> Dictionary:
    if curve.is_empty():
        return {"point": Vector2.ZERO, "tangent": Vector2.UP}
    if curve.size() == 1:
        return {"point": curve[0], "tangent": Vector2.UP}

    var p := clampf(fraction, 0.0, 1.0) if is_finite(fraction) else 0.0
    var total_length := 0.0
    for index in range(1, curve.size()):
        var segment_length := curve[index - 1].distance_to(curve[index])
        if is_finite(segment_length):
            total_length += segment_length

    if total_length <= READ_SPATIAL_EPSILON:
        return {"point": curve[0], "tangent": Vector2.UP}

    var target_length := total_length * p
    var traversed := 0.0
    var fallback_tangent := Vector2.UP
    for index in range(1, curve.size()):
        var a := curve[index - 1]
        var b := curve[index]
        var segment_length := a.distance_to(b)
        if not is_finite(segment_length) or segment_length <= READ_SPATIAL_EPSILON:
            continue
        var tangent := (b - a) / segment_length
        fallback_tangent = tangent
        if traversed + segment_length >= target_length:
            var local_t := clampf((target_length - traversed) / segment_length, 0.0, 1.0)
            return {"point": a.lerp(b, local_t), "tangent": tangent}
        traversed += segment_length

    return {"point": curve[curve.size() - 1], "tangent": fallback_tangent}

func _read_baseline_deviation(point: Vector2, start: Vector2, finish: Vector2) -> float:
    var baseline := finish - start
    var baseline_length := baseline.length()
    if baseline_length <= READ_SPATIAL_EPSILON:
        return 0.0
    return absf(baseline.cross(point - start)) / baseline_length

func _read_apex_point(offset_m: float) -> Vector2:
    var curve := _v183_path(offset_m)
    if curve.is_empty():
        return V183_MAP_ORIGIN + V183_MAP_SIZE * 0.5
    if curve.size() < 3:
        return _read_path_sample(curve, 0.5)["point"] as Vector2

    # APEX must mean the strongest visible break, not simply 50% of sample travel. On asymmetric
    # reads (late fall-off, crowns, bowls) the midpoint can sit well before or after the real maximum
    # departure from the ball-to-cup baseline. For a polyline the maximum perpendicular departure
    # occurs at a vertex, so scanning the existing rendered recommendation is exact, allocation-free,
    # and independent of uneven solver sample spacing. This is presentation-only; no read value moves.
    var start := curve[0]
    var finish := curve[curve.size() - 1]
    if start.distance_to(finish) <= READ_SPATIAL_EPSILON:
        return _read_path_sample(curve, 0.5)["point"] as Vector2

    var best_point := _read_path_sample(curve, 0.5)["point"] as Vector2
    var best_deviation := 0.0
    for index in range(1, curve.size() - 1):
        var deviation := _read_baseline_deviation(curve[index], start, finish)
        if is_finite(deviation) and deviation > best_deviation:
            best_deviation = deviation
            best_point = curve[index]

    # Near-straight reads do not have a meaningful geometric apex. Preserve the stable spatial
    # midpoint in that case so tiny floating-point wiggles cannot make the landmark jump around.
    if best_deviation < READ_APEX_MIN_DEVIATION_PX:
        return _read_path_sample(curve, 0.5)["point"] as Vector2
    return best_point

func _read_launch_geometry(offset_m: float) -> Dictionary:
    var curve := _v183_path(offset_m)
    if curve.size() < 3:
        return super._read_launch_geometry(offset_m)

    # The launch cue used to choose its tip from a raw sample index while the other read landmarks
    # already used traveled distance. Uneven solver samples could therefore make the launch arrow
    # visually too short/long and point along a dense neighboring segment. Use the same spatial
    # sampler for a coherent, truthful commercial read overlay.
    var sample := _read_path_sample(curve, READ_LAUNCH_FRACTION)
    var start: Vector2 = curve[0]
    var tip: Vector2 = sample["point"]
    var tangent: Vector2 = sample["tangent"]
    if tangent.length_squared() < 0.5:
        tangent = (tip - start).normalized()
    if tangent.length_squared() < 0.5:
        tangent = Vector2.UP
    var normal := Vector2(-tangent.y, tangent.x)
    var base := tip - tangent * READ_LAUNCH_WING_LENGTH
    return {
        "start": start,
        "tip": tip,
        "left": base + normal * READ_LAUNCH_WING_HALF_WIDTH,
        "right": base - normal * READ_LAUNCH_WING_HALF_WIDTH,
        "tangent": tangent
    }

func _read_start_gate_geometry(offset_m: float) -> Dictionary:
    var curve := _v183_path(offset_m)
    if curve.size() < 3:
        return super._read_start_gate_geometry(offset_m)
    var sample := _read_path_sample(curve, READ_START_GATE_FRACTION)
    var center: Vector2 = sample["point"]
    var tangent: Vector2 = sample["tangent"]
    if tangent.length_squared() < 0.5:
        tangent = Vector2.UP
    var normal := Vector2(-tangent.y, tangent.x)
    return {
        "center": center,
        "left": center + normal * READ_START_GATE_HALF_WIDTH,
        "right": center - normal * READ_START_GATE_HALF_WIDTH,
        "tangent": tangent
    }

func _read_flow_geometry(offset_m: float, fraction: float) -> Dictionary:
    var curve := _v183_path(offset_m)
    if curve.size() < 3:
        return super._read_flow_geometry(offset_m, fraction)
    var sample := _read_path_sample(curve, clampf(fraction, 0.05, 0.95))
    var center: Vector2 = sample["point"]
    var tangent: Vector2 = sample["tangent"]
    if tangent.length_squared() < 0.5:
        tangent = Vector2.UP
    var normal := Vector2(-tangent.y, tangent.x)
    var base := center - tangent * READ_FLOW_TIP_LENGTH
    return {
        "center": center,
        "tip": center + tangent * READ_FLOW_TIP_LENGTH,
        "left": base + normal * READ_FLOW_WING_HALF_WIDTH,
        "right": base - normal * READ_FLOW_WING_HALF_WIDTH,
        "tangent": tangent
    }

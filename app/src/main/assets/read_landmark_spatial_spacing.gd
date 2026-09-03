extends "res://practice_ring_boundary_finish.gd"

# Presentation-only green-read landmark spacing. Recommended-read samples are authoritative input;
# this layer only positions the existing apex, start gate, launch vector and directional flow cues by
# traveled path length rather than raw sample index. That keeps the read visually truthful when curve
# samples are unevenly spaced without touching Android V135-V137, GreenTerrain, GreenReadAdvisor,
# aim or scoring.
const READ_SPATIAL_EPSILON := 0.0001
const READ_APEX_MIN_DEVIATION_PX := 0.5
const READ_TANGENT_SAMPLE_FRACTION := 0.025

func _read_point_is_finite(point: Vector2) -> bool:
    return is_finite(point.x) and is_finite(point.y)

func _read_first_finite_point(curve: PackedVector2Array, fallback: Vector2 = Vector2.ZERO) -> Vector2:
    for point in curve:
        if _read_point_is_finite(point):
            return point
    return fallback

func _read_last_finite_point(curve: PackedVector2Array, fallback: Vector2 = Vector2.ZERO) -> Vector2:
    for index in range(curve.size() - 1, -1, -1):
        var point := curve[index]
        if _read_point_is_finite(point):
            return point
    return fallback

func _read_path_sample(curve: PackedVector2Array, fraction: float) -> Dictionary:
    if curve.is_empty():
        return {"point": Vector2.ZERO, "tangent": Vector2.UP}
    if curve.size() == 1:
        var only_point := curve[0]
        return {"point": only_point if _read_point_is_finite(only_point) else Vector2.ZERO, "tangent": Vector2.UP}

    var p := clampf(fraction, 0.0, 1.0) if is_finite(fraction) else 0.0
    var first_finite := _read_first_finite_point(curve)
    var total_length := 0.0
    for index in range(1, curve.size()):
        var a := curve[index - 1]
        var b := curve[index]
        if not _read_point_is_finite(a) or not _read_point_is_finite(b):
            continue
        var segment_length := a.distance_to(b)
        if is_finite(segment_length):
            total_length += segment_length

    if total_length <= READ_SPATIAL_EPSILON:
        return {"point": first_finite, "tangent": Vector2.UP}

    var target_length := total_length * p
    var traversed := 0.0
    var fallback_tangent := Vector2.UP
    var fallback_point := first_finite
    for index in range(1, curve.size()):
        var a := curve[index - 1]
        var b := curve[index]
        if not _read_point_is_finite(a) or not _read_point_is_finite(b):
            continue
        var segment_length := a.distance_to(b)
        if not is_finite(segment_length) or segment_length <= READ_SPATIAL_EPSILON:
            continue
        var tangent := (b - a) / segment_length
        fallback_tangent = tangent
        fallback_point = b
        if traversed + segment_length >= target_length:
            var local_t := clampf((target_length - traversed) / segment_length, 0.0, 1.0)
            return {"point": a.lerp(b, local_t), "tangent": tangent}
        traversed += segment_length

    return {"point": fallback_point, "tangent": fallback_tangent}

func _read_smoothed_tangent(curve: PackedVector2Array, fraction: float, fallback: Vector2) -> Vector2:
    # Landmark positions remain exactly on the authoritative rendered read path. Directional glyphs,
    # however, should not snap from an incoming segment to an outgoing segment when their spatial
    # anchor lands on a solver vertex. Sample a tiny path-length neighborhood and use its chord as a
    # presentation-only tangent. This produces stable launch/gate/flow orientation without changing
    # any read point, aim value or physics result.
    if curve.size() < 3 or not is_finite(fraction):
        return fallback
    var p := clampf(fraction, 0.0, 1.0)
    var before_fraction := maxf(0.0, p - READ_TANGENT_SAMPLE_FRACTION)
    var after_fraction := minf(1.0, p + READ_TANGENT_SAMPLE_FRACTION)
    if after_fraction - before_fraction <= READ_SPATIAL_EPSILON:
        return fallback
    var before: Vector2 = _read_path_sample(curve, before_fraction)["point"]
    var after: Vector2 = _read_path_sample(curve, after_fraction)["point"]
    var chord := after - before
    if not is_finite(chord.x) or not is_finite(chord.y) or chord.length_squared() <= READ_SPATIAL_EPSILON:
        return fallback
    return chord.normalized()

func _read_baseline_deviation(point: Vector2, start: Vector2, finish: Vector2) -> float:
    if not _read_point_is_finite(point) or not _read_point_is_finite(start) or not _read_point_is_finite(finish):
        return 0.0
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
    # Malformed bridge points are ignored at this display boundary so one bad sample cannot poison
    # every landmark transform; native terrain/read solving remains untouched.
    var start := _read_first_finite_point(curve)
    var finish := _read_last_finite_point(curve, start)
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

    # The launch cue uses traveled distance for both anchor and orientation. The tiny symmetric
    # tangent window keeps the arrow visually continuous at solver vertices while preserving the
    # exact anchor point selected from the authoritative recommendation path.
    var sample := _read_path_sample(curve, READ_LAUNCH_FRACTION)
    var start: Vector2 = _read_first_finite_point(curve)
    var tip: Vector2 = sample["point"]
    var tangent: Vector2 = _read_smoothed_tangent(curve, READ_LAUNCH_FRACTION, sample["tangent"])
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
    var tangent: Vector2 = _read_smoothed_tangent(curve, READ_START_GATE_FRACTION, sample["tangent"])
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
    var p := clampf(fraction, 0.05, 0.95)
    var sample := _read_path_sample(curve, p)
    var center: Vector2 = sample["point"]
    var tangent: Vector2 = _read_smoothed_tangent(curve, p, sample["tangent"])
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

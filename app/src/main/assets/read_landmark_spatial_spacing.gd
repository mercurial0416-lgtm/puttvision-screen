extends "res://practice_ring_boundary_finish.gd"

# Presentation-only green-read landmark spacing. Recommended-read samples are authoritative input;
# this layer only positions the existing apex, start gate and directional flow cues by traveled path
# length rather than raw sample index. That keeps the read visually truthful when curve samples are
# unevenly spaced without touching Android V135-V137, GreenTerrain, GreenReadAdvisor, aim or scoring.
const READ_SPATIAL_EPSILON := 0.0001

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

func _read_apex_point(offset_m: float) -> Vector2:
    var curve := _v183_path(offset_m)
    if curve.is_empty():
        return V183_MAP_ORIGIN + V183_MAP_SIZE * 0.5
    return _read_path_sample(curve, 0.5)["point"] as Vector2

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

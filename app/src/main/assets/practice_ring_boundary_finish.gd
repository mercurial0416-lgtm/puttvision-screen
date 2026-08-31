extends "res://replay_spatial_pacing.gd"

# Presentation-only finish for clipped practice dispersion rings. The true center/radius from the
# existing practice model remain unchanged; this only snaps visible arc endpoints to the exact plot
# boundary instead of stopping at the nearest coarse circle sample. Authoritative physics,
# GreenTerrain and GreenReadAdvisor remain untouched.
const PRACTICE_RING_BOUNDARY_BISECT_STEPS := 16

func _practice_ring_boundary_snap(center: Vector2, radius: float, inside_point: Vector2, outside_angle: float) -> Vector2:
    if radius <= 0.0:
        return inside_point
    var inside_angle := atan2(inside_point.y - center.y, inside_point.x - center.x)
    # Keep the outside endpoint on the same local angular arc as the inside sample. Without this,
    # a boundary crossing around +PI/-PI averages through angle zero and the bisection can jump to
    # the opposite side of the ring instead of snapping the visible arc to the nearby plot edge.
    var outside := inside_angle + wrapf(outside_angle - inside_angle, -PI, PI)
    for _step in range(PRACTICE_RING_BOUNDARY_BISECT_STEPS):
        var mid := (inside_angle + outside) * 0.5
        var point := center + Vector2(cos(mid), sin(mid)) * radius
        if _practice_ring_point_inside(point):
            inside_angle = mid
        else:
            outside = mid
    return center + Vector2(cos(inside_angle), sin(inside_angle)) * radius

func _practice_recent_ring_geometry(samples: Array[Vector2]) -> Dictionary:
    var geometry := super._practice_recent_ring_geometry(samples)
    if not bool(geometry.get("visible", false)) or not bool(geometry.get("clipped", false)):
        return geometry

    var points: PackedVector2Array = geometry.get("points", PackedVector2Array())
    if points.size() < 2:
        return geometry

    var center: Vector2 = geometry.get("center", Vector2.ZERO)
    var radius := float(geometry.get("radius", 0.0))
    if radius <= 0.0:
        return geometry

    var angular_step := TAU / float(PRACTICE_RECENT_RING_SEGMENTS)
    var first := points[0]
    var last := points[points.size() - 1]
    var first_angle := atan2(first.y - center.y, first.x - center.x)
    var last_angle := atan2(last.y - center.y, last.x - center.x)

    # The inherited arc walks forward around the sampled circle. Its immediate predecessor and
    # successor are outside the clipping rectangle, so bisect those angular spans to the true edge.
    points[0] = _practice_ring_boundary_snap(center, radius, first, first_angle - angular_step)
    points[points.size() - 1] = _practice_ring_boundary_snap(center, radius, last, last_angle + angular_step)
    geometry["points"] = points
    geometry["boundary_snapped"] = true
    return geometry

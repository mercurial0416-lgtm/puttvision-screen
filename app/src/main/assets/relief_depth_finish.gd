extends "res://address_relief_camera.gd"

# Presentation-only depth finish for the stationary TV green. Android V135-V137, GreenTerrain,
# GreenReadAdvisor, solver paths and shot coordinates remain authoritative. This layer only increases
# depth cues already derived from the same terrain samples and keeps the work bounded for Forward Mobile.

const RELIEF_DEPTH_CAMERA_LOWER_M := 0.035
const RELIEF_DEPTH_CLEARANCE_GUARD_M := 0.08

func _terrain_relief_material() -> ShaderMaterial:
    var material := super._terrain_relief_material()
    if material == null or material.shader == null:
        return material

    # The base relief shell deliberately stayed subtle to avoid a painted-island look. On a TV that
    # made the already-exaggerated mesh read too flat because the unshaded overlay carried only a
    # six-percent light/dark swing. Strengthen directional hillshade and physical elevation ribbons
    # without adding lights, per-frame geometry, or touching the opaque terrain coordinates.
    var code := material.shader.code
    code = code.replace("mix(0.94, 1.06, primary_hillshade * 0.5 + 0.5)", "mix(0.88, 1.12, primary_hillshade * 0.5 + 0.5)")
    code = code.replace("vec3(0.014, 0.005, -0.012) * cross_hillshade", "vec3(0.024, 0.009, -0.018) * cross_hillshade")
    # Add one broad, continuous form-light lobe from the already encoded terrain grade. Unlike the
    # contour ribbons this reads on every crown/bowl face, so a low TV camera sees actual volume
    # instead of a uniformly tinted sheet. Flats stay neutral through the active blend.
    code = code.replace(
        "float cross_hillshade = clamp(cross_facing * slope_signal, -1.0, 1.0);",
        "float cross_hillshade = clamp(cross_facing * slope_signal, -1.0, 1.0);\n    float form_light = clamp(primary_hillshade * 0.78 + cross_hillshade * 0.22, -1.0, 1.0);\n    float form_lobe = mix(1.0, mix(0.82, 1.18, form_light * 0.5 + 0.5), active);"
    )
    code = code.replace("relief_color *= cross_tint;", "relief_color *= cross_tint;\n    relief_color *= form_lobe;")
    code = code.replace("elevation_ribbon * active * 0.34", "elevation_ribbon * active * 0.44")
    code = code.replace("0.018 + active * (0.072 + 0.012 * abs(height_bias))", "0.026 + active * (0.108 + 0.016 * abs(height_bias))")
    code = code.replace("elevation_ribbon * active * 0.22", "elevation_ribbon * active * 0.28")
    code = code.replace("min(0.32, base_alpha + ribbon_alpha)", "min(0.40, base_alpha + ribbon_alpha)")
    material.shader.code = code
    return material

func _address_relief_camera_plan(ball_world: Vector3, distance_to_cup: float) -> Dictionary:
    var plan := super._address_relief_camera_plan(ball_world, distance_to_cup)
    var clearance_raise := float(plan.get("clearance_raise", 0.0))
    if clearance_raise <= RELIEF_DEPTH_CLEARANCE_GUARD_M:
        var position: Vector3 = plan["position"]
        # A small extra grazing-angle bias exposes crown/bowl silhouette and parallax. It is disabled
        # whenever the inherited sightline guard is already working hard, so the cup cannot disappear
        # behind an exaggerated ridge.
        position.y -= RELIEF_DEPTH_CAMERA_LOWER_M
        plan["position"] = position
    return plan

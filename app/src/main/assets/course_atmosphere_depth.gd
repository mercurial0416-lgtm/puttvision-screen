extends Node

# One-shot presentation-only sky depth pass. It enriches the existing procedural sky shell without
# adding volumetric fog, shadow maps, particles, polling, or any physics/read-state dependency.

const SKY_NODE_NAME := "V161GradientSkyShell"
const ATMOSPHERE_MARKER := "// PUTTVISION_COURSE_ATMOSPHERE_V1"

func _ready() -> void:
    call_deferred("_install_course_atmosphere")

func _install_course_atmosphere() -> void:
    var root := get_tree().current_scene
    if root == null:
        return
    var sky := root.find_child(SKY_NODE_NAME, true, false) as MeshInstance3D
    if sky == null:
        return
    var material := sky.material_override as ShaderMaterial
    if material == null or material.shader == null:
        return
    var source := material.shader.code
    if source.contains(ATMOSPHERE_MARKER):
        return

    var anchor := "    col = mix(col, vec3(0.77, 0.83, 0.82), haze * 0.16);\n    ALBEDO = col;"
    if not source.contains(anchor):
        return

    var replacement := "    col = mix(col, vec3(0.77, 0.83, 0.82), haze * 0.16);\n" + ATMOSPHERE_MARKER + "\n    // A broad warm horizon lobe and a restrained soft sun add broadcast depth without HDRI/fog.\n    float horizon_band = 1.0 - smoothstep(0.035, 0.22, abs(UV.y - 0.50));\n    float sun_dx = min(abs(UV.x - 0.73), 1.0 - abs(UV.x - 0.73));\n    vec2 sun_delta = vec2(sun_dx * 1.75, (UV.y - 0.39) * 1.18);\n    float sun_core = 1.0 - smoothstep(0.018, 0.052, length(sun_delta));\n    float sun_bloom = 1.0 - smoothstep(0.045, 0.155, length(sun_delta));\n    vec3 warm_haze = vec3(0.92, 0.79, 0.61);\n    col = mix(col, warm_haze, horizon_band * 0.075);\n    col += vec3(0.34, 0.25, 0.13) * sun_bloom * 0.16;\n    col = mix(col, vec3(1.0, 0.92, 0.72), sun_core * 0.74);\n    // Slightly deepen the opposite upper sky so foreground trees/flag separate cleanly on TV.\n    float upper_depth = smoothstep(0.62, 0.94, h) * (1.0 - sun_bloom);\n    col *= mix(1.0, 0.94, upper_depth);\n    ALBEDO = col;"

    var patched := source.replace(anchor, replacement)
    if not patched.contains(ATMOSPHERE_MARKER) or not patched.contains("float sun_bloom"):
        return
    material.shader.code = patched
    set_process(false)

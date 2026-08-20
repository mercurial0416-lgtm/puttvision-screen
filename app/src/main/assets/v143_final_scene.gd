extends "res://v143_premium_finish.gd"

# Reference-frame driven finishing pass. The CI preview exposed three presentation issues:
# gray compatibility-renderer sky, blob-like foreground shrubs, and a clubhouse facade
# accidentally authored on the far side of the building. This layer corrects those without
# touching measurement, HFR, bridge snapshots or V135-V137 physics.

func _build_materials() -> void:
    super._build_materials()
    # Reduce daylight clipping on architectural surfaces and give the facade more separation.
    mat_house = _pbr(Color("#aaa494"), 0.88, 0.0)
    mat_roof = _pbr(Color("#252c30"), 0.76, 0.02)
    premium_stone = _pbr(Color("#5f5d57"), 0.95, 0.0)
    premium_deck = _pbr(Color("#715a43"), 0.84, 0.0)
    premium_glass = _pbr(Color("#173746"), 0.17, 0.08)
    premium_trim = _pbr(Color("#ddd9ce"), 0.66, 0.0)

func _build_environment() -> void:
    super._build_environment()
    _build_sky_dome()

func _build_sky_dome() -> void:
    # The GL compatibility reference renderer does not reproduce ProceduralSkyMaterial the
    # same way as the Android mobile renderer. A large inward-facing unshaded sphere gives
    # both paths the same blue daylight horizon while the Environment still supplies lighting.
    var dome := MeshInstance3D.new()
    dome.name = "DeterministicSkyDome"
    var mesh := SphereMesh.new()
    mesh.radius = 95.0
    mesh.height = 190.0
    mesh.radial_segments = 64
    mesh.rings = 32
    dome.mesh = mesh
    dome.position = Vector3(0.0, -14.0, -24.0)

    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_front;
float cloud_noise(vec2 p){
    float a = sin(p.x * 23.0 + sin(p.y * 7.0) * 1.7);
    float b = sin(p.x * 41.0 - p.y * 19.0) * 0.45;
    float c = sin(p.x * 79.0 + p.y * 31.0) * 0.18;
    return (a + b + c) * 0.5 + 0.5;
}
void fragment(){
    float v = clamp(UV.y, 0.0, 1.0);
    float horizon = 1.0 - abs(v - 0.50) * 2.0;
    horizon = pow(clamp(horizon, 0.0, 1.0), 1.7);
    vec3 top = vec3(0.105, 0.33, 0.61);
    vec3 mid = vec3(0.49, 0.69, 0.80);
    vec3 sky = mix(top, mid, horizon * 0.82);
    float n = cloud_noise(UV * vec2(2.0, 1.0));
    float cloud = smoothstep(0.69, 0.86, n) * smoothstep(0.12, 0.52, horizon);
    sky = mix(sky, vec3(0.91, 0.94, 0.94), cloud * 0.42);
    ALBEDO = sky;
    EMISSION = sky * 0.24;
    ROUGHNESS = 1.0;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    dome.material_override = material
    add_child(dome)

func _build_horizon() -> void:
    horizon_root = Node3D.new()
    horizon_root.name = "FinalHorizon"
    add_child(horizon_root)

    _build_pavilion(Vector3(-3.75, 0.0, -2.95))
    _build_fence(-4.15)

    # Photographic CC0 trees stay in the optical far field. Removing the earlier geometric
    # shrubs avoids the obvious green-sphere look while keeping real 3D architecture/parallax.
    var tree_data = [
        [TREE_A, -11.0, -7.6, 4.7, false, 0.84],
        [TREE_A, -8.7, -8.7, 5.1, true, 0.80],
        [TREE_A, -6.3, -10.2, 4.8, false, 0.77],
        [TREE_A, 5.8, -9.4, 4.8, true, 0.81],
        [TREE_A, 8.1, -8.0, 5.3, false, 0.82],
        [TREE_A, 10.7, -9.9, 5.7, true, 0.76],
        [TREE_A, -13.8, -13.0, 5.9, true, 0.72],
        [TREE_A, 14.1, -12.6, 6.1, false, 0.72]
    ]
    for data in tree_data:
        _build_tree_impostor(str(data[0]), Vector3(float(data[1]), 0.0, float(data[2])), float(data[3]), bool(data[4]), float(data[5]))

func _build_pavilion(local_pos: Vector3) -> void:
    var house := Node3D.new()
    house.name = "ModernClubhouseFinal"
    house.position = local_pos
    horizon_root.add_child(house)

    # Warm stone-and-plaster massing.
    _box(house, Vector3(5.20, 0.96, 1.72), Vector3(0.0, 0.70, 0.0), mat_house)
    _box(house, Vector3(5.34, 0.22, 1.82), Vector3(0.0, 0.11, 0.0), premium_stone)
    _box(house, Vector3(5.82, 0.12, 2.16), Vector3(0.04, 1.32, -0.02), mat_roof, Vector3(0.0, 0.0, -4.5))

    # Camera is on +Z looking toward -Z, so the visible facade belongs on +Z.
    for i in range(5):
        var x: float = -1.55 + float(i) * 0.76
        var glass_material: Material = premium_glass if i < 3 else premium_warm_glass
        _box(house, Vector3(0.61, 0.62, 0.036), Vector3(x, 0.72, 0.882), glass_material)
        _box(house, Vector3(0.036, 0.68, 0.065), Vector3(x + 0.34, 0.72, 0.91), premium_trim)

    # Glazed entry and projecting terrace/canopy create real perspective depth.
    _box(house, Vector3(0.92, 0.72, 0.052), Vector3(2.02, 0.70, 0.89), mat_window)
    _box(house, Vector3(4.35, 0.08, 0.92), Vector3(0.18, 0.08, 1.28), premium_deck)
    _box(house, Vector3(3.12, 0.09, 1.02), Vector3(0.34, 1.08, 1.12), mat_roof)
    for x_value in [-1.02, 0.18, 1.38]:
        _box(house, Vector3(0.065, 0.98, 0.065), Vector3(float(x_value), 0.57, 1.20), premium_trim)

    # Stone blade/chimney and side return stop the building reading as a single white box.
    _box(house, Vector3(0.58, 1.58, 1.84), Vector3(-2.24, 0.79, 0.02), premium_stone)
    _box(house, Vector3(0.33, 1.98, 0.46), Vector3(-2.02, 1.10, 0.40), premium_stone)
    _box(house, Vector3(0.16, 0.92, 1.50), Vector3(2.48, 0.54, -0.02), premium_trim)

extends "res://v143_visual_overhaul.gd"

# Final presentation polish layered over the V143 visual overhaul.
# Physics coordinates/quaternion still come exclusively from the Android bridge.

var premium_ball_material: ShaderMaterial
var premium_flag_material: StandardMaterial3D
var premium_pin_dark: StandardMaterial3D

func _build_ball() -> void:
    ball = MeshInstance3D.new()
    ball.name = "PremiumBall"
    var mesh := SphereMesh.new()
    mesh.radius = BALL_RADIUS
    mesh.height = BALL_RADIUS * 2.0
    mesh.radial_segments = 64
    mesh.rings = 36
    ball.mesh = mesh

    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
void fragment(){
    vec2 grid = UV * vec2(34.0, 18.0);
    float row = floor(grid.y);
    grid.x += mod(row, 2.0) * 0.5;
    vec2 cell = fract(grid) - vec2(0.5);
    float r = length(cell);
    float dimple = smoothstep(0.245, 0.145, r);
    vec2 nxy = cell * dimple * 0.62;
    ALBEDO = vec3(0.945, 0.94, 0.91) * (1.0 - dimple * 0.055);
    NORMAL_MAP = vec3(0.5 + nxy.x, 0.5 + nxy.y, 1.0);
    NORMAL_MAP_DEPTH = 0.34;
    ROUGHNESS = mix(0.34, 0.42, dimple);
    SPECULAR = 0.34;
}
"""
    premium_ball_material = ShaderMaterial.new()
    premium_ball_material.shader = shader
    ball.material_override = premium_ball_material
    add_child(ball)

    # A low-profile alignment mark shares the exact physics quaternion with the ball.
    ball_marker = MeshInstance3D.new()
    ball_marker.name = "AlignmentMark"
    var marker_mesh := SphereMesh.new()
    marker_mesh.radius = 0.0028
    marker_mesh.height = 0.0015
    marker_mesh.radial_segments = 16
    marker_mesh.rings = 8
    ball_marker.mesh = marker_mesh
    ball_marker.material_override = mat_dark
    ball_marker.position = Vector3(0.0, BALL_RADIUS * 0.985, 0.0)
    ball_marker.scale = Vector3(1.85, 0.48, 0.72)
    ball.add_child(ball_marker)

func _build_target() -> void:
    target_root = Node3D.new()
    target_root.name = "PremiumTarget"
    add_child(target_root)

    premium_pin_dark = _pbr(Color("#1a2326"), 0.66, 0.0)
    premium_flag_material = _pbr(Color("#c9333a"), 0.58, 0.0)
    premium_flag_material.cull_mode = BaseMaterial3D.CULL_DISABLED

    # Recessed dark cup opening.
    var cup := MeshInstance3D.new()
    var cup_mesh := CylinderMesh.new()
    cup_mesh.top_radius = 0.0535
    cup_mesh.bottom_radius = 0.0515
    cup_mesh.height = 0.045
    cup_mesh.radial_segments = 72
    cup.mesh = cup_mesh
    cup.material_override = mat_dark
    cup.position.y = -0.024
    target_root.add_child(cup)

    # Thin regulation-scale liner/lip; unlike the old white disc this keeps the center open.
    var lip := MeshInstance3D.new()
    var lip_mesh := TorusMesh.new()
    lip_mesh.inner_radius = 0.0505
    lip_mesh.outer_radius = 0.0575
    lip_mesh.rings = 12
    lip_mesh.ring_segments = 64
    lip.mesh = lip_mesh
    lip.material_override = mat_white
    lip.position.y = -0.0015
    target_root.add_child(lip)

    # Segmented tournament-style pin for depth/rotation readability.
    var segment_h := 0.31
    for i in range(6):
        var pole := MeshInstance3D.new()
        var pole_mesh := CylinderMesh.new()
        pole_mesh.top_radius = 0.0054
        pole_mesh.bottom_radius = 0.0054
        pole_mesh.height = segment_h
        pole_mesh.radial_segments = 20
        pole.mesh = pole_mesh
        pole.material_override = mat_white if i % 2 == 0 else premium_pin_dark
        pole.position.y = segment_h * 0.5 + float(i) * segment_h
        target_root.add_child(pole)

    # Lightweight cloth plane. It is double-sided so cup cameras never see a black back face.
    var flag := MeshInstance3D.new()
    var flag_mesh := QuadMesh.new()
    flag_mesh.size = Vector2(0.38, 0.22)
    flag.mesh = flag_mesh
    flag.material_override = premium_flag_material
    flag.position = Vector3(0.195, 1.68, 0.008)
    target_root.add_child(flag)

    # Small dark cap gives the flagstick a finished silhouette.
    var cap := MeshInstance3D.new()
    var cap_mesh := SphereMesh.new()
    cap_mesh.radius = 0.009
    cap_mesh.height = 0.018
    cap_mesh.radial_segments = 16
    cap_mesh.rings = 8
    cap.mesh = cap_mesh
    cap.material_override = premium_pin_dark
    cap.position = Vector3(0.0, 1.865, 0.0)
    target_root.add_child(cap)

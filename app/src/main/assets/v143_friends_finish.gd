extends "res://v143_final_polish.gd"

# Final clean-room calibration toward the publicly visible Friends-style putting presentation.
# No proprietary logos, characters, textures, binaries, or hidden parameters are used.

var address_ring: MeshInstance3D

func _build_materials() -> void:
    super._build_materials()
    mat_green = _broadcast_grass(Color("#47764a"), Vector2(8.0, 25.0), 0.86, 0.20, 0.050)
    mat_fringe = _broadcast_grass(Color("#3a653f"), Vector2(9.5, 21.0), 0.76, 0.28, 0.032)
    mat_rough = _broadcast_grass(Color("#2d5134"), Vector2(12.0, 18.0), 0.66, 0.36, 0.016)

func _build_course() -> void:
    super._build_course()
    address_ring = MeshInstance3D.new()
    address_ring.name = "AddressBallRing"
    var ring_mesh := TorusMesh.new()
    ring_mesh.inner_radius = 0.040
    ring_mesh.outer_radius = 0.046
    ring_mesh.rings = 10
    ring_mesh.ring_segments = 48
    address_ring.mesh = ring_mesh
    var ring_mat := StandardMaterial3D.new()
    ring_mat.albedo_color = Color(0.96, 0.97, 0.93, 0.76)
    ring_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    ring_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    address_ring.material_override = ring_mat
    address_ring.position = Vector3(0.0, 0.0055, 0.0)
    add_child(address_ring)

func _build_hud() -> void:
    var layer := CanvasLayer.new()
    layer.layer = 20
    add_child(layer)
    var root := Control.new()
    root.set_anchors_preset(Control.PRESET_FULL_RECT)
    root.mouse_filter = Control.MOUSE_FILTER_IGNORE
    layer.add_child(root)

    var top_panel := ColorRect.new()
    top_panel.color = Color(0.025, 0.035, 0.040, 0.86)
    top_panel.position = Vector2(696, 18)
    top_panel.size = Vector2(528, 60)
    root.add_child(top_panel)

    var accent := ColorRect.new()
    accent.color = Color("#d9bc4b")
    accent.position = Vector2(0, 58)
    accent.size = Vector2(528, 2)
    top_panel.add_child(accent)

    distance_label = _label(top_panel, Vector2(8, 0), Vector2(190, 58), 21, Color("#f0d45f"))
    stimp_label = _label(top_panel, Vector2(198, 0), Vector2(150, 58), 17, Color("#b7d98f"))
    speed_label = _label(top_panel, Vector2(348, 0), Vector2(172, 58), 18, Color("#f0f2ee"))

    var break_panel := ColorRect.new()
    break_panel.color = Color(0.035, 0.055, 0.060, 0.72)
    break_panel.position = Vector2(1540, 24)
    break_panel.size = Vector2(338, 52)
    root.add_child(break_panel)
    slope_label = _label(break_panel, Vector2(8, 0), Vector2(322, 52), 15, Color(0.94, 0.97, 0.94, 0.92))

    var wait_bg := ColorRect.new()
    wait_bg.color = Color(0.025, 0.035, 0.032, 0.80)
    wait_bg.position = Vector2(910, 958)
    wait_bg.size = Vector2(100, 36)
    root.add_child(wait_bg)
    wait_label = _label(wait_bg, Vector2(0, 0), Vector2(100, 36), 15, Color("#f2f0e7"))

    result_label = _label(root, Vector2(650, 112), Vector2(620, 64), 32, Color.WHITE)
    result_label.visible = false

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    var running: bool = bool(s.get("running", false))
    var holed: bool = bool(s.get("holed", false))
    var lip_out: bool = bool(s.get("lipOut", false))
    if address_ring != null:
        address_ring.position = Vector3(ball.position.x, 0.0055, ball.position.z)
        address_ring.visible = !running and !holed and !lip_out

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    var desired_pos: Vector3
    var desired_look: Vector3
    var desired_fov: float = 42.0
    var cup_world := Vector3(0.0, last_cup_z + 0.026, -target_distance)
    var cup_action: bool = phase == "RIM" or phase == "DROP" or phase == "SETTLED" or distance_to_cup < 0.72

    if cup_action:
        desired_pos = cup_world + Vector3(0.92, 0.33, 0.82)
        desired_look = cup_world + Vector3(0.0, 0.028, 0.0)
        desired_fov = 38.5
    elif running:
        var forward_to_cup: Vector3 = cup_world - ball_world
        forward_to_cup.y = 0.0
        if forward_to_cup.length() < 0.01:
            forward_to_cup = Vector3(0.0, 0.0, -1.0)
        forward_to_cup = forward_to_cup.normalized()
        desired_pos = ball_world - forward_to_cup * 1.42 + Vector3(0.0, 0.37, 0.0)
        var lead: float = min(1.36, max(0.40, distance_to_cup * 0.36))
        desired_look = ball_world + forward_to_cup * lead + Vector3(0.0, 0.040, 0.0)
        desired_fov = 42.0
    else:
        desired_pos = Vector3(0.0, 0.355, 1.36)
        var look_distance: float = min(6.2, max(2.45, target_distance * 0.62))
        desired_look = Vector3(0.0, 0.072, -look_distance)
        desired_fov = 42.0

    if immediate:
        camera_pos = desired_pos
        camera_look = desired_look
    else:
        var pos_alpha: float = 1.0 - exp(-delta * (6.8 if cup_action else 4.9))
        var look_alpha: float = 1.0 - exp(-delta * (8.2 if cup_action else 5.8))
        camera_pos = camera_pos.lerp(desired_pos, pos_alpha)
        camera_look = camera_look.lerp(desired_look, look_alpha)
    camera.position = camera_pos
    camera.fov = lerp(camera.fov, desired_fov, 1.0 if immediate else min(1.0, delta * 4.6))
    camera.look_at(camera_look, Vector3.UP)

func _update_hud(s: Dictionary, running: bool, holed: bool, lip_out: bool, speed: float) -> void:
    distance_label.text = "TARGET  %.1fm" % target_distance
    stimp_label.text = "GREEN  %.1fm" % float(s.get("stimp", 2.8))
    speed_label.text = "%.2f m/s" % speed if running else "PUTTER"
    var side: float = float(s.get("sideSlope", 0.0))
    var long_slope: float = float(s.get("longSlope", 0.0))
    slope_label.text = "BREAK   L/R %+.2f%%   F/B %+.2f%%" % [side, long_slope]
    wait_label.text = "ROLL" if running else "READY"
    result_label.visible = holed or lip_out
    if holed:
        result_label.text = "HOLE IN"
        result_label.add_theme_color_override("font_color", Color("#ffe58a"))
    elif lip_out:
        result_label.text = "LIP OUT"
        result_label.add_theme_color_override("font_color", Color("#ffb4a8"))

extends "res://v167_broadcast_polish.gd"

# Final cleanup driven by the first V167 CI reference frame.

func _build_hud() -> void:
    super._build_hud()
    _v167_final_hide_dead_chrome(self)

func _v167_final_hide_dead_chrome(node: Node) -> void:
    for child_node in node.get_children():
        if child_node is Control:
            var control := child_node as Control
            var rect: Rect2 = control.get_global_rect()
            var top_left_dead: bool = (
                rect.position.x >= 0.0 and rect.position.x <= 60.0
                and rect.position.y >= 0.0 and rect.position.y <= 60.0
                and rect.size.x >= 250.0 and rect.size.x <= 520.0
                and rect.size.y >= 34.0 and rect.size.y <= 100.0
            )
            var bottom_wait_dead: bool = (
                rect.position.x >= 820.0 and rect.position.x <= 1040.0
                and rect.position.y >= 900.0 and rect.position.y <= 1030.0
                and rect.size.x >= 70.0 and rect.size.x <= 190.0
                and rect.size.y >= 24.0 and rect.size.y <= 72.0
            )
            if top_left_dead or bottom_wait_dead:
                control.visible = false
        _v167_final_hide_dead_chrome(child_node)

func _build_course() -> void:
    super._build_course()
    if aim_line != null and aim_line.material_override is StandardMaterial3D:
        var material := aim_line.material_override as StandardMaterial3D
        var c := material.albedo_color
        c.a = 0.24
        material.albedo_color = c

# Taper the first-pass rectangular cloth into a more natural practice-green flag.
func _v167_build_flag_cloth() -> void:
    var vertices := PackedVector3Array()
    var normals := PackedVector3Array()
    var indices := PackedInt32Array()
    var columns := 6
    for i in range(columns):
        var t: float = float(i) / float(columns - 1)
        var x: float = t * 0.40
        var half_h: float = lerp(0.095, 0.064, smoothstep(0.65, 1.0, t))
        var center_y: float = lerp(0.0, 0.012, t)
        var wave: float = sin(t * PI * 1.45) * 0.014
        vertices.append(Vector3(x, center_y + half_h, wave))
        vertices.append(Vector3(x, center_y - half_h, wave + sin(t * PI * 2.0) * 0.004))
        normals.append(Vector3(0.0, 0.0, 1.0))
        normals.append(Vector3(0.0, 0.0, 1.0))
    for i in range(columns - 1):
        var a: int = i * 2
        var b: int = a + 1
        var c: int = a + 2
        var d: int = a + 3
        indices.append_array(PackedInt32Array([a, b, c, c, b, d]))

    var arrays := []
    arrays.resize(Mesh.ARRAY_MAX)
    arrays[Mesh.ARRAY_VERTEX] = vertices
    arrays[Mesh.ARRAY_NORMAL] = normals
    arrays[Mesh.ARRAY_INDEX] = indices
    var mesh := ArrayMesh.new()
    mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)

    var flag := MeshInstance3D.new()
    flag.name = "V167FinalPracticeFlag"
    flag.mesh = mesh
    var material := StandardMaterial3D.new()
    material.albedo_color = Color("#bd3034")
    material.roughness = 0.66
    material.cull_mode = BaseMaterial3D.CULL_DISABLED
    flag.material_override = material
    flag.position = Vector3(0.008, 1.675, 0.006)
    flag.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    target_root.add_child(flag)

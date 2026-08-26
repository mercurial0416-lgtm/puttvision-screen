extends "res://v168_commercial_grid_read.gd"

# V169: live practice-green shape parity.
# Android GreenTerrain remains the physical source of truth. This renderer layer
# makes the selected terrain profile visibly change the TV green footprint too,
# matching the six preview-shape families used by PracticeGreenPreviewView.

var _v169_profile_id := 0

func _v169_shape_limits(nz: float) -> Vector2:
    var z: float = clamp(nz, -1.0, 1.0)
    var t: float = (z + 1.0) * 0.5
    var ellipse: float = sqrt(max(0.0, 1.0 - z * z))
    var center := 0.0
    var width := ellipse * 0.92
    match posmod(_v169_profile_id, 6):
        0:
            # Classic oval.
            center = 0.0
            width = ellipse * 0.92
        1:
            # Left-heavy kidney / right taper.
            center = -0.11 + t * 0.17 + sin(t * PI) * 0.035
            width = ellipse * (0.79 + (1.0 - t) * 0.10)
        2:
            # Broad organic tournament green.
            center = sin(t * PI * 1.25) * 0.055
            width = ellipse * (0.90 + sin(t * PI) * 0.055)
        3:
            # Long narrow oval.
            center = sin(t * PI * 1.4) * 0.025
            width = ellipse * 0.78
        4:
            # Asymmetric hook.
            center = -0.08 + t * 0.15 - sin(t * PI * 1.15) * 0.035
            width = ellipse * (0.80 + t * 0.09)
        _:
            # Compound S-shaped green.
            center = sin((t - 0.12) * PI * 1.55) * 0.10
            width = ellipse * (0.82 + sin(t * PI * 2.0) * 0.045)
    return Vector2(center - max(0.0, width), center + max(0.0, width))

func _v169_inside_shape(x: float, local_z: float, size: Vector2, expansion: float = 0.0) -> bool:
    var nx: float = x / max(0.001, size.x * 0.5)
    var nz: float = local_z / max(0.001, size.y * 0.5)
    if abs(nz) > 1.0 + expansion:
        return false
    var limits := _v169_shape_limits(clamp(nz, -1.0, 1.0))
    return nx >= limits.x - expansion and nx <= limits.y + expansion

func _v166_surface_mesh(size: Vector2, sub_x: int, sub_z: int, world_z_origin: float, encode_read: bool) -> ArrayMesh:
    var vertices := PackedVector3Array()
    var normals := PackedVector3Array()
    var uvs := PackedVector2Array()
    var colors := PackedColorArray()
    var indices := PackedInt32Array()
    var columns: int = sub_x + 1
    var shaped: bool = size.x < 20.0
    var expansion := 0.025 if size.x > 13.0 else 0.0

    for iz in range(sub_z + 1):
        var fz: float = float(iz) / float(max(1, sub_z))
        var local_z: float = lerp(-size.y * 0.5, size.y * 0.5, fz)
        var physics_y: float = -(world_z_origin + local_z)
        for ix in range(sub_x + 1):
            var fx: float = float(ix) / float(max(1, sub_x))
            var x: float = lerp(-size.x * 0.5, size.x * 0.5, fx)
            var terrain := _v166_sample(x, physics_y)
            vertices.append(Vector3(x, terrain.x, local_z))
            normals.append(Vector3(terrain.y * 0.01, 1.0, -terrain.z * 0.01).normalized())
            uvs.append(Vector2(fx, fz))
            if encode_read:
                colors.append(Color(
                    clamp(terrain.x / 4.0 + 0.5, 0.0, 1.0),
                    clamp(terrain.y / 24.0 + 0.5, 0.0, 1.0),
                    clamp(terrain.z / 24.0 + 0.5, 0.0, 1.0),
                    1.0
                ))
            else:
                colors.append(Color.WHITE)

    for iz in range(sub_z):
        var z0: float = lerp(-size.y * 0.5, size.y * 0.5, float(iz) / float(max(1, sub_z)))
        var z1: float = lerp(-size.y * 0.5, size.y * 0.5, float(iz + 1) / float(max(1, sub_z)))
        var cell_z: float = (z0 + z1) * 0.5
        for ix in range(sub_x):
            var x0: float = lerp(-size.x * 0.5, size.x * 0.5, float(ix) / float(max(1, sub_x)))
            var x1: float = lerp(-size.x * 0.5, size.x * 0.5, float(ix + 1) / float(max(1, sub_x)))
            var cell_x: float = (x0 + x1) * 0.5
            if shaped and not _v169_inside_shape(cell_x, cell_z, size, expansion):
                continue
            var a: int = iz * columns + ix
            var b: int = a + 1
            var c: int = a + columns
            var d: int = c + 1
            indices.append_array(PackedInt32Array([a, c, b, b, c, d]))

    var arrays: Array = []
    arrays.resize(Mesh.ARRAY_MAX)
    arrays[Mesh.ARRAY_VERTEX] = vertices
    arrays[Mesh.ARRAY_NORMAL] = normals
    arrays[Mesh.ARRAY_TEX_UV] = uvs
    arrays[Mesh.ARRAY_COLOR] = colors
    arrays[Mesh.ARRAY_INDEX] = indices
    var mesh := ArrayMesh.new()
    if indices.size() >= 3:
        mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
    return mesh

func _v166_ground_open_grass() -> void:
    var green_surface := get_node_or_null("Green") as MeshInstance3D
    var fringe_surface := get_node_or_null("Fringe") as MeshInstance3D
    var grass_nodes: Array = [_v163_green_blades, _v163_fringe_blades]
    for grass_node_variant in grass_nodes:
        var grass_node := grass_node_variant as MultiMeshInstance3D
        if grass_node == null or grass_node.multimesh == null:
            continue
        var is_green: bool = grass_node == _v163_green_blades
        var surface := green_surface if is_green else fringe_surface
        if surface == null:
            continue
        var size := Vector2(11.8, 34.5) if is_green else Vector2(13.8, 36.0)
        var expansion := 0.0 if is_green else 0.035
        var base_y: float = 0.0009 if is_green else -0.0106
        var mm := grass_node.multimesh
        for i in range(mm.instance_count):
            var transform := mm.get_instance_transform(i)
            var world_x: float = grass_node.position.x + transform.origin.x
            var world_z: float = grass_node.position.z + transform.origin.z
            var local_z: float = world_z - surface.position.z
            if _v169_inside_shape(world_x - surface.position.x, local_z, size, expansion):
                var physics_y: float = -world_z
                transform.origin.y = _v166_sample(world_x, physics_y).x + base_y
            else:
                # Keep the deterministic X/Z placement but bury blades outside the
                # selected green footprint so a profile switch can restore them.
                transform.origin.y = -2.5
            mm.set_instance_transform(i, transform)

func _v166_refresh_terrain(key: String) -> void:
    if key == "" or (key == _v166_terrain_key and _v166_terrain_ready) or bridge == null or not bridge.has_method("terrainFieldJson"):
        return
    var parsed = JSON.parse_string(bridge.terrainFieldJson())
    if not (parsed is Dictionary):
        return
    var field := parsed as Dictionary

    # Snapshot and field are transported separately. Never lock a requested key
    # to a payload from another frame; simply retry on the next 16 ms snapshot.
    var payload_key: String = str(field.get("key", ""))
    if payload_key != key:
        return

    var cols: int = int(field.get("cols", 0))
    var rows: int = int(field.get("rows", 0))
    var samples_variant: Variant = field.get("samples", [])
    if cols < 2 or rows < 2 or not (samples_variant is Array):
        return
    var samples_array := samples_variant as Array
    if samples_array.size() < cols * rows * 3:
        return

    _v166_cols = cols
    _v166_rows = rows
    _v166_x_min = float(field.get("xMin", -8.6))
    _v166_x_max = float(field.get("xMax", 8.6))
    _v166_y_min = float(field.get("yMin", -3.0))
    _v166_y_max = float(field.get("yMax", 31.5))
    _v166_samples = samples_array.duplicate()
    _v166_terrain_ready = true
    _v166_terrain_key = key

    _v166_rebuild_surface("Green", Vector2(11.8, 34.5), 30, 86)
    _v166_rebuild_surface("Fringe", Vector2(13.8, 36.0), 24, 64)
    _v166_rebuild_surface("Rough", Vector2(42.0, 72.0), 22, 42)
    if _v164_grid != null:
        _v164_grid.mesh = _v166_surface_mesh(Vector2(V164_GREEN_WIDTH, V164_GREEN_DEPTH), 30, 86, V164_GREEN_CENTER_Z, true)
    _v166_ground_open_grass()
    if _v164_grid_mat != null:
        _v164_grid_mat.set_shader_parameter("terrain_ready", 1.0)

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    # Set profile before V166 refresh runs so the rebuilt geometry gets the new
    # footprint in the same frame as the new Android terrain field.
    var incoming_profile: int = int(s.get("terrainProfile", 0))
    if incoming_profile != _v169_profile_id:
        _v169_profile_id = incoming_profile
        # Force one coherent rebuild even if an upstream caller accidentally
        # reuses a terrain key while changing the visual profile.
        _v166_terrain_key = ""
    super._apply_snapshot(s, immediate, delta)

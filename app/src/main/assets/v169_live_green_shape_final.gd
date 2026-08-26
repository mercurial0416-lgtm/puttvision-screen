extends "res://v169_live_green_shape.gd"

# V169 final: apply the selected-profile footprint even before the Android
# terrain bridge's first frame so startup/reference rendering exercises the same
# shaped Green / Fringe / grid path. Non-preset game mode uses the classic oval.

func _build_course() -> void:
    super._build_course()
    _v166_rebuild_surface("Green", Vector2(11.8, 34.5), 30, 86)
    _v166_rebuild_surface("Fringe", Vector2(13.8, 36.0), 24, 64)
    _v166_rebuild_surface("Rough", Vector2(42.0, 72.0), 22, 42)
    if _v164_grid != null:
        _v164_grid.mesh = _v166_surface_mesh(Vector2(V164_GREEN_WIDTH, V164_GREEN_DEPTH), 30, 86, V164_GREEN_CENTER_Z, true)
    _v166_ground_open_grass()

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    if int(s.get("terrainProfile", 0)) < 0:
        var visual_snapshot := s.duplicate()
        visual_snapshot["terrainProfile"] = 0
        super._apply_snapshot(visual_snapshot, immediate, delta)
        return
    super._apply_snapshot(s, immediate, delta)

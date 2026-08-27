extends "res://v172_final_scene_completion.gd"

var _preview_frames := 0
var _capture_started := false
var _profile_switch_checked := false

func _v171_profile_switch_selftest() -> bool:
    var original_profile: int = _v169_profile_id
    var widths: Dictionary = {}
    for profile in [0, 1, 2, 3, 4, 5, 11, 23]:
        _v169_profile_id = profile
        var mesh := _v166_surface_mesh(Vector2(11.8, 34.5), 18, 42, -14.25, false)
        if mesh == null or mesh.get_surface_count() < 1:
            push_error("V171 profile %d produced no green mesh" % profile)
            _v169_profile_id = original_profile
            return false
        var aabb := mesh.get_aabb()
        if not is_finite(aabb.size.x) or aabb.size.x < 5.0 or aabb.size.x > 13.0:
            push_error("V171 profile %d invalid footprint width %.3f" % [profile, aabb.size.x])
            _v169_profile_id = original_profile
            return false
        widths[int(round(aabb.size.x * 100.0))] = true
    _v169_profile_id = original_profile
    if widths.size() < 4:
        push_error("V171 profile footprints collapsed to %d distinct widths" % widths.size())
        return false
    print("V171_PROFILE_SWITCH_OK=%d" % widths.size())
    return true

func _process(delta: float) -> void:
    super._process(delta)
    _preview_frames += 1

    if not _profile_switch_checked and _preview_frames >= 3:
        _profile_switch_checked = true
        if not _v171_profile_switch_selftest():
            get_tree().quit(3)
            return
        _v169_profile_id = 5
        _v166_rebuild_surface("Green", Vector2(11.8, 34.5), 30, 86)
        _v166_rebuild_surface("Fringe", Vector2(13.8, 36.0), 24, 64)
        if _v164_grid != null:
            _v164_grid.mesh = _v166_surface_mesh(Vector2(V164_GREEN_WIDTH, V164_GREEN_DEPTH), 30, 86, V164_GREEN_CENTER_Z, true)
        _v171_configure_grass_materials()

    if !_capture_started and _preview_frames >= 12:
        _capture_started = true
        _capture_preview.call_deferred()

func _capture_preview() -> void:
    await RenderingServer.frame_post_draw
    var image := get_viewport().get_texture().get_image()
    var output := ProjectSettings.globalize_path("res://v143-preview.png")
    var error := image.save_png(output)
    if error != OK:
        push_error("V143 preview save failed: %s" % error)
        get_tree().quit(2)
        return
    print("V143_PREVIEW_SAVED=" + output)
    get_tree().quit()

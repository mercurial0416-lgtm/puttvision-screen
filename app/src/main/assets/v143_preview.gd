extends "res://v175_cinematic_replay.gd"

var _preview_frames := 0
var _capture_started := false
var _profile_switch_checked := false
var _premium_nodes_checked := false
var _broadcast_hud_checked := false
var _cinematic_replay_checked := false

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

func _v173_premium_selftest() -> bool:
    if _v173_ball_shadow == null:
        push_error("V173 ball contact shadow missing")
        return false
    if _v173_flag_cloth == null or _v173_flag_cloth.mesh == null:
        push_error("V173 animated flag cloth missing")
        return false
    if target_root == null or target_root.get_node_or_null("V173CupLiner") == null:
        push_error("V173 cup liner missing")
        return false
    print("V173_PREMIUM_NODES_OK=1")
    return true

func _v174_hud_selftest() -> bool:
    var hud := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if hud == null:
        push_error("V174 broadcast HUD layer missing")
        return false
    if _v174_remaining_label == null or _v174_surface_label == null or _v174_break_value == null:
        push_error("V174 primary telemetry labels missing")
        return false
    if _v174_result_panel == null or _v174_result_subtitle == null:
        push_error("V174 result package missing")
        return false
    if distance_label == null or speed_label == null or slope_label == null or wait_label == null:
        push_error("V174 inherited HUD contracts not bound")
        return false
    print("V174_BROADCAST_HUD_OK=1")
    return true

func _v175_replay_selftest() -> bool:
    if _v175_replay_panel == null or _v175_replay_fill == null or _v175_replay_marker == null:
        push_error("V175 replay package missing")
        return false
    var synthetic: Array = [Vector2(0.0, 0.0), Vector2(0.4, 1.0), Vector2(0.8, 2.0)]
    var midpoint := _v175_trail_point(synthetic, 0.5)
    if midpoint.distance_to(Vector2(0.4, 1.0)) > 0.001:
        push_error("V175 replay interpolation regression: %s" % midpoint)
        return false
    var heading := _v175_trail_heading(synthetic, 0.5)
    if not is_finite(heading.x) or not is_finite(heading.y) or heading.length() < 0.99:
        push_error("V175 replay heading regression: %s" % heading)
        return false
    print("V175_CINEMATIC_REPLAY_OK=1")
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

    if not _premium_nodes_checked and _preview_frames >= 4:
        _premium_nodes_checked = true
        if not _v173_premium_selftest():
            get_tree().quit(4)
            return

    if not _broadcast_hud_checked and _preview_frames >= 5:
        _broadcast_hud_checked = true
        if not _v174_hud_selftest():
            get_tree().quit(5)
            return

    if not _cinematic_replay_checked and _preview_frames >= 6:
        _cinematic_replay_checked = true
        if not _v175_replay_selftest():
            get_tree().quit(6)
            return

    if !_capture_started and _preview_frames >= 14:
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
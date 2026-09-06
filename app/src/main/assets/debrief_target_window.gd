extends Node

# Presentation-only target bands for the post-shot debrief bars.
# The widths mirror the existing GOOD WINDOW thresholds from v177_shot_debrief.gd:
# START LINE < 1.5 cm on a 30 cm half-scale and PACE < 8 cm on a 70 cm half-scale.
# This helper never mutates telemetry, scoring, camera, GreenTerrain or GreenReadAdvisor state.
const BAR_LEFT_X := 22.0
const BAR_HALF_PX := 75.0
const BAR_CENTER_X := BAR_LEFT_X + BAR_HALF_PX
const LINE_TRACK_Y := 82.0
const PACE_TRACK_Y := 135.0
const BAND_HEIGHT := 13.0
const LINE_GOOD_HALF_PX := BAR_HALF_PX * 1.5 / 30.0
const PACE_GOOD_HALF_PX := BAR_HALF_PX * 8.0 / 70.0
const BAND_COLOR := Color(0.46, 0.84, 0.71, 0.10)
const EDGE_COLOR := Color(0.58, 0.91, 0.78, 0.54)
const IDEAL_COLOR := Color(0.78, 0.98, 0.89, 0.76)

var _installed := false

func _ready() -> void:
    process_priority = 141
    set_process(true)

func _add_rect_if_missing(panel: Control, name_value: String, position_value: Vector2, size_value: Vector2, color_value: Color) -> void:
    if panel.get_node_or_null(name_value) != null:
        return
    var rect := ColorRect.new()
    rect.name = name_value
    rect.position = position_value
    rect.size = size_value
    rect.color = color_value
    rect.mouse_filter = Control.MOUSE_FILTER_IGNORE
    panel.add_child(rect)

func _ensure_target_band(panel: Control, name_value: String, y: float, half_width: float) -> void:
    _add_rect_if_missing(
        panel,
        name_value,
        Vector2(BAR_CENTER_X - half_width, y - 4.0),
        Vector2(half_width * 2.0, BAND_HEIGHT),
        BAND_COLOR
    )
    _add_rect_if_missing(
        panel,
        "%sLeftEdge" % name_value,
        Vector2(BAR_CENTER_X - half_width, y - 4.0),
        Vector2(1.0, BAND_HEIGHT),
        EDGE_COLOR
    )
    _add_rect_if_missing(
        panel,
        "%sRightEdge" % name_value,
        Vector2(BAR_CENTER_X + half_width, y - 4.0),
        Vector2(1.0, BAND_HEIGHT),
        EDGE_COLOR
    )
    # A restrained center tick differentiates the ideal result from the wider acceptable window.
    # It is static presentation geometry, so it adds no per-frame Forward Mobile cost after install.
    _add_rect_if_missing(
        panel,
        "%sIdeal" % name_value,
        Vector2(BAR_CENTER_X - 0.5, y - 5.0),
        Vector2(1.0, BAND_HEIGHT + 2.0),
        IDEAL_COLOR
    )

func _install_target_windows(panel: Control) -> void:
    # Repair each component independently. A partially populated scene can happen after hot reload or
    # another presentation helper racing this installer; treating one existing node as "done" used to
    # leave the other GOOD WINDOW (or its edges) missing for the rest of the session.
    _ensure_target_band(panel, "LineGoodWindow", LINE_TRACK_Y, LINE_GOOD_HALF_PX)
    _ensure_target_band(panel, "PaceGoodWindow", PACE_TRACK_Y, PACE_GOOD_HALF_PX)
    _installed = true
    set_process(false)

func _process(_delta: float) -> void:
    if _installed:
        set_process(false)
        return
    var root := get_parent()
    if root == null:
        return
    var panel := root.find_child("V177ShotDebrief", true, false) as Control
    if panel == null:
        return
    _install_target_windows(panel)

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

var _installed := false

func _ready() -> void:
    process_priority = 141
    set_process(true)

func _add_target_band(panel: Control, name_value: String, y: float, half_width: float) -> void:
    var band := ColorRect.new()
    band.name = name_value
    band.position = Vector2(BAR_CENTER_X - half_width, y - 4.0)
    band.size = Vector2(half_width * 2.0, BAND_HEIGHT)
    band.color = BAND_COLOR
    band.mouse_filter = Control.MOUSE_FILTER_IGNORE
    panel.add_child(band)

    var left_edge := ColorRect.new()
    left_edge.name = "%sLeftEdge" % name_value
    left_edge.position = Vector2(BAR_CENTER_X - half_width, y - 4.0)
    left_edge.size = Vector2(1.0, BAND_HEIGHT)
    left_edge.color = EDGE_COLOR
    left_edge.mouse_filter = Control.MOUSE_FILTER_IGNORE
    panel.add_child(left_edge)

    var right_edge := ColorRect.new()
    right_edge.name = "%sRightEdge" % name_value
    right_edge.position = Vector2(BAR_CENTER_X + half_width, y - 4.0)
    right_edge.size = Vector2(1.0, BAND_HEIGHT)
    right_edge.color = EDGE_COLOR
    right_edge.mouse_filter = Control.MOUSE_FILTER_IGNORE
    panel.add_child(right_edge)

func _install_target_windows(panel: Control) -> void:
    if panel.get_node_or_null("LineGoodWindow") != null:
        _installed = true
        set_process(false)
        return
    _add_target_band(panel, "LineGoodWindow", LINE_TRACK_Y, LINE_GOOD_HALF_PX)
    _add_target_band(panel, "PaceGoodWindow", PACE_TRACK_Y, PACE_GOOD_HALF_PX)
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

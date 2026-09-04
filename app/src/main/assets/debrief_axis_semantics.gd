extends Node

# Presentation-only semantic anchors for the post-shot debrief error bars. The bars already encode
# signed authoritative read/pace deltas around a zero center; these tiny labels make the direction
# readable from TV distance without asking the player to infer what left/right bar growth means.
# No telemetry, scoring, camera, GreenTerrain or GreenReadAdvisor state is changed.
const AXIS_FONT_SIZE := 9
const AXIS_OUTLINE_SIZE := 1
const AXIS_TEXT_COLOR := Color(0.66, 0.73, 0.70, 0.88)
const AXIS_OUTLINE_COLOR := Color(0.015, 0.025, 0.028, 0.88)
const AXIS_LEFT_X := 22.0
const AXIS_RIGHT_X := 112.0
const AXIS_LABEL_WIDTH := 60.0
const LINE_AXIS_Y := 88.0
const PACE_AXIS_Y := 141.0

var _installed := false

func _ready() -> void:
    process_priority = 140
    set_process(true)

func _axis_label(panel: Control, name_value: String, text_value: String, position_value: Vector2, alignment: HorizontalAlignment) -> Label:
    var label := Label.new()
    label.name = name_value
    label.text = text_value
    label.position = position_value
    label.size = Vector2(AXIS_LABEL_WIDTH, 14.0)
    label.horizontal_alignment = alignment
    label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    label.mouse_filter = Control.MOUSE_FILTER_IGNORE
    label.add_theme_font_size_override("font_size", AXIS_FONT_SIZE)
    label.add_theme_constant_override("outline_size", AXIS_OUTLINE_SIZE)
    label.add_theme_color_override("font_color", AXIS_TEXT_COLOR)
    label.add_theme_color_override("font_outline_color", AXIS_OUTLINE_COLOR)
    panel.add_child(label)
    return label

func _install_axis_semantics(panel: Control) -> void:
    if panel.get_node_or_null("LineAxisLeft") != null:
        _installed = true
        set_process(false)
        return

    _axis_label(panel, "LineAxisLeft", "LEFT", Vector2(AXIS_LEFT_X, LINE_AXIS_Y), HORIZONTAL_ALIGNMENT_LEFT)
    _axis_label(panel, "LineAxisRight", "RIGHT", Vector2(AXIS_RIGHT_X, LINE_AXIS_Y), HORIZONTAL_ALIGNMENT_RIGHT)
    _axis_label(panel, "PaceAxisShort", "SHORT", Vector2(AXIS_LEFT_X, PACE_AXIS_Y), HORIZONTAL_ALIGNMENT_LEFT)
    _axis_label(panel, "PaceAxisLong", "LONG", Vector2(AXIS_RIGHT_X, PACE_AXIS_Y), HORIZONTAL_ALIGNMENT_RIGHT)
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
    _install_axis_semantics(panel)

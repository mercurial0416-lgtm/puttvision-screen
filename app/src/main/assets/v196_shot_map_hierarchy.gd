extends "res://v195_practice_bias_vector.gd"

# Presentation-only SHOT MAP hierarchy polish. Recent practice layers added useful GROUP and BIAS
# reads, but their footer labels could collide with the inherited legend. This pass establishes a
# deterministic vertical rhythm and adds a subtle divider without touching physics, terrain,
# green-read, aiming, scoring, or shot capture.

var _v196_footer_divider: Line2D
var _v196_center_legend: Label

const V196_GROUP_Y := 126.0
const V196_BIAS_Y := 139.0
const V196_LEGEND_Y := 153.0
const V196_DETAIL_Y := 170.0

func _v196_find_center_legend() -> Label:
    if _v188_panel == null:
        return null
    for child in _v188_panel.get_children():
        if child is Label and (child as Label).text == "CENTER = READ + PACE":
            return child as Label
    return null

func _build_hud() -> void:
    super._build_hud()
    if _v188_panel == null:
        return

    _v196_center_legend = _v196_find_center_legend()
    if _v196_center_legend != null:
        _v196_center_legend.position = Vector2(14, V196_LEGEND_Y)
        _v196_center_legend.size = Vector2(122, 12)

    if _v194_spread_label != null:
        _v194_spread_label.position = Vector2(14, V196_GROUP_Y)
        _v194_spread_label.size = Vector2(122, 10)
    if _v195_bias_label != null:
        _v195_bias_label.position = Vector2(14, V196_BIAS_Y)
        _v195_bias_label.size = Vector2(122, 10)
    if _v188_detail != null:
        _v188_detail.position = Vector2(10, V196_DETAIL_Y)
        _v188_detail.size = Vector2(130, 36)

    _v196_footer_divider = Line2D.new()
    _v196_footer_divider.name = "ShotMapFooterDivider"
    _v196_footer_divider.width = 1.0
    _v196_footer_divider.default_color = Color(0.76, 0.84, 0.79, 0.14)
    _v196_footer_divider.points = PackedVector2Array([Vector2(20, 149), Vector2(130, 149)])
    _v188_panel.add_child(_v196_footer_divider)

func _v196_rect(control: Control) -> Rect2:
    return Rect2(control.position, control.size)

func _v196_labels_do_not_overlap() -> bool:
    if _v194_spread_label == null or _v195_bias_label == null or _v196_center_legend == null or _v188_detail == null:
        return false
    var controls: Array[Control] = [_v194_spread_label, _v195_bias_label, _v196_center_legend, _v188_detail]
    for i in range(controls.size()):
        for j in range(i + 1, controls.size()):
            if _v196_rect(controls[i]).intersects(_v196_rect(controls[j])):
                return false
    return true

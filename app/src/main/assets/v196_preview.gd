extends "res://v195_preview.gd"

var _v196_checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    if _v196_checks_done or _preview_frames < 13:
        return
    _v196_checks_done = true

    if _v196_center_legend == null or _v196_footer_divider == null:
        push_error("Shot map hierarchy package missing")
        get_tree().quit(27)
        return

    _v179_preview_force_visible = true
    _v179_samples = [
        Vector2(7.0, 18.0),
        Vector2(9.0, 22.0),
        Vector2(5.0, 14.0),
        Vector2(8.0, 20.0)
    ]
    _v188_refresh(8.0, 20.0, true)
    _v179_refresh()

    if not _v196_labels_do_not_overlap():
        push_error("Shot map footer overlap regression")
        get_tree().quit(27)
        return
    if _v194_spread_label.position.y != V196_GROUP_Y or _v195_bias_label.position.y != V196_BIAS_Y:
        push_error("Shot map coaching label hierarchy regression")
        get_tree().quit(27)
        return
    if _v196_center_legend.position.y != V196_LEGEND_Y or _v188_detail.position.y != V196_DETAIL_Y:
        push_error("Shot map legend/detail hierarchy regression")
        get_tree().quit(27)
        return
    if _v196_footer_divider.points.size() != 2:
        push_error("Shot map footer divider regression")
        get_tree().quit(27)
        return

    print("SHOT_MAP_HIERARCHY_OK=1")

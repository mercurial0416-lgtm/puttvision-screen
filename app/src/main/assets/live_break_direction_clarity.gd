extends Node

# Presentation-only TV readability pass for live/replay break telemetry. The underlying labels are
# still populated by the established replay truth chain; this expands terse R/L tokens after update
# so a player can parse direction instantly from viewing distance. No physics/read/scoring state changes.
# A tiny 20 Hz post-parent pass keeps the rendered preview and live HUD deterministic while remaining
# negligible on Forward Mobile (two existing labels, no allocations unless text actually changes).

const REFRESH_INTERVAL_S := 0.05

var _elapsed_s := REFRESH_INTERVAL_S

func _expand_direction(text: String) -> String:
    if text.begins_with("REST R "):
        return text.replace("REST R ", "REST RIGHT ")
    if text.begins_with("REST L "):
        return text.replace("REST L ", "REST LEFT ")
    if text.begins_with("LAST OBS R "):
        return text.replace("LAST OBS R ", "LAST OBS RIGHT ")
    if text.begins_with("LAST OBS L "):
        return text.replace("LAST OBS L ", "LAST OBS LEFT ")
    if text.begins_with("PEAK R "):
        return text.replace("PEAK R ", "PEAK RIGHT ")
    if text.begins_with("PEAK L "):
        return text.replace("PEAK L ", "PEAK LEFT ")
    if text.begins_with("R "):
        return "RIGHT " + text.substr(2)
    if text.begins_with("L "):
        return "LEFT " + text.substr(2)
    return text

func _rewrite_label(root: Node, property_name: String) -> void:
    var candidate = root.get(property_name)
    if candidate is Label:
        var label := candidate as Label
        var expanded := _expand_direction(label.text)
        if expanded != label.text:
            label.text = expanded

func _refresh() -> void:
    var root := get_parent()
    if root == null:
        return
    _rewrite_label(root, "_live_curve_value")
    _rewrite_label(root, "_live_curve_peak_label")

func _process(delta: float) -> void:
    # Parent/root _process executes before child nodes, so this runs after the authoritative HUD has
    # written the current telemetry. Starting hot guarantees the first rendered preview frame is clear.
    _elapsed_s += delta
    if _elapsed_s < REFRESH_INTERVAL_S:
        return
    _elapsed_s = 0.0
    _refresh()

extends Node

# Presentation-only helper for practice-session dispersion clipping. Keep the established TV root
# script intact (many production regression contracts depend on it) and reshape only existing dots
# after the root refreshes them. No new draw nodes, physics writes, terrain writes, or advisor writes.
const SESSION_EDGE_BAR_THIN := 6.0
const SESSION_EDGE_BAR_LONG := 18.0
const SESSION_CORNER_SIZE := Vector2(14.0, 14.0)
const SESSION_LINE_SCALE_CM := 5.0
const SESSION_PACE_SCALE_CM := 15.0

var _last_signature := ""

func _session_dispersion_clip_axes(sample: Vector2) -> Vector2i:
    return Vector2i(
        1 if absf(sample.x) > SESSION_LINE_SCALE_CM else 0,
        1 if absf(sample.y) > SESSION_PACE_SCALE_CM else 0
    )

func _session_dispersion_clipped_size(sample: Vector2) -> Vector2:
    var axes := _session_dispersion_clip_axes(sample)
    if axes.x == 1 and axes.y == 0:
        return Vector2(SESSION_EDGE_BAR_THIN, SESSION_EDGE_BAR_LONG)
    if axes.x == 0 and axes.y == 1:
        return Vector2(SESSION_EDGE_BAR_LONG, SESSION_EDGE_BAR_THIN)
    return SESSION_CORNER_SIZE

func _process(_delta: float) -> void:
    var root := get_parent()
    if root == null or not root.has_method("_session_dispersion_is_outside_view") or not root.has_method("_v179_plot_position"):
        return
    var samples_value = root.get("_v179_samples")
    var points_value = root.get("_v179_points")
    if not samples_value is Array or not points_value is Array:
        return
    var samples: Array = samples_value
    var points: Array = points_value
    var signature := str(samples)
    if signature == _last_signature:
        return
    _last_signature = signature
    var visible_count := mini(samples.size(), points.size())
    for index in range(visible_count):
        var sample = samples[index]
        var dot = points[index]
        if not sample is Vector2 or dot == null:
            continue
        if not bool(root.call("_session_dispersion_is_outside_view", sample)):
            continue
        dot.size = _session_dispersion_clipped_size(sample)
        dot.position = root.call("_v179_plot_position", sample) - dot.size * 0.5

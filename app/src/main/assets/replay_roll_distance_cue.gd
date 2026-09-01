extends Node

# Presentation-only replay roll-distance cue. It annotates the existing replay detail label with
# physical distance remaining along the already-recorded actual ball trail. The trail length is
# cached once when replay starts; per-frame work is constant-time and never touches physics/read data.

var _root: Node
var _detail: Label
var _was_active := false
var _trail_length_m := 0.0

const MIN_SEGMENT_M := 0.000001

func _ready() -> void:
    process_priority = 100
    _root = get_parent()

func _trail_total_length(points: Array) -> float:
    if points.size() < 2:
        return 0.0
    var total := 0.0
    for i in range(1, points.size()):
        var a := points[i - 1] as Vector2
        var b := points[i] as Vector2
        var segment := a.distance_to(b)
        if is_finite(segment) and segment > MIN_SEGMENT_M:
            total += segment
    return total

func _format_remaining(progress: float) -> String:
    var clamped := clampf(progress, 0.0, 1.0)
    var remaining_m := maxf(0.0, _trail_length_m * (1.0 - clamped))
    var percent := int(round(clamped * 100.0))
    if remaining_m < 1.0:
        return "%3d%%  •  %d cm TO REST" % [percent, int(round(remaining_m * 100.0))]
    return "%3d%%  •  %.1f m TO REST" % [percent, remaining_m]

func _process(_delta: float) -> void:
    if _root == null:
        return
    if _detail == null:
        _detail = _root.get("_v175_replay_detail") as Label

    var points_value = _root.get("_v171_replay_actual")
    if typeof(points_value) != TYPE_ARRAY:
        _was_active = false
        return
    var points: Array = points_value
    var remaining := float(_root.get("_v171_replay_remaining"))
    var active := remaining > 0.0 and points.size() >= 2
    if active and not _was_active:
        _trail_length_m = _trail_total_length(points)
    _was_active = active

    if not active or _detail == null:
        return
    var progress := float(_root.call("_v175_replay_progress"))
    _detail.text = _format_remaining(progress)

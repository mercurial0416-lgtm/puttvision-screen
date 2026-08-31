extends "res://replay_timeline_camera_truth.gd"

# Presentation-only polish for the bounded practice dispersion history.
# The base map preallocates five dots, so coloring the fifth slot as "latest" means sessions with
# only one to four completed putts never show a highlighted current rep. Recolor the visible tail
# after each refresh so the newest completed putt is always immediately identifiable.
# Physics, GreenTerrain, GreenReadAdvisor, scoring and shot telemetry remain untouched.

const SESSION_HISTORY_DOT_COLOR := Color("#76d7b6")
const SESSION_LATEST_DOT_COLOR := Color("#f4dda0")

func _v179_refresh() -> void:
    super._v179_refresh()
    var visible_count := mini(_v179_samples.size(), _v179_points.size())
    var latest_index := visible_count - 1
    for index in range(_v179_points.size()):
        var dot := _v179_points[index]
        if dot == null:
            continue
        dot.color = SESSION_LATEST_DOT_COLOR if index == latest_index and latest_index >= 0 else SESSION_HISTORY_DOT_COLOR

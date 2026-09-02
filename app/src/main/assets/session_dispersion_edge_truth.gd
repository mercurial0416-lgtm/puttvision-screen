extends "res://replay_timeline_camera_truth.gd"

# Presentation-only finish for practice-session dispersion clipping. Samples beyond the fixed
# comparison window already clamp to the chart edge; shape those clipped markers into edge bars so
# the viewer can read which boundary the shot escaped instead of mistaking a large square for a
# precise endpoint. Authoritative putting physics, GreenTerrain and GreenReadAdvisor stay untouched.
const SESSION_EDGE_BAR_THIN := 6.0
const SESSION_EDGE_BAR_LONG := 18.0

func _session_dispersion_clip_axes(sample: Vector2) -> Vector2i:
    return Vector2i(
        1 if absf(sample.x) > V179_LINE_SCALE_CM else 0,
        1 if absf(sample.y) > V179_PACE_SCALE_CM else 0
    )

func _session_dispersion_clipped_size(sample: Vector2) -> Vector2:
    var axes := _session_dispersion_clip_axes(sample)
    if axes.x == 1 and axes.y == 0:
        # Left/right escape: a vertical edge bar sits flush against the side boundary.
        return Vector2(SESSION_EDGE_BAR_THIN, SESSION_EDGE_BAR_LONG)
    if axes.x == 0 and axes.y == 1:
        # Short/long escape: a horizontal edge bar sits flush against the pace boundary.
        return Vector2(SESSION_EDGE_BAR_LONG, SESSION_EDGE_BAR_THIN)
    if axes.x == 1 and axes.y == 1:
        # Corner escape carries both directions, so retain the larger square treatment.
        return SESSION_CLIPPED_DOT_SIZE
    return SESSION_NORMAL_DOT_SIZE

func _v179_refresh() -> void:
    super._v179_refresh()
    var visible_count := mini(_v179_samples.size(), _v179_points.size())
    for index in range(visible_count):
        var dot := _v179_points[index]
        if dot == null:
            continue
        var sample := _v179_samples[index]
        if not _session_dispersion_is_outside_view(sample):
            continue
        dot.size = _session_dispersion_clipped_size(sample)
        dot.position = _v179_plot_position(sample) - dot.size * 0.5

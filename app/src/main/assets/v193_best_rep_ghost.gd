extends "res://v192_drill_progression.gd"

# Presentation-only best-prior-rep ghost for practice. The ghost is derived exclusively from
# recent session dispersion samples and never feeds back into Android physics, GreenTerrain,
# GreenReadAdvisor, aiming, scoring, or shot capture.

var _v193_ghost: Line2D
var _v193_ghost_label: Label

const V193_LINE_SCALE_CM := 9.0
const V193_PACE_SCALE_CM := 22.0

func _v193_best_prior_sample() -> Dictionary:
    if _v179_samples.size() < 2:
        return {"found": false, "sample": Vector2.ZERO}
    var best := _v179_samples[0]
    var best_score := INF
    # Exclude the newest sample: the solid SHOT MAP dot already represents the current rep.
    for index in range(_v179_samples.size() - 1):
        var sample: Vector2 = _v179_samples[index]
        var line_n := sample.x / V193_LINE_SCALE_CM
        var pace_n := sample.y / V193_PACE_SCALE_CM
        var score := line_n * line_n + pace_n * pace_n
        if score < best_score:
            best_score = score
            best = sample
    return {"found": true, "sample": best}

func _v193_diamond(radius: float) -> PackedVector2Array:
    return PackedVector2Array([
        Vector2(0.0, -radius),
        Vector2(radius, 0.0),
        Vector2(0.0, radius),
        Vector2(-radius, 0.0),
        Vector2(0.0, -radius)
    ])

func _build_hud() -> void:
    super._build_hud()
    if _v188_panel == null:
        return
    _v193_ghost = Line2D.new()
    _v193_ghost.name = "BestPriorRepGhost"
    _v193_ghost.width = 1.8
    _v193_ghost.default_color = Color(0.46, 0.84, 0.72, 0.72)
    _v193_ghost.points = _v193_diamond(6.4)
    _v193_ghost.visible = false
    _v188_panel.add_child(_v193_ghost)

    _v193_ghost_label = _v174_text(
        _v188_panel,
        Vector2(14, 204),
        Vector2(122, 10),
        "◇ BEST PRIOR",
        7,
        Color(0.50, 0.78, 0.69, 0.88),
        HORIZONTAL_ALIGNMENT_CENTER
    )
    _v193_ghost_label.visible = false
    _v193_refresh_ghost()

func _v193_refresh_ghost() -> void:
    if _v193_ghost == null or _v193_ghost_label == null:
        return
    var best := _v193_best_prior_sample()
    var show := _v188_panel != null and _v188_panel.visible and bool(best.get("found", false))
    _v193_ghost.visible = show
    _v193_ghost_label.visible = show
    if not show:
        return
    var sample: Vector2 = best.get("sample", Vector2.ZERO)
    _v193_ghost.position = _v188_point(sample.x, sample.y)

func _v188_refresh(line_delta_cm: float, pace_delta_cm: float, visible: bool) -> void:
    super._v188_refresh(line_delta_cm, pace_delta_cm, visible)
    _v193_refresh_ghost()

func _v179_refresh() -> void:
    super._v179_refresh()
    _v193_refresh_ghost()

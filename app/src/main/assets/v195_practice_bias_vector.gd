extends "res://v194_dispersion_envelope.gd"

# Presentation-only session bias vector. It turns the recent-shot centroid into an immediate
# coaching read (push/pull + long/short) and never feeds back into Android physics,
# GreenTerrain, GreenReadAdvisor, scoring, aiming, or shot capture.

var _v195_bias_line: Line2D
var _v195_bias_tip: Line2D
var _v195_bias_label: Label

const V195_MIN_SAMPLES := 3
const V195_LINE_DEADBAND_CM := 2.5
const V195_PACE_DEADBAND_CM := 8.0

func _v195_bias_text(mean: Vector2) -> String:
    var line_text := "CENTERED"
    if mean.x > V195_LINE_DEADBAND_CM:
        line_text = "PUSH RIGHT"
    elif mean.x < -V195_LINE_DEADBAND_CM:
        line_text = "PULL LEFT"

    var pace_text := "PACE OK"
    if mean.y > V195_PACE_DEADBAND_CM:
        pace_text = "LONG"
    elif mean.y < -V195_PACE_DEADBAND_CM:
        pace_text = "SHORT"

    return "%s · %s" % [line_text, pace_text]

func _v195_arrow_head(direction: Vector2) -> PackedVector2Array:
    if direction.length() < 0.5:
        return PackedVector2Array()
    var d := direction.normalized()
    var n := Vector2(-d.y, d.x)
    return PackedVector2Array([
        Vector2.ZERO,
        -d * 7.0 + n * 4.0,
        Vector2.ZERO,
        -d * 7.0 - n * 4.0
    ])

func _build_hud() -> void:
    super._build_hud()
    if _v188_panel == null:
        return

    _v195_bias_line = Line2D.new()
    _v195_bias_line.name = "SessionBiasVector"
    _v195_bias_line.width = 2.0
    _v195_bias_line.default_color = Color(0.96, 0.72, 0.28, 0.82)
    _v195_bias_line.visible = false
    _v188_panel.add_child(_v195_bias_line)

    _v195_bias_tip = Line2D.new()
    _v195_bias_tip.name = "SessionBiasArrowTip"
    _v195_bias_tip.width = 2.0
    _v195_bias_tip.default_color = Color(0.98, 0.78, 0.34, 0.88)
    _v195_bias_tip.visible = false
    _v188_panel.add_child(_v195_bias_tip)

    _v195_bias_label = _v174_text(
        _v188_panel,
        Vector2(14, 140),
        Vector2(122, 10),
        "BIAS —",
        7,
        Color(0.96, 0.76, 0.38, 0.94),
        HORIZONTAL_ALIGNMENT_CENTER
    )
    _v195_bias_label.visible = false
    _v195_refresh_bias()

func _v195_refresh_bias() -> void:
    if _v195_bias_line == null or _v195_bias_tip == null or _v195_bias_label == null:
        return
    var show := _v188_panel != null and _v188_panel.visible and _v179_samples.size() >= V195_MIN_SAMPLES
    _v195_bias_line.visible = show
    _v195_bias_tip.visible = show
    _v195_bias_label.visible = show
    if not show:
        return

    var mean := _v194_mean_sample()
    var origin := _v188_point(0.0, 0.0)
    var target := _v188_point(mean.x, mean.y)
    _v195_bias_line.points = PackedVector2Array([origin, target])
    _v195_bias_tip.position = target
    _v195_bias_tip.points = _v195_arrow_head(target - origin)
    _v195_bias_label.text = "BIAS %s" % _v195_bias_text(mean)

func _v188_refresh(line_delta_cm: float, pace_delta_cm: float, visible: bool) -> void:
    super._v188_refresh(line_delta_cm, pace_delta_cm, visible)
    _v195_refresh_bias()

func _v179_refresh() -> void:
    super._v179_refresh()
    _v195_refresh_bias()

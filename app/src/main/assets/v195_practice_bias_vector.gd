extends "res://v194_dispersion_envelope.gd"

# Presentation-only session bias vector. It turns the robust coaching center into an immediate
# push/pull + long/short read while preserving raw averages and every individual shot in the
# dispersion panel. It never feeds back into Android physics, GreenTerrain, GreenReadAdvisor,
# scoring, aiming, or shot capture.

var _v195_bias_line: Line2D
var _v195_bias_tip: Line2D
var _v195_bias_label: Label

const V195_MIN_SAMPLES := 3
const V195_STABLE_SAMPLES := 5
const V195_LINE_DEADBAND_CM := 2.5
const V195_PACE_DEADBAND_CM := 8.0
const V195_EARLY_COLOR := Color(0.93, 0.75, 0.43, 0.88)
const V195_STABLE_COLOR := Color(0.96, 0.76, 0.38, 0.98)

func _v195_coaching_bias() -> Vector2:
    # Keep this read consistent with NEXT REP coaching. A single gross mishit must stay visible in
    # the raw AVG/plot without flipping the player's actionable bias in the opposite direction.
    return Vector2(_v179_coaching_center(0), _v179_coaching_center(1))

func _v195_bias_text(mean: Vector2) -> String:
    var line_text := "CENTER"
    if mean.x > V195_LINE_DEADBAND_CM:
        line_text = "R %.0f CM" % absf(mean.x)
    elif mean.x < -V195_LINE_DEADBAND_CM:
        line_text = "L %.0f CM" % absf(mean.x)

    var pace_text := "PACE OK"
    if mean.y > V195_PACE_DEADBAND_CM:
        pace_text = "LONG %.0f CM" % absf(mean.y)
    elif mean.y < -V195_PACE_DEADBAND_CM:
        pace_text = "SHORT %.0f CM" % absf(mean.y)

    return "%s · %s" % [line_text, pace_text]

func _v195_confidence_text(sample_count: int) -> String:
    return "STABLE" if sample_count >= V195_STABLE_SAMPLES else "EARLY"

func _v195_confidence_color(sample_count: int) -> Color:
    return V195_STABLE_COLOR if sample_count >= V195_STABLE_SAMPLES else V195_EARLY_COLOR

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
        Vector2(8, 140),
        Vector2(134, 10),
        "BIAS —",
        7,
        V195_STABLE_COLOR,
        HORIZONTAL_ALIGNMENT_CENTER
    )
    _v195_bias_label.visible = false
    _v195_refresh_bias()

func _v195_refresh_bias() -> void:
    if _v195_bias_line == null or _v195_bias_tip == null or _v195_bias_label == null:
        return
    var sample_count := _v179_samples.size()
    var show := _v188_panel != null and _v188_panel.visible and sample_count >= V195_MIN_SAMPLES
    _v195_bias_line.visible = show
    _v195_bias_tip.visible = show
    _v195_bias_label.visible = show
    if not show:
        return

    var bias := _v195_coaching_bias()
    var origin := _v188_point(0.0, 0.0)
    var target := _v188_point(bias.x, bias.y)
    _v195_bias_line.points = PackedVector2Array([origin, target])
    _v195_bias_tip.position = target
    _v195_bias_tip.points = _v195_arrow_head(target - origin)
    _v195_bias_label.text = "%s BIAS · %s" % [_v195_confidence_text(sample_count), _v195_bias_text(bias)]
    _v195_bias_label.add_theme_color_override("font_color", _v195_confidence_color(sample_count))

func _v188_refresh(line_delta_cm: float, pace_delta_cm: float, visible: bool) -> void:
    super._v188_refresh(line_delta_cm, pace_delta_cm, visible)
    _v195_refresh_bias()

func _v179_refresh() -> void:
    super._v179_refresh()
    _v195_refresh_bias()

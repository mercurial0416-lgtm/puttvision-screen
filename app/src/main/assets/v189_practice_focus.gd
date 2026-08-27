extends "res://v188_miss_map.gd"

# Presentation-only practice focus coach. It turns the existing recent-session dispersion
# samples into one prioritized correction cue. No value feeds back into Android physics,
# GreenTerrain, GreenReadAdvisor, aiming, or scoring.

var _v189_focus_title: Label
var _v189_focus_value: Label
var _v189_focus_cue: Label
var _v189_focus_meter_bg: ColorRect
var _v189_focus_meter: ColorRect

const V189_LINE_REFERENCE_CM := 12.0
const V189_PACE_REFERENCE_CM := 28.0
const V189_METER_WIDTH := 190.0

func _v189_focus_metric() -> Dictionary:
    if _v179_samples.is_empty():
        return {"kind": "BUILDING", "mean": 0.0, "severity": 0.0}

    var line_mean := _v179_mean(0)
    var pace_mean := _v179_mean(1)
    var line_severity: float = absf(line_mean) / V189_LINE_REFERENCE_CM
    var pace_severity: float = absf(pace_mean) / V189_PACE_REFERENCE_CM

    if line_severity < 0.25 and pace_severity < 0.25:
        return {"kind": "HOLD", "mean": 0.0, "severity": maxf(line_severity, pace_severity)}
    if line_severity >= pace_severity:
        return {"kind": "LINE", "mean": line_mean, "severity": line_severity}
    return {"kind": "PACE", "mean": pace_mean, "severity": pace_severity}

func _v189_focus_copy(metric: Dictionary) -> Dictionary:
    var kind := str(metric.get("kind", "BUILDING"))
    var mean := float(metric.get("mean", 0.0))
    match kind:
        "LINE":
            var bias := "RIGHT" if mean > 0.0 else "LEFT"
            return {
                "title": "START LINE",
                "value": "%s BIAS  %.0f cm" % [bias, absf(mean)],
                "cue": "SQUARE FACE  ·  REMOVE %s BIAS" % bias
            }
        "PACE":
            if mean > 0.0:
                return {"title": "PACE", "value": "LONG  %.0f cm" % absf(mean), "cue": "SOFTER STRIKE  ·  TAKE PACE OFF"}
            return {"title": "PACE", "value": "SHORT  %.0f cm" % absf(mean), "cue": "COMMIT THROUGH  ·  ADD PACE"}
        "HOLD":
            return {"title": "PATTERN", "value": "CENTERED", "cue": "HOLD START LINE  ·  HOLD CUP PACE"}
        _:
            return {"title": "NEXT FOCUS", "value": "BUILDING PATTERN", "cue": "COMPLETE MORE PUTTS FOR A READ"}

func _build_hud() -> void:
    super._build_hud()
    if _v179_panel == null:
        return

    var divider := ColorRect.new()
    divider.position = Vector2(340, 50)
    divider.size = Vector2(1, 112)
    divider.color = Color(0.74, 0.82, 0.82, 0.14)
    divider.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v179_panel.add_child(divider)

    _v174_text(_v179_panel, Vector2(360, 49), Vector2(170, 18), "NEXT FOCUS", 10, Color(0.56, 0.66, 0.67, 0.92))
    _v189_focus_title = _v174_text(_v179_panel, Vector2(360, 68), Vector2(170, 22), "BUILDING", 13, Color("#76d7b6"))
    _v189_focus_value = _v174_text(_v179_panel, Vector2(360, 90), Vector2(170, 25), "BUILDING PATTERN", 15, Color("#f4dda0"))
    _v189_focus_cue = _v174_text(_v179_panel, Vector2(360, 119), Vector2(170, 34), "COMPLETE MORE PUTTS", 9, Color(0.80, 0.86, 0.82, 0.96))
    _v189_focus_cue.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART

    _v189_focus_meter_bg = ColorRect.new()
    _v189_focus_meter_bg.position = Vector2(360, 154)
    _v189_focus_meter_bg.size = Vector2(V189_METER_WIDTH, 4)
    _v189_focus_meter_bg.color = Color(0.75, 0.82, 0.78, 0.12)
    _v189_focus_meter_bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v179_panel.add_child(_v189_focus_meter_bg)

    _v189_focus_meter = ColorRect.new()
    _v189_focus_meter.position = _v189_focus_meter_bg.position
    _v189_focus_meter.size = Vector2(0, 4)
    _v189_focus_meter.color = Color("#76d7b6")
    _v189_focus_meter.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v179_panel.add_child(_v189_focus_meter)
    _v189_refresh()

func _v189_refresh() -> void:
    if _v189_focus_title == null:
        return
    var metric := _v189_focus_metric()
    var copy := _v189_focus_copy(metric)
    _v189_focus_title.text = str(copy["title"])
    _v189_focus_value.text = str(copy["value"])
    _v189_focus_cue.text = str(copy["cue"])

    var severity: float = clampf(float(metric.get("severity", 0.0)), 0.0, 1.0)
    var sample_confidence: float = clampf(float(_v179_samples.size()) / float(V179_HISTORY), 0.2, 1.0) if not _v179_samples.is_empty() else 0.0
    _v189_focus_meter.size.x = V189_METER_WIDTH * severity * sample_confidence
    _v189_focus_meter.color = Color("#76d7b6") if severity < 0.45 else (Color("#f4dda0") if severity < 0.85 else Color("#f0a56d"))

func _v179_refresh() -> void:
    super._v179_refresh()
    _v189_refresh()

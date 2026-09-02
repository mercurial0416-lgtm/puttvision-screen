extends Node

# Presentation-only truth guard for the SESSION DISPERSION next-rep cue.
# The production coach deliberately caps a suggested line adjustment at 9 cm so one bad cluster
# cannot command an extreme address move. When the measured median bias exceeds that cap, the old
# label still printed an exact-looking `9cm LEFT/RIGHT`, hiding that the cue had saturated. Keep the
# safe cap, but mark it as `9cm+` so the TV never implies false precision. Physics, terrain, advisor,
# scoring and the underlying session samples remain untouched.

const COACH_MIN_SAMPLES := 3
const LINE_DEADBAND_CM := 3.0
const LINE_CAP_CM := 9.0

var _last_samples_hash := 0
var _has_samples_hash := false

func _ready() -> void:
    # Run after the root presentation script has refreshed SESSION DISPERSION for this frame.
    process_priority = 130

func _median_line(samples: Array) -> float:
    if samples.is_empty():
        return 0.0
    var values: Array[float] = []
    for sample in samples:
        if sample is Vector2:
            values.append((sample as Vector2).x)
    if values.is_empty():
        return 0.0
    values.sort()
    var middle := int(values.size() / 2)
    if values.size() % 2 == 1:
        return values[middle]
    return (values[middle - 1] + values[middle]) * 0.5

func _truthful_line_cue(samples: Array) -> String:
    if samples.size() < COACH_MIN_SAMPLES:
        return ""
    var line_cm := _median_line(samples)
    if absf(line_cm) <= LINE_DEADBAND_CM:
        return "HOLD LINE"
    var correction_direction := "LEFT" if line_cm > 0.0 else "RIGHT"
    if absf(line_cm) > LINE_CAP_CM:
        return "%dcm+ %s" % [int(LINE_CAP_CM), correction_direction]
    return "%dcm %s" % [clampi(int(round(absf(line_cm))), int(LINE_DEADBAND_CM), int(LINE_CAP_CM)), correction_direction]

func _process(_delta: float) -> void:
    var root := get_parent()
    if root == null or not root.has_method("get"):
        return
    var label := root.get("_v179_tendency_label") as Label
    var samples_variant: Variant = root.get("_v179_samples")
    if label == null or not (samples_variant is Array):
        return
    var samples := samples_variant as Array
    var samples_hash := hash(samples)
    if _has_samples_hash and samples_hash == _last_samples_hash:
        return
    _has_samples_hash = true
    _last_samples_hash = samples_hash

    var cue := _truthful_line_cue(samples)
    if cue.is_empty():
        return
    # Preserve the pace instruction produced by the existing coach; replace only the line cue.
    var pace_separator := label.text.rfind(" · ")
    if pace_separator < 0:
        return
    label.text = "NEXT · %s%s" % [cue, label.text.substr(pace_separator)]

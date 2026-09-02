extends Node

# Presentation-only guard for the completed live-break summary. The parent telemetry script owns the
# measured values; this helper only keeps the PEAK/BACK copy inside its existing TV HUD column.
# GreenTerrain, GreenReadAdvisor, scoring, aiming and shot physics remain untouched.

const RETURN_SEPARATOR := " · BACK "

var _peak_label: Label
var _panel: Panel

func _ready() -> void:
    call_deferred("_bind_live_break_summary")

func _bind_live_break_summary() -> void:
    var root := get_parent()
    if root == null:
        return
    _panel = root.get_node_or_null("V174BroadcastHUD/V174HUDRoot/LiveBreakMeter") as Panel
    if _panel == null:
        return
    _peak_label = _panel.get_node_or_null("LiveBreakPeak") as Label
    if _peak_label == null:
        return

    # The existing 196 px column safely fits either measurement on one line, not both together.
    # Force the return measurement onto a deliberate second line rather than shrinking the whole HUD
    # or allowing it to bleed left into the REST field. The 42 px label already has two-line height.
    _peak_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    _peak_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    _peak_label.clip_text = true

func _process(_delta: float) -> void:
    if _peak_label == null or _panel == null or not _panel.visible:
        return
    if _peak_label.text.contains(RETURN_SEPARATOR):
        _peak_label.text = _peak_label.text.replace(RETURN_SEPARATOR, "\nBACK ")

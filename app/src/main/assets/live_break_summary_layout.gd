extends Node

# Presentation-only guard for the completed live-break summary. The parent telemetry script owns the
# measured values; this helper only keeps the PEAK/BACK copy inside its existing TV HUD column and
# gives the completed read a deliberate broadcast-style visual hierarchy.
# GreenTerrain, GreenReadAdvisor, scoring, aiming and shot physics remain untouched.

const RETURN_SEPARATOR := " · BACK "
const STACKED_RETURN_PREFIX := "\nBACK "
const SUMMARY_FONT_SIZE := 18
const SUMMARY_LINE_SPACING := -2

var _peak_label: Label
var _panel: Panel
var _last_source_text := ""

func _ready() -> void:
    set_process(false)
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
    # Stack the return measurement instead of shrinking the entire HUD. The label already owns
    # enough height for two lines, so this remains allocation-free during live play.
    _peak_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    _peak_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    _peak_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
    _peak_label.clip_text = true
    _peak_label.add_theme_font_size_override("font_size", SUMMARY_FONT_SIZE)
    _peak_label.add_theme_constant_override("line_spacing", SUMMARY_LINE_SPACING)
    set_process(true)

func _process(_delta: float) -> void:
    if _peak_label == null or _panel == null or not _panel.visible:
        return

    var source := _peak_label.text
    if source == _last_source_text:
        return
    _last_source_text = source

    if source.contains(RETURN_SEPARATOR):
        _peak_label.text = source.replace(RETURN_SEPARATOR, STACKED_RETURN_PREFIX)
        _last_source_text = _peak_label.text

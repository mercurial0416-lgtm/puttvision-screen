extends Node

# Presentation-only semantic guard for the GREEN OVERVIEW apex landmark.
# The apex ring already sits on the actual peak of the rendered recommended path. The legacy badge
# incorrectly reused recommended launch offset centimetres, so it labeled one geometric landmark with
# a different quantity. This helper reads only the existing apex geometry and names its visual side.
# It never writes aim, scoring, GreenTerrain, GreenReadAdvisor, or ball/physics state.

const MAP_CENTER_X := 34.0 + 248.0 * 0.5
const APEX_CENTER_DEADBAND_PX := 2.0

var _panel: Control
var _ring: Line2D
var _badge: Label
var _last_ring_x := INF
var _last_text := ""

func _ready() -> void:
    process_priority = 180
    set_process(false)
    call_deferred("_bind_apex_landmark")

func _bind_apex_landmark() -> void:
    var root := get_tree().current_scene
    if root == null:
        return
    _panel = root.find_child("GreenReadOverview", true, false) as Control
    if _panel == null:
        return
    _ring = _panel.get_node_or_null("CommercialReadApexRing") as Line2D
    _badge = _panel.get_node_or_null("CommercialReadApexBadge") as Label
    if _ring == null or _badge == null:
        return
    set_process(true)

func _truthful_apex_text(apex_x: float) -> String:
    if not is_finite(apex_x):
        return "APEX  --"
    var delta := apex_x - MAP_CENTER_X
    if absf(delta) <= APEX_CENTER_DEADBAND_PX:
        return "APEX  CENTER"
    return "APEX  RIGHT" if delta > 0.0 else "APEX  LEFT"

func _process(_delta: float) -> void:
    if _panel == null or _ring == null or _badge == null or not _panel.visible or not _ring.visible:
        return
    var apex_x := _ring.position.x
    var text := _truthful_apex_text(apex_x)
    if is_equal_approx(apex_x, _last_ring_x) and text == _last_text and _badge.text == text:
        return
    _last_ring_x = apex_x
    _last_text = text
    if _badge.text != text:
        _badge.text = text

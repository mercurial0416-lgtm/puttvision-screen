extends "res://live_origin_truth_guard.gd"

# Production presentation override: APEX describes the actual path landmark, while START remains
# the authoritative recommended launch-offset readout in centimetres.

const TRUTHFUL_APEX_CENTER_DEADBAND_PX := 2.0

func _read_apex_descriptor(offset_m: float) -> String:
    if not _read_overlay_telemetry_valid(offset_m):
        return "APEX  --"
    var apex := _read_apex_point(offset_m)
    if not is_finite(apex.x):
        return "APEX  --"
    var center_x := V183_MAP_ORIGIN.x + V183_MAP_SIZE.x * 0.5
    var delta_px := apex.x - center_x
    if absf(delta_px) <= TRUTHFUL_APEX_CENTER_DEADBAND_PX:
        return "APEX  CENTER"
    return "APEX  RIGHT" if delta_px > 0.0 else "APEX  LEFT"

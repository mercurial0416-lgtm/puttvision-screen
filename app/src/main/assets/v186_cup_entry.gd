extends "res://v185_pace_intent.gd"

# Compatibility layer for the former heuristic terminal-speed ring. The commercial read now uses the
# separate cup-entry gate derived from the already-rendered authoritative solver path. Do not invent a
# numeric entry-speed band from distance/grade here: no terminal-speed truth is present in this layer.

var _v186_entry_ring: Line2D
var _v186_entry_label: Label
var _v186_entry_low := 0.0
var _v186_entry_high := 0.0

func _build_hud() -> void:
    super._build_hud()
    # Intentionally do not construct the old CupEntryWindow / ENTRY x.x–x.x controls. The production
    # scene owns one path-derived CUP ENTRY gate, so a second heuristic ring only adds contradictory
    # visual authority and clutter.

func _v186_hide_legacy_entry() -> void:
    # Fail closed after hot reload or mixed packed-scene state where the old nodes may still exist.
    if _v186_entry_ring != null:
        _v186_entry_ring.visible = false
        _v186_entry_ring.points = PackedVector2Array()
    if _v186_entry_label != null:
        _v186_entry_label.visible = false
        _v186_entry_label.text = ""
    _v186_entry_low = 0.0
    _v186_entry_high = 0.0

func _v186_refresh_entry(_distance_m: float, _long_pct: float) -> void:
    _v186_hide_legacy_entry()

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    super._v183_update(s, force_visible)
    _v186_refresh_entry(
        maxf(0.0, float(s.get("distanceToCup", 0.0))),
        float(s.get("longSlope", 0.0))
    )

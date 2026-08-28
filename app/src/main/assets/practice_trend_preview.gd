extends "res://replay_playhead_preview.gd"

const PracticeTrendScene = preload("res://practice_trend_vector.gd")
var _trend_checked := false

func _process(delta: float) -> void:
    super._process(delta)
    if _trend_checked or _preview_frames < 16:
        return
    _trend_checked = true

    var probe = PracticeTrendScene.new()
    var tightening: Array[Vector2] = [Vector2(20, 42), Vector2(17, 34), Vector2(8, 15), Vector2(4, 7)]
    var result := probe._practice_trend_geometry(tightening)
    assert(str(result.get("state", "")) == "TIGHTENING")
    assert(bool(result.get("visible", false)))
    assert(float(result.get("recent_error", 99.0)) < float(result.get("early_error", 0.0)))

    var drifting: Array[Vector2] = [Vector2(4, 7), Vector2(8, 15), Vector2(17, 34), Vector2(20, 42)]
    assert(str(probe._practice_trend_geometry(drifting).get("state", "")) == "DRIFTING")

    var building: Array[Vector2] = [Vector2(10, 20), Vector2(8, 16), Vector2(6, 12)]
    assert(not bool(probe._practice_trend_geometry(building).get("visible", true)))
    probe.free()
    print("PRACTICE_TREND_VECTOR_OK=1")

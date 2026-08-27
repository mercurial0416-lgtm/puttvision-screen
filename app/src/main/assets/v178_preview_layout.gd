extends "res://v143_preview.gd"

# Mirror the production placement in the CI reference render so visual overlap is caught.
func _build_hud() -> void:
    super._build_hud()
    if _v178_panel != null:
        _v178_panel.position = Vector2(44, 640)

extends "res://v178_session_form.gd"

# Shared production placement refinement: keep the session panel clear of the inherited
# lower-left green-read package while leaving the ball-to-cup sight line open.
func _build_hud() -> void:
    super._build_hud()
    if _v178_panel != null:
        _v178_panel.position = Vector2(44, 640)

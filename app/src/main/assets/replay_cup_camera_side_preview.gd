extends "res://read_landmark_spatial_spacing_preview.gd"

var _replay_cup_camera_side_checked := false

func _process(delta: float) -> void:
    super._process(delta)
    if _replay_cup_camera_side_checked or _preview_frames < 20:
        return
    _replay_cup_camera_side_checked = true

    var heading := Vector2.UP
    var cup := Vector2.ZERO
    var side := Vector2(-heading.y, heading.x)
    var right_finish := cup + side * 0.18
    var left_finish := cup - side * 0.18
    var center_finish := cup + side * 0.02

    if _v180_cup_camera_side_sign(right_finish, cup, heading) != -1.0:
        push_error("Replay cup camera must oppose right-side finish")
        get_tree().quit(29)
        return
    if _v180_cup_camera_side_sign(left_finish, cup, heading) != 1.0:
        push_error("Replay cup camera must oppose left-side finish")
        get_tree().quit(29)
        return
    if _v180_cup_camera_side_sign(center_finish, cup, heading) != 1.0:
        push_error("Replay cup camera deadband stability regression")
        get_tree().quit(29)
        return

    print("REPLAY_CUP_CAMERA_SIDE_OK=1")

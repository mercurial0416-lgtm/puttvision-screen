extends "res://address_relief_camera.gd"

# Presentation-only semantic correction for the commercial GREEN READ card.
# Android V135-V137, GreenTerrain and GreenReadAdvisor remain authoritative; this layer only makes
# the direction text agree with the authoritative recommendation and with GREEN OVERVIEW semantics.
func _v165_update_hud(side_pct: float, long_pct: float) -> void:
    if _v165_aim_label == null:
        return

    var side_abs: float = abs(side_pct)
    var aim_text := "AIM CENTER"
    if abs(_v165_recommended_offset) >= 0.015:
        # The recommendation can be supplied directly by the authoritative advisor. Its sign is the
        # source of truth for aim direction; do not infer aim side from the local side slope.
        var aim_dir := "R" if _v165_recommended_offset > 0.0 else "L"
        aim_text = "AIM %s %.2f m" % [aim_dir, abs(_v165_recommended_offset)]

    var break_dir := "STRAIGHT"
    if side_abs >= 0.03:
        # GreenSettings semantics: positive side slope means the right side is lower, so gravity
        # moves the ball right. This matches GREEN OVERVIEW and avoids contradictory read cards.
        break_dir = "BREAK R" if side_pct > 0.0 else "BREAK L"

    _v165_aim_label.text = "%s   |   %s" % [aim_text, _v165_read_level(side_pct, long_pct)]
    _v165_detail_label.text = "%s %.2f%%   |   LIVE FLOW | CONTOUR | CUP 0.125m" % [break_dir, side_abs]

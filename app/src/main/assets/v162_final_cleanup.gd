extends "res://v162_ultra_real_green.gd"

# Final V162 cleanup after CI reference-frame review.
# Legacy visual families come from several inherited generations (V155-V161), can be nested and
# can receive numeric suffixes. Match semantic family names recursively instead of exact node names.

func _v162_hide_legacy_visuals(root: Node) -> void:
    for child_node in root.get_children():
        var node_name := String(child_node.name)
        var lower_name := node_name.to_lower()
        var hide_visual: bool = (
            lower_name.find("cloud") >= 0
            or lower_name.find("conifer") >= 0
            or lower_name.find("pine") >= 0
            or node_name.find("V161GradientSkyShell") >= 0
        )
        if hide_visual and child_node is Node3D:
            (child_node as Node3D).visible = false
        _v162_hide_legacy_visuals(child_node)

func _build_environment() -> void:
    super._build_environment()
    # Let V162NaturalSkyBackdrop be the only authored sky layer in front of the WorldEnvironment.
    _v162_hide_legacy_visuals(self)

func _build_horizon() -> void:
    super._build_horizon()
    # Remove every nested/auto-suffixed legacy cloud and cone-tree instance, regardless of version.
    _v162_hide_legacy_visuals(horizon_root)

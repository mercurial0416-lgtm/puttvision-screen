extends "res://v162_ultra_real_green.gd"

# Final V162 cleanup after CI reference-frame review.
# Some inherited V161 horizon nodes are nested and receive auto-renamed suffixes, so hide them
# recursively instead of relying on direct-child exact-name matching.

func _v162_hide_legacy_visuals(root: Node) -> void:
    for child_node in root.get_children():
        var node_name := String(child_node.name)
        var hide_visual: bool = (
            node_name.find("V161VolumetricMeshCloud") >= 0
            or node_name.find("V161DistantConifer") >= 0
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
    # Remove every nested/auto-suffixed cartoon cloud and cone-tree instance.
    _v162_hide_legacy_visuals(horizon_root)

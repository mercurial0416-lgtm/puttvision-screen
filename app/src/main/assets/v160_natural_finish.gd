extends "res://v160_real_green_cup_test.gd"

# Final V160 grading pass.  The first pass deliberately exaggerated mowing bands so CI/device
# differences were easy to spot; this pass brings them back to a real bentgrass range.

func _build_materials() -> void:
    super._build_materials()
    mat_green = _v160_turf_material(Color("#405f36"), 0.033, 108.0, 0.054, 0.820, 0.034)
    mat_fringe = _v160_turf_material(Color("#365431"), 0.022, 66.0, 0.082, 0.865, 0.039)
    mat_rough = _v160_turf_material(Color("#2d4830"), 0.012, 32.0, 0.120, 0.905, 0.045)

    _v155_leaf_a = _v155_mat(Color("#38583a"), 0.95)
    _v155_leaf_b = _v155_mat(Color("#2e4a32"), 0.96)
    _v155_leaf_c = _v155_mat(Color("#496849"), 0.94)
    _v155_hill_near = _v155_mat(Color("#536b49"), 0.99)
    _v155_hill_far = _v155_mat(Color("#66775b"), 0.99)

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "V160NaturalFinishEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("#76b2d0")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#cedbd4")
    env.ambient_light_energy = 0.36
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.adjustment_enabled = true
    env.adjustment_brightness = 0.86
    env.adjustment_contrast = 1.13
    env.adjustment_saturation = 0.93
    env.fog_enabled = false
    env_node.environment = env
    add_child(env_node)

    var sun := DirectionalLight3D.new()
    sun.name = "V160NaturalSun"
    sun.light_color = Color("#fff0d7")
    sun.light_energy = 0.98
    sun.shadow_enabled = false
    sun.rotation_degrees = Vector3(-43.0, -33.0, 0.0)
    add_child(sun)

    var fill := DirectionalLight3D.new()
    fill.name = "V160NaturalFill"
    fill.light_color = Color("#bfd5da")
    fill.light_energy = 0.17
    fill.shadow_enabled = false
    fill.rotation_degrees = Vector3(-58.0, 137.0, 0.0)
    add_child(fill)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 42.0
    camera.near = 0.014
    camera.far = 145.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

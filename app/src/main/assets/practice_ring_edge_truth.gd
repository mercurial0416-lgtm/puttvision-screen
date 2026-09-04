extends "res://replay_transition_cues.gd"

# Presentation-only production bridge for the practice consistency envelope. The authoritative
# geometry now lives in practice_trend_vector.gd and intentionally hides an envelope whenever the
# recent group cannot be represented at its true bounded radius inside the plot. Delegating here
# prevents this production-layer override from reintroducing clipped arcs or radius capping that
# could make an edge-biased group look artificially tight. Physics, GreenTerrain and
# GreenReadAdvisor remain untouched.

func _practice_recent_ring_geometry(samples: Array[Vector2]) -> Dictionary:
    return super._practice_recent_ring_geometry(samples)

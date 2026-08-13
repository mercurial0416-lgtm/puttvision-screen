package com.puttvision.screen

import android.opengl.Matrix

data class V25ScreenPoint(val x: Float, val y: Float)

/** Shares the exact OpenGL camera matrix with the TV HUD so flag information follows the 3D flag. */
object V25FlagProjectionRuntime {
    private val lock = Any()
    private var mvp: FloatArray? = null
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0

    fun publish(matrix: FloatArray, width: Int, height: Int) {
        if (matrix.size < 16 || width <= 0 || height <= 0) return
        synchronized(lock) {
            mvp = matrix.copyOf(16)
            viewportWidth = width
            viewportHeight = height
        }
    }

    fun project(x: Double, y: Double, z: Double): V25ScreenPoint? {
        val matrix: FloatArray
        val width: Int
        val height: Int
        synchronized(lock) {
            matrix = mvp?.copyOf() ?: return null
            width = viewportWidth
            height = viewportHeight
        }
        val world = floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat(), 1f)
        val clip = FloatArray(4)
        Matrix.multiplyMV(clip, 0, matrix, 0, world, 0)
        val w = clip[3]
        if (w <= 0.0001f || !w.isFinite()) return null
        val nx = clip[0] / w
        val ny = clip[1] / w
        if (!nx.isFinite() || !ny.isFinite() || nx !in -1.08f..1.08f || ny !in -1.08f..1.08f) return null
        return V25ScreenPoint(
            x = (nx * 0.5f + 0.5f) * width,
            y = (1f - (ny * 0.5f + 0.5f)) * height
        )
    }

    fun projectFlag(settings: GreenSettings): V25ScreenPoint? {
        val y = settings.holeDistanceM
        val ground = GreenTerrain.effectiveHeightAt(settings, 0.0, y)
        return project(0.0, y, ground + 0.49)
    }
}

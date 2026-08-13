package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max


data class V22GreenNode(
    val at: Double,
    val sidePct: Double,
    val longPct: Double
)

data class V22CustomGreenProfile(
    val enabled: Boolean,
    val nodes: List<V22GreenNode>
)

object V22CustomGreenRuntime {
    private const val PREF = "puttvision_v22_custom_green"
    private const val KEY_ENABLED = "enabled"
    private val fractions = doubleArrayOf(0.0, .25, .50, .75, 1.0)

    @Volatile var profile = V22CustomGreenProfile(false, defaultNodes())
        private set

    fun install(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val nodes = fractions.mapIndexed { i, at ->
            V22GreenNode(
                at,
                prefs.getFloat("side_$i", 0f).toDouble(),
                prefs.getFloat("long_$i", 0f).toDouble()
            )
        }
        profile = V22CustomGreenProfile(prefs.getBoolean(KEY_ENABLED, false), nodes)
    }

    fun save(context: Context, enabled: Boolean, nodes: List<V22GreenNode>) {
        val normalized = fractions.mapIndexed { i, at ->
            val n = nodes.getOrNull(i) ?: V22GreenNode(at, 0.0, 0.0)
            V22GreenNode(at, n.sidePct.coerceIn(-5.0, 5.0), n.longPct.coerceIn(-5.0, 5.0))
        }
        profile = V22CustomGreenProfile(enabled, normalized)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_ENABLED, enabled)
            normalized.forEachIndexed { i, n ->
                putFloat("side_$i", n.sidePct.toFloat())
                putFloat("long_$i", n.longPct.toFloat())
            }
        }.apply()
    }

    fun disable(context: Context) = save(context, false, profile.nodes)

    fun reset(context: Context) = save(context, false, defaultNodes())

    fun slopeAt(x: Double, y: Double, holeDistanceM: Double): TerrainSlope? {
        val p = profile
        if (!p.enabled || p.nodes.size < 2 || holeDistanceM <= .1) return null
        val t = (y / holeDistanceM).coerceIn(0.0, 1.0)
        val (a, b) = segment(p.nodes, t)
        val span = (b.at - a.at).coerceAtLeast(.001)
        val u = ((t - a.at) / span).coerceIn(0.0, 1.0)
        val side = lerp(a.sidePct, b.sidePct, smooth(u))
        val longBase = lerp(a.longPct, b.longPct, smooth(u))
        // z = -0.01*S(y)*x - integral(L dy); include dS/dy*x in longitudinal slope
        // so render height and physical slope remain mathematically consistent.
        val dsDt = (b.sidePct - a.sidePct) / span * smoothDerivative(u)
        val dsDy = dsDt / holeDistanceM
        return TerrainSlope(side, longBase + dsDy * x)
    }

    fun heightAt(x: Double, y: Double, holeDistanceM: Double): Double? {
        val p = profile
        if (!p.enabled || p.nodes.size < 2 || holeDistanceM <= .1) return null
        val clampedY = y.coerceIn(0.0, holeDistanceM)
        val t = clampedY / holeDistanceM
        val side = interpolateSide(p.nodes, t)
        val longitudinalIntegralPctM = integrateLong(p.nodes, t, holeDistanceM)
        return -0.01 * side * x - 0.01 * longitudinalIntegralPctM
    }

    fun label(): String {
        val p = profile
        if (!p.enabled) return "OFF · 기본/프리셋 그린"
        val maxSide = p.nodes.maxOf { abs(it.sidePct) }
        val maxLong = p.nodes.maxOf { abs(it.longPct) }
        return "ON · 5존 · SIDE ${"%.1f".format(maxSide)}% · GRADE ${"%.1f".format(maxLong)}%"
    }

    private fun integrateLong(nodes: List<V22GreenNode>, tEnd: Double, distance: Double): Double {
        if (tEnd <= 0.0) return 0.0
        // Simpson integration is cheap here (24 samples) and keeps smoothstep interpolation and
        // physics/rendering in agreement without storing a second height curve.
        val n = 24
        val step = tEnd / n
        var sum = 0.0
        for (i in 0..n) {
            val t = i * step
            val v = interpolateLong(nodes, t)
            val weight = when {
                i == 0 || i == n -> 1.0
                i % 2 == 0 -> 2.0
                else -> 4.0
            }
            sum += weight * v
        }
        return sum * step / 3.0 * distance
    }

    private fun interpolateSide(nodes: List<V22GreenNode>, t: Double): Double {
        val (a, b) = segment(nodes, t)
        val u = ((t - a.at) / (b.at - a.at).coerceAtLeast(.001)).coerceIn(0.0, 1.0)
        return lerp(a.sidePct, b.sidePct, smooth(u))
    }

    private fun interpolateLong(nodes: List<V22GreenNode>, t: Double): Double {
        val (a, b) = segment(nodes, t)
        val u = ((t - a.at) / (b.at - a.at).coerceAtLeast(.001)).coerceIn(0.0, 1.0)
        return lerp(a.longPct, b.longPct, smooth(u))
    }

    private fun segment(nodes: List<V22GreenNode>, t: Double): Pair<V22GreenNode, V22GreenNode> {
        val idx = nodes.indexOfLast { it.at <= t }.coerceIn(0, nodes.lastIndex - 1)
        return nodes[idx] to nodes[idx + 1]
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    private fun smooth(t: Double) = t * t * (3.0 - 2.0 * t)
    private fun smoothDerivative(t: Double) = 6.0 * t * (1.0 - t)
    private fun defaultNodes() = fractions.map { V22GreenNode(it, 0.0, 0.0) }
}

fun showV22CustomGreenDialog(context: Context) {
    val draft = V22CustomGreenRuntime.profile.nodes.map { it.copy() }.toMutableList()
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(16), context.pvDp(6), context.pvDp(16), context.pvDp(6))
    }
    root.addView(TextView(context).apply {
        text = "CUSTOM GREEN · 5 ZONES"
        setTextColor(Pv.primary)
        textSize = context.pvSp(7f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .10f
    })
    root.addView(TextView(context).apply {
        text = "공 위치 → 25% → 50% → 75% → 홀 위치의 좌우/오르막 경사를 직접 만듭니다."
        setTextColor(Pv.textMid)
        textSize = context.pvSp(7.5f)
        setPadding(0, context.pvDp(4), 0, context.pvDp(7))
    })

    val labels = listOf("BALL", "25%", "50%", "75%", "CUP")
    draft.forEachIndexed { index, node ->
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = context.pvRounded(Pv.surfaceHi, Pv.rMd, Pv.lineSoft)
            setPadding(context.pvDp(9), context.pvDp(5), context.pvDp(9), context.pvDp(3))
        }
        val title = TextView(context).apply {
            text = labels[index]
            setTextColor(Pv.textHi)
            textSize = context.pvSp(7.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        row.addView(title)
        fun slider(name: String, initial: Double, update: (Double) -> Unit): LinearLayout {
            val value = TextView(context).apply {
                text = "$name ${if (initial >= 0) "+" else ""}${"%.1f".format(initial)}%"
                setTextColor(Pv.textMid)
                textSize = context.pvSp(6.8f)
                gravity = Gravity.CENTER_VERTICAL
            }
            val seek = SeekBar(context).apply {
                max = 100
                progress = (initial * 10 + 50).toInt().coerceIn(0, 100)
                progressTintList = ColorStateList.valueOf(Pv.primary)
                progressBackgroundTintList = ColorStateList.valueOf(Pv.line)
                thumbTintList = ColorStateList.valueOf(Pv.primary)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val v = (progress - 50) / 10.0
                        update(v)
                        value.text = "$name ${if (v >= 0) "+" else ""}${"%.1f".format(v)}%"
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(value, LinearLayout.LayoutParams(context.pvDp(112), -2))
                addView(seek, LinearLayout.LayoutParams(0, context.pvDp(28), 1f))
            }
        }
        row.addView(slider("SIDE", node.sidePct) { v -> draft[index] = draft[index].copy(sidePct = v) })
        row.addView(slider("GRADE", node.longPct) { v -> draft[index] = draft[index].copy(longPct = v) })
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = context.pvDp(4) })
    }

    AlertDialog.Builder(context)
        .setTitle("커스텀 그린")
        .setView(root)
        .setNeutralButton("초기화") { _, _ -> V22CustomGreenRuntime.reset(context) }
        .setNegativeButton("OFF") { _, _ -> V22CustomGreenRuntime.disable(context) }
        .setPositiveButton("적용") { _, _ -> V22CustomGreenRuntime.save(context, true, draft) }
        .show()
}

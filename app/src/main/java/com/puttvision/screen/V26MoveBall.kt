package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object V26BallStartRuntime {
    private const val PREF = "puttvision_v26_move_ball"
    @Volatile private var installed = false
    @Volatile private var storedX = 0.0
    @Volatile private var storedY = 0.0
    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            storedX = java.lang.Double.longBitsToDouble(p.getLong("x", 0L))
            storedY = java.lang.Double.longBitsToDouble(p.getLong("y", 0L))
            installed = true
        }
    }
    fun current(settings: GreenSettings): Pair<Double, Double> {
        val maxX = max(.15, min(1.25, settings.holeDistanceM * .25))
        val maxY = max(0.0, settings.holeDistanceM - .50)
        return storedX.coerceIn(-maxX, maxX) to storedY.coerceIn(0.0, maxY)
    }
    fun isOrigin(settings: GreenSettings): Boolean { val p = current(settings); return kotlin.math.abs(p.first) < .005 && kotlin.math.abs(p.second) < .005 }
    fun set(context: Context, settings: GreenSettings, x: Double, y: Double) {
        install(context)
        val maxX = max(.15, min(1.25, settings.holeDistanceM * .25)); val maxY = max(0.0, settings.holeDistanceM - .50)
        storedX = x.coerceIn(-maxX, maxX); storedY = y.coerceIn(0.0, maxY)
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong("x", java.lang.Double.doubleToRawLongBits(storedX)).putLong("y", java.lang.Double.doubleToRawLongBits(storedY)).apply()
        GreenReadRuntime.clearRuntimeCache()
    }
    fun reset(context: Context, settings: GreenSettings) = set(context, settings, 0.0, 0.0)
    fun label(settings: GreenSettings): String { val p = current(settings); return if (isOrigin(settings)) "기본 시작점" else String.format(Locale.US, "좌우 %+.2f m · 전진 %.2f m", p.first, p.second) }
}

fun showV26MoveBallDialog(context: Context, settings: GreenSettings) {
    V26BallStartRuntime.install(context)
    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(context.pvDp(18), context.pvDp(10), context.pvDp(18), context.pvDp(6)) }
    val status = TextView(context).apply { setTextColor(Pv.textHi); textSize = context.pvSp(10f); typeface = Typeface.DEFAULT_BOLD }
    val hint = TextView(context).apply { text = "실제 매트의 공 위치는 그대로 두고 TV 속 가상 시작점만 옮깁니다. 물리·추천라인·남은거리도 같은 시작점을 사용합니다."; setTextColor(Pv.textMid); textSize = context.pvSp(8f); setPadding(0, context.pvDp(5), 0, context.pvDp(8)) }
    val lateral = SeekBar(context).apply { max = 200 }; val forward = SeekBar(context).apply { max = 200 }
    fun caption(value: String) = TextView(context).apply { text = value; setTextColor(Pv.textLo); textSize = context.pvSp(7f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.START }
    root.addView(status); root.addView(hint); root.addView(caption("좌우 위치")); root.addView(lateral); root.addView(caption("홀 방향 전진")); root.addView(forward)
    val maxX = max(.15, min(1.25, settings.holeDistanceM * .25)); val maxY = max(0.0, settings.holeDistanceM - .50); val start = V26BallStartRuntime.current(settings)
    lateral.progress = (((start.first / maxX + 1.0) * .5 * 200.0).toInt()).coerceIn(0, 200)
    forward.progress = if (maxY <= .001) 0 else ((start.second / maxY * 200.0).toInt()).coerceIn(0, 200)
    fun apply() { val x = ((lateral.progress / 200.0) * 2.0 - 1.0) * maxX; val y = if (maxY <= .001) 0.0 else forward.progress / 200.0 * maxY; V26BallStartRuntime.set(context, settings, x, y); status.text = V26BallStartRuntime.label(settings) }
    val listener = object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) apply() }; override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit; override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit }
    lateral.setOnSeekBarChangeListener(listener); forward.setOnSeekBarChangeListener(listener); apply()
    AlertDialog.Builder(context).setTitle("MOVE BALL").setView(root).setNeutralButton("기본 위치") { _, _ -> V26BallStartRuntime.reset(context, settings) }.setPositiveButton("완료", null).show()
}

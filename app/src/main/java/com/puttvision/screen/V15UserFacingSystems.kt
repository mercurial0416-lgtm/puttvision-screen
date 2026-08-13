package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

object V15PutterFitRuntime {
    @Volatile var latest: V15PutterFitRecommendation? = null
        private set

    @Volatile var currentPutterName: String = ProductRuntime.putterProfileName
        private set

    @Volatile var currentSampleCount: Int = 0
        private set

    fun update(records: List<ShotRecord>) {
        val putter = ProductRuntime.putterProfileName
        currentPutterName = putter
        currentSampleCount = records.count { it.putterProfileName == putter }
        latest = V15PutterFitter.fit(records, putter)
    }

    fun clear() {
        latest = null
        currentSampleCount = 0
        currentPutterName = ProductRuntime.putterProfileName
    }
}

fun showV15PutterFitDialog(context: Context) {
    val fit = V15PutterFitRuntime.latest
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(18), context.pvDp(8), context.pvDp(18), context.pvDp(8))
    }

    fun text(value: String, size: Float, strong: Boolean = false, accent: Boolean = false): TextView =
        TextView(context).apply {
            this.text = value
            setTextColor(if (accent) Pv.primary else if (strong) Pv.textHi else Pv.textMid)
            textSize = context.pvSp(size)
            if (strong) typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }

    root.addView(text("AI PUTTER FIT", 7f, strong = true, accent = true))
    root.addView(text("${V15PutterFitRuntime.currentPutterName} 기준 카메라 스트로크 데이터", 8f).apply {
        setPadding(0, context.pvDp(4), 0, context.pvDp(12))
    })

    if (fit == null) {
        val n = V15PutterFitRuntime.currentSampleCount
        root.addView(text("${n}/20구", 26f, strong = true, accent = true))
        root.addView(text("같은 퍼터로 최소 20구가 쌓이면 밸런스·헤드 타입·시험 조정값을 자동 추천합니다.", 9f).apply {
            setPadding(0, context.pvDp(8), 0, context.pvDp(6))
        })
    } else {
        val confidence = (fit.confidence * 100.0).roundToInt()
        root.addView(text("${fit.balance.label}  ·  ${fit.head.label}", 16f, strong = true, accent = true))
        root.addView(text("신뢰도 ${confidence}%  ·  ${fit.sampleCount}구", 8f).apply {
            setPadding(0, context.pvDp(4), 0, context.pvDp(12))
        })

        val length = when {
            fit.suggestedLengthDeltaMm > 0 -> "+${fit.suggestedLengthDeltaMm}mm 시험"
            fit.suggestedLengthDeltaMm < 0 -> "${fit.suggestedLengthDeltaMm}mm 시험"
            else -> "현재 길이 유지"
        }
        val lie = when {
            fit.suggestedLieDeltaDeg > 0 -> "+${"%.1f".format(fit.suggestedLieDeltaDeg)}° 시험"
            fit.suggestedLieDeltaDeg < 0 -> "${"%.1f".format(fit.suggestedLieDeltaDeg)}° 시험"
            else -> "현재 라이 유지"
        }

        listOf(
            "권장 밸런스" to fit.balance.label,
            "권장 헤드" to fit.head.label,
            "길이" to length,
            "라이" to lie
        ).forEach { (label, value) ->
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, context.pvDp(5), 0, context.pvDp(5))
                addView(text(label, 8f), LinearLayout.LayoutParams(0, -2, .42f))
                addView(text(value, 9f, strong = true), LinearLayout.LayoutParams(0, -2, .58f))
            })
        }
        root.addView(text(fit.reason, 7.5f).apply { setPadding(0, context.pvDp(10), 0, context.pvDp(4)) })
        root.addView(text("※ 길이/라이는 폰 영상만으로 확정하는 피팅값이 아니라 실제 퍼터에서 시험할 작은 조정 범위입니다.", 7f))
    }

    AlertDialog.Builder(context)
        .setTitle("퍼터 피팅")
        .setView(root)
        .setPositiveButton("확인", null)
        .show()
}

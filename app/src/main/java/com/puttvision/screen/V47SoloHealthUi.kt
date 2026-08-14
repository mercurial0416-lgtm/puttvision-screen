package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView

fun showV47SoloHealthDialog(context: Context) {
    val snapshot = V47SoloIntegrityRuntime.health()
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(18), context.pvDp(8), context.pvDp(18), context.pvDp(8))
    }

    fun text(value: String, size: Float, strong: Boolean = false, accent: Boolean = false) = TextView(context).apply {
        this.text = value
        textSize = context.pvSp(size)
        setTextColor(if (accent) Pv.primary else if (strong) Pv.textHi else Pv.textMid)
        if (strong) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    root.addView(text("SOLO SYSTEM HEALTH", 7f, true, true))
    root.addView(text(snapshot.shortLabel, 17f, true, snapshot.score >= 80).apply {
        setPadding(0, context.pvDp(7), 0, context.pvDp(5))
    })
    root.addView(text(
        if (snapshot.insufficientData) "표본이 아직 부족해 READY 판정을 하지 않습니다."
        else "샷 입력·기록·HFR·데이터 커버리지의 소프트웨어 무결성 점수입니다.",
        8f
    ))

    snapshot.sections.forEach { section ->
        root.addView(text(
            "${section.name} · ${section.status} · ${section.score}/100${if (section.optional) " · OPTIONAL" else ""}",
            8.5f,
            strong = true,
            accent = section.score >= 85
        ).apply { setPadding(0, context.pvDp(8), 0, 0) })
        root.addView(text(section.detail, 7.3f))
    }

    root.addView(text("NEXT ACTION", 7f, true, true).apply { setPadding(0, context.pvDp(12), 0, context.pvDp(3)) })
    snapshot.nextActions.forEachIndexed { index, action ->
        root.addView(text("${index + 1}. $action", 8f, index == 0))
    }
    root.addView(text(
        "※ SOLO HEALTH는 앱 내부 데이터/성능 상태를 보는 진단입니다. 갤럭시 S25 또는 기준장비와의 실측 정확도 인증을 대신하지 않습니다.",
        6.8f
    ).apply { setPadding(0, context.pvDp(12), 0, 0) })

    AlertDialog.Builder(context)
        .setTitle("솔로 시스템 상태")
        .setView(root)
        .setPositiveButton("닫기", null)
        .show()
}

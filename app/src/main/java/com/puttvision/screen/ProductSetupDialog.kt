package com.puttvision.screen

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

fun showProductSetupDialog(
    context: Context,
    putterStore: PutterProfileStore,
    matManager: MatCalibrationManager,
    voiceCoach: HandsFreeVoiceCoach
) {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(16), context.pvDp(8), context.pvDp(16), context.pvDp(8))
    }
    val scroll = ScrollView(context).apply {
        isFillViewport = true
        addView(root, FrameLayout.LayoutParams(-1, -2))
    }

    root.addView(TextView(context).apply {
        text = "EQUIPMENT & PERFORMANCE"
        setTextColor(Pv.primary)
        textSize = context.pvSp(7f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .12f
    })
    root.addView(TextView(context).apply {
        text = "장비 기준, 기기 보정, AI 코칭, 커스텀 그린, 사운드와 멀티폰 카메라를 설정합니다."
        setTextColor(Pv.textMid)
        textSize = context.pvSp(8.5f)
        setPadding(0, context.pvDp(4), 0, context.pvDp(10))
    })

    lateinit var dialog: AlertDialog

    fun action(kicker: String, title: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = context.pvRounded(Pv.surfaceHi, Pv.rMd, Pv.line)
            setPadding(context.pvDp(12), context.pvDp(7), context.pvDp(10), context.pvDp(7))
            val copy = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            copy.addView(TextView(context).apply {
                text = kicker
                setTextColor(Pv.textLo)
                textSize = context.pvSp(6f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = .08f
            })
            copy.addView(TextView(context).apply {
                text = title
                setTextColor(Pv.textHi)
                textSize = context.pvSp(9f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            })
            addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply {
                text = "›"
                setTextColor(Pv.primary)
                textSize = context.pvSp(20f)
                typeface = Typeface.DEFAULT_BOLD
            })
            isClickable = true
            isFocusable = true
            installProductPressFeedback()
            setOnClickListener { onClick() }
        }

    fun addAction(view: LinearLayout) {
        root.addView(view, LinearLayout.LayoutParams(-1, context.pvDp(48)).apply {
            if (root.childCount > 2) topMargin = context.pvDp(5)
        })
    }

    val current = putterStore.current()
    addAction(action("PUTTER PROFILE", "${current.name} · ${"%.1f".format(current.headWidthCm)}cm") {
        dialog.dismiss()
        showPutterProfileManager(context, putterStore) { }
    })

    val fit2 = V16PutterFit2Runtime.snapshot
    val fitTitle = fit2.current?.let {
        "적합도 ${it.fitScore} · ${fit2.currentRecommendation?.head?.label ?: "분석중"}"
    } ?: "${V15PutterFitRuntime.currentSampleCount}/8구 · 비교 데이터 수집"
    addAction(action("AI PUTTER FIT 2.0", fitTitle) { showV16PutterFit2Dialog(context) })

    val coach = V16Runtime.personalCoach
    addAction(action(
        "PERSONAL AI COACH",
        coach?.let { "${it.primary.headline} · 개선 ${it.improvementScore}" } ?: "8구부터 개인 패턴 학습"
    ) { showV16PersonalCoachDialog(context) })

    addAction(action("GREEN READ TRAINING", "${V20GreenReadTrainingRuntime.mode.label} · 샷 후 정답 공개") {
        showV20GreenReadModeDialog(context)
    })

    addAction(action("GREEN VISUALS", V26GreenVisualRuntime.label()) {
        showV26GreenVisualSettingsDialog(context)
    })

    addAction(action("MOVE BALL", V26BallStartRuntime.label(V26ProductSettingsRuntime.settings)) {
        showV26MoveBallDialog(context, V26ProductSettingsRuntime.settings)
    })

    addAction(action("CUSTOM GREEN", V22CustomGreenRuntime.label()) {
        showV22CustomGreenDialog(context)
    })

    addAction(action("CUSTOM GREEN SHARE", "JSON 공유 · 불러오기") {
        V28CustomGreenShare.show(context)
    })

    addAction(action("PERFORMANCE COMPARE", V20PerformanceRuntime.report.headline) {
        showV20PerformanceCompareDialog(context)
    })

    val validationLab = AccuracyValidationLab(context)
    addAction(action(
        "REAL DEVICE CI",
        "기준값 ${validationLab.matched().size}샷 · CI reference/measured 원클릭"
    ) {
        (context as? Activity)?.let(V40AccuracyCiFixtures::show)
            ?: android.widget.Toast.makeText(context, "현재 화면에서는 파일 내보내기를 열 수 없습니다", android.widget.Toast.LENGTH_SHORT).show()
    })

    addAction(action("REPORT BUILDER", V26ReportPreferences.summary()) {
        showV26ReportBuilderDialog(context)
    })

    addAction(action("DEVICE AUTO CAL", V16DeviceAutoCalibrationRuntime.statusLabel()) {
        showV16DeviceCalibrationDialog(context)
    })

    addAction(action("MULTI PHONE CAMERA", V16CompanionLinkRuntime.status().label) {
        showV16CompanionDialog(context)
    })

    val stereoPrep = V44StereoPrepRuntime.snapshot()
    addAction(action("STEREO PREP", stereoPrep.shortLabel) {
        showV44StereoPrepDialog(context)
    })

    addAction(action("PHYSICAL MAT", "${matManager.statusLabel()} · ${"%.0f".format(V16MatGeometryRuntime.lengthCm)}cm") {
        showV16MatSetupDialog(context, matManager)
    })

    addAction(action("TV 3D QUALITY", V24TvQualityRuntime.label()) {
        showV24TvQualityDialog(context)
    })

    addAction(action("PUTTING AUDIO", if (V22AudioRuntime.enabled) "공 타격 · 롤 · 컵 사운드 ON" else "사운드 OFF") {
        val enabled = V22AudioRuntime.toggle(context)
        dialog.dismiss()
        showProductSetupDialog(context, putterStore, matManager, voiceCoach)
        android.widget.Toast.makeText(context, if (enabled) "퍼팅 사운드 ON" else "사운드 OFF", android.widget.Toast.LENGTH_SHORT).show()
    })

    addAction(action("HANDS FREE VOICE", if (voiceCoach.enabled) "음성 안내 ON" else "음성 안내 OFF") {
        val enabled = voiceCoach.toggle()
        dialog.dismiss()
        showProductSetupDialog(context, putterStore, matManager, voiceCoach)
        android.widget.Toast.makeText(
            context,
            if (enabled) "음성 안내 ON" else "음성 안내 OFF",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    })

    dialog = AlertDialog.Builder(context)
        .setTitle("제품 설정")
        .setView(scroll)
        .setNegativeButton("닫기", null)
        .create()
    dialog.show()
}

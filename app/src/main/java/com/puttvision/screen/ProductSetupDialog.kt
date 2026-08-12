package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

fun showProductSetupDialog(
    context: Context,
    putterStore: PutterProfileStore,
    matManager: MatCalibrationManager,
    voiceCoach: HandsFreeVoiceCoach
) {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(16), context.pvDp(8), context.pvDp(16), context.pvDp(4))
    }

    root.addView(TextView(context).apply {
        text = "EQUIPMENT & HANDS FREE"
        setTextColor(Pv.primary)
        textSize = context.pvSp(7f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .12f
    })
    root.addView(TextView(context).apply {
        text = "실제 장비 기준을 맞추고 폰을 만지지 않는 자동 세션을 설정합니다."
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

    val current = putterStore.current()
    root.addView(
        action("PUTTER PROFILE", "${current.name} · ${"%.1f".format(current.headWidthCm)}cm") {
            dialog.dismiss()
            showPutterProfileManager(context, putterStore) { }
        },
        LinearLayout.LayoutParams(-1, context.pvDp(52))
    )

    root.addView(
        action("PHYSICAL MAT", matManager.statusLabel()) {
            showMatCalibrationManager(context, matManager)
        },
        LinearLayout.LayoutParams(-1, context.pvDp(52)).apply { topMargin = context.pvDp(6) }
    )

    root.addView(
        action("HANDS FREE VOICE", if (voiceCoach.enabled) "음성 안내 ON" else "음성 안내 OFF") {
            val enabled = voiceCoach.toggle()
            dialog.dismiss()
            showProductSetupDialog(context, putterStore, matManager, voiceCoach)
            android.widget.Toast.makeText(
                context,
                if (enabled) "음성 안내 ON" else "음성 안내 OFF",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        },
        LinearLayout.LayoutParams(-1, context.pvDp(52)).apply { topMargin = context.pvDp(6) }
    )

    dialog = AlertDialog.Builder(context)
        .setTitle("제품 설정")
        .setView(root)
        .setNegativeButton("닫기", null)
        .create()
    dialog.show()
}

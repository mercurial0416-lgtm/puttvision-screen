package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

/**
 * PuttVision design system.
 *
 * A single source of truth for colour, spacing, radius and typography so every
 * screen (measurement HUD, home, entrances, dialogs, deploy tool and the TV
 * canvas) reads as one premium "precision instrument" product.
 *
 * Direction: deep ink base + disciplined neutrals + one confident fairway-green
 * accent, an amber signal colour, and a monospace face reserved for numeric
 * read-outs so the app feels like real launch-monitor hardware.
 *
 * This file is purely presentational. It introduces no behaviour and holds no
 * references to game state — the redesign layers on top of the existing logic.
 */
object Pv {

    // ---- Core surfaces (neutrals) ----------------------------------------
    val ink = Color.rgb(9, 12, 16)          // app background
    val inkDeep = Color.rgb(6, 8, 11)        // full-bleed menu / letterbox
    val surface = Color.rgb(18, 23, 30)      // primary panel
    val surfaceHi = Color.rgb(26, 33, 42)    // raised panel / card
    val surfaceLo = Color.rgb(13, 17, 22)    // inset / well
    val line = Color.rgb(42, 52, 64)         // visible hairline border
    val lineSoft = Color.rgb(28, 36, 46)     // subtle divider

    // ---- Text ------------------------------------------------------------
    val textHi = Color.rgb(236, 241, 247)    // primary text
    val textMid = Color.rgb(150, 162, 177)   // secondary text
    val textLo = Color.rgb(97, 108, 123)     // muted / captions

    // ---- Accents ---------------------------------------------------------
    val primary = Color.rgb(78, 209, 121)    // fairway green (single signature)
    val primaryDim = Color.rgb(23, 49, 33)   // green-tinted fill
    val primaryLine = Color.rgb(45, 112, 67) // borders / guides on green-tinted surfaces
    val primaryInk = Color.rgb(5, 19, 10)    // text/icons on primary
    val amber = Color.rgb(246, 190, 74)      // signal / scores / warnings
    val amberInk = Color.rgb(26, 19, 4)      // text on amber
    val danger = Color.rgb(240, 98, 92)      // errors / miss
    val info = Color.rgb(96, 178, 255)       // secondary data (toe / path)

    // ---- Type scale (sp, before uiScale) ---------------------------------
    const val display = 34f
    const val title = 20f
    const val headline = 15f
    const val body = 12.5f
    const val label = 10.5f
    const val caption = 9f
    const val micro = 7.5f

    // ---- Radius scale (dp) ----------------------------------------------
    const val rSm = 10f
    const val rMd = 14f
    const val rLg = 18f
    const val rXl = 24f
}

// ---- Density / scale helpers (shared across all Contexts) ----------------

/**
 * Adaptive UI scale so the landscape layout stays legible from small phones to
 * tablets. Mirrors the original tiering the app shipped with.
 */
fun Context.pvScale(): Float {
    val dm = resources.displayMetrics
    val shortestDp = min(dm.widthPixels / dm.density, dm.heightPixels / dm.density)
    return when {
        shortestDp < 360f -> 0.82f
        shortestDp < 420f -> 0.90f
        shortestDp < 520f -> 0.96f
        else -> 1.0f
    }
}

/** Fixed dp -> px. */
fun Context.pvDp(value: Int): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()

fun Context.pvDp(value: Float): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()

/** Scaled dp -> px (respects [pvScale]). */
fun Context.pvSdp(value: Int): Int =
    (value * pvScale() * resources.displayMetrics.density + 0.5f).toInt()

/** Scaled sp value for text sizes. */
fun Context.pvSp(value: Float): Float = value * pvScale()

/** Rounded rectangle drawable with optional hairline stroke. */
fun Context.pvRounded(
    fill: Int,
    radiusDp: Float,
    strokeColor: Int = Color.TRANSPARENT,
    strokeWidthDp: Float = 1f
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(fill)
    cornerRadius = radiusDp * resources.displayMetrics.density
    if (strokeColor != Color.TRANSPARENT) {
        setStroke(pvDp(strokeWidthDp), strokeColor)
    }
}

/** Vertical two-stop gradient (top -> bottom) for hero surfaces. */
fun Context.pvVGradient(top: Int, bottom: Int, radiusDp: Float = 0f): GradientDrawable =
    GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(top, bottom)
    ).apply {
        if (radiusDp > 0f) cornerRadius = radiusDp * resources.displayMetrics.density
    }

// ---- Reusable components -------------------------------------------------

enum class PvButtonStyle { PRIMARY, SECONDARY, GHOST, AMBER }

/**
 * Themed button. [scaled] controls whether the text size respects [pvScale]
 * (menu flow) or stays fixed (compact HUD chrome).
 */
fun Context.pvButton(
    label: String,
    style: PvButtonStyle = PvButtonStyle.SECONDARY,
    textSp: Float = Pv.label,
    scaled: Boolean = false,
    radiusDp: Float = Pv.rMd,
    onClick: () -> Unit
): Button = Button(this).apply {
    text = label
    isAllCaps = false
    setSingleLine(false)
    gravity = Gravity.CENTER
    typeface = Typeface.DEFAULT_BOLD
    textSize = if (scaled) pvSp(textSp) else textSp
    minHeight = 0
    minimumHeight = 0
    stateListAnimator = null
    isClickable = true
    isFocusable = true
    includeFontPadding = false
    setPadding(pvDp(12), pvDp(6), pvDp(12), pvDp(6))
    when (style) {
        PvButtonStyle.PRIMARY -> {
            setTextColor(Pv.primaryInk)
            background = pvRounded(Pv.primary, radiusDp)
        }
        PvButtonStyle.AMBER -> {
            setTextColor(Pv.amberInk)
            background = pvRounded(Pv.amber, radiusDp)
        }
        PvButtonStyle.SECONDARY -> {
            setTextColor(Pv.textHi)
            background = pvRounded(Pv.surfaceHi, radiusDp, Pv.line)
        }
        PvButtonStyle.GHOST -> {
            setTextColor(Pv.textMid)
            background = pvRounded(Color.TRANSPARENT, radiusDp, Pv.line)
        }
    }
    installProductPressFeedback()
    setOnClickListener { onClick() }
}

/** Selectable chip used across entrance config panels. */
fun Context.pvChip(
    label: String,
    selected: Boolean,
    textSp: Float = Pv.label,
    onClick: () -> Unit
): Button = Button(this).apply {
    text = label
    isAllCaps = false
    setSingleLine(false)
    gravity = Gravity.CENTER
    typeface = Typeface.DEFAULT_BOLD
    textSize = pvSp(textSp)
    minHeight = 0
    minimumHeight = 0
    stateListAnimator = null
    isClickable = true
    isFocusable = true
    includeFontPadding = false
    setPadding(pvDp(6), pvDp(6), pvDp(6), pvDp(6))
    if (selected) {
        setTextColor(Pv.primaryInk)
        background = pvRounded(Pv.primary, Pv.rMd)
    } else {
        setTextColor(Pv.textHi)
        background = pvRounded(Pv.surfaceHi, Pv.rMd, Pv.line)
    }
    installProductPressFeedback()
    setOnClickListener { onClick() }
}

/** Small uppercase section/eyebrow label. */
fun Context.pvEyebrow(text: String): TextView = TextView(this).apply {
    this.text = text
    setTextColor(Pv.textMid)
    textSize = pvSp(Pv.caption)
    typeface = Typeface.DEFAULT_BOLD
    letterSpacing = 0.08f
    setPadding(pvDp(2), 0, 0, pvDp(6))
}

/** Panel container with consistent fill/stroke/padding. */
fun Context.pvPanel(
    radiusDp: Float = Pv.rLg,
    fill: Int = Pv.surface,
    stroke: Int = Pv.lineSoft,
    padDp: Int = 12
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = pvRounded(fill, radiusDp, stroke)
    setPadding(pvSdp(padDp), pvSdp(padDp), pvSdp(padDp), pvSdp(padDp))
}

/** Circular icon control (glyph + optional caption) used in menu chrome. */
fun Context.pvIconControl(
    glyph: String,
    caption: String,
    onClick: () -> Unit
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    isClickable = true
    isFocusable = true
    minimumWidth = pvDp(48)
    minimumHeight = pvDp(if (caption.isEmpty()) 48 else 58)
    contentDescription = caption.ifBlank { glyph }

    val iconSize = pvDp(46) // Keep Android's recommended ~48dp touch target even on small phones.
    val icon = TextView(this@pvIconControl).apply {
        text = glyph
        textSize = pvSp(16f)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setTextColor(Pv.textHi)
        background = pvRounded(Pv.surfaceHi, 100f, Pv.line)
        isClickable = false
        isFocusable = false
    }
    addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
    if (caption.isNotEmpty()) {
        addView(TextView(this@pvIconControl).apply {
            text = caption
            textSize = pvSp(Pv.caption)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            setTextColor(Pv.textMid)
            setPadding(0, pvDp(3), 0, 0)
            isClickable = false
            isFocusable = false
        })
    }
    setPadding(pvDp(2), pvDp(2), pvDp(2), pvDp(2))
    installProductPressFeedback()
    setOnClickListener { onClick() }
}

/** 1px hairline divider, themed. Caller sets LayoutParams(-1, pvDp(1)) with margins. */
fun Context.pvDivider(): View = View(this).apply {
    setBackgroundColor(Pv.lineSoft)
}

/** Small stat tile: label over a monospace numeric read-out, used in stats/results screens. */
fun Context.pvStatTile(label: String, value: String, accent: Int = Pv.textHi): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = pvRounded(Pv.surfaceHi, Pv.rMd, Pv.lineSoft)
        setPadding(pvSdp(8), pvSdp(10), pvSdp(8), pvSdp(10))
        addView(TextView(this@pvStatTile).apply {
            text = label
            setTextColor(Pv.textMid)
            textSize = pvSp(Pv.caption)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(false)
        })
        addView(TextView(this@pvStatTile).apply {
            text = value
            setTextColor(accent)
            textSize = pvSp(Pv.title)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, pvDp(3), 0, 0)
        })
    }

/**
 * Premium themed dialog shell: dark rounded card, brand tick + title, hairline
 * rule, caller-supplied content, and a bottom action row. Replaces the stock
 * platform [AlertDialog] chrome (white title bar, default buttons) so floating
 * dialogs read as part of the same "precision instrument" product as the HUD.
 *
 * Content is wrapped in a [ScrollView] automatically and the card is capped so
 * it never grows edge-to-edge on tablets/landscape.
 */
fun Context.pvDialog(
    title: String,
    content: View,
    dismissLabel: String = "닫기",
    extraActions: List<Pair<String, () -> Unit>> = emptyList(),
    maxWidthDp: Int = 420,
    onDismissTap: (() -> Unit)? = null
): AlertDialog {
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = pvRounded(Pv.surface, Pv.rXl, Pv.line)
        setPadding(pvDp(20), pvDp(18), pvDp(20), pvDp(16))
    }

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    header.addView(View(this).apply {
        background = pvRounded(Pv.primary, 100f)
    }, LinearLayout.LayoutParams(pvDp(7), pvDp(20)).apply { marginEnd = pvDp(9) })
    header.addView(TextView(this).apply {
        text = title
        setTextColor(Pv.textHi)
        textSize = pvSp(Pv.title)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        maxLines = 2
    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    card.addView(header)

    card.addView(View(this).apply { setBackgroundColor(Pv.lineSoft) },
        LinearLayout.LayoutParams(-1, pvDp(1)).apply { topMargin = pvDp(12); bottomMargin = pvDp(2) })

    val maxScrollHeight = (resources.displayMetrics.heightPixels * 0.62f).toInt()
    val scroll = object : ScrollView(this) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val capped = View.MeasureSpec.makeMeasureSpec(
                min(View.MeasureSpec.getSize(heightMeasureSpec), maxScrollHeight),
                View.MeasureSpec.AT_MOST
            )
            super.onMeasure(widthMeasureSpec, capped)
        }
    }.apply {
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        isFillViewport = false
        isVerticalScrollBarEnabled = true
        addView(content, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    card.addView(scroll, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = pvDp(4) })

    var dialogRef: AlertDialog? = null
    val actionsRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, pvDp(16), 0, 0)
    }
    extraActions.forEach { (label, onClick) ->
        actionsRow.addView(pvButton(label, PvButtonStyle.GHOST, onClick = {
            onClick()
            dialogRef?.dismiss()
        }), LinearLayout.LayoutParams(0, pvDp(46), 1f).apply { marginEnd = pvDp(8) })
    }
    actionsRow.addView(pvButton(dismissLabel, PvButtonStyle.PRIMARY, onClick = {
        onDismissTap?.invoke()
        dialogRef?.dismiss()
    }), LinearLayout.LayoutParams(0, pvDp(46), 1f))
    card.addView(actionsRow)

    val maxDialogHeight = (resources.displayMetrics.heightPixels * 0.92f).toInt()
    val outer = object : FrameLayout(this) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cappedHeight = View.MeasureSpec.makeMeasureSpec(
                min(View.MeasureSpec.getSize(heightMeasureSpec), maxDialogHeight),
                View.MeasureSpec.AT_MOST
            )
            super.onMeasure(widthMeasureSpec, cappedHeight)
        }
    }.apply {
        setPadding(pvDp(12), pvDp(12), pvDp(12), pvDp(12))
        val cardWidth = min(pvDp(maxWidthDp), (resources.displayMetrics.widthPixels * 0.94f).toInt())
        addView(card, FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })
    }

    val dialog = AlertDialog.Builder(this).setView(outer).create()
    dialog.setOnShowListener {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
    dialogRef = dialog
    return dialog
}

/**
 * Themed drop-in replacement for a plain `AlertDialog.Builder(...).setTitle/setMessage(...)`
 * confirm/alert dialog. Keeps call sites terse for the many one- and two-button
 * status dialogs across update-checking and deploy flows.
 */
fun Context.pvMessageDialog(
    title: String,
    message: String,
    positiveLabel: String = "확인",
    onPositive: (() -> Unit)? = null,
    negativeLabel: String? = null,
    onNegative: (() -> Unit)? = null
): AlertDialog {
    val body = TextView(this).apply {
        text = message
        setTextColor(Pv.textMid)
        textSize = Pv.body
        setLineSpacing(pvDp(3).toFloat(), 1f)
    }
    return if (negativeLabel != null) {
        pvDialog(
            title = title,
            content = body,
            dismissLabel = positiveLabel,
            onDismissTap = onPositive,
            extraActions = listOf(negativeLabel to { onNegative?.invoke() })
        )
    } else {
        pvDialog(
            title = title,
            content = body,
            dismissLabel = positiveLabel,
            onDismissTap = onPositive
        )
    }
}

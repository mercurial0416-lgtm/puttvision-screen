package com.puttvision.screen

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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
    setPadding(pvDp(6), pvDp(6), pvDp(6), pvDp(6))
    if (selected) {
        setTextColor(Pv.primaryInk)
        background = pvRounded(Pv.primary, Pv.rMd)
    } else {
        setTextColor(Pv.textHi)
        background = pvRounded(Pv.surfaceHi, Pv.rMd, Pv.line)
    }
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
    val icon = TextView(this@pvIconControl).apply {
        text = glyph
        textSize = pvSp(16f)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Pv.textHi)
        background = pvRounded(Pv.surfaceHi, 100f, Pv.line)
    }
    addView(icon, LinearLayout.LayoutParams(pvSdp(42), pvSdp(42)))
    if (caption.isNotEmpty()) {
        addView(TextView(this@pvIconControl).apply {
            text = caption
            textSize = pvSp(Pv.caption)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Pv.textMid)
            setPadding(0, pvDp(3), 0, 0)
        })
    }
    setOnClickListener { onClick() }
}

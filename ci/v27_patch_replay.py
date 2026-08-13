from pathlib import Path
p=Path('app/src/main/java/com/puttvision/screen/ImpactReplayView.kt')
t=p.read_text(encoding='utf-8')
if 'V27ReplayAnnotationSession' in t:
    print('ImpactReplayView: current'); raise SystemExit(0)
t=t.replace('import android.view.View\nimport kotlin.math.max','import android.view.MotionEvent\nimport android.view.View\nimport kotlin.math.max\nimport kotlin.math.roundToInt',1)
t=t.replace('''    private var frame = 0
    private var loops = 0

    private val tick''','''    private var frame = 0
    private var loops = 0
    private var paused = false
    private val annotations = V27ReplayAnnotationSession()
    private val mediaRect = RectF()
    private val progressRect = RectF()
    private val toolbarButtons = mutableListOf<Pair<String, RectF>>()

    private val tick''',1)
t=t.replace('''            val r = replay ?: return
            frame++
            if (frame >= r.frames.size) {''','''            val r = replay ?: return
            if (paused) return
            frame++
            if (frame >= r.frames.size) {''',1)
t=t.replace('            handler.postDelayed(this, 55L)','            if (!paused) handler.postDelayed(this, 55L)',1)
t=t.replace('        isClickable = false\n        isFocusable = false','        isClickable = true\n        isFocusable = true',1)
t=t.replace('''        frame = 0
        loops = 0
        visibility = VISIBLE''','''        frame = 0
        loops = 0
        paused = false
        annotations.clear()
        annotations.setTool(V27ReplayTool.NONE)
        visibility = VISIBLE''',1)
t=t.replace('''        frame = 0
        loops = 0
        visibility = GONE''','''        frame = 0
        loops = 0
        paused = false
        annotations.clear()
        annotations.setTool(V27ReplayTool.NONE)
        visibility = GONE''',1)
t=t.replace('''        val media = RectF(mediaLeft, mediaTop, mediaRight, mediaBottom)

        // Header''','''        val media = RectF(mediaLeft, mediaTop, mediaRight, mediaBottom)
        mediaRect.set(media)

        // Header''',1)
t=t.replace('''        val trackY = top + headerH * .56f
        paint.color''','''        val trackY = top + headerH * .56f
        progressRect.set(trackLeft, trackY - h * .030f, trackRight, trackY + h * .030f)
        paint.color''',1)
t=t.replace('''            drawBestShotComparison(canvas, media)
            drawV19StudioComparison(canvas, media)
        }

        // Telemetry rail''','''            drawBestShotComparison(canvas, media)
            drawV19StudioComparison(canvas, media)
        }
        annotations.draw(canvas, media, paint, w)
        drawV27Toolbar(canvas, media, w, h)

        // Telemetry rail''',1)
marker='''    override fun onDetachedFromWindow() {
        stopReplay(recycleFrames = true)'''
if marker not in t: raise SystemExit('replay methods marker')
methods=r'''    private fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        handler.removeCallbacks(tick)
        if (!paused && replay != null) handler.postDelayed(tick, 55L)
        invalidate()
    }

    private fun drawV27Toolbar(canvas: Canvas, media: RectF, w: Float, h: Float) {
        toolbarButtons.clear()
        val items = listOf(
            "TOGGLE" to if (paused) "PLAY" else "PAUSE",
            "LINE" to "LINE", "CIRCLE" to "CIRCLE", "ANGLE" to "ANGLE",
            "UNDO" to "UNDO", "CLEAR" to "CLEAR"
        )
        val gap = w * .004f
        val bh = h * .038f
        val bw = w * .066f
        var right = media.right - w * .010f
        items.asReversed().forEach { (code, label) ->
            val rect = RectF(right - bw, media.top + h * .010f, right, media.top + h * .010f + bh)
            val active = when (code) {
                "LINE" -> annotations.tool == V27ReplayTool.LINE
                "CIRCLE" -> annotations.tool == V27ReplayTool.CIRCLE
                "ANGLE" -> annotations.tool == V27ReplayTool.ANGLE
                else -> false
            }
            paint.color = if (active) Color.argb(220,174,123,25) else Color.argb(188,8,12,15)
            canvas.drawRoundRect(rect,bh*.35f,bh*.35f,paint)
            paint.textAlign=Paint.Align.CENTER; paint.typeface=Typeface.DEFAULT_BOLD
            paint.textSize=max(7f,w*.0053f); paint.color=Color.WHITE
            canvas.drawText(label,rect.centerX(),rect.centerY()+paint.textSize*.34f,paint)
            paint.textAlign=Paint.Align.LEFT
            toolbarButtons += code to RectF(rect); right=rect.left-gap
        }
    }

    private fun handleToolbar(code: String) {
        when (code) {
            "TOGGLE" -> setPaused(!paused)
            "LINE" -> { setPaused(true); annotations.setTool(if (annotations.tool==V27ReplayTool.LINE) V27ReplayTool.NONE else V27ReplayTool.LINE) }
            "CIRCLE" -> { setPaused(true); annotations.setTool(if (annotations.tool==V27ReplayTool.CIRCLE) V27ReplayTool.NONE else V27ReplayTool.CIRCLE) }
            "ANGLE" -> { setPaused(true); annotations.setTool(if (annotations.tool==V27ReplayTool.ANGLE) V27ReplayTool.NONE else V27ReplayTool.ANGLE) }
            "UNDO" -> { setPaused(true); annotations.undo() }
            "CLEAR" -> { setPaused(true); annotations.clear() }
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val r = replay ?: return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            toolbarButtons.firstOrNull { it.second.contains(event.x,event.y) }?.let { handleToolbar(it.first); return true }
            if (progressRect.contains(event.x,event.y) && r.frames.isNotEmpty()) {
                val ratio=((event.x-progressRect.left)/progressRect.width()).coerceIn(0f,1f)
                frame=(ratio*(r.frames.size-1)).roundToInt().coerceIn(0,r.frames.lastIndex)
                setPaused(true); invalidate(); return true
            }
        }
        if (annotations.tool != V27ReplayTool.NONE && mediaRect.contains(event.x,event.y)) {
            setPaused(true)
            val handled=annotations.handle(event,mediaRect)
            if (handled) invalidate()
            return handled
        }
        return true
    }

    override fun onDetachedFromWindow() {
        stopReplay(recycleFrames = true)'''
t=t.replace(marker,methods,1)
p.write_text(t,encoding='utf-8'); print('ImpactReplayView: V27 patched')

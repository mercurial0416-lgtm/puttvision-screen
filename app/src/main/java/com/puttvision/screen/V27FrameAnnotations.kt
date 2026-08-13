package com.puttvision.screen

/** Keeps manual replay markup attached to the exact HFR frame it was drawn on. */
class V27FrameAnnotationBook {
    private val byFrame = mutableMapOf<Int, List<V27ReplayMark>>()

    fun save(frame: Int, session: V27ReplayAnnotationSession) {
        if (frame < 0) return
        if (session.marks.isEmpty()) byFrame.remove(frame)
        else byFrame[frame] = session.marks.toList()
    }

    fun load(frame: Int, session: V27ReplayAnnotationSession) {
        val selectedTool = session.tool
        session.clear()
        session.marks += byFrame[frame].orEmpty()
        session.selectTool(selectedTool)
    }

    fun clear() = byFrame.clear()

    fun annotatedFrames(): Set<Int> = byFrame.keys.toSet()
}

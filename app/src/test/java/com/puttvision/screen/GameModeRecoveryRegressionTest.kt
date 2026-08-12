package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class GameModeRecoveryRegressionTest {
    @Test fun snapshotRestoresMultiplayerState() {
        val settings = GreenSettings()
        val engine = GameModeEngine(settings)
        engine.configurePlayers(3)
        engine.setMode(PracticeMode.NINE_HOLE)
        val snap = engine.snapshot()
        val restored = GameModeEngine(GreenSettings())
        restored.restore(snap)
        assertEquals(snap.mode, restored.status.mode)
        assertEquals(3, restored.status.playerCount)
        assertEquals(snap.hole, restored.status.hole)
        assertEquals(snap.scores, restored.status.playerScores)
    }
}

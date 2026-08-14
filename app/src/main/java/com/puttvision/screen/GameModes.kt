package com.puttvision.screen

import kotlin.math.roundToInt
import kotlin.random.Random

enum class PracticeMode(val label: String) {
    PRACTICE("연습"),
    DISTANCE("거리 맞추기"),
    NINE_HOLE("9홀"),
    EIGHTEEN_HOLE("18홀"),
    STREAK("연속 성공"),
    PRESSURE("압박 퍼팅"),
    RANDOM_SLOPE("랜덤 경사"),
    DART("퍼팅 다트"),
    CURLING("퍼팅 컬링"),
    BATTLE("배틀 퍼팅"),
    GHOST("고스트")
}

data class GameStatus(
    var mode: PracticeMode = PracticeMode.PRACTICE,
    var hole: Int = 1,
    var totalHoles: Int = 0,
    var gameScore: Int = 0,
    var streak: Int = 0,
    var bestStreak: Int = 0,
    var shots: Int = 0,
    var made: Int = 0,
    var lastPoints: Int = 0,
    var completed: Boolean = false,
    var playerCount: Int = 1,
    var activePlayer: Int = 1,
    var playerScores: List<Int> = listOf(0)
)

data class GameModeSnapshot(
    val mode: PracticeMode,
    val hole: Int,
    val totalHoles: Int,
    val gameScore: Int,
    val streak: Int,
    val bestStreak: Int,
    val shots: Int,
    val made: Int,
    val lastPoints: Int,
    val completed: Boolean,
    val playerCount: Int,
    val activePlayer: Int,
    val pendingPrepare: Boolean,
    val scores: List<Int>,
    val streaks: List<Int>,
    val bestStreaks: List<Int>
)

class GameModeEngine(
    private val settings: GreenSettings
) {
    val status = GameStatus()
    private val random = Random(20260811)
    private var pendingPrepare = false
    private var scores = IntArray(1)
    private var streaks = IntArray(1)
    private var bestStreaks = IntArray(1)

    fun configurePlayers(count: Int) {
        val safe = count.coerceIn(1, 4)
        status.playerCount = safe
        scores = IntArray(safe)
        streaks = IntArray(safe)
        bestStreaks = IntArray(safe)
        status.activePlayer = 1
        syncActivePlayerState()
    }

    fun nextMode(): PracticeMode {
        val values = PracticeMode.entries
        val next = values[(values.indexOf(status.mode) + 1) % values.size]
        setMode(next)
        return next
    }

    fun setMode(mode: PracticeMode) {
        status.mode = mode
        status.hole = 1
        status.gameScore = 0
        status.streak = 0
        status.bestStreak = 0
        status.shots = 0
        status.made = 0
        status.lastPoints = 0
        status.completed = false
        status.activePlayer = 1
        scores = IntArray(status.playerCount.coerceIn(1, 4))
        streaks = IntArray(scores.size)
        bestStreaks = IntArray(scores.size)
        status.playerScores = scores.toList()

        status.totalHoles = when (mode) {
            PracticeMode.NINE_HOLE -> 9
            PracticeMode.EIGHTEEN_HOLE -> 18
            else -> 0
        }

        prepareHole()
    }

    /**
     * Advances turns only when the next shot is actually armed. This leaves the just-finished
     * player's name/score visible on the result screen instead of jumping to the next player early.
     */
    fun prepareNextIfNeeded() {
        if (!pendingPrepare || status.completed) return
        pendingPrepare = false

        if (status.activePlayer < status.playerCount) {
            status.activePlayer++
            syncActivePlayerState()
            if (status.mode == PracticeMode.PRESSURE) prepareHole()
            return
        }

        status.activePlayer = 1
        syncActivePlayerState()

        if (status.mode == PracticeMode.NINE_HOLE || status.mode == PracticeMode.EIGHTEEN_HOLE) {
            if (status.hole < status.totalHoles) status.hole++
        }
        prepareHole()
    }

    fun prepareHole() {
        pendingPrepare = false

        when (status.mode) {
            PracticeMode.PRACTICE -> Unit

            PracticeMode.DISTANCE -> {
                settings.holeDistanceM = random.nextDouble(1.5, 8.0)
                settings.sideSlopePct = 0.0
                settings.longSlopePct = random.nextDouble(-1.0, 1.0)
            }

            PracticeMode.NINE_HOLE,
            PracticeMode.EIGHTEEN_HOLE -> {
                val online = onlineHoleRandom()
                val r = online ?: random
                settings.holeDistanceM = r.nextDouble(1.2, 10.0)
                settings.sideSlopePct = r.nextDouble(-2.6, 2.6)
                settings.longSlopePct = r.nextDouble(-2.0, 2.0)
                if (online != null) settings.terrainProfileId = r.nextInt(0, 24)
            }

            PracticeMode.STREAK -> {
                settings.holeDistanceM = 2.0
                settings.sideSlopePct = 0.0
                settings.longSlopePct = 0.0
            }

            PracticeMode.PRESSURE -> {
                settings.holeDistanceM = 1.0 + status.streak * 0.25
                settings.sideSlopePct = if (status.streak >= 4) random.nextDouble(-1.8, 1.8) else 0.0
                settings.longSlopePct = 0.0
            }

            PracticeMode.RANDOM_SLOPE -> {
                settings.holeDistanceM = random.nextDouble(1.5, 9.0)
                settings.sideSlopePct = random.nextDouble(-4.0, 4.0)
                settings.longSlopePct = random.nextDouble(-3.0, 3.0)
            }

            PracticeMode.DART -> {
                settings.holeDistanceM = random.nextDouble(2.0, 6.5)
                settings.sideSlopePct = random.nextDouble(-1.2, 1.2)
                settings.longSlopePct = random.nextDouble(-.8, .8)
            }

            PracticeMode.CURLING -> {
                settings.holeDistanceM = 4.0
                settings.sideSlopePct = random.nextDouble(-.55, .55)
                settings.longSlopePct = 0.0
            }

            PracticeMode.BATTLE -> {
                settings.holeDistanceM = random.nextDouble(1.8, 5.5)
                settings.sideSlopePct = random.nextDouble(-1.8, 1.8)
                settings.longSlopePct = random.nextDouble(-1.0, 1.0)
            }

            PracticeMode.GHOST -> {
                val ghost = V15GhostRuntime.referenceForCurrent(settings)
                if (ghost != null) {
                    settings.holeDistanceM = ghost.record.targetDistanceM.coerceAtLeast(.5)
                    settings.stimpMeters = ghost.record.stimpMeters
                    settings.sideSlopePct = ghost.record.sideSlopePct
                    settings.longSlopePct = ghost.record.longSlopePct
                    settings.terrainProfileId = ghost.record.terrainProfileId
                } else {
                    settings.holeDistanceM = 3.0
                    settings.sideSlopePct = 0.0
                    settings.longSlopePct = 0.0
                }
            }
        }
    }

    fun onResult(result: SimResult) {
        status.shots++
        if (result.holed) status.made++

        val playerIndex = (status.activePlayer - 1).coerceIn(0, scores.lastIndex)
        status.lastPoints = when (status.mode) {
            PracticeMode.PRACTICE -> if (result.holed) 100 else {
                (100 - result.distanceToCupM * 80.0).roundToInt().coerceIn(0, 99)
            }

            PracticeMode.DISTANCE ->
                (100 - result.distanceToCupM * 100.0).roundToInt().coerceIn(0, 100)

            PracticeMode.NINE_HOLE,
            PracticeMode.EIGHTEEN_HOLE -> if (result.holed) 100 else {
                (100 - result.distanceToCupM * 70.0).roundToInt().coerceIn(0, 95)
            }

            PracticeMode.STREAK -> {
                if (result.holed) {
                    streaks[playerIndex]++
                    bestStreaks[playerIndex] = maxOf(bestStreaks[playerIndex], streaks[playerIndex])
                    100 + streaks[playerIndex] * 10
                } else {
                    val points = streaks[playerIndex] * 10
                    streaks[playerIndex] = 0
                    points
                }
            }

            PracticeMode.PRESSURE -> {
                if (result.holed) {
                    streaks[playerIndex]++
                    bestStreaks[playerIndex] = maxOf(bestStreaks[playerIndex], streaks[playerIndex])
                    100 + streaks[playerIndex] * 20
                } else {
                    streaks[playerIndex] = 0
                    0
                }
            }

            PracticeMode.RANDOM_SLOPE -> if (result.holed) 150 else {
                (120 - result.distanceToCupM * 80.0).roundToInt().coerceIn(0, 120)
            }

            PracticeMode.DART -> when {
                result.holed -> 300
                result.distanceToCupM <= .10 -> 250
                result.distanceToCupM <= .20 -> 200
                result.distanceToCupM <= .35 -> 150
                result.distanceToCupM <= .60 -> 100
                result.distanceToCupM <= 1.00 -> 50
                else -> 0
            }

            PracticeMode.CURLING -> when {
                result.holed -> 250
                result.distanceToCupM <= .15 -> 220
                result.distanceToCupM <= .30 -> 170
                result.distanceToCupM <= .55 -> 110
                result.distanceToCupM <= .90 -> 60
                else -> 0
            }

            PracticeMode.BATTLE -> {
                val base = if (result.holed) 180 else (140 - result.distanceToCupM * 100.0).roundToInt().coerceIn(0, 140)
                val pressureBonus = if (status.playerCount > 1 && scores.maxOrNull() != null && scores[playerIndex] < (scores.maxOrNull() ?: 0)) 20 else 0
                base + pressureBonus
            }

            PracticeMode.GHOST -> {
                val ghost = V15GhostRuntime.lastComparison
                when {
                    ghost == null -> if (result.holed) 150 else (100 - result.distanceToCupM * 80.0).roundToInt().coerceIn(0, 100)
                    ghost.beatGhost -> 200 + ghost.scoreDelta.coerceAtLeast(0) * 5
                    else -> (120 + ghost.scoreDelta * 4).coerceIn(0, 119)
                }
            }
        }

        if (status.mode != PracticeMode.PRACTICE) scores[playerIndex] += status.lastPoints
        status.gameScore = scores[playerIndex]
        status.streak = streaks[playerIndex]
        status.bestStreak = bestStreaks.maxOrNull() ?: 0
        status.playerScores = scores.toList()

        when (status.mode) {
            PracticeMode.PRACTICE -> Unit
            PracticeMode.NINE_HOLE,
            PracticeMode.EIGHTEEN_HOLE -> {
                val lastPlayer = status.activePlayer >= status.playerCount
                val lastHole = status.hole >= status.totalHoles
                if (lastPlayer && lastHole) status.completed = true else pendingPrepare = true
            }
            else -> pendingPrepare = true
        }
    }

    fun snapshot(): GameModeSnapshot = GameModeSnapshot(
        mode = status.mode, hole = status.hole, totalHoles = status.totalHoles, gameScore = status.gameScore,
        streak = status.streak, bestStreak = status.bestStreak, shots = status.shots, made = status.made,
        lastPoints = status.lastPoints, completed = status.completed, playerCount = status.playerCount,
        activePlayer = status.activePlayer, pendingPrepare = pendingPrepare, scores = scores.toList(),
        streaks = streaks.toList(), bestStreaks = bestStreaks.toList()
    )

    fun restore(snapshot: GameModeSnapshot) {
        val count = snapshot.playerCount.coerceIn(1, 4)
        fun normalized(values: List<Int>): IntArray = IntArray(count) { i -> values.getOrElse(i) { 0 } }
        scores = normalized(snapshot.scores)
        streaks = normalized(snapshot.streaks)
        bestStreaks = normalized(snapshot.bestStreaks)
        pendingPrepare = snapshot.pendingPrepare
        status.mode = snapshot.mode
        status.hole = snapshot.hole.coerceAtLeast(1)
        status.totalHoles = snapshot.totalHoles.coerceAtLeast(0)
        status.gameScore = snapshot.gameScore
        status.streak = snapshot.streak
        status.bestStreak = snapshot.bestStreak
        status.shots = snapshot.shots.coerceAtLeast(0)
        status.made = snapshot.made.coerceAtLeast(0)
        status.lastPoints = snapshot.lastPoints
        status.completed = snapshot.completed
        status.playerCount = count
        status.activePlayer = snapshot.activePlayer.coerceIn(1, count)
        status.playerScores = scores.toList()
    }

    private fun onlineHoleRandom(): Random? {
        val seed = V33OnlineOutbox.onlineSeedOrNull() ?: return null
        val mixed = seed xor (status.hole.toLong() * -7046029254386353131L)
        return Random((mixed xor (mixed ushr 32)).toInt())
    }

    private fun syncActivePlayerState() {
        val i = (status.activePlayer - 1).coerceIn(0, scores.lastIndex)
        status.gameScore = scores[i]
        status.streak = streaks[i]
        status.bestStreak = bestStreaks.maxOrNull() ?: 0
        status.playerScores = scores.toList()
        status.lastPoints = 0
    }
}

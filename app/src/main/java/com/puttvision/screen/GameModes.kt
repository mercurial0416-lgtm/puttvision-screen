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
    RANDOM_SLOPE("랜덤 경사")
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
    var completed: Boolean = false
)

class GameModeEngine(
    private val settings: GreenSettings
) {
    val status = GameStatus()
    private val random = Random(20260811)
    private var pendingPrepare = false

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
        status.shots = 0
        status.made = 0
        status.lastPoints = 0
        status.completed = false

        status.totalHoles = when (mode) {
            PracticeMode.NINE_HOLE -> 9
            PracticeMode.EIGHTEEN_HOLE -> 18
            else -> 0
        }

        prepareHole()
    }

    fun prepareNextIfNeeded() {
        if (!pendingPrepare) return

        if (
            status.mode == PracticeMode.NINE_HOLE ||
            status.mode == PracticeMode.EIGHTEEN_HOLE
        ) {
            if (status.hole < status.totalHoles) {
                status.hole++
            }
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
                settings.holeDistanceM = random.nextDouble(1.2, 10.0)
                settings.sideSlopePct = random.nextDouble(-2.6, 2.6)
                settings.longSlopePct = random.nextDouble(-2.0, 2.0)
            }

            PracticeMode.STREAK -> {
                settings.holeDistanceM = 2.0
                settings.sideSlopePct = 0.0
                settings.longSlopePct = 0.0
            }

            PracticeMode.PRESSURE -> {
                settings.holeDistanceM = 1.0 + status.streak * 0.25
                settings.sideSlopePct = if (status.streak >= 4) {
                    random.nextDouble(-1.8, 1.8)
                } else 0.0
                settings.longSlopePct = 0.0
            }

            PracticeMode.RANDOM_SLOPE -> {
                settings.holeDistanceM = random.nextDouble(1.5, 9.0)
                settings.sideSlopePct = random.nextDouble(-4.0, 4.0)
                settings.longSlopePct = random.nextDouble(-3.0, 3.0)
            }
        }
    }

    fun onResult(result: SimResult) {
        status.shots++
        if (result.holed) status.made++

        when (status.mode) {
            PracticeMode.PRACTICE -> {
                status.lastPoints = if (result.holed) 100 else {
                    (100 - result.distanceToCupM * 80.0).roundToInt().coerceIn(0, 99)
                }
            }

            PracticeMode.DISTANCE -> {
                status.lastPoints =
                    (100 - result.distanceToCupM * 100.0).roundToInt().coerceIn(0, 100)
                status.gameScore += status.lastPoints
                pendingPrepare = true
            }

            PracticeMode.NINE_HOLE,
            PracticeMode.EIGHTEEN_HOLE -> {
                status.lastPoints = if (result.holed) 100 else {
                    (100 - result.distanceToCupM * 70.0).roundToInt().coerceIn(0, 95)
                }
                status.gameScore += status.lastPoints

                if (status.hole < status.totalHoles) {
                    pendingPrepare = true
                } else {
                    status.completed = true
                }
            }

            PracticeMode.STREAK -> {
                if (result.holed) {
                    status.streak++
                    status.bestStreak = maxOf(status.bestStreak, status.streak)
                    status.lastPoints = 100 + status.streak * 10
                } else {
                    status.lastPoints = status.streak * 10
                    status.streak = 0
                }
                status.gameScore += status.lastPoints
            }

            PracticeMode.PRESSURE -> {
                if (result.holed) {
                    status.streak++
                    status.bestStreak = maxOf(status.bestStreak, status.streak)
                    status.lastPoints = 100 + status.streak * 20
                } else {
                    status.lastPoints = 0
                    status.streak = 0
                }
                status.gameScore += status.lastPoints
                pendingPrepare = true
            }

            PracticeMode.RANDOM_SLOPE -> {
                status.lastPoints = if (result.holed) 150 else {
                    (120 - result.distanceToCupM * 80.0).roundToInt().coerceIn(0, 120)
                }
                status.gameScore += status.lastPoints
                pendingPrepare = true
            }
        }
    }
}

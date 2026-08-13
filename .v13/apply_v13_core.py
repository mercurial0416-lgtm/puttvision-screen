from pathlib import Path

ROOT = Path('app/src/main/java/com/puttvision/screen')

def patch(path, transforms):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    original = s
    for old, new, label in transforms:
        if old not in s:
            raise RuntimeError(f'{path}: missing anchor {label}')
        s = s.replace(old, new, 1)
    if s == original:
        raise RuntimeError(f'{path}: no changes')
    p.write_text(s, encoding='utf-8')

# HFR: derive per-metric uncertainty from actual detection ratios and frame rate.
patch('HfrVideoAnalyzer.kt', [
    (
'''                ).coerceIn(0.0, 1.0)\n\n        return ShotMetrics(''',
'''                ).coerceIn(0.0, 1.0)\n\n        val uncertainty = MeasurementUncertaintyEstimator.forHfr(\n            fps = fps,\n            ballDetectionRatio = ballDetected,\n            headDetectionRatio = headDetected,\n            matDecelAvailable = matData.decelMps2 != null,\n            ballSpeedMps = correctedBallSpeed,\n            headSpeedMps = headSpeed,\n            impactOffsetMm = impactOffsetMm\n        )\n\n        return ShotMetrics(''',
        'HFR uncertainty calculation'
    ),
    (
'''            estimatedMatStimpM = matData.stimpM,\n            confidence = confidence\n        ) to impactFrame''',
'''            estimatedMatStimpM = matData.stimpM,\n            confidence = confidence,\n            uncertainty = uncertainty\n        ) to impactFrame''',
        'HFR uncertainty attach'
    )
])

# Room v3: persist uncertainty and lip-out/cup-contact result semantics.
patch('StatsRepository.kt', [
    (
'''    val estimatedMatStimpM: Double?,\n    val confidence: Double?,\n\n    val scoreTotal: Int,''',
'''    val estimatedMatStimpM: Double?,\n    val confidence: Double?,\n    val uncertaintyBallSpeedMps: Double?,\n    val uncertaintyLaunchDeg: Double?,\n    val uncertaintyHeadSpeedMps: Double?,\n    val uncertaintyFaceDeg: Double?,\n    val uncertaintyPathDeg: Double?,\n    val uncertaintyImpactMm: Double?,\n    val uncertaintyBasis: String?,\n\n    val scoreTotal: Int,''',
        'entity uncertainty columns'
    ),
    (
'''    val distanceToCupM: Double?,\n    val elapsedSec: Double?\n)''',
'''    val distanceToCupM: Double?,\n    val elapsedSec: Double?,\n    val lipOut: Boolean,\n    val cupContacts: Int\n)''',
        'entity lip columns'
    ),
    (
'''@Database(entities = [ShotEntity::class], version = 2, exportSchema = false)''',
'''@Database(entities = [ShotEntity::class], version = 3, exportSchema = false)''',
        'db version'
    ),
    (
'''}\n\nclass StatsRepository(context: Context) {''',
''' }\n\nprivate val MIGRATION_2_3 = object : Migration(2, 3) {\n    override fun migrate(db: SupportSQLiteDatabase) {\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyBallSpeedMps REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyLaunchDeg REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyHeadSpeedMps REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyFaceDeg REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyPathDeg REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyImpactMm REAL")\n        db.execSQL("ALTER TABLE shots ADD COLUMN uncertaintyBasis TEXT")\n        db.execSQL("ALTER TABLE shots ADD COLUMN lipOut INTEGER NOT NULL DEFAULT 0")\n        db.execSQL("ALTER TABLE shots ADD COLUMN cupContacts INTEGER NOT NULL DEFAULT 0")\n    }\n}\n\nclass StatsRepository(context: Context) {''',
        'migration 2-3 insertion'
    ),
    (
'''        .addMigrations(MIGRATION_1_2)''',
'''        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)''',
        'migration registration'
    ),
    (
'''            estimatedMatStimpM = m.estimatedMatStimpM,\n            confidence = m.confidence,\n            scoreTotal = score.total,''',
'''            estimatedMatStimpM = m.estimatedMatStimpM,\n            confidence = m.confidence,\n            uncertaintyBallSpeedMps = m.uncertainty?.ballSpeedMps,\n            uncertaintyLaunchDeg = m.uncertainty?.launchDeg,\n            uncertaintyHeadSpeedMps = m.uncertainty?.headSpeedMps,\n            uncertaintyFaceDeg = m.uncertainty?.faceDeg,\n            uncertaintyPathDeg = m.uncertainty?.pathDeg,\n            uncertaintyImpactMm = m.uncertainty?.impactMm,\n            uncertaintyBasis = m.uncertainty?.basis,\n            scoreTotal = score.total,''',
        'entity uncertainty mapping'
    ),
    (
'''            distanceToCupM = result?.distanceToCupM,\n            elapsedSec = result?.elapsedSec\n        )''',
'''            distanceToCupM = result?.distanceToCupM,\n            elapsedSec = result?.elapsedSec,\n            lipOut = result?.lipOut ?: false,\n            cupContacts = result?.cupContacts ?: 0\n        )''',
        'entity result mapping'
    ),
    (
'''            estimatedMatStimpM = e.estimatedMatStimpM,\n            confidence = e.confidence\n        )\n        val result = if (e.hasResult) {\n            SimResult(e.holed, e.finishX ?: 0.0, e.finishY ?: 0.0, e.distanceToCupM ?: 0.0, e.elapsedSec ?: 0.0)''',
'''            estimatedMatStimpM = e.estimatedMatStimpM,\n            confidence = e.confidence,\n            uncertainty = if (e.uncertaintyBallSpeedMps != null && e.uncertaintyLaunchDeg != null) {\n                MeasurementUncertainty(\n                    ballSpeedMps = e.uncertaintyBallSpeedMps,\n                    launchDeg = e.uncertaintyLaunchDeg,\n                    headSpeedMps = e.uncertaintyHeadSpeedMps,\n                    faceDeg = e.uncertaintyFaceDeg,\n                    pathDeg = e.uncertaintyPathDeg,\n                    impactMm = e.uncertaintyImpactMm,\n                    basis = e.uncertaintyBasis ?: "RESTORED"\n                )\n            } else null\n        )\n        val result = if (e.hasResult) {\n            SimResult(\n                e.holed,\n                e.finishX ?: 0.0,\n                e.finishY ?: 0.0,\n                e.distanceToCupM ?: 0.0,\n                e.elapsedSec ?: 0.0,\n                e.lipOut,\n                e.cupContacts\n            )''',
        'record uncertainty and lip restore'
    ),
    (
'''        putNullable("rawBall", m.rawBallSpeedMps); putNullable("matDecel", m.estimatedMatDecelMps2); putNullable("matStimp", m.estimatedMatStimpM); putNullable("confidence", m.confidence)\n        val s = r.strokeScore''',
'''        putNullable("rawBall", m.rawBallSpeedMps); putNullable("matDecel", m.estimatedMatDecelMps2); putNullable("matStimp", m.estimatedMatStimpM); putNullable("confidence", m.confidence)\n        m.uncertainty?.let { u ->\n            put("uncertainty", JSONObject().apply {\n                put("ball", u.ballSpeedMps); put("launch", u.launchDeg); putNullable("head", u.headSpeedMps); putNullable("face", u.faceDeg); putNullable("path", u.pathDeg); putNullable("impact", u.impactMm); put("basis", u.basis)\n            })\n        }\n        val s = r.strokeScore''',
        'json uncertainty export'
    ),
    (
'''        r.result?.let { result -> put("result", JSONObject().apply { put("holed", result.holed); put("x", result.finishX); put("y", result.finishY); put("cup", result.distanceToCupM); put("elapsed", result.elapsedSec) }) }''',
'''        r.result?.let { result -> put("result", JSONObject().apply { put("holed", result.holed); put("x", result.finishX); put("y", result.finishY); put("cup", result.distanceToCupM); put("elapsed", result.elapsedSec); put("lipOut", result.lipOut); put("contacts", result.cupContacts) }) }''',
        'json result export'
    ),
    (
'''    private fun jsonToRecord(j: JSONObject): ShotRecord? = runCatching {\n        val m = ShotMetrics(''',
'''    private fun jsonToRecord(j: JSONObject): ShotRecord? = runCatching {\n        val uj = j.optJSONObject("uncertainty")\n        val uncertainty = uj?.let {\n            MeasurementUncertainty(\n                ballSpeedMps = it.optDouble("ball", 0.0),\n                launchDeg = it.optDouble("launch", 0.0),\n                headSpeedMps = it.optNullableDouble("head"),\n                faceDeg = it.optNullableDouble("face"),\n                pathDeg = it.optNullableDouble("path"),\n                impactMm = it.optNullableDouble("impact"),\n                basis = it.optString("basis", "RESTORED")\n            )\n        }\n        val m = ShotMetrics(''',
        'json uncertainty parse'
    ),
    (
'''            backswingMs = j.optNullableDouble("backswingMs"), downswingMs = j.optNullableDouble("downswingMs"), tempoRatio = j.optNullableDouble("tempo"), backswingLengthCm = j.optNullableDouble("backswingLength"), peakHeadAccelerationMps2 = j.optNullableDouble("accel"), rawBallSpeedMps = j.optNullableDouble("rawBall"), estimatedMatDecelMps2 = j.optNullableDouble("matDecel"), estimatedMatStimpM = j.optNullableDouble("matStimp"), confidence = j.optNullableDouble("confidence")\n        )''',
'''            backswingMs = j.optNullableDouble("backswingMs"), downswingMs = j.optNullableDouble("downswingMs"), tempoRatio = j.optNullableDouble("tempo"), backswingLengthCm = j.optNullableDouble("backswingLength"), peakHeadAccelerationMps2 = j.optNullableDouble("accel"), rawBallSpeedMps = j.optNullableDouble("rawBall"), estimatedMatDecelMps2 = j.optNullableDouble("matDecel"), estimatedMatStimpM = j.optNullableDouble("matStimp"), confidence = j.optNullableDouble("confidence"), uncertainty = uncertainty\n        )''',
        'json uncertainty attach'
    ),
    (
'''        val result = rj?.let { SimResult(it.optBoolean("holed", false), it.optDouble("x", 0.0), it.optDouble("y", 0.0), it.optDouble("cup", 0.0), it.optDouble("elapsed", 0.0)) }''',
'''        val result = rj?.let { SimResult(it.optBoolean("holed", false), it.optDouble("x", 0.0), it.optDouble("y", 0.0), it.optDouble("cup", 0.0), it.optDouble("elapsed", 0.0), it.optBoolean("lipOut", false), it.optInt("contacts", 0)) }''',
        'json lip restore'
    )
])

print('V13 core patch applied')

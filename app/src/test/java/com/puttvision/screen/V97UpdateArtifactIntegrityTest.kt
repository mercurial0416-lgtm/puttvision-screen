package com.puttvision.screen

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V97UpdateArtifactIntegrityTest {
    @Test
    fun artifactVersionMustExactlyMatchManifestAndAdvanceInstalledVersion() {
        assertTrue(V49UpdatePolicy.validateArtifactVersion(100, 101, 101).valid)
        assertFalse(V49UpdatePolicy.validateArtifactVersion(100, 101, 102).valid)
        assertFalse(V49UpdatePolicy.validateArtifactVersion(101, 101, 101).valid)
        assertFalse(V49UpdatePolicy.validateArtifactVersion(-1, 101, 101).valid)
    }

    @Test
    fun downloadedApkSizeHasBothLowerAndUpperBounds() {
        assertFalse(V49UpdatePolicy.validateDownloadedApkSize(V49UpdatePolicy.MIN_APK_BYTES).valid)
        assertTrue(V49UpdatePolicy.validateDownloadedApkSize(V49UpdatePolicy.MIN_APK_BYTES + 1).valid)
        assertFalse(V49UpdatePolicy.validateDownloadedApkSize(V49UpdatePolicy.MAX_APK_BYTES + 1).valid)
    }

    @Test
    fun manifestUrlRejectsFragmentUserInfoAndHttp() {
        assertFalse(V49UpdatePolicy.validateManifestUrl("https://example.com/update.json#stale").valid)
        assertFalse(V49UpdatePolicy.validateManifestUrl("https://user@example.com/update.json").valid)
        assertFalse(V49UpdatePolicy.validateManifestUrl("http://example.com/update.json").valid)
        assertTrue(V49UpdatePolicy.validateManifestUrl("https://example.com/update.json").valid)
    }

    @Test
    fun limitedStreamRejectsInvalidLimitAndOverflow() {
        assertTrue(runCatching { V49UpdatePolicy.limited(ByteArrayInputStream(byteArrayOf(1)), 0) }.isFailure)
        val overflow = runCatching {
            val input = V49UpdatePolicy.limited(ByteArrayInputStream(ByteArray(9)), 8)
            input.read(ByteArray(16))
        }
        assertTrue(overflow.isFailure)
    }

    @Test
    fun cacheCleanupRemovesFarFutureTimestampPoisoning() {
        val root = Files.createTempDirectory("pv97-update").toFile()
        val updates = File(root, "updates").apply { mkdirs() }
        val now = 1_000_000L
        val future = File(updates, "future.apk").apply {
            writeBytes(ByteArray(4))
            setLastModified(now + V49UpdatePolicy.MAX_FUTURE_FILE_SKEW_MS + 1)
        }
        V49UpdatePolicy.cleanCache(updates, nowMs = now)
        assertFalse(future.exists())
        root.deleteRecursively()
    }

    @Test
    fun hardwarelessUpdateSuiteExercisesFourteenGuards() {
        val report = V97HardwarelessUpdateIntegrity.run()
        assertTrue(report.passed)
        assertTrue(report.checksPassed >= 14)
        assertTrue(report.checksTotal >= 14)
    }
}

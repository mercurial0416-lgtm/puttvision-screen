package com.puttvision.screen

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V97UpdateArtifactIntegrityTest {
    private val publicApk =
        "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/puttvision-update/releases/puttvision.apk"
    private val sha = "a".repeat(64)

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
    fun publicUpdateArtifactStaysPinnedToSupabaseBucket() {
        assertTrue(V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", publicApk, sha), true).valid)
        assertFalse(V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", publicApk, null), true).valid)
        assertFalse(V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", publicApk, "abcd"), true).valid)
        assertFalse(V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", "https://example.com/app.apk", sha), true).valid)
        assertFalse(V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/other/app.apk", sha), true).valid)
        assertFalse(V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", "$publicApk?download=1", sha), true).valid)
        assertFalse(V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", "https://razejagceyznnajioxgx.supabase.co:444/storage/v1/object/public/puttvision-update/app.apk", sha), true).valid)
        assertFalse(V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/puttvision-update/update.json", sha), true).valid)
    }

    @Test
    fun contentLengthPolicyRejectsZeroAndOversizeButAllowsUnknownLength() {
        assertFalse(V49UpdatePolicy.validateContentLength(0).valid)
        assertTrue(V49UpdatePolicy.validateContentLength(-1).valid)
        assertFalse(V49UpdatePolicy.validateContentLength(V49UpdatePolicy.MAX_APK_BYTES + 1).valid)
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
    fun hardwarelessUpdateSuiteExercisesTwentyFourGuards() {
        val report = V97HardwarelessUpdateIntegrity.run()
        assertTrue(report.passed)
        assertTrue(report.checksPassed >= 24)
        assertTrue(report.checksTotal >= 24)
    }
}

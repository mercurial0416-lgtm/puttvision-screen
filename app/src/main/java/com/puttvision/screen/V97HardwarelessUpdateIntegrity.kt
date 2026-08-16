package com.puttvision.screen

import java.io.ByteArrayInputStream

data class V97HardwarelessUpdateIntegrityReport(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val reason: String
)

/** Pure update-channel guard suite that can run without cameras, mats, or network access. */
object V97HardwarelessUpdateIntegrity {
    private const val PUBLIC_APK =
        "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/puttvision-update/releases/puttvision.apk"

    fun run(): V97HardwarelessUpdateIntegrityReport {
        var passed = 0
        var total = 0
        val failures = ArrayList<String>()

        fun check(name: String, ok: Boolean) {
            total++
            if (ok) passed++ else failures += name
        }

        val sha = "a".repeat(64)
        check("HTTPS", V49UpdatePolicy.validateManifestUrl("https://example.com/update.json").valid)
        check("HTTP_REJECT", !V49UpdatePolicy.validateManifestUrl("http://example.com/update.json").valid)
        check("FRAGMENT_REJECT", !V49UpdatePolicy.validateManifestUrl("https://example.com/update.json#old").valid)
        check("USERINFO_REJECT", !V49UpdatePolicy.validateManifestUrl("https://user@example.com/update.json").valid)

        check("PUBLIC_SHA_REQUIRED", !V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", PUBLIC_APK, null), true).valid)
        check("PUBLIC_SHA_VALID", V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", PUBLIC_APK, sha), true).valid)
        check("PUBLIC_BAD_SHA", !V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", PUBLIC_APK, "abcd"), true).valid)
        check("PUBLIC_HOST_PIN", !V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", "https://example.com/app.apk", sha), true).valid)
        check("PUBLIC_BUCKET_PIN", !V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/other/app.apk", sha), true).valid)
        check("PUBLIC_QUERY_REJECT", !V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", "$PUBLIC_APK?download=1", sha), true).valid)
        check("PUBLIC_ALT_PORT_REJECT", !V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", "https://razejagceyznnajioxgx.supabase.co:444/storage/v1/object/public/puttvision-update/app.apk", sha), true).valid)
        check("PUBLIC_NON_APK_REJECT", !V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/puttvision-update/update.json", sha), true).valid)

        check("VERSION_BIND", V49UpdatePolicy.validateArtifactVersion(100, 101, 101).valid)
        check("VERSION_MISMATCH", !V49UpdatePolicy.validateArtifactVersion(100, 101, 102).valid)
        check("NON_UPGRADE", !V49UpdatePolicy.validateArtifactVersion(101, 101, 101).valid)
        check("BAD_INSTALLED_VERSION", !V49UpdatePolicy.validateArtifactVersion(-1, 101, 101).valid)

        check("SMALL_APK", !V49UpdatePolicy.validateDownloadedApkSize(V49UpdatePolicy.MIN_APK_BYTES).valid)
        check("NORMAL_APK", V49UpdatePolicy.validateDownloadedApkSize(V49UpdatePolicy.MIN_APK_BYTES + 1).valid)
        check("LARGE_APK", !V49UpdatePolicy.validateDownloadedApkSize(V49UpdatePolicy.MAX_APK_BYTES + 1).valid)
        check("ZERO_CONTENT_LENGTH", !V49UpdatePolicy.validateContentLength(0).valid)
        check("UNKNOWN_CONTENT_LENGTH", V49UpdatePolicy.validateContentLength(-1).valid)
        check("OVERSIZE_CONTENT_LENGTH", !V49UpdatePolicy.validateContentLength(V49UpdatePolicy.MAX_APK_BYTES + 1).valid)

        check("BAD_STREAM_LIMIT", runCatching { V49UpdatePolicy.limited(ByteArrayInputStream(byteArrayOf(1)), 0) }.isFailure)
        check("STREAM_OVERFLOW", runCatching {
            val input = V49UpdatePolicy.limited(ByteArrayInputStream(ByteArray(9)), 8)
            input.read(ByteArray(16))
        }.isFailure)

        return V97HardwarelessUpdateIntegrityReport(
            passed = passed == total,
            checksPassed = passed,
            checksTotal = total,
            reason = if (failures.isEmpty()) "update artifact/version/transport guards pass" else failures.joinToString(",")
        )
    }
}

object V97HardwarelessUpdateIntegrityRuntime {
    @Volatile private var latest: V97HardwarelessUpdateIntegrityReport? = null

    fun run(): V97HardwarelessUpdateIntegrityReport =
        V97HardwarelessUpdateIntegrity.run().also { latest = it }

    fun snapshot(): V97HardwarelessUpdateIntegrityReport? = latest

    fun clear() {
        latest = null
    }
}

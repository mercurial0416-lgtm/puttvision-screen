package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V49UpdateReleaseTagContractRegressionTest {
    private fun info(versionCode: Int, tag: String): UpdateInfo = UpdateInfo(
        versionCode = versionCode,
        versionName = "0.7.${versionCode - 100000}",
        apkUrl = "https://github.com/mercurial0416-lgtm/puttvision-screen/releases/download/$tag/puttvision-consumer.apk",
        sha256 = "a".repeat(64)
    )

    private fun info(versionCode: Int, tagVersionCode: Int): UpdateInfo = info(versionCode, "pv-$tagVersionCode")

    @Test
    fun publicGithubReleaseTagMustMatchManifestVersionCode() {
        assertTrue(V49UpdatePolicy.validateInfo(info(100506, 100506), publicChannel = true).valid)
        val mismatch = V49UpdatePolicy.validateInfo(info(100506, 100505), publicChannel = true)
        assertFalse(mismatch.valid)
        assertTrue(mismatch.reason.orEmpty().contains("versionCode"))
    }

    @Test
    fun oversizedGithubReleaseTagMustNotBypassVersionContract() {
        val oversized = info(100506, "pv-9999999999999999999999999999999999999999")
        val result = V49UpdatePolicy.validateInfo(oversized, publicChannel = true)
        assertFalse(result.valid)
        assertTrue(result.reason.orEmpty().contains("versionCode"))
    }
}

package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V49UpdateReleaseTagContractRegressionTest {
    private fun info(versionCode: Int, tagVersionCode: Int): UpdateInfo = UpdateInfo(
        versionCode = versionCode,
        versionName = "0.7.${versionCode - 100000}",
        apkUrl = "https://github.com/mercurial0416-lgtm/puttvision-screen/releases/download/pv-$tagVersionCode/puttvision-consumer.apk",
        sha256 = "a".repeat(64)
    )

    @Test
    fun publicGithubReleaseTagMustMatchManifestVersionCode() {
        assertTrue(V49UpdatePolicy.validateInfo(info(100506, 100506), publicChannel = true).valid)
        val mismatch = V49UpdatePolicy.validateInfo(info(100506, 100505), publicChannel = true)
        assertFalse(mismatch.valid)
        assertTrue(mismatch.reason.orEmpty().contains("versionCode"))
    }
}

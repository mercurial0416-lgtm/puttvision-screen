package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedManifestEndpointRegressionTest {
    private val manifest = "https://example.com/update.json"
    private val publicApk = "https://github.com/mercurial0416-lgtm/puttvision-screen/releases/download/pv-100552/puttvision-consumer.apk"

    @Test
    fun manifestTransportRejectsQueryAndNonStandardHttpsPort() {
        assertTrue(V49UpdatePolicy.validateManifestUrl(manifest).valid)
        assertTrue(V49UpdatePolicy.validateManifestUrl("https://example.com:443/update.json").valid)

        assertFalse(V49UpdatePolicy.validateManifestUrl("$manifest?cache=bust").valid)
        assertFalse(V49UpdatePolicy.validateManifestUrl("https://example.com:444/update.json").valid)
    }

    @Test
    fun publicApkRejectsAmbiguousEncodedAndDotSegmentPaths() {
        assertTrue(V49UpdatePolicy.validatePublicApkUrl(publicApk).valid)

        assertFalse(
            V49UpdatePolicy.validatePublicApkUrl(
                "https://github.com/mercurial0416-lgtm/puttvision-screen/releases/download/pv-100552/%70uttvision-consumer.apk"
            ).valid
        )
        assertFalse(
            V49UpdatePolicy.validatePublicApkUrl(
                "https://github.com/mercurial0416-lgtm/puttvision-screen/releases/download/pv-100552/../puttvision-consumer.apk"
            ).valid
        )
    }
}

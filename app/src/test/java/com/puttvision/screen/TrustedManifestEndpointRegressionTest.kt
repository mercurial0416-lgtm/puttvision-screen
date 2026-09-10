package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedManifestEndpointRegressionTest {
    private val manifest = "https://example.com/update.json"

    @Test
    fun manifestTransportRejectsQueryAndNonStandardHttpsPort() {
        assertTrue(V49UpdatePolicy.validateManifestUrl(manifest).valid)
        assertTrue(V49UpdatePolicy.validateManifestUrl("https://example.com:443/update.json").valid)

        assertFalse(V49UpdatePolicy.validateManifestUrl("$manifest?cache=bust").valid)
        assertFalse(V49UpdatePolicy.validateManifestUrl("https://example.com:444/update.json").valid)
    }
}

package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedManifestEndpointRegressionTest {
    private val trustedManifest =
        "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/puttvision-update/update-v2.json"

    @Test
    fun acceptsOnlyTheTrustedPublicManifestEndpoint() {
        assertTrue(V49UpdatePolicy.validateManifestUrl(trustedManifest).valid)

        assertFalse(
            V49UpdatePolicy.validateManifestUrl(
                "https://example.com/storage/v1/object/public/puttvision-update/update-v2.json"
            ).valid
        )
        assertFalse(
            V49UpdatePolicy.validateManifestUrl(
                "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/puttvision-update/other.json"
            ).valid
        )
        assertFalse(
            V49UpdatePolicy.validateManifestUrl("$trustedManifest?cache=bust").valid
        )
        assertFalse(
            V49UpdatePolicy.validateManifestUrl(
                "https://razejagceyznnajioxgx.supabase.co:444/storage/v1/object/public/puttvision-update/update-v2.json"
            ).valid
        )
    }
}

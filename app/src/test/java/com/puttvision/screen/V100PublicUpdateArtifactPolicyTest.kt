package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V100PublicUpdateArtifactPolicyTest {
    private val sha = "a".repeat(64)

    @Test
    fun trustedPublicBucketApkIsAccepted() {
        val info = UpdateInfo(
            versionCode = 123,
            versionName = "0.7.123",
            apkUrl = "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/puttvision-update/releases/puttvision-123.apk",
            sha256 = sha
        )
        assertTrue(V49UpdatePolicy.validateInfo(info, publicChannel = true).valid)
    }

    @Test
    fun arbitraryHttpsHostCannotReplacePublicApk() {
        val result = V49UpdatePolicy.validateInfo(
            UpdateInfo(123, "0.7.123", "https://example.com/puttvision.apk", sha),
            publicChannel = true
        )
        assertFalse(result.valid)
    }

    @Test
    fun siblingSupabaseBucketCannotReplacePublicApk() {
        val result = V49UpdatePolicy.validateInfo(
            UpdateInfo(
                123,
                "0.7.123",
                "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/other-bucket/puttvision.apk",
                sha
            ),
            publicChannel = true
        )
        assertFalse(result.valid)
    }

    @Test
    fun publicApkQueryFragmentAndNonApkAreRejected() {
        val base = "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/puttvision-update/puttvision.apk"
        assertFalse(V49UpdatePolicy.validatePublicApkUrl("$base?redirect=https://evil.example").valid)
        assertFalse(V49UpdatePolicy.validatePublicApkUrl("$base#payload").valid)
        assertFalse(
            V49UpdatePolicy.validatePublicApkUrl(
                "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/puttvision-update/update.json"
            ).valid
        )
    }

    @Test
    fun developerPrivateGithubAssetRemainsAllowedByGenericPolicy() {
        val info = UpdateInfo(
            versionCode = 123,
            versionName = "0.7.123",
            apkUrl = "https://api.github.com/repos/mercurial0416-lgtm/puttvision-screen/releases/assets/12345",
            sha256 = null,
            githubToken = "private"
        )
        assertTrue(V49UpdatePolicy.validateInfo(info, publicChannel = false).valid)
    }
}

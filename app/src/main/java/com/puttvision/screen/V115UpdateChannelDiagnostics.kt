package com.puttvision.screen

enum class V115UpdateChannelState {
    CURRENT,
    UPDATE_AVAILABLE,
    CHANNEL_BEHIND,
    NAME_MISMATCH,
    INVALID
}

data class V115UpdateChannelStatus(
    val state: V115UpdateChannelState,
    val healthy: Boolean,
    val installedVersionCode: Long,
    val manifestVersionCode: Long,
    val installedVersionName: String,
    val manifestVersionName: String,
    val summary: String
)

/**
 * Pure update-channel diagnostics used by the hardwareless lab and updater UI plumbing.
 * This deliberately distinguishes a genuinely current install from a stale manifest so
 * "latest" can never silently mean "the release channel is behind this APK".
 */
object V115UpdateChannelDiagnostics {
    private const val MAX_NAME_CHARS = 48

    fun classify(
        installedVersionCode: Long,
        installedVersionName: String?,
        manifestVersionCode: Long,
        manifestVersionName: String?
    ): V115UpdateChannelStatus {
        val installedName = normalizeName(installedVersionName, installedVersionCode)
        val manifestName = normalizeName(manifestVersionName, manifestVersionCode)

        if (installedVersionCode <= 0L || manifestVersionCode <= 0L) {
            return V115UpdateChannelStatus(
                state = V115UpdateChannelState.INVALID,
                healthy = false,
                installedVersionCode = installedVersionCode,
                manifestVersionCode = manifestVersionCode,
                installedVersionName = installedName,
                manifestVersionName = manifestName,
                summary = "invalid version metadata"
            )
        }

        val state = when {
            manifestVersionCode > installedVersionCode -> V115UpdateChannelState.UPDATE_AVAILABLE
            manifestVersionCode < installedVersionCode -> V115UpdateChannelState.CHANNEL_BEHIND
            installedName != manifestName -> V115UpdateChannelState.NAME_MISMATCH
            else -> V115UpdateChannelState.CURRENT
        }
        val healthy = state == V115UpdateChannelState.CURRENT || state == V115UpdateChannelState.UPDATE_AVAILABLE
        val summary = when (state) {
            V115UpdateChannelState.CURRENT -> "current · installed $installedName · channel $manifestName"
            V115UpdateChannelState.UPDATE_AVAILABLE -> "update available · installed $installedName · channel $manifestName"
            V115UpdateChannelState.CHANNEL_BEHIND -> "channel behind · installed $installedName · channel $manifestName"
            V115UpdateChannelState.NAME_MISMATCH -> "same code/name mismatch · installed $installedName · channel $manifestName"
            V115UpdateChannelState.INVALID -> "invalid version metadata"
        }
        return V115UpdateChannelStatus(
            state = state,
            healthy = healthy,
            installedVersionCode = installedVersionCode,
            manifestVersionCode = manifestVersionCode,
            installedVersionName = installedName,
            manifestVersionName = manifestName,
            summary = summary
        )
    }

    internal fun normalizeName(raw: String?, fallbackCode: Long): String {
        val compact = raw.orEmpty()
            .trim()
            .removePrefix("PuttVision ")
            .removePrefix("v")
            .replace(Regex("\\s+"), " ")
            .take(MAX_NAME_CHARS)
        return compact.ifBlank { fallbackCode.toString() }
    }
}

data class V115HardwarelessUpdateStatusReport(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val reason: String
)

/** Deterministic update-state guard suite; no network, APK, camera, or mat required. */
object V115HardwarelessUpdateStatusSuite {
    fun run(): V115HardwarelessUpdateStatusReport {
        var passed = 0
        var total = 0
        val failures = ArrayList<String>()

        fun check(name: String, ok: Boolean) {
            total++
            if (ok) passed++ else failures += name
        }

        val current = V115UpdateChannelDiagnostics.classify(100147, "0.7.147", 100147, "0.7.147")
        check("CURRENT", current.state == V115UpdateChannelState.CURRENT && current.healthy)

        val newer = V115UpdateChannelDiagnostics.classify(100147, "0.7.147", 100148, "0.7.148")
        check("UPDATE_AVAILABLE", newer.state == V115UpdateChannelState.UPDATE_AVAILABLE && newer.healthy)

        val lag = V115UpdateChannelDiagnostics.classify(100148, "0.7.148", 100147, "0.7.147")
        check("CHANNEL_BEHIND", lag.state == V115UpdateChannelState.CHANNEL_BEHIND && !lag.healthy)

        val mismatch = V115UpdateChannelDiagnostics.classify(100147, "0.7.147", 100147, "0.7.147-hotfix")
        check("NAME_MISMATCH", mismatch.state == V115UpdateChannelState.NAME_MISMATCH && !mismatch.healthy)

        check("BAD_INSTALLED", V115UpdateChannelDiagnostics.classify(0, "0", 100147, "0.7.147").state == V115UpdateChannelState.INVALID)
        check("BAD_MANIFEST", V115UpdateChannelDiagnostics.classify(100147, "0.7.147", -1, "bad").state == V115UpdateChannelState.INVALID)

        val prefixes = V115UpdateChannelDiagnostics.classify(100147, " PuttVision 0.7.147 ", 100147, "v0.7.147")
        check("PREFIX_NORMALIZE", prefixes.state == V115UpdateChannelState.CURRENT)

        val whitespace = V115UpdateChannelDiagnostics.classify(100147, "0.7.147", 100147, "  0.7.147   ")
        check("WHITESPACE_NORMALIZE", whitespace.state == V115UpdateChannelState.CURRENT)

        val blanks = V115UpdateChannelDiagnostics.classify(100147, null, 100147, "")
        check("BLANK_FALLBACK", blanks.state == V115UpdateChannelState.CURRENT && blanks.installedVersionName == "100147")

        val longName = "x".repeat(200)
        val bounded = V115UpdateChannelDiagnostics.classify(100147, longName, 100147, longName)
        check("NAME_BOUND", bounded.installedVersionName.length <= 48 && bounded.manifestVersionName.length <= 48)

        val large = V115UpdateChannelDiagnostics.classify(Long.MAX_VALUE - 1, "future", Long.MAX_VALUE, "future+1")
        check("LONG_CODE_SAFE", large.state == V115UpdateChannelState.UPDATE_AVAILABLE)

        check("SUMMARY_BOUND", current.summary.length <= 160 && lag.summary.length <= 160)

        return V115HardwarelessUpdateStatusReport(
            passed = passed == total,
            checksPassed = passed,
            checksTotal = total,
            reason = if (failures.isEmpty()) "update channel state diagnostics pass" else failures.joinToString(",")
        )
    }
}

object V115HardwarelessUpdateStatusRuntime {
    @Volatile private var latest: V115HardwarelessUpdateStatusReport? = null

    fun run(): V115HardwarelessUpdateStatusReport =
        V115HardwarelessUpdateStatusSuite.run().also { latest = it }

    fun snapshot(): V115HardwarelessUpdateStatusReport? = latest

    fun clear() {
        latest = null
    }
}

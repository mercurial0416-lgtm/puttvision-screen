package com.puttvision.screen

/**
 * Fail-closed signer transition policy for the self-updater.
 *
 * Android PackageManager may omit proof-of-rotation history when inspecting an APK archive,
 * even though Package Installer can validate the v3 signing lineage. Keep exact-signer
 * compatibility, and allow a signer transition only inside the audited PuttVision lineage.
 */
internal object V130UpdateSignerPolicy {
    internal val trustedSignerSha256: Set<String> = setOf(
        "09a9865aea664f10cd368a42a7b720a611a8b29d3a44dad8354b457f6281cac7",
        "5fe0e687feb855c03fbda9fcc5d15fc8651beef3e30880cda0e9c65af2a421f8",
        "7a5962aee08d9edc655da5f58cb8199b4d41604793ef539bd0111df2ca0900cb"
    )

    internal fun allows(
        installedSigners: Set<String>,
        candidateSigners: Set<String>,
        candidateCurrentSigners: Set<String>
    ): Boolean {
        val installed = installedSigners.normalized()
        val candidate = candidateSigners.normalized()
        val current = candidateCurrentSigners.normalized()
        if (installed.isEmpty() || candidate.isEmpty() || current.isEmpty()) return false

        if (installed.any { it in candidate }) return true

        val installedIsTrusted = installed.any { it in trustedSignerSha256 }
        val candidateCurrentIsTrusted = current.size == 1 && current.single() in trustedSignerSha256
        return installedIsTrusted && candidateCurrentIsTrusted
    }

    private fun Set<String>.normalized(): Set<String> =
        asSequence()
            .map { it.lowercase().replace(":", "").trim() }
            .filter { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } }
            .toSet()
}

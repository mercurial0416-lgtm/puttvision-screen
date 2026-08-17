package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V130UpdateSignerPolicyTest {
    private val original = "09a9865aea664f10cd368a42a7b720a611a8b29d3a44dad8354b457f6281cac7"
    private val rotated2026 = "5fe0e687feb855c03fbda9fcc5d15fc8651beef3e30880cda0e9c65af2a421f8"
    private val current = "7a5962aee08d9edc655da5f58cb8199b4d41604793ef539bd0111df2ca0900cb"
    private val unknown = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val rogue = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

    @Test
    fun exactSignerStillWorksEvenIfLegacyDigestIsNotPinned() {
        assertTrue(V130UpdateSignerPolicy.allows(setOf(unknown), setOf(unknown), setOf(unknown)))
    }

    @Test
    fun auditedOriginalCanMoveToCurrentSignerWhenArchiveHistoryIsMissing() {
        assertTrue(V130UpdateSignerPolicy.allows(setOf(original), setOf(current), setOf(current)))
    }

    @Test
    fun audited2026SignerCanMoveToCurrentSignerWhenArchiveHistoryIsMissing() {
        assertTrue(V130UpdateSignerPolicy.allows(setOf(rotated2026), setOf(current), setOf(current)))
    }

    @Test
    fun auditedLineageHistoryAlsoAllowsCurrentSigner() {
        assertTrue(
            V130UpdateSignerPolicy.allows(
                setOf(original, rotated2026),
                setOf(current),
                setOf(current)
            )
        )
    }

    @Test
    fun untrustedCandidateSignerIsRejected() {
        assertFalse(V130UpdateSignerPolicy.allows(setOf(rotated2026), setOf(rogue), setOf(rogue)))
    }

    @Test
    fun multipleCandidateCurrentSignersAreRejectedForRotation() {
        assertFalse(
            V130UpdateSignerPolicy.allows(
                setOf(rotated2026),
                setOf(current, rogue),
                setOf(current, rogue)
            )
        )
    }

    @Test
    fun emptySignerDataIsRejected() {
        assertFalse(V130UpdateSignerPolicy.allows(emptySet(), setOf(current), setOf(current)))
        assertFalse(V130UpdateSignerPolicy.allows(setOf(rotated2026), emptySet(), setOf(current)))
        assertFalse(V130UpdateSignerPolicy.allows(setOf(rotated2026), setOf(current), emptySet()))
    }
}

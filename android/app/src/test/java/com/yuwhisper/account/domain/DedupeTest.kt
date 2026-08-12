package com.yuwhisper.account.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DedupeTest {

    @Test
    fun dedupeSamePaymentWithinWindow() {
        val a = Candidate(
            source = "wechat",
            amountCents = 3650,
            merchant = "瑞幸",
            occurredAt = Instant.parse("2026-08-12T02:00:00Z"),
        )
        val b = Candidate(
            source = "wechat",
            amountCents = 3650,
            merchant = "瑞幸",
            occurredAt = Instant.parse("2026-08-12T02:00:20Z"),
        )
        assertTrue(Dedupe.isDuplicate(a, listOf(a), windowSeconds = 120))
        assertTrue(Dedupe.isDuplicate(b, listOf(a), windowSeconds = 120))
    }

    @Test
    fun notDuplicateOutsideWindow() {
        val a = Candidate(
            source = "wechat",
            amountCents = 3650,
            merchant = "瑞幸",
            occurredAt = Instant.parse("2026-08-12T02:00:00Z"),
        )
        val b = Candidate(
            source = "wechat",
            amountCents = 3650,
            merchant = "瑞幸",
            occurredAt = Instant.parse("2026-08-12T02:05:00Z"),
        )
        assertFalse(Dedupe.isDuplicate(b, listOf(a), windowSeconds = 120))
    }

    @Test
    fun notDuplicateDifferentAmount() {
        val a = Candidate(
            source = "wechat",
            amountCents = 3650,
            merchant = "瑞幸",
            occurredAt = Instant.parse("2026-08-12T02:00:00Z"),
        )
        val b = Candidate(
            source = "wechat",
            amountCents = 3651,
            merchant = "瑞幸",
            occurredAt = Instant.parse("2026-08-12T02:00:20Z"),
        )
        assertFalse(Dedupe.isDuplicate(b, listOf(a), windowSeconds = 120))
    }
}

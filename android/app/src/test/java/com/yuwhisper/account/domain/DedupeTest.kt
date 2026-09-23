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
    fun notDuplicateWhenSameAmountMuchLater() {
        val first = Candidate(
            source = "wechat",
            amountCents = 100,
            merchant = "未知商户",
            occurredAt = Instant.parse("2026-08-12T02:00:00Z"),
        )
        val later = Candidate(
            source = "wechat",
            amountCents = 100,
            merchant = "未知商户",
            occurredAt = Instant.parse("2026-08-12T02:10:00Z"),
        )
        assertFalse(Dedupe.isDuplicate(later, listOf(first), windowSeconds = 8))
    }

    @Test
    fun duplicateWhenNotificationReusesSameTimestamp() {
        val t = Instant.parse("2026-08-12T02:00:00Z")
        val first = Candidate("wechat", 100, "未知商户", t)
        val update = Candidate("wechat", 100, "未知商户", t)
        assertTrue(Dedupe.isDuplicate(update, listOf(first), windowSeconds = 8))
    }
}

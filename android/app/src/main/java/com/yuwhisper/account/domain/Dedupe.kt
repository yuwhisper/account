package com.yuwhisper.account.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * A payment candidate considered for confirmation / ledger insert.
 */
data class Candidate(
    val source: String,
    val amountCents: Int,
    val merchant: String,
    val occurredAt: Instant,
)

/**
 * Deduplicates dual-channel / repeated payment events within a time window.
 *
 * Match key: source + amountCents + merchant (normalized) within [windowSeconds].
 */
object Dedupe {

    fun isDuplicate(
        candidate: Candidate,
        existing: List<Candidate>,
        windowSeconds: Int,
    ): Boolean {
        val window = Duration.ofSeconds(windowSeconds.toLong())
        val keyMerchant = normalizeMerchant(candidate.merchant)
        return existing.any { other ->
            other.source == candidate.source &&
                other.amountCents == candidate.amountCents &&
                normalizeMerchant(other.merchant) == keyMerchant &&
                abs(Duration.between(other.occurredAt, candidate.occurredAt).seconds) <= window.seconds
        }
    }

    private fun normalizeMerchant(merchant: String): String =
        merchant.trim().lowercase()
}

package com.yuwhisper.account.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * A payment candidate considered for confirmation / ledger insert.
 */
data class Candidate(
    val source: String,
    val amountCents: Int?,
    val merchant: String,
    val occurredAt: Instant,
)

/**
 * Deduplicates dual-channel / repeated payment events within a time window.
 *
 * - Known amount: source + amount + merchant
 * - Unknown amount: only dedupe very recent identical source+merchant (avoid blocking next real pay)
 */
object Dedupe {

    fun isDuplicate(
        candidate: Candidate,
        existing: List<Candidate>,
        windowSeconds: Int,
    ): Boolean {
        val keyMerchant = normalizeMerchant(candidate.merchant)
        val amount = candidate.amountCents
        if (amount != null) {
            val window = Duration.ofSeconds(windowSeconds.toLong())
            return existing.any { other ->
                other.source == candidate.source &&
                    other.amountCents == amount &&
                    normalizeMerchant(other.merchant) == keyMerchant &&
                    abs(Duration.between(other.occurredAt, candidate.occurredAt).seconds) <= window.seconds
            }
        }
        // Null amount: short window only (15s) — first empty scan must not block a later filled scan
        // of a *new* payment minutes later.
        val short = Duration.ofSeconds(15)
        return existing.any { other ->
            other.source == candidate.source &&
                other.amountCents == null &&
                normalizeMerchant(other.merchant) == keyMerchant &&
                abs(Duration.between(other.occurredAt, candidate.occurredAt).seconds) <= short.seconds
        }
    }

    private fun normalizeMerchant(merchant: String): String =
        merchant.trim().lowercase()
}

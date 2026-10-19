package com.apporganizer.launcher.domain

import kotlin.math.pow

/**
 * Favourite-apps ranking engine (pure functions, no Android dependencies).
 *
 * score = launches(24h) × 5 + launches(7d) × 2 + launches(30d) × 0.5 + recency bonus
 * recency bonus = 6 × 0.5^(hours since last use / 24), i.e. it halves every 24 hours.
 * Stability: apps already in yesterday's favourites get score × 1.10, so borderline apps don't flip daily.
 *
 * Scores are computed once a day. Display uses only the stored scores
 * plus the current lock/exclude/dock state, so unlocked icons don't move during the day.
 */
object RankingEngine {
    const val W_24H = 5.0
    const val W_7D = 2.0
    const val W_30D = 0.5
    const val RECENCY_MAX = 6.0
    const val RECENCY_HALF_LIFE_HOURS = 24.0
    const val STICKINESS = 1.10

    private const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR

    data class Stats(
        val c24h: Int = 0,
        val c7d: Int = 0,
        val c30d: Int = 0,
        val lastUsedAt: Long? = null,
    )

    data class Item(
        val key: String,
        val label: String,
        val score: Double,
        val lastUsedAt: Long?,
        val lockedSlot: Int?,
        val excluded: Boolean,
        val inDock: Boolean,
    )

    fun statsFrom(launchTimes: List<Long>, now: Long): Stats {
        var c24 = 0; var c7 = 0; var c30 = 0; var last: Long? = null
        for (t in launchTimes) {
            val age = now - t
            if (age < 0) continue
            if (age <= DAY) c24++
            if (age <= 7 * DAY) c7++
            if (age <= 30 * DAY) c30++
            if (last == null || t > last) last = t
        }
        return Stats(c24, c7, c30, last)
    }

    fun score(s: Stats, now: Long): Double {
        val base = W_24H * s.c24h + W_7D * s.c7d + W_30D * s.c30d
        val recency = s.lastUsedAt?.let {
            val hours = (now - it).coerceAtLeast(0L).toDouble() / HOUR
            RECENCY_MAX * 0.5.pow(hours / RECENCY_HALF_LIFE_HOURS)
        } ?: 0.0
        return base + recency
    }

    /**
     * Arranges the favourites row. Locked apps stay in lockedSlot; free slots are filled by score.
     * Excluded, docked and zero-score apps are not auto-placed (a lock overrides these rules).
     */
    fun arrange(items: List<Item>, slotCount: Int): List<String?> {
        val slots = arrayOfNulls<String>(slotCount)
        items.asSequence()
            .filter { it.lockedSlot != null && it.lockedSlot in 0 until slotCount }
            .sortedBy { it.lockedSlot }
            .forEach { item -> if (slots[item.lockedSlot!!] == null) slots[item.lockedSlot] = item.key }

        val placed = slots.filterNotNull().toSet()
        val ranked = items
            .filter { it.key !in placed && !it.excluded && !it.inDock && it.score > 0.0 }
            .sortedWith(
                compareByDescending<Item> { it.score }
                    .thenByDescending { it.lastUsedAt ?: 0L }
                    .thenBy { it.label.lowercase() }
            )
        val iter = ranked.iterator()
        for (i in slots.indices) {
            if (slots[i] == null && iter.hasNext()) slots[i] = iter.next().key
        }
        return slots.toList()
    }
}

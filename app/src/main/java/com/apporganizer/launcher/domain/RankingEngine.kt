package com.apporganizer.launcher.domain

import kotlin.math.pow

/**
 * 常用应用排序引擎（纯函数，无 Android 依赖）。
 *
 * score = 24h 启动次数 × 5 + 7 天启动次数 × 2 + 30 天启动次数 × 0.5 + 最近使用加分
 * 最近使用加分 = 6 × 0.5^(距上次使用小时数 / 24)，即每 24 小时减半。
 * 稳定性：前一天已在常用区的应用，新分数 × 1.10，减少边界应用每天来回跳动。
 *
 * 分数每天只计算一次（DailyRankingWorker）。首页显示时只用“已存储的分数”
 * + 当前的锁定/排除/Dock 状态做排列，因此未锁定图标一天之内不会移动。
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
     * 排列常用区。锁定应用固定在 lockedSlot；其余空位按分数填充。
     * 被排除、已在 Dock、分数为 0 的应用不自动进入常用区（锁定优先于这些条件）。
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

package com.apporganizer.launcher.domain

import com.apporganizer.launcher.domain.RankingEngine.DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingEngineTest {
    private val now = 1_800_000_000_000L

    private fun item(key: String, score: Double, locked: Int? = null, excluded: Boolean = false, dock: Boolean = false) =
        RankingEngine.Item(key, key, score, null, locked, excluded, dock)

    @Test fun `stats windows overlap as specified`() {
        val s = RankingEngine.statsFrom(listOf(now - DAY / 2, now - 3 * DAY, now - 20 * DAY, now - 40 * DAY), now)
        assertEquals(1, s.c24h)
        assertEquals(2, s.c7d)
        assertEquals(3, s.c30d)
        assertEquals(now - DAY / 2, s.lastUsedAt)
    }

    @Test fun `recent usage beats old heavy usage`() {
        // 旧应用：25 天前用了 30 次；新习惯：最近 2 天用了 6 次
        val old = RankingEngine.statsFrom(List(30) { now - 25 * DAY }, now)
        val recent = RankingEngine.statsFrom(List(6) { now - DAY - it * 1000L }, now)
        assertTrue(RankingEngine.score(recent, now) > RankingEngine.score(old, now))
    }

    @Test fun `recency bonus halves every 24h`() {
        val a = RankingEngine.score(RankingEngine.Stats(lastUsedAt = now), now)
        val b = RankingEngine.score(RankingEngine.Stats(lastUsedAt = now - DAY), now)
        assertEquals(RankingEngine.RECENCY_MAX, a, 1e-9)
        assertEquals(RankingEngine.RECENCY_MAX / 2, b, 1e-9)
    }

    @Test fun `locked apps keep slot and others fill by score`() {
        val slots = RankingEngine.arrange(
            listOf(item("a", 10.0), item("b", 50.0), item("c", 1.0, locked = 0), item("d", 30.0)),
            3,
        )
        assertEquals(listOf("c", "b", "d"), slots)
    }

    @Test fun `excluded, dock and zero-score apps are not auto placed`() {
        val slots = RankingEngine.arrange(
            listOf(item("x", 99.0, excluded = true), item("y", 99.0, dock = true), item("z", 0.0), item("w", 1.0)),
            3,
        )
        assertEquals(listOf("w", null, null), slots)
    }

    @Test fun `lock beyond slot count is ignored`() {
        val slots = RankingEngine.arrange(listOf(item("a", 1.0, locked = 10), item("b", 2.0)), 2)
        assertEquals(listOf("b", "a"), slots)
    }
}

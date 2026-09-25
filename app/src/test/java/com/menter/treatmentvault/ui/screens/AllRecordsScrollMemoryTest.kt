package com.menter.treatmentvault.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AllRecordsScrollMemoryTest {

    @Test
    fun `capture then resolve by anchor key`() {
        val memory = AllRecordsScrollMemory().capture(anchorKey = "note:3", lazyColumnIndex = 10, offset = 40)
        val target = resolveAllRecordsRestoreTarget(
            memory = memory,
            orderedRecordKeys = listOf("exam:1", "note:3", "supply:7"),
            headerCount = 3,
        )
        assertEquals(AllRecordsScrollTarget(4, 40), target)
    }

    @Test
    fun `resolve by raw index when anchor missing`() {
        val memory = AllRecordsScrollMemory().capture(anchorKey = "note:999", lazyColumnIndex = 5, offset = 12)
        val target = resolveAllRecordsRestoreTarget(
            memory = memory,
            orderedRecordKeys = listOf("exam:1", "note:3", "supply:7"),
            headerCount = 3,
        )
        assertEquals(AllRecordsScrollTarget(5, 12), target)
    }

    @Test
    fun `data refresh with reorder still anchors to same record`() {
        val memory = AllRecordsScrollMemory().capture(anchorKey = "note:3", lazyColumnIndex = 10, offset = 40)
        // 返回后记录被重排，note:3 从 index 1 换到 index 0
        val target = resolveAllRecordsRestoreTarget(
            memory = memory,
            orderedRecordKeys = listOf("note:3", "exam:1", "supply:7"),
            headerCount = 3,
        )
        assertEquals(AllRecordsScrollTarget(3, 40), target)
    }

    @Test
    fun `header count change shifts resolved index`() {
        val memory = AllRecordsScrollMemory().capture(anchorKey = "note:3", lazyColumnIndex = 10, offset = 0)
        val withThree = resolveAllRecordsRestoreTarget(memory, listOf("exam:1", "note:3"), 3)
        val withTwo = resolveAllRecordsRestoreTarget(memory, listOf("exam:1", "note:3"), 2)
        assertEquals(AllRecordsScrollTarget(4, 0), withThree)
        assertEquals(AllRecordsScrollTarget(3, 0), withTwo)
    }

    @Test
    fun `no restore when pending false`() {
        val memory = AllRecordsScrollMemory(anchorKey = "note:3", restorePending = false)
        assertNull(resolveAllRecordsRestoreTarget(memory, listOf("note:3"), 3))
    }

    @Test
    fun `no restore when record list empty`() {
        val memory = AllRecordsScrollMemory().capture(anchorKey = "note:3", lazyColumnIndex = 10, offset = 0)
        assertNull(resolveAllRecordsRestoreTarget(memory, emptyList(), 3))
    }

    @Test
    fun `visible anchor key resolves record under header offset`() {
        val keys = listOf("exam:1", "note:3", "supply:7")
        assertEquals("exam:1", visibleRecordAnchorKey(3, keys, 3))
        assertEquals("note:3", visibleRecordAnchorKey(4, keys, 3))
        assertEquals("supply:7", visibleRecordAnchorKey(5, keys, 3))
    }

    @Test
    fun `sorting ignores pinned and todo completion`() {
        // 模拟 createdAt 排序：只按 createdAt 决定顺序，pinned / completed 不参与
        data class R(val key: String, val createdAt: Long, val pinned: Boolean, val completed: Boolean)
        val list = listOf(
            R("a", 1, pinned = true, completed = false),
            R("b", 3, pinned = false, completed = true),
            R("c", 2, pinned = false, completed = false),
        )
        val sorted = list.sortedWith(
            compareByDescending<R> { it.createdAt }.thenByDescending { it.key }
        )
        assertEquals(listOf("b", "c", "a"), sorted.map { it.key })
    }
}

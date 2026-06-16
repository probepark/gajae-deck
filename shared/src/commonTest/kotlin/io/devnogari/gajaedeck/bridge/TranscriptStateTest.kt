package io.devnogari.gajaedeck.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class TranscriptStateTest {
    @Test
    fun capacityMustBeAtLeastOne() {
        assertFailsWith<IllegalArgumentException> {
            TranscriptState(capacity = 0)
        }
    }

    @Test
    fun addMaintainsBoundedRingInInsertionOrder() {
        val state = TranscriptState(capacity = 3)

        repeat(5) { index ->
            state.add(NoticeItem(key = "notice:$index", kind = "notice", raw = "$index"))
        }

        val snapshot = state.snapshot()
        assertEquals(3, state.size)
        assertEquals(listOf("notice:2", "notice:3", "notice:4"), snapshot.map { it.key })
    }

    @Test
    fun snapshotReturnsACopy() {
        val state = TranscriptState(capacity = 3)
        state.add(NoticeItem(key = "notice:1", kind = "notice", raw = "1"))

        val first = state.snapshot()
        val second = state.snapshot()

        assertNotSame(first, second)
        assertEquals(first, second)
    }
}

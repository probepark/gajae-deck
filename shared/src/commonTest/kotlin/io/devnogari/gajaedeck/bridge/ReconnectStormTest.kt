package io.devnogari.gajaedeck.bridge

import kotlin.test.Test
import kotlin.test.assertEquals

class ReconnectStormTest {
    @Test
    fun reconnectStormCountsDuplicatesWithoutReapplying() {
        val frames = listOf(
            BridgeStreamParser.parseFrame("""{"type":"event","seq":1}""")!!,
            BridgeStreamParser.parseFrame("""{"type":"event","seq":2}""")!!,
            BridgeStreamParser.parseFrame("""{"type":"event","seq":3}""")!!,
        )
        val reducer = TimelineReducer()

        frames.forEach { reducer.accept(it) }

        assertEquals(3, reducer.state.applied)
        assertEquals(3L, reducer.state.lastSeq)
        assertEquals(0, reducer.state.duplicates)

        frames.forEach { reducer.accept(it) }

        assertEquals(3, reducer.state.applied)
        assertEquals(3L, reducer.state.lastSeq)
        assertEquals(3, reducer.state.duplicates)
    }
}

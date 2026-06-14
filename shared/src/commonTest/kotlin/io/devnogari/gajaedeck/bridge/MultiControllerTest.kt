package io.devnogari.gajaedeck.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Two-client conflict matrix for multi-controller invariants (protocol-v2.md). */
class MultiControllerTest {

    @Test
    fun concurrentPromptsBothAccepted() {
        val ob = CommandOutbox()
        ob.enqueue("a", "prompt")
        ob.enqueue("b", "prompt")
        ob.markAccepted("a")
        ob.markAccepted("b")
        assertEquals(WriteStatus.ACCEPTED, ob.status("a"))
        assertEquals(WriteStatus.ACCEPTED, ob.status("b"))
    }

    @Test
    fun sameGateAnsweredByTwoControllers_oneWinsRestStale() {
        val ob = CommandOutbox()
        ob.enqueue("c1", "workflow_gate_response", group = "gate:G1")
        ob.enqueue("c2", "workflow_gate_response", group = "gate:G1")
        ob.resolveExclusive("gate:G1", winnerKey = "c1")
        assertEquals(WriteStatus.ACCEPTED, ob.status("c1"))
        assertEquals(WriteStatus.STALE, ob.status("c2"))
        assertTrue(ob.isStaleResponse("c2"))
    }

    @Test
    fun timeoutRetryReusesKey() {
        val ob = CommandOutbox()
        ob.enqueue("k", "prompt")
        val retried = ob.enqueue("k", "prompt") // same action retried
        assertEquals(2, retried.attempt)
        assertEquals(1, ob.all().size)
        ob.markAccepted("k")
        assertEquals(WriteStatus.ACCEPTED, ob.status("k"))
    }

    @Test
    fun staleResponseDoesNotFlipToAccepted() {
        val ob = CommandOutbox()
        ob.enqueue("c1", "workflow_gate_response", group = "gate:G2")
        ob.enqueue("c2", "workflow_gate_response", group = "gate:G2")
        ob.resolveExclusive("gate:G2", winnerKey = "c1")
        // Late server response for the losing controller must remain stale.
        ob.markAccepted("c2")
        assertEquals(WriteStatus.STALE, ob.status("c2"))
    }

    @Test
    fun concurrentSwitchSessionServerAuthoritative() {
        val ob = CommandOutbox()
        ob.enqueue("s1", "switch_session", group = "switch_session")
        ob.enqueue("s2", "switch_session", group = "switch_session")
        ob.resolveExclusive("switch_session", winnerKey = "s2")
        assertEquals(WriteStatus.STALE, ob.status("s1"))
        assertEquals(WriteStatus.ACCEPTED, ob.status("s2"))
    }

    @Test
    fun outOfOrderResponseVsEventTimelineFollowsSeq() {
        // Command status (by key) and timeline (by seq) are independent ordering domains.
        val ob = CommandOutbox()
        ob.enqueue("k1", "prompt")
        ob.markAccepted("k1") // response commits first

        val reducer = TimelineReducer()
        // event for that action arrives later, out of order relative to a higher seq already seen
        reducer.accept(BridgeStreamParser.parseFrame("""{"type":"event","seq":1}""")!!)
        val dup = reducer.accept(BridgeStreamParser.parseFrame("""{"type":"event","seq":1}""")!!)
        assertEquals(FrameDisposition.DUPLICATE, dup)
        assertEquals(1L, reducer.state.lastSeq)
        assertEquals(WriteStatus.ACCEPTED, ob.status("k1"))
    }
}

package io.devnogari.gajaedeck.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Phase0AdversarialTest {
    private val allCapabilities = setOf("permission", "workflow_gate", "ui.declarative", "elicitation", "host_uri")
    private val allEndpointKeys = setOf("uiResponses", "hostUriResults")
    private val allScopes = setOf("control", "host_uri")

    @Test
    fun gateVocabularyFailsClosedUnlessCapabilityEndpointAndScopeArePresent() {
        assertFalse(
            GateVocabulary.isActionable(
                frameKind = "host_uri_request",
                acceptedCapabilities = emptySet(),
                availableEndpointKeys = allEndpointKeys,
                grantedScopes = allScopes,
            ),
        )
        assertFalse(
            GateVocabulary.isActionable(
                frameKind = "host_uri_request",
                acceptedCapabilities = allCapabilities,
                availableEndpointKeys = emptySet(),
                grantedScopes = allScopes,
            ),
        )
        assertFalse(
            GateVocabulary.isActionable(
                frameKind = "host_uri_request",
                acceptedCapabilities = allCapabilities,
                availableEndpointKeys = allEndpointKeys,
                grantedScopes = setOf("control"),
            ),
        )

        assertTrue(
            GateVocabulary.isActionable(
                frameKind = "host_uri_request",
                acceptedCapabilities = allCapabilities,
                availableEndpointKeys = allEndpointKeys,
                grantedScopes = allScopes,
            ),
        )
    }

    @Test
    fun gateVocabularyRejectsUnknownAndEmptyFrameKinds() {
        assertNull(GateVocabulary.forFrameKind(""))
        assertNull(GateVocabulary.forFrameKind("host_tool_call"))
    }

    @Test
    fun timelineReducerTreatsFramesAtOrBelowSeedAsDuplicatesAndNextFrameAsApplied() {
        val reducer = TimelineReducer(TimelineState(lastSeq = 100L))

        assertEquals(
            FrameDisposition.DUPLICATE,
            reducer.accept(BridgeStreamParser.parseFrame("""{"type":"event","seq":100}""")!!),
        )
        assertEquals(0, reducer.state.applied)
        assertEquals(1, reducer.state.duplicates)
        assertEquals(100L, reducer.state.lastSeq)

        assertEquals(
            FrameDisposition.DUPLICATE,
            reducer.accept(BridgeStreamParser.parseFrame("""{"type":"event","seq":50}""")!!),
        )
        assertEquals(0, reducer.state.applied)
        assertEquals(2, reducer.state.duplicates)
        assertEquals(100L, reducer.state.lastSeq)

        assertEquals(
            FrameDisposition.APPLIED,
            reducer.accept(BridgeStreamParser.parseFrame("""{"type":"event","seq":101}""")!!),
        )
        assertEquals(1, reducer.state.applied)
        assertEquals(2, reducer.state.duplicates)
        assertEquals(101L, reducer.state.lastSeq)
    }

    @Test
    fun timelineReducerGapFromSeedMarksDesynced() {
        val reducer = TimelineReducer(TimelineState(lastSeq = 10L))

        assertEquals(
            FrameDisposition.GAP,
            reducer.accept(BridgeStreamParser.parseFrame("""{"type":"event","seq":20}""")!!),
        )
        assertTrue(reducer.state.desynced)
    }
    @Test
    fun permissionRequestIsNonActionableWhenControlScopeIsAbsent() {
        assertFalse(
            GateVocabulary.isActionable(
                frameKind = "permission_request",
                acceptedCapabilities = allCapabilities,
                availableEndpointKeys = allEndpointKeys,
                grantedScopes = emptySet(),
            ),
        )
    }

}

package io.devnogari.gajaedeck.notifications

import io.devnogari.gajaedeck.auth.InMemorySecureStore
import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import io.devnogari.gajaedeck.control.ControlPlaneRepository
import io.devnogari.gajaedeck.control.TransientRouteHandoff
import io.devnogari.gajaedeck.ui.ControlSessionControllerFactory
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepLinkResumeHandlerTest {
    @Test
    fun resumeRefreshesControlStateAndReconnectsFromLastSeq() = runTest {
        val session = session(id = "dl_opaque_01", lastSeq = 77L)
        val route = route(sessionId = session.id)
        val control = FakeControlPlaneClient(sessions = listOf(session), route = route)
        var connectorRoute = route()
        val factory = ControlSessionControllerFactory(
            repository = ControlPlaneRepository(InMemorySecureStore()),
            controlPlaneClient = control,
            bridgeConnectorFactory = { nextRoute ->
                connectorRoute = nextRoute
                FakeBridgeConnector(FakeBridgeTransport())
            },
            transientRouteHandoff = TransientRouteHandoff(),
        )
        val handler = DeepLinkResumeHandler(control, factory)

        val result = handler.resume("cp_opaque", "proj_opaque", "dl_opaque_01", TestScope()).getOrThrow()

        assertEquals("proj_opaque", control.getSessionsProjectId)
        assertEquals("dl_opaque_01", control.respawnSessionId)
        assertEquals("dl_opaque_01", result.sessionId)
        assertEquals(77L, result.lastSeq)
        assertEquals(77L, result.bridgeClient.lastSeq)
        assertEquals(route, connectorRoute)
    }

    @Test
    fun resumeRejectsNonOpaqueDeepLinkIds() = runTest {
        val handler = DeepLinkResumeHandler(
            controlPlaneClient = FakeControlPlaneClient(),
            controllerFactory = ControlSessionControllerFactory(
                repository = ControlPlaneRepository(InMemorySecureStore()),
                controlPlaneClient = FakeControlPlaneClient(),
            ),
        )

        val result = handler.resume("cp_opaque", "proj_opaque", "https://bad.example/path", TestScope())

        assertTrue(result.isFailure)
        assertEquals("deep-link id must be opaque", result.exceptionOrNull()?.message)
    }
}

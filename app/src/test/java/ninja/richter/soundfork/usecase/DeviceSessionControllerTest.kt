package ninja.richter.soundfork.usecase

import ninja.richter.soundfork.core.runSuspendCatching

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSessionControllerTest {
    @Test fun switchingDevicesCancelsRequestsWithoutPublishingAnError() = runTest {
        val sessions = DeviceSessionController(backgroundScope)
        sessions.open("A", 8090)
        val response = CompletableDeferred<String>()
        var displayed: String? = null
        var errorShown = false
        val oldRequest = sessions.launch {
            runSuspendCatching { withContext(NonCancellable) { response.await() } }
                .onSuccess { displayed = it }
                .onFailure { errorShown = true }
        }!!
        runCurrent()
        sessions.open("B", 8090)
        response.complete("Old station from A")
        runCurrent()
        assertTrue(oldRequest.isCancelled)
        assertNull(displayed)
        assertFalse(errorShown)
    }

    @Test fun oldWebsocketCallbackIsIgnoredEvenAfterReturningToSameDevice() = runTest {
        val sessions = DeviceSessionController(backgroundScope)
        val firstA = sessions.open("A", 8090)
        sessions.open("B", 8090)
        val secondA = sessions.open("A", 8090)
        var staleCallbackApplied = false
        assertNull(sessions.launch(firstA) { staleCallbackApplied = true })
        assertFalse(sessions.isCurrent(firstA))
        assertTrue(sessions.isCurrent(secondA))
        runCurrent()
        assertFalse(staleCallbackApplied)
    }

    @Test fun disconnectCancelsWorkAndRejectsNewDeviceCommands() = runTest {
        val sessions = DeviceSessionController(backgroundScope)
        sessions.open("A", 8090)
        val job = sessions.launch { awaitCancellation() }!!
        runCurrent()
        sessions.close()
        assertTrue(job.isCancelled)
        assertNull(sessions.current)
        assertNull(sessions.launch { fail("Disconnected session executed a command") })
    }

    @Test fun failuresStillReachTheCallerWhenSessionIsActive() = runTest {
        val failure = java.io.IOException("Offline")
        val result = runSuspendCatching<String> { throw failure }
        assertSame(failure, result.exceptionOrNull())
    }

    @Test fun cancellationIsNeverReturnedAsAResult() = runTest {
        val cancellation = CancellationException("Switched speaker")
        try {
            runSuspendCatching<Unit> { throw cancellation }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }
}

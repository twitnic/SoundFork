package codes.offline.soundfork.usecase

import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import codes.offline.soundfork.data.*
import codes.offline.soundfork.model.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadioBrowserSearchTest {
    @Test fun queryEncodesSpecialCharactersAndAppliesMp3AndReachabilityFilters() {
        val query = RadioBrowserQuery(" Jazz & Soul + ", " Germany ", " german ", " jazz ").normalized()
        val url = RadioBrowserRepository.searchUrl("https://de1.api.radio-browser.info/", query, 50)
        assertEquals("Jazz & Soul +", url.queryParameter("name"))
        assertEquals("Germany", url.queryParameter("country"))
        assertEquals("german", url.queryParameter("language"))
        assertEquals("jazz", url.queryParameter("tag"))
        assertEquals("MP3", url.queryParameter("codec"))
        assertEquals("true", url.queryParameter("hidebroken"))
        assertEquals("50", url.queryParameter("offset"))
        assertFalse(RadioBrowserQuery(tag = "rock").isEmpty())
        assertTrue(RadioBrowserQuery(name = "  ").isEmpty())
    }

    @Test fun changedSearchCannotPublishAnOlderResponse() = runTest {
        val oldResponse = CompletableDeferred<RadioBrowserPage>()
        val controller = RadioBrowserSearchController(backgroundScope) { query, _ ->
            if (query.name == "old") withContext(NonCancellable) { oldResponse.await() }
            else RadioBrowserPage(listOf(station("new")), 1, false)
        }
        val published = mutableListOf<String>()
        controller.search(RadioBrowserQuery("old"), 0) { published += it.getOrThrow().stations.single().name }
        runCurrent()
        controller.search(RadioBrowserQuery("new"), 0) { published += it.getOrThrow().stations.single().name }
        runCurrent()
        oldResponse.complete(RadioBrowserPage(listOf(station("old")), 1, false))
        runCurrent()
        assertEquals(listOf("new"), published)
    }

    @Test fun editingTheQueryCancelsTheRequestWithoutReportingAnError() = runTest {
        var cancelled = false
        val controller = RadioBrowserSearchController(backgroundScope) { _, _ ->
            try { awaitCancellation() } finally { cancelled = true }
        }
        controller.search(RadioBrowserQuery("radio"), 0) { fail("Cancelled query must not publish") }
        runCurrent()
        controller.cancel()
        runCurrent()
        assertTrue(cancelled)
    }

    @Test fun failedPageCanBeRetriedAtTheSameOffset() = runTest {
        var attempts = 0
        val controller = RadioBrowserSearchController(backgroundScope) { _, offset ->
            assertEquals(50, offset)
            if (++attempts == 1) throw IOException("Offline")
            RadioBrowserPage(listOf(station("radio")), 51, false)
        }
        var failure: Throwable? = null
        controller.search(RadioBrowserQuery("radio"), 50) { failure = it.exceptionOrNull() }
        runCurrent()
        assertTrue(failure is IOException)
        var page: RadioBrowserPage? = null
        controller.search(RadioBrowserQuery("radio"), 50) { page = it.getOrThrow() }
        runCurrent()
        assertEquals(51, page?.nextOffset)
    }

    private fun station(name: String) = RadioStation(name, "MP3", "https://example.test/$name", name)
}

package codes.offline.soundfork.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import codes.offline.soundfork.core.runSuspendCatching
import codes.offline.soundfork.data.RadioBrowserPage
import codes.offline.soundfork.model.RadioBrowserQuery

class RadioBrowserSearchController(
    private val scope: CoroutineScope,
    private val search: suspend (RadioBrowserQuery, Int) -> RadioBrowserPage
) {
    private var job: Job? = null
    private var revision = 0L

    fun cancel() {
        revision++
        job?.cancel()
    }

    fun search(query: RadioBrowserQuery, offset: Int, onResult: (Result<RadioBrowserPage>) -> Unit) {
        cancel()
        val requestedRevision = revision
        job = scope.launch {
            val result = runSuspendCatching { search.invoke(query, offset) }
            if (revision == requestedRevision) onResult(result)
        }
    }
}

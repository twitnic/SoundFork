package ninja.richter.soundfork

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ninja.richter.soundfork.ui.theme.SoundForkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SoundFork starting")
        enableEdgeToEdge()
        setContent {
            val foldsFlow = remember {
                WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this)
                    .map { it.separatingFolds() }.distinctUntilChanged()
            }
            val folds by foldsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
            SoundForkTheme {
                SoundForkAppScreen(folds = folds)
            }
        }
    }

    private companion object {
        const val TAG = "SoundForkUI"
    }
}

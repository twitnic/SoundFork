package ninja.richter.soundfork

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ninja.richter.soundfork.ui.theme.SoundForkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SoundFork starting")
        enableEdgeToEdge()
        setContent {
            SoundForkTheme {
                SoundForkAppScreen()
            }
        }
    }

    private companion object {
        const val TAG = "SoundForkUI"
    }
}

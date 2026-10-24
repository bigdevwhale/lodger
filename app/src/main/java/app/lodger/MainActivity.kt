package app.lodger

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lodger.data.Settings
import app.lodger.domain.House
import app.lodger.ui.LodgerRoot
import app.lodger.ui.theme.LodgerTheme

class MainActivity : AppCompatActivity() {
    /** Bumped when a note's notification is tapped, so the notes open. */
    private val toNotes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(t), navigationBarStyle = SystemBarStyle.dark(t))
        if (intent?.getBooleanExtra(EXTRA_NOTES, false) == true) toNotes.intValue++
        setContent {
            val house: House? by container.store.house.collectAsStateWithLifecycle(initialValue = null)
            val settings: Settings? by container.store.settings.collectAsStateWithLifecycle(initialValue = null)
            // Wait for both so the first screen doesn't flash for someone already living here.
            val h = house; val s = settings
            if (h != null && s != null) LodgerTheme { LodgerRoot(h, s, toNotes.intValue) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_NOTES, false)) toNotes.intValue++
    }

    companion object {
        const val EXTRA_NOTES = "notes"
    }
}

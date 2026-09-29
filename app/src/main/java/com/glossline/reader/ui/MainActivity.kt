package com.glossline.reader.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glossline.reader.R
import com.glossline.reader.nav.AppShell
import com.glossline.reader.ui.theme.GlossLineTheme
import java.io.File

/**
 * The Activity. It exists to own the window and the two things that genuinely need a real Intent:
 * the share target and the launch window. Everything visual lives in `ui/nav/AppShell.kt`.
 */
class MainActivity : ComponentActivity() {

    private val model: MainViewModel by viewModels()

    /**
     * Whether the launch window is still being held.
     *
     * Held for exactly one composition, which is the whole job: without it, Android 12+ shows the
     * splash, tears it down, and briefly paints the window background before Compose's first
     * frame lands. Deliberately *not* held for the duration of the first article fetch, because
     * the reader has a designed loading state and holding the splash would hide it.
     */
    private var holdSplash = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super: the launch theme is still on screen and this is the only chance to hand
        // over to the content theme. Backports to API 26..30 through core-splashscreen.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Draw behind the system bars instead of letterboxing around them, and let the library
        // resolve dark or light bar icons per configuration. The old theme hardcoded
        // windowLightStatusBar=true, so in dark mode the status bar drew black icons on top of a
        // near-black surface.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        splash.setKeepOnScreenCondition { holdSplash }

        handleIntent(intent)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val settings by model.settings.collectAsStateWithLifecycle()

            GlossLineTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                AppShell(
                    state = state,
                    settings = settings,
                    model = model,
                    onReleaseSplash = { holdSplash = false },
                    onExportTsv = { exportTsv(model) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * The share target.
     *
     * A share from Chrome carries the page text rather than a bare URL, so the extractor is given
     * the raw text; a VIEW intent carries the URL itself.
     */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!shared.isNullOrBlank()) model.loadUrl(shared)
            }
            Intent.ACTION_VIEW -> intent.dataString?.let { model.loadUrl(it) }
        }
    }

    /** Writes the Anki-ready TSV into cache and hands it to the system share sheet. */
    private fun exportTsv(model: MainViewModel) {
        val file = File(cacheDir, model.exportTsvFileName())
        file.writeText(model.exportTsv(), Charsets.UTF_8)
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = getString(R.string.share_anki_mime)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, getString(R.string.share_anki_export)))
    }
}

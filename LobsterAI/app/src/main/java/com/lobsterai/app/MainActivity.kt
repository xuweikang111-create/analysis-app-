package com.lobsterai.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.lobsterai.app.data.preferences.SettingsStore
import com.lobsterai.app.ui.navigation.LobsterApp
import com.lobsterai.app.ui.share.SharedContentManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sharedContentManager: SharedContentManager
    @Inject lateinit var settingsStore: SettingsStore

    private var lastClipboardText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleIntent(intent)
        setContent {
            LobsterApp(sharedContentManager = sharedContentManager)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (!settingsStore.clipboardDetection.first()) return@launch
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) return@launch
            val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()?.trim().orEmpty()
            val url = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(text)?.value
            if (url != null && text != lastClipboardText) {
                lastClipboardText = text
                sharedContentManager.offer(text)
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        sharedContentManager.offer(text)
    }
}

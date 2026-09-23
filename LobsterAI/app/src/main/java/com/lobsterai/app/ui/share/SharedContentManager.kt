package com.lobsterai.app.ui.share

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingContent(
    val id: Long,
    val text: String,
    val url: String? = extractUrl(text)
) {
    companion object {
        private val URL = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
        fun extractUrl(text: String): String? = URL.find(text)?.value
    }
}

@Singleton
class SharedContentManager @Inject constructor() {
    private val ids = AtomicLong(0L)
    private val pending = MutableStateFlow<IncomingContent?>(null)
    val incoming: Flow<IncomingContent> = pending.filterNotNull()

    fun offer(text: String) {
        val clean = text.trim()
        if (clean.isNotBlank()) {
            pending.value = IncomingContent(
                id = ids.incrementAndGet(),
                text = clean
            )
        }
    }

    fun consume(id: Long) {
        if (pending.value?.id == id) pending.value = null
    }
}
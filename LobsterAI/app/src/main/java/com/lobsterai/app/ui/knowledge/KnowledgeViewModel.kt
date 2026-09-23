package com.lobsterai.app.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lobsterai.app.data.preferences.SettingsStore
import com.lobsterai.app.domain.model.ChatRole
import com.lobsterai.app.domain.model.KnowledgeItem
import com.lobsterai.app.domain.model.KnowledgeType
import com.lobsterai.app.domain.model.Memory
import com.lobsterai.app.domain.model.Message
import com.lobsterai.app.domain.model.WebPageContent
import com.lobsterai.app.domain.repository.AppRepository
import com.lobsterai.app.ui.share.SharedContentManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val repository: AppRepository,
    private val settings: SettingsStore,
    private val sharedContentManager: SharedContentManager
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val status = MutableStateFlow<String?>(null)
    private val incomingText = MutableStateFlow("")
    private val incomingEventId = MutableStateFlow<Long?>(null)
    private val currentWeb = MutableStateFlow<WebPageContent?>(null)
    private val aiOutput = MutableStateFlow("")
    private var aiJob: Job? = null

    private val libraryState = combine(
        repository.observeKnowledge(),
        repository.observeMemories(),
        busy,
        status,
        incomingText
    ) { items, memories, isBusy, message, incoming ->
        LibraryState(items, memories, isBusy, message, incoming)
    }

    val state: StateFlow<KnowledgeUiState> = combine(
        libraryState,
        currentWeb,
        aiOutput
    ) { library, web, output ->
        KnowledgeUiState(
            items = library.items,
            memories = library.memories,
            busy = library.busy,
            status = library.status,
            incomingText = library.incomingText,
            currentWeb = web,
            aiOutput = output
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        KnowledgeUiState()
    )

    init {
        viewModelScope.launch {
            sharedContentManager.incoming.collect { incoming ->
                incomingEventId.value = incoming.id
                incomingText.value = incoming.url ?: incoming.text
            }
        }
    }

    fun clearStatus() {
        status.value = null
    }

    fun clearIncoming() {
        incomingEventId.value?.let(sharedContentManager::consume)
        incomingEventId.value = null
        incomingText.value = ""
    }

    fun stopAi() {
        aiJob?.cancel()
        aiJob = null
        busy.value = false
    }

    fun fetch(url: String, save: Boolean = false) = viewModelScope.launch {
        busy.value = true
        status.value = null
        try {
            repository.fetchWebPage(url).fold(
                onSuccess = { page ->
                    currentWeb.value = page
                    if (save) {
                        repository.saveKnowledge(
                            KnowledgeType.WEB,
                            page.title,
                            page.text,
                            page.url,
                            page.description
                        )
                        status.value = "网页已保存到智识库"
                    } else {
                        status.value = "正文提取完成"
                    }
                },
                onFailure = {
                    status.value = "抓取失败：${it.message}"
                }
            )
        } finally {
            busy.value = false
        }
    }

    fun summarizeUrl(url: String) {
        if (busy.value) return
        aiJob = viewModelScope.launch {
            busy.value = true
            status.value = "正在抓取网页…"
            aiOutput.value = ""
            try {
                val page = repository.fetchWebPage(url).getOrElse {
                    status.value = "抓取失败：${it.message}"
                    return@launch
                }
                currentWeb.value = page
                repository.saveKnowledge(
                    KnowledgeType.WEB,
                    page.title,
                    page.text,
                    page.url,
                    page.description
                )

                val configId = settings.activeModelId.first()
                val config = configId?.let { repository.getModelConfig(it) }
                if (config == null) {
                    status.value = "网页已保存；请先配置模型后再总结"
                    return@launch
                }

                val chunks = splitText(page.text, 12_000)
                val partials = mutableListOf<String>()
                chunks.forEachIndexed { index, chunk ->
                    status.value = "AI 总结 ${index + 1}/${chunks.size}…"
                    var part = ""
                    repository.streamChat(
                        config,
                        listOf(
                            Message(
                                conversationId = 0,
                                role = ChatRole.USER,
                                content = "准确总结以下正文第 ${index + 1}/${chunks.size} 部分，保留事实、数字和结论，不要臆测。\n\n$chunk",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    ).collect { delta ->
                        part += delta
                        aiOutput.value =
                            partials.joinToString("\n\n") +
                            if (partials.isNotEmpty()) "\n\n$part" else part
                    }
                    partials += part
                }

                val finalSummary = if (partials.size <= 1) {
                    partials.firstOrNull().orEmpty()
                } else {
                    status.value = "正在合并摘要…"
                    var merged = ""
                    repository.streamChat(
                        config,
                        listOf(
                            Message(
                                conversationId = 0,
                                role = ChatRole.USER,
                                content = "把下面分段摘要合并成结构清晰、去重、忠于原文的最终摘要。\n\n${partials.joinToString("\n\n---\n\n")}",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    ).collect { delta ->
                        merged += delta
                        aiOutput.value = merged
                    }
                    merged
                }

                if (finalSummary.isNotBlank()) {
                    repository.saveKnowledge(
                        type = KnowledgeType.NOTE,
                        title = "AI摘要 · ${page.title}",
                        content = finalSummary,
                        sourceUrl = page.url,
                        description = "由当前模型生成"
                    )
                }
                status.value = "网页与摘要已保存"
            } catch (cancel: CancellationException) {
                status.value = "已停止"
                throw cancel
            } catch (t: Throwable) {
                status.value = "总结失败：${t.message ?: t.javaClass.simpleName}"
            } finally {
                busy.value = false
            }
        }
    }

    fun ask(item: KnowledgeItem, question: String) {
        askText(item.title, item.content, question)
    }

    fun askMemory(memory: Memory, question: String) {
        askText(memory.title, memory.content, question)
    }

    private fun askText(title: String, content: String, question: String) {
        if (question.isBlank() || busy.value) return
        aiJob = viewModelScope.launch {
            busy.value = true
            aiOutput.value = ""
            status.value = "正在基于智识回答…"
            try {
                val configId = settings.activeModelId.first()
                val config = configId?.let { repository.getModelConfig(it) }
                    ?: error("请先配置模型")

                repository.streamChat(
                    config,
                    listOf(
                        Message(
                            conversationId = 0,
                            role = ChatRole.USER,
                            content = "只根据以下资料回答；不足时明确说不知道。\n\n标题：$title\n内容：\n${content.take(40_000)}\n\n问题：$question",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                ).collect { delta ->
                    aiOutput.value += delta
                }
                status.value = "回答完成"
            } catch (cancel: CancellationException) {
                status.value = "已停止"
                throw cancel
            } catch (t: Throwable) {
                status.value = "问答失败：${t.message ?: t.javaClass.simpleName}"
            } finally {
                busy.value = false
            }
        }
    }

    fun addNote(title: String, content: String) = viewModelScope.launch {
        if (content.isBlank()) return@launch
        repository.saveKnowledge(
            KnowledgeType.NOTE,
            title.ifBlank { content.take(20) },
            content
        )
        status.value = "笔记已保存"
    }

    fun importFile(fileName: String, content: String) = viewModelScope.launch {
        if (content.isBlank()) {
            status.value = "文件没有可读取文本"
            return@launch
        }
        repository.saveKnowledge(
            KnowledgeType.FILE,
            fileName.ifBlank { "导入文件" },
            content.take(500_000)
        )
        status.value = "文件已导入智识库"
    }

    fun delete(item: KnowledgeItem) = viewModelScope.launch {
        repository.deleteKnowledge(item)
        status.value = "已删除"
    }

    fun deleteMemory(memory: Memory) = viewModelScope.launch {
        repository.deleteMemory(memory)
        status.value = "长期记忆已删除"
    }

    fun clearMemories() = viewModelScope.launch {
        repository.clearMemories()
        status.value = "长期记忆已清空"
    }

    private fun splitText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val result = mutableListOf<String>()
        var buffer = StringBuilder()

        text.split(Regex("\\n{2,}")).forEach { paragraph ->
            if (buffer.length + paragraph.length + 2 > maxChars && buffer.isNotEmpty()) {
                result += buffer.toString()
                buffer = StringBuilder()
            }

            if (paragraph.length > maxChars) {
                paragraph.chunked(maxChars).forEach { result += it }
            } else {
                buffer.append(paragraph).append("\n\n")
            }
        }

        if (buffer.isNotEmpty()) result += buffer.toString()
        return result.filter { it.isNotBlank() }
    }
}

data class KnowledgeUiState(
    val items: List<KnowledgeItem> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val busy: Boolean = false,
    val status: String? = null,
    val incomingText: String = "",
    val currentWeb: WebPageContent? = null,
    val aiOutput: String = ""
)

private data class LibraryState(
    val items: List<KnowledgeItem>,
    val memories: List<Memory>,
    val busy: Boolean,
    val status: String?,
    val incomingText: String
)

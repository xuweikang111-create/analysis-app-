package com.lobsterai.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lobsterai.app.data.preferences.SettingsStore
import com.lobsterai.app.domain.model.ChatRole
import com.lobsterai.app.domain.model.Conversation
import com.lobsterai.app.domain.model.Lobster
import com.lobsterai.app.domain.model.KnowledgeType
import com.lobsterai.app.domain.model.Message
import com.lobsterai.app.domain.model.ModelConfig
import com.lobsterai.app.domain.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: AppRepository,
    private val settings: SettingsStore,
    private val json: Json
) : ViewModel() {
    private val streamingText = MutableStateFlow("")
    private val generating = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private var generationJob: Job? = null

    private val activeLobsterId = settings.activeLobsterId.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val activeConversationId = settings.activeConversationId.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val activeModelId = settings.activeModelId.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val lobsters = repository.observeLobsters().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val conversations = activeLobsterId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeConversations(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val messages = activeConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val configs = repository.observeModelConfigs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val contentState = combine(
        lobsters,
        conversations,
        messages,
        configs,
        activeLobsterId
    ) { lobsterList, conversationList, messageList, configList, lobsterId ->
        ChatContentState(
            lobsterList = lobsterList,
            conversationList = conversationList,
            messageList = messageList,
            configList = configList,
            lobsterId = lobsterId
        )
    }

    private val selectionState = combine(
        activeConversationId,
        activeModelId,
        streamingText,
        generating,
        error
    ) { conversationId, modelId, stream, isGenerating, currentError ->
        ChatSelectionState(
            conversationId = conversationId,
            modelId = modelId,
            streamingText = stream,
            generating = isGenerating,
            error = currentError
        )
    }

    val state: StateFlow<ChatUiState> = combine(contentState, selectionState) { content, selection ->
        ChatUiState(
            lobster = content.lobsterList.firstOrNull { it.id == content.lobsterId } ?: content.lobsterList.firstOrNull(),
            conversations = content.conversationList,
            selectedConversationId = selection.conversationId,
            messages = content.messageList,
            configs = content.configList,
            selectedConfig = content.configList.firstOrNull { it.id == selection.modelId } ?: content.configList.firstOrNull(),
            streamingText = selection.streamingText,
            generating = selection.generating,
            error = selection.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch {
            combine(configs, activeModelId) { all, selected -> all to selected }.collect { (all, selected) ->
                if (all.isNotEmpty() && all.none { it.id == selected }) settings.setActiveModel(all.first().id)
            }
        }
    }

    fun dismissError() { error.value = null }

    fun selectConversation(id: Long) = viewModelScope.launch { settings.setActiveConversation(id) }
    fun selectModel(id: Long) = viewModelScope.launch { settings.setActiveModel(id) }

    fun newConversation() = viewModelScope.launch {
        val lobster = state.value.lobster ?: return@launch
        val id = repository.createConversation(lobster.id, "新对话", state.value.selectedConfig?.id)
        settings.setActiveConversation(id)
    }

    fun deleteConversation(conversation: Conversation) = viewModelScope.launch {
        repository.deleteConversation(conversation)
        if (state.value.selectedConversationId == conversation.id) settings.setActiveConversation(null)
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || generating.value) return
        viewModelScope.launch {
            val lobster = state.value.lobster ?: run {
                error.value = "请先创建龙虾角色"
                return@launch
            }
            val config = state.value.selectedConfig ?: run {
                error.value = "请先到设置页添加模型配置"
                return@launch
            }
            val conversationId = state.value.selectedConversationId ?: repository.createConversation(
                lobsterId = lobster.id,
                title = clean.take(24),
                modelConfigId = config.id
            ).also { settings.setActiveConversation(it) }

            repository.addMessage(
                Message(
                    conversationId = conversationId,
                    role = ChatRole.USER,
                    content = clean,
                    createdAt = System.currentTimeMillis(),
                    inputTokens = approximateTokens(clean)
                )
            )
            repository.applyGrowth(lobster.id, exp = 8, intimacy = 2, mood = 1, satiety = -1)
            repository.markDailyAction(lobster.id, "chat")
            generate(conversationId, lobster, config)
        }
    }

    fun stop() {
        generationJob?.cancel()
        generationJob = null
    }

    fun regenerate() = viewModelScope.launch {
        if (generating.value) return@launch
        val conversationId = state.value.selectedConversationId ?: return@launch
        val lobster = state.value.lobster ?: return@launch
        val config = state.value.selectedConfig ?: return@launch
        val all = repository.getMessages(conversationId)
        all.lastOrNull { it.role == ChatRole.ASSISTANT }?.let { repository.deleteMessage(it) }
        generate(conversationId, lobster, config)
    }

    fun editMessage(message: Message, content: String) = viewModelScope.launch {
        val clean = content.trim()
        if (clean.isNotBlank()) repository.updateMessage(message.copy(content = clean, inputTokens = if (message.role == ChatRole.USER) approximateTokens(clean) else message.inputTokens))
    }

    fun deleteMessage(message: Message) = viewModelScope.launch { repository.deleteMessage(message) }

    fun saveConversationToKnowledge() = viewModelScope.launch {
        val conversationId = state.value.selectedConversationId ?: return@launch
        val conversation = state.value.conversations.firstOrNull { it.id == conversationId }
        val all = repository.getMessages(conversationId)
        if (all.isEmpty()) return@launch
        val text = all.joinToString("\n\n") { message ->
            "${message.role.name}: ${message.content}"
        }
        repository.saveKnowledge(
            type = KnowledgeType.CHAT,
            title = "聊天记录 · ${conversation?.title ?: "会话"}",
            content = text,
            description = "来自养龙虾 AI 的聊天会话"
        )
        error.value = "聊天记录已保存到知识库"
    }

    suspend fun exportConversation(): String {
        val conversationId = state.value.selectedConversationId ?: return ""
        val conversation = state.value.conversations.firstOrNull { it.id == conversationId }
        val all = repository.getMessages(conversationId)
        val root = buildJsonObject {
            put("format", JsonPrimitive("lobster-ai-conversation-v1"))
            put("title", JsonPrimitive(conversation?.title ?: "会话"))
            put("messages", buildJsonArray {
                all.forEach { message ->
                    add(buildJsonObject {
                        put("role", JsonPrimitive(message.role.name.lowercase()))
                        put("content", JsonPrimitive(message.content))
                        put("createdAt", JsonPrimitive(message.createdAt))
                    })
                }
            })
        }
        return json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), root)
    }

    fun importConversation(content: String) = viewModelScope.launch {
        runCatching {
            val lobster = state.value.lobster ?: error("请先创建龙虾")
            val root = json.parseToJsonElement(content).jsonObject
            val title = root["title"]?.jsonPrimitive?.contentOrNull ?: "导入会话"
            val items = root["messages"]?.jsonArray ?: error("文件中没有 messages")
            val conversationId = repository.createConversation(lobster.id, title, state.value.selectedConfig?.id)
            items.forEach { element ->
                val obj = element.jsonObject
                val role = when (obj["role"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                    "assistant" -> ChatRole.ASSISTANT
                    "system" -> ChatRole.SYSTEM
                    else -> ChatRole.USER
                }
                val text = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (text.isNotBlank()) repository.addMessage(Message(conversationId = conversationId, role = role, content = text, createdAt = System.currentTimeMillis()))
            }
            settings.setActiveConversation(conversationId)
        }.onFailure { error.value = "导入失败：${it.message ?: "格式错误"}" }
    }

    private fun generate(conversationId: Long, lobster: Lobster, config: ModelConfig) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            generating.value = true
            streamingText.value = ""
            error.value = null
            var finalText = ""
            try {
                val context = repository.getMessages(conversationId).takeLast(40).toMutableList()
                if (lobster.prompt.isNotBlank()) {
                    context.add(0, Message(conversationId = conversationId, role = ChatRole.SYSTEM, content = lobster.prompt, createdAt = 0L))
                }
                repository.streamChat(config, context).collect { delta ->
                    finalText += delta
                    streamingText.value = finalText
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                error.value = "生成失败：${t.message ?: t.javaClass.simpleName}"
            } finally {
                withContext(NonCancellable) {
                    if (finalText.isNotBlank()) {
                        repository.addMessage(
                            Message(
                                conversationId = conversationId,
                                role = ChatRole.ASSISTANT,
                                content = finalText,
                                createdAt = System.currentTimeMillis(),
                                outputTokens = approximateTokens(finalText)
                            )
                        )
                        repository.applyGrowth(lobster.id, exp = 10, intimacy = 1)
                    }
                    streamingText.value = ""
                    generating.value = false
                }
            }
        }
    }

    private fun approximateTokens(text: String): Int = max(1, text.length / 4)
}

data class ChatUiState(
    val lobster: Lobster? = null,
    val conversations: List<Conversation> = emptyList(),
    val selectedConversationId: Long? = null,
    val messages: List<Message> = emptyList(),
    val configs: List<ModelConfig> = emptyList(),
    val selectedConfig: ModelConfig? = null,
    val streamingText: String = "",
    val generating: Boolean = false,
    val error: String? = null
)

private data class ChatContentState(
    val lobsterList: List<Lobster>,
    val conversationList: List<Conversation>,
    val messageList: List<Message>,
    val configList: List<ModelConfig>,
    val lobsterId: Long?
)

private data class ChatSelectionState(
    val conversationId: Long?,
    val modelId: Long?,
    val streamingText: String,
    val generating: Boolean,
    val error: String?
)

package com.lobsterai.app.domain.repository

import com.lobsterai.app.domain.model.Conversation
import com.lobsterai.app.domain.model.DailyProgress
import com.lobsterai.app.domain.model.KnowledgeItem
import com.lobsterai.app.domain.model.KnowledgeType
import com.lobsterai.app.domain.model.Lobster
import com.lobsterai.app.domain.model.Memory
import com.lobsterai.app.domain.model.MemoryType
import com.lobsterai.app.domain.model.Message
import com.lobsterai.app.domain.model.ModelConfig
import com.lobsterai.app.domain.model.ProviderType
import com.lobsterai.app.domain.model.RagHit
import com.lobsterai.app.domain.model.WebPageContent
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    fun observeLobsters(): Flow<List<Lobster>>
    fun observeLobster(id: Long): Flow<Lobster?>
    suspend fun createLobster(name: String, prompt: String): Long
    suspend fun updateLobster(lobster: Lobster)
    suspend fun deleteLobster(lobster: Lobster)
    suspend fun applyGrowth(lobsterId: Long, exp: Int = 0, mood: Int = 0, satiety: Int = 0, intimacy: Int = 0)

    fun observeConversations(lobsterId: Long): Flow<List<Conversation>>
    suspend fun searchConversations(lobsterId: Long, query: String, limit: Int = 50): List<Conversation>
    suspend fun createConversation(lobsterId: Long, title: String, modelConfigId: Long?): Long
    suspend fun renameConversation(id: Long, title: String)
    suspend fun deleteConversation(conversation: Conversation)
    fun observeMessages(conversationId: Long): Flow<List<Message>>
    suspend fun getMessages(conversationId: Long): List<Message>
    suspend fun addMessage(message: Message): Long
    suspend fun updateMessage(message: Message)
    suspend fun deleteMessage(message: Message)
    suspend fun deleteMessagesFrom(conversationId: Long, messageId: Long)

    fun observeModelConfigs(): Flow<List<ModelConfig>>
    suspend fun getModelConfig(id: Long): ModelConfig?
    suspend fun saveModelConfig(
        existingId: Long?,
        name: String,
        provider: ProviderType,
        baseUrl: String,
        modelName: String,
        apiKey: String,
        customHeadersJson: String,
        systemPrompt: String,
        temperature: Double
    ): Long
    suspend fun deleteModelConfig(config: ModelConfig)
    suspend fun testModelConnection(config: ModelConfig, apiKeyOverride: String? = null): Result<String>
    suspend fun fetchModels(
        existingConfigId: Long?,
        provider: ProviderType,
        baseUrl: String,
        apiKeyOverride: String?,
        customHeadersJson: String
    ): Result<List<String>>
    fun streamChat(
        config: ModelConfig,
        messages: List<Message>,
        apiKeyOverride: String? = null,
        thinkingEnabled: Boolean = false,
        visionEnabled: Boolean = true
    ): Flow<String>

    fun observeKnowledge(): Flow<List<KnowledgeItem>>
    suspend fun getRecentKnowledge(limit: Int = 50): List<KnowledgeItem>
    suspend fun saveKnowledge(
        type: KnowledgeType,
        title: String,
        content: String,
        sourceUrl: String? = null,
        description: String? = null
    ): Long
    suspend fun deleteKnowledge(item: KnowledgeItem)
    suspend fun fetchWebPage(url: String): Result<WebPageContent>

    fun observeMemories(): Flow<List<Memory>>
    suspend fun saveMemory(
        type: MemoryType,
        title: String,
        content: String,
        importance: Int,
        sourceConversationId: Long?
    ): Long
    suspend fun deleteMemory(memory: Memory)
    suspend fun clearMemories()
    suspend fun searchRag(query: String, limit: Int = 6): List<RagHit>

    fun observeDailyProgress(lobsterId: Long): Flow<DailyProgress>
    suspend fun markDailyAction(lobsterId: Long, action: String): Boolean
}

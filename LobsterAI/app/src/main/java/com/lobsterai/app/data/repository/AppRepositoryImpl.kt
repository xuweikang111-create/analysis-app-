package com.lobsterai.app.data.repository

import com.lobsterai.app.data.local.AppDatabase
import com.lobsterai.app.data.local.ConversationEntity
import com.lobsterai.app.data.local.DailyActionEntity
import com.lobsterai.app.data.local.KnowledgeItemEntity
import com.lobsterai.app.data.local.LobsterEntity
import com.lobsterai.app.data.local.MemoryEntity
import com.lobsterai.app.data.local.MessageEntity
import com.lobsterai.app.data.local.ModelConfigEntity
import com.lobsterai.app.data.local.toDomain
import com.lobsterai.app.data.rag.HybridRagEngine
import com.lobsterai.app.data.remote.AiGateway
import com.lobsterai.app.data.security.ApiKeyVault
import com.lobsterai.app.data.web.WebContentExtractor
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
import com.lobsterai.app.domain.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class AppRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val vault: ApiKeyVault,
    private val aiGateway: AiGateway,
    private val webExtractor: WebContentExtractor,
    private val ragEngine: HybridRagEngine
) : AppRepository {
    override fun observeLobsters(): Flow<List<Lobster>> =
        db.lobsterDao().observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeLobster(id: Long): Flow<Lobster?> =
        db.lobsterDao().observeById(id).map { it?.toDomain() }

    override suspend fun createLobster(name: String, prompt: String): Long =
        db.lobsterDao().insert(
            LobsterEntity(
                name = name.trim().ifBlank { "小龙" },
                prompt = prompt.trim()
            )
        )

    override suspend fun updateLobster(lobster: Lobster) =
        db.lobsterDao().update(lobster.toEntity())

    override suspend fun deleteLobster(lobster: Lobster) =
        db.lobsterDao().delete(lobster.toEntity())

    override suspend fun applyGrowth(
        lobsterId: Long,
        exp: Int,
        mood: Int,
        satiety: Int,
        intimacy: Int
    ) {
        val current = db.lobsterDao().getById(lobsterId) ?: return
        val nextExp = max(0, current.experience + exp)
        db.lobsterDao().update(
            current.copy(
                experience = nextExp,
                level = levelForExp(nextExp),
                mood = (current.mood + mood).coerceIn(0, 100),
                satiety = (current.satiety + satiety).coerceIn(0, 100),
                intimacy = max(0, current.intimacy + intimacy)
            )
        )
    }

    override fun observeConversations(lobsterId: Long): Flow<List<Conversation>> =
        db.conversationDao().observeByLobster(lobsterId)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun searchConversations(
        lobsterId: Long,
        query: String,
        limit: Int
    ): List<Conversation> =
        db.conversationDao()
            .searchByLobster(lobsterId, query.trim(), limit.coerceIn(1, 100))
            .map { it.toDomain() }

    override suspend fun createConversation(
        lobsterId: Long,
        title: String,
        modelConfigId: Long?
    ): Long =
        db.conversationDao().insert(
            ConversationEntity(
                lobsterId = lobsterId,
                title = title.ifBlank { "新对话" },
                modelConfigId = modelConfigId
            )
        )

    override suspend fun renameConversation(id: Long, title: String) {
        val clean = title.trim().take(80)
        if (clean.isNotBlank()) db.conversationDao().rename(id, clean)
    }

    override suspend fun deleteConversation(conversation: Conversation) =
        db.conversationDao().delete(conversation.toEntity())

    override fun observeMessages(conversationId: Long): Flow<List<Message>> =
        db.messageDao().observeByConversation(conversationId)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getMessages(conversationId: Long): List<Message> =
        db.messageDao().getByConversation(conversationId).map { it.toDomain() }

    override suspend fun addMessage(message: Message): Long {
        val id = db.messageDao().insert(message.toEntity())
        db.conversationDao().touch(message.conversationId)
        return id
    }

    override suspend fun updateMessage(message: Message) {
        db.messageDao().update(message.toEntity())
        db.conversationDao().touch(message.conversationId)
    }

    override suspend fun deleteMessage(message: Message) =
        db.messageDao().delete(message.toEntity())

    override suspend fun deleteMessagesFrom(conversationId: Long, messageId: Long) =
        db.messageDao().deleteFrom(conversationId, messageId)

    override fun observeModelConfigs(): Flow<List<ModelConfig>> =
        db.modelConfigDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getModelConfig(id: Long): ModelConfig? =
        db.modelConfigDao().getById(id)?.toDomain()

    override suspend fun saveModelConfig(
        existingId: Long?,
        name: String,
        provider: ProviderType,
        baseUrl: String,
        modelName: String,
        apiKey: String,
        customHeadersJson: String,
        systemPrompt: String,
        temperature: Double
    ): Long {
        require(baseUrl.trim().startsWith("https://")) { "API Base URL 必须使用 HTTPS" }
        require(modelName.isNotBlank()) { "Model Name 不能为空" }

        val existing = existingId?.let { db.modelConfigDao().getById(it) }
        val keyRef = if (apiKey.isNotBlank()) {
            vault.put(apiKey.trim(), existing?.apiKeyRef)
        } else {
            existing?.apiKeyRef ?: error("API Key 不能为空")
        }

        val entity = ModelConfigEntity(
            id = existingId ?: 0,
            name = name.trim().ifBlank { modelName.trim() },
            provider = provider,
            baseUrl = baseUrl.trim().trimEnd('/'),
            modelName = modelName.trim(),
            apiKeyRef = keyRef,
            customHeadersJson = customHeadersJson.trim().ifBlank { "{}" },
            systemPrompt = systemPrompt.trim(),
            temperature = temperature.coerceIn(0.0, 2.0),
            enabled = true,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )

        return if (existing == null) {
            db.modelConfigDao().insert(entity)
        } else {
            db.modelConfigDao().update(entity)
            entity.id
        }
    }

    override suspend fun deleteModelConfig(config: ModelConfig) {
        db.modelConfigDao().delete(config.toEntity())
        vault.delete(config.apiKeyRef)
    }

    override suspend fun testModelConnection(
        config: ModelConfig,
        apiKeyOverride: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val key = apiKeyOverride?.takeIf { it.isNotBlank() } ?: vault.get(config.apiKeyRef)
        aiGateway.test(config, key)
    }

    override suspend fun fetchModels(
        existingConfigId: Long?,
        provider: ProviderType,
        baseUrl: String,
        apiKeyOverride: String?,
        customHeadersJson: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            require(baseUrl.trim().startsWith("https://")) { "API Base URL 必须使用 HTTPS" }
            val existing = existingConfigId?.let { db.modelConfigDao().getById(it) }
            val key = apiKeyOverride?.takeIf { it.isNotBlank() }
                ?: existing?.apiKeyRef?.let(vault::get)
                ?: error("请先填写 API Key")

            val probe = ModelConfig(
                id = existingConfigId ?: 0,
                name = existing?.name ?: "模型探测",
                provider = provider,
                baseUrl = baseUrl.trim().trimEnd('/'),
                modelName = existing?.modelName.orEmpty(),
                apiKeyRef = existing?.apiKeyRef.orEmpty(),
                customHeadersJson = customHeadersJson.ifBlank { "{}" },
                systemPrompt = existing?.systemPrompt.orEmpty(),
                temperature = existing?.temperature ?: 0.7,
                enabled = true,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            aiGateway.fetchModels(probe, key).getOrThrow()
        }
    }

    override fun streamChat(
        config: ModelConfig,
        messages: List<Message>,
        apiKeyOverride: String?,
        thinkingEnabled: Boolean,
        visionEnabled: Boolean
    ): Flow<String> {
        val key = apiKeyOverride?.takeIf { it.isNotBlank() } ?: vault.get(config.apiKeyRef)
        return aiGateway.stream(
            config = config,
            apiKey = key,
            messages = messages,
            thinkingEnabled = thinkingEnabled,
            visionEnabled = visionEnabled
        )
    }

    override fun observeKnowledge(): Flow<List<KnowledgeItem>> =
        db.knowledgeDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getRecentKnowledge(limit: Int): List<KnowledgeItem> =
        db.knowledgeDao()
            .getRecent(limit.coerceIn(1, 200))
            .map { it.toDomain() }

    override suspend fun saveKnowledge(
        type: KnowledgeType,
        title: String,
        content: String,
        sourceUrl: String?,
        description: String?
    ): Long =
        db.knowledgeDao().insert(
            KnowledgeItemEntity(
                type = type,
                title = title.ifBlank { "未命名" },
                sourceUrl = sourceUrl,
                content = content,
                description = description
            )
        )

    override suspend fun deleteKnowledge(item: KnowledgeItem) =
        db.knowledgeDao().delete(item.toEntity())

    override suspend fun fetchWebPage(url: String): Result<WebPageContent> =
        runCatching { webExtractor.fetch(url) }

    override fun observeMemories(): Flow<List<Memory>> =
        db.memoryDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun saveMemory(
        type: MemoryType,
        title: String,
        content: String,
        importance: Int,
        sourceConversationId: Long?
    ): Long {
        val cleanContent = content.trim().take(8_000)
        require(cleanContent.isNotBlank()) { "记忆内容不能为空" }
        val now = System.currentTimeMillis()
        val entity = MemoryEntity(
            type = type,
            title = title.trim().ifBlank { cleanContent.take(32) },
            content = cleanContent,
            importance = importance.coerceIn(1, 5),
            fingerprint = ragEngine.fingerprint(type, cleanContent),
            vector = ragEngine.vectorize(cleanContent),
            sourceConversationId = sourceConversationId,
            createdAt = now,
            updatedAt = now,
            lastAccessedAt = now
        )
        return db.memoryDao().upsert(entity)
    }

    override suspend fun deleteMemory(memory: Memory) =
        db.memoryDao().delete(memory.toEntity())

    override suspend fun clearMemories() =
        db.memoryDao().clearAll()

    override suspend fun searchRag(query: String, limit: Int): List<RagHit> =
        withContext(Dispatchers.Default) {
            val memories = db.memoryDao().getRecent(300).map { it.toDomain() }
            val knowledge = db.knowledgeDao().getRecent(120).map { it.toDomain() }
            val hits = ragEngine.rank(query, memories, knowledge, limit)
            val memoryIds = hits.mapNotNull { hit ->
                hit.source
                    .takeIf { it.startsWith("memory:") }
                    ?.substringAfterLast(':')
                    ?.toLongOrNull()
            }
            if (memoryIds.isNotEmpty()) db.memoryDao().touch(memoryIds)
            hits
        }

    override fun observeDailyProgress(lobsterId: Long): Flow<DailyProgress> {
        val day = LocalDate.now().toString()
        return db.dailyActionDao().observeDay(lobsterId, day).map { list ->
            val actions = list.map { it.action }.toSet()
            DailyProgress(
                checkedIn = "checkin" in actions,
                fed = "feed" in actions,
                interacted = "interact" in actions,
                chatted = "chat" in actions
            )
        }
    }

    override suspend fun markDailyAction(lobsterId: Long, action: String): Boolean {
        val inserted = db.dailyActionDao().insert(
            DailyActionEntity(
                lobsterId = lobsterId,
                dayKey = LocalDate.now().toString(),
                action = action
            )
        )
        return inserted > 0
    }

    private fun levelForExp(exp: Int): Int {
        var level = 1
        var threshold = 100
        var remaining = exp
        while (remaining >= threshold && level < 100) {
            remaining -= threshold
            level += 1
            threshold = 100 + (level - 1) * 40
        }
        return level
    }

    private fun Lobster.toEntity() =
        LobsterEntity(id, name, prompt, level, experience, mood, satiety, intimacy, createdAt)

    private fun Conversation.toEntity() =
        ConversationEntity(id, lobsterId, title, modelConfigId, createdAt, updatedAt)

    private fun Message.toEntity() =
        MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role,
            content = content,
            imageUri = imageUri,
            createdAt = createdAt,
            inputTokens = inputTokens,
            outputTokens = outputTokens
        )

    private fun ModelConfig.toEntity() =
        ModelConfigEntity(
            id,
            name,
            provider,
            baseUrl,
            modelName,
            apiKeyRef,
            customHeadersJson,
            systemPrompt,
            temperature,
            enabled,
            createdAt
        )

    private fun KnowledgeItem.toEntity() =
        KnowledgeItemEntity(
            id,
            type,
            title,
            sourceUrl,
            content,
            description,
            createdAt,
            updatedAt
        )

    private fun Memory.toEntity() =
        MemoryEntity(
            id = id,
            type = type,
            title = title,
            content = content,
            importance = importance,
            fingerprint = fingerprint,
            vector = vector,
            sourceConversationId = sourceConversationId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastAccessedAt = lastAccessedAt
        )
}

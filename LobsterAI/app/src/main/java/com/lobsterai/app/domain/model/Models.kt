package com.lobsterai.app.domain.model

enum class ProviderType {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI
}

enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT
}

enum class KnowledgeType {
    WEB,
    NOTE,
    FILE,
    CHAT,
    MEMORY
}

enum class MemoryType {
    PROFILE,
    PREFERENCE,
    PROJECT,
    DECISION,
    GOAL,
    FACT,
    TODO,
    RELATIONSHIP,
    OTHER
}

data class Lobster(
    val id: Long = 0,
    val name: String,
    val prompt: String,
    val level: Int,
    val experience: Int,
    val mood: Int,
    val satiety: Int,
    val intimacy: Int,
    val createdAt: Long
)

data class Conversation(
    val id: Long = 0,
    val lobsterId: Long,
    val title: String,
    val modelConfigId: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

data class Message(
    val id: Long = 0,
    val conversationId: Long,
    val role: ChatRole,
    val content: String,
    val imageUri: String? = null,
    val createdAt: Long,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
)

data class ModelConfig(
    val id: Long = 0,
    val name: String,
    val provider: ProviderType,
    val baseUrl: String,
    val modelName: String,
    val apiKeyRef: String,
    val customHeadersJson: String,
    val systemPrompt: String,
    val temperature: Double,
    val enabled: Boolean,
    val createdAt: Long
)

data class KnowledgeItem(
    val id: Long = 0,
    val type: KnowledgeType,
    val title: String,
    val sourceUrl: String?,
    val content: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class Memory(
    val id: Long = 0,
    val type: MemoryType,
    val title: String,
    val content: String,
    val importance: Int,
    val fingerprint: String,
    val vector: String,
    val sourceConversationId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAccessedAt: Long
)

data class RagHit(
    val title: String,
    val content: String,
    val source: String,
    val score: Double
)

data class WebPageContent(
    val url: String,
    val title: String,
    val description: String,
    val text: String
)

data class DailyProgress(
    val checkedIn: Boolean,
    val fed: Boolean,
    val interacted: Boolean,
    val chatted: Boolean
) {
    val completedCount: Int get() = listOf(checkedIn, fed, interacted, chatted).count { it }
}

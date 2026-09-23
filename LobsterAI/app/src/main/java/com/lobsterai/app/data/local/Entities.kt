package com.lobsterai.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lobsterai.app.domain.model.ChatRole
import com.lobsterai.app.domain.model.KnowledgeType
import com.lobsterai.app.domain.model.ProviderType

@Entity(tableName = "lobsters")
data class LobsterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val prompt: String,
    val level: Int = 1,
    val experience: Int = 0,
    val mood: Int = 80,
    val satiety: Int = 80,
    val intimacy: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = LobsterEntity::class,
            parentColumns = ["id"],
            childColumns = ["lobsterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lobsterId")]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lobsterId: Long,
    val title: String,
    val modelConfigId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: ChatRole,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
)

@Entity(tableName = "model_configs")
data class ModelConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val provider: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val baseUrl: String,
    val modelName: String,
    val apiKeyRef: String,
    val customHeadersJson: String = "{}",
    val systemPrompt: String = "",
    val temperature: Double = 0.7,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "knowledge_items")
data class KnowledgeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: KnowledgeType,
    val title: String,
    val sourceUrl: String? = null,
    val content: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "daily_actions",
    indices = [Index(value = ["lobsterId", "dayKey", "action"], unique = true)]
)
data class DailyActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lobsterId: Long,
    val dayKey: String,
    val action: String,
    val createdAt: Long = System.currentTimeMillis()
)
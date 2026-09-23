package com.lobsterai.app.data.local

import com.lobsterai.app.domain.model.Conversation
import com.lobsterai.app.domain.model.KnowledgeItem
import com.lobsterai.app.domain.model.Lobster
import com.lobsterai.app.domain.model.Memory
import com.lobsterai.app.domain.model.Message
import com.lobsterai.app.domain.model.ModelConfig

fun LobsterEntity.toDomain() = Lobster(id, name, prompt, level, experience, mood, satiety, intimacy, createdAt)
fun ConversationEntity.toDomain() = Conversation(id, lobsterId, title, modelConfigId, createdAt, updatedAt)
fun MessageEntity.toDomain() = Message(id, conversationId, role, content, imageUri, createdAt, inputTokens, outputTokens)
fun ModelConfigEntity.toDomain() = ModelConfig(id, name, provider, baseUrl, modelName, apiKeyRef, customHeadersJson, systemPrompt, temperature, enabled, createdAt)
fun KnowledgeItemEntity.toDomain() = KnowledgeItem(id, type, title, sourceUrl, content, description, createdAt, updatedAt)
fun MemoryEntity.toDomain() = Memory(id, type, title, content, importance, fingerprint, vector, sourceConversationId, createdAt, updatedAt, lastAccessedAt)

package com.lobsterai.app.data.memory

import com.lobsterai.app.domain.model.ChatRole
import com.lobsterai.app.domain.model.MemoryType
import com.lobsterai.app.domain.model.Message
import com.lobsterai.app.domain.model.ModelConfig
import com.lobsterai.app.domain.repository.AppRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryService @Inject constructor(
    private val repository: AppRepository,
    private val json: Json
) {
    suspend fun buildRagContext(query: String, limit: Int = 6): String {
        if (query.isBlank()) return ""
        val hits = repository.searchRag(query, limit)
        if (hits.isEmpty()) return ""

        return hits.joinToString("\n\n---\n\n") { hit ->
            "【${hit.title}】\n${hit.content.take(2_000)}"
        }.take(9_000)
    }

    suspend fun extractAndSave(
        config: ModelConfig,
        conversationId: Long,
        userText: String,
        assistantText: String
    ) {
        if (userText.isBlank()) return

        val prompt = """
            你是本地长期记忆提取器。只提取未来对用户真正有帮助、相对稳定的信息。
            不要保存一次性寒暄、临时问题、模型猜测、密码、API Key、验证码、银行卡号等秘密。
            最多 3 条；没有值得记住的内容就输出 []。
            
            只能输出 JSON 数组，不要 Markdown，不要解释。
            每项格式：
            {"type":"PREFERENCE|PROJECT|DECISION|GOAL|FACT|TODO|PROFILE|RELATIONSHIP|OTHER","title":"短标题","content":"独立完整事实","importance":1}
            importance 必须是 1 到 5。
            
            用户：
            ${userText.take(8_000)}
            
            助手：
            ${assistantText.take(8_000)}
        """.trimIndent()

        var raw = ""
        runCatching {
            repository.streamChat(
                config = config,
                messages = listOf(
                    Message(
                        conversationId = 0,
                        role = ChatRole.USER,
                        content = prompt,
                        createdAt = System.currentTimeMillis()
                    )
                ),
                thinkingEnabled = false,
                visionEnabled = false
            ).collect { raw += it }
        }

        val candidates = parse(raw).ifEmpty { fallback(userText) }
        candidates.take(3).forEach { item ->
            repository.saveMemory(
                type = item.type,
                title = item.title,
                content = item.content,
                importance = item.importance,
                sourceConversationId = conversationId
            )
        }
    }

    private fun parse(raw: String): List<MemoryCandidate> = runCatching {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return@runCatching emptyList()

        json.parseToJsonElement(raw.substring(start, end + 1))
            .jsonArray
            .mapNotNull { element ->
                val obj = element.jsonObject
                val content = obj["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (content.length < 4) return@mapNotNull null

                val type = runCatching {
                    MemoryType.valueOf(
                        obj["type"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "OTHER"
                    )
                }.getOrDefault(MemoryType.OTHER)

                MemoryCandidate(
                    type = type,
                    title = obj["title"]?.jsonPrimitive?.contentOrNull
                        ?.trim()
                        ?.take(80)
                        .orEmpty()
                        .ifBlank { content.take(32) },
                    content = content.take(8_000),
                    importance = obj["importance"]?.jsonPrimitive?.intOrNull
                        ?.coerceIn(1, 5)
                        ?: 3
                )
            }
    }.getOrDefault(emptyList())

    private fun fallback(userText: String): List<MemoryCandidate> {
        val clean = userText.trim()
        if (clean.length < 12) return emptyList()

        val lower = clean.lowercase()
        val markers = listOf(
            "我喜欢", "我不喜欢", "我习惯", "以后", "记住",
            "我的项目", "我打算", "我计划", "我决定", "我的目标", "我正在做",
            "i prefer", "i like", "i dislike", "remember", "my project",
            "my goal", "i plan", "i decided"
        )
        if (markers.none { lower.contains(it) }) return emptyList()

        val type = when {
            clean.contains("喜欢") || clean.contains("习惯") || lower.contains("prefer") ->
                MemoryType.PREFERENCE
            clean.contains("项目") || lower.contains("project") ->
                MemoryType.PROJECT
            clean.contains("决定") || lower.contains("decided") ->
                MemoryType.DECISION
            clean.contains("目标") || clean.contains("计划") ||
                lower.contains("goal") || lower.contains("plan") ->
                MemoryType.GOAL
            else -> MemoryType.FACT
        }

        return listOf(
            MemoryCandidate(
                type = type,
                title = clean.lineSequence().first().take(32),
                content = clean.take(2_000),
                importance = 3
            )
        )
    }
}

private data class MemoryCandidate(
    val type: MemoryType,
    val title: String,
    val content: String,
    val importance: Int
)

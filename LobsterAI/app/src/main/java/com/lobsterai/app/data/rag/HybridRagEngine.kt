package com.lobsterai.app.data.rag

import com.lobsterai.app.domain.model.KnowledgeItem
import com.lobsterai.app.domain.model.Memory
import com.lobsterai.app.domain.model.MemoryType
import com.lobsterai.app.domain.model.RagHit
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

@Singleton
class HybridRagEngine @Inject constructor() {
    fun fingerprint(type: MemoryType, content: String): String {
        val normalized = content.lowercase().replace(Regex("\\s+"), " ").trim()
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("${type.name}|$normalized".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun vectorize(text: String): String = encode(sparseVector(text))

    fun rank(
        query: String,
        memories: List<Memory>,
        knowledge: List<KnowledgeItem>,
        limit: Int
    ): List<RagHit> {
        if (query.isBlank()) return emptyList()
        val queryVector = sparseVector(query)
        val queryTerms = terms(query)
        val now = System.currentTimeMillis()

        val memoryHits = memories.map { memory ->
            val semantic = cosine(queryVector, decode(memory.vector))
            val lexical = lexicalScore(queryTerms, terms(memory.title + "\n" + memory.content))
            val importance = memory.importance.coerceIn(1, 5) / 5.0
            val ageDays = ((now - memory.updatedAt).coerceAtLeast(0L) / 86_400_000.0)
            val recency = exp(-ageDays / 120.0)
            val score = semantic * 0.58 + lexical * 0.22 + importance * 0.14 + recency * 0.06
            RagHit(
                title = memory.title,
                content = memory.content,
                source = "memory:${memory.type.name}:${memory.id}",
                score = score
            )
        }

        val knowledgeHits = knowledge.map { item ->
            val text = item.title + "\n" + item.content.take(12_000)
            val semantic = cosine(queryVector, sparseVector(text))
            val lexical = lexicalScore(queryTerms, terms(text))
            val ageDays = ((now - item.updatedAt).coerceAtLeast(0L) / 86_400_000.0)
            val recency = exp(-ageDays / 180.0)
            val score = semantic * 0.66 + lexical * 0.28 + recency * 0.06
            RagHit(
                title = item.title,
                content = item.content,
                source = "knowledge:${item.type.name}:${item.id}",
                score = score
            )
        }

        return (memoryHits + knowledgeHits)
            .filter { it.score > 0.05 }
            .sortedByDescending { it.score }
            .take(limit.coerceIn(1, 12))
    }

    private fun sparseVector(text: String): Map<Int, Float> {
        val counts = HashMap<Int, Float>()
        val tokens = terms(text)
        if (tokens.isEmpty()) return emptyMap()

        tokens.forEach { token ->
            val index = positiveHash(token) % DIMENSIONS
            val sign = if ((positiveHash("sign:$token") and 1) == 0) 1f else -1f
            val weight = (1.0 + ln(1.0 + token.length)).toFloat()
            counts[index] = (counts[index] ?: 0f) + sign * weight
        }

        val norm = sqrt(counts.values.sumOf { (it * it).toDouble() }).toFloat()
        if (norm <= 0f) return emptyMap()
        return counts.mapValues { (_, value) -> value / norm }
    }

    private fun terms(text: String): Set<String> {
        val normalized = text.lowercase(Locale.ROOT)
        val result = LinkedHashSet<String>()

        Regex("[a-z0-9_]{2,}").findAll(normalized).forEach { result += it.value }

        val chinese = normalized.filter { it.code in 0x4E00..0x9FFF }
        if (chinese.length >= 2) {
            chinese.windowed(2).forEach { result += it }
            if (chinese.length >= 3) chinese.windowed(3).take(96).forEach { result += it }
        }

        normalized
            .split(Regex("[^a-z0-9_\\u4e00-\\u9fff]+"))
            .filter { it.length >= 2 }
            .take(128)
            .forEach { result += it }

        return result.take(MAX_TERMS).toSet()
    }

    private fun lexicalScore(queryTerms: Set<String>, docTerms: Set<String>): Double {
        if (queryTerms.isEmpty() || docTerms.isEmpty()) return 0.0
        val intersection = queryTerms.count { it in docTerms }.toDouble()
        if (intersection == 0.0) return 0.0
        return intersection / sqrt(queryTerms.size.toDouble() * docTerms.size.toDouble())
    }

    private fun cosine(a: Map<Int, Float>, b: Map<Int, Float>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val small = if (a.size <= b.size) a else b
        val large = if (a.size <= b.size) b else a
        var dot = 0.0
        small.forEach { (index, value) ->
            dot += value * (large[index] ?: 0f)
        }
        return dot.coerceIn(-1.0, 1.0).coerceAtLeast(0.0)
    }

    private fun encode(vector: Map<Int, Float>): String = vector.entries
        .sortedBy { it.key }
        .joinToString(";") { (index, value) ->
            "$index:${String.format(Locale.US, "%.6f", value)}"
        }

    private fun decode(encoded: String): Map<Int, Float> {
        if (encoded.isBlank()) return emptyMap()
        val result = HashMap<Int, Float>()
        encoded.split(';').forEach { item ->
            val separator = item.indexOf(':')
            if (separator <= 0 || separator >= item.lastIndex) return@forEach
            val index = item.substring(0, separator).toIntOrNull() ?: return@forEach
            val value = item.substring(separator + 1).toFloatOrNull() ?: return@forEach
            result[index] = value
        }
        return result
    }

    private fun positiveHash(value: String): Int = value.hashCode() and Int.MAX_VALUE

    private companion object {
        const val DIMENSIONS = 384
        const val MAX_TERMS = 256
    }
}

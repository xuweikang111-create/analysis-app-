package com.lobsterai.app.domain.rag

/** Reserved extension point for optional local/remote RAG vector databases. */
interface VectorStore {
    suspend fun upsert(id: String, text: String, metadata: Map<String, String> = emptyMap())
    suspend fun search(query: String, topK: Int = 5): List<VectorMatch>
    suspend fun delete(id: String)
}

data class VectorMatch(
    val id: String,
    val score: Float,
    val text: String,
    val metadata: Map<String, String>
)
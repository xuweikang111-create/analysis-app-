package com.lobsterai.app.data.remote

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.lobsterai.app.domain.model.ChatRole
import com.lobsterai.app.domain.model.Message
import com.lobsterai.app.domain.model.ModelConfig
import com.lobsterai.app.domain.model.ProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class OpenAiModelList(
    val data: List<OpenAiModel> = emptyList()
)

@Serializable
data class OpenAiModel(
    val id: String
)

interface OpenAiModelsService {
    @GET("models")
    suspend fun models(): OpenAiModelList
}

@Singleton
class AiGateway @Inject constructor(
    private val baseClient: OkHttpClient,
    private val json: Json
) {
    fun stream(config: ModelConfig, apiKey: String, messages: List<Message>): Flow<String> = when (config.provider) {
        ProviderType.OPENAI_COMPATIBLE -> streamOpenAi(config, apiKey, messages)
        ProviderType.ANTHROPIC -> streamAnthropic(config, apiKey, messages)
        ProviderType.GEMINI -> streamGemini(config, apiKey, messages)
    }

    suspend fun test(config: ModelConfig, apiKey: String): Result<String> = runCatching {
        require(config.baseUrl.startsWith("https://")) { "仅允许 HTTPS API 地址" }
        when (config.provider) {
            ProviderType.OPENAI_COMPATIBLE -> testOpenAi(config, apiKey)
            ProviderType.ANTHROPIC -> testAnthropic(config, apiKey)
            ProviderType.GEMINI -> testGemini(config, apiKey)
        }
    }

    private suspend fun testOpenAi(config: ModelConfig, apiKey: String): String {
        val client = clientFor(config, apiKey)
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(config.baseUrl))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val modelListResult = runCatching { retrofit.create(OpenAiModelsService::class.java).models() }
        modelListResult.getOrNull()?.let { result ->
            return "连接成功，可见 ${result.data.size} 个模型"
        }

        val body = buildJsonObject {
            put("model", JsonPrimitive(config.modelName))
            put("stream", JsonPrimitive(false))
            put("max_tokens", JsonPrimitive(1))
            put("messages", buildJsonArray { add(openAiMessage("user", "ping")) })
        }
        val request = Request.Builder()
            .url(normalizeBaseUrl(config.baseUrl) + "chat/completions")
            .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer $apiKey")
            .applyCustomHeaders(config)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val modelsError = modelListResult.exceptionOrNull()?.message.orEmpty().take(180)
                val chatError = response.body?.string().orEmpty().take(300)
                error("连接失败 HTTP ${response.code}: ${chatError.ifBlank { modelsError }}")
            }
        }
        return "连接成功，Chat Completions 可用"
    }

    private fun testAnthropic(config: ModelConfig, apiKey: String): String {
        val url = normalizeBaseUrl(config.baseUrl) + "messages"
        val body = buildJsonObject {
            put("model", JsonPrimitive(config.modelName))
            put("max_tokens", JsonPrimitive(1))
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive("hi"))
                })
            })
        }
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON_MEDIA))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .applyCustomHeaders(config)
            .build()
        baseClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("连接失败 HTTP ${response.code}: ${response.body?.string().orEmpty().take(300)}")
        }
        return "Claude 连接成功"
    }

    private fun testGemini(config: ModelConfig, apiKey: String): String {
        val base = normalizeGeminiBase(config.baseUrl)
        val request = Request.Builder()
            .url(base + "models")
            .get()
            .header("x-goog-api-key", apiKey)
            .applyCustomHeaders(config)
            .build()
        baseClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("连接失败 HTTP ${response.code}: ${response.body?.string().orEmpty().take(300)}")
        }
        return "Gemini 连接成功"
    }

    private fun streamOpenAi(config: ModelConfig, apiKey: String, messages: List<Message>): Flow<String> {
        val payload = buildJsonObject {
            put("model", JsonPrimitive(config.modelName))
            put("stream", JsonPrimitive(true))
            put("temperature", JsonPrimitive(config.temperature))
            put("messages", buildJsonArray {
                if (config.systemPrompt.isNotBlank()) {
                    add(openAiMessage("system", config.systemPrompt))
                }
                messages.forEach { message ->
                    add(openAiMessage(message.role.toWireRole(), message.content))
                }
            })
        }
        val request = Request.Builder()
            .url(normalizeBaseUrl(config.baseUrl) + "chat/completions")
            .post(json.encodeToString(JsonElement.serializer(), payload).toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .applyCustomHeaders(config)
            .build()
        return sseFlow(request) { data ->
            if (data == "[DONE]") return@sseFlow null
            runCatching {
                val root = json.parseToJsonElement(data).jsonObject
                root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        }
    }

    private fun streamAnthropic(config: ModelConfig, apiKey: String, messages: List<Message>): Flow<String> {
        val system = buildString {
            if (config.systemPrompt.isNotBlank()) append(config.systemPrompt)
            messages.filter { it.role == ChatRole.SYSTEM }.forEach {
                if (isNotEmpty()) append("\n\n")
                append(it.content)
            }
        }
        val payload = buildJsonObject {
            put("model", JsonPrimitive(config.modelName))
            put("max_tokens", JsonPrimitive(4096))
            put("stream", JsonPrimitive(true))
            put("temperature", JsonPrimitive(config.temperature))
            if (system.isNotBlank()) put("system", JsonPrimitive(system))
            put("messages", buildJsonArray {
                messages.filter { it.role != ChatRole.SYSTEM }.forEach { message ->
                    add(buildJsonObject {
                        put("role", JsonPrimitive(if (message.role == ChatRole.ASSISTANT) "assistant" else "user"))
                        put("content", JsonPrimitive(message.content))
                    })
                }
            })
        }
        val request = Request.Builder()
            .url(normalizeBaseUrl(config.baseUrl) + "messages")
            .post(json.encodeToString(JsonElement.serializer(), payload).toRequestBody(JSON_MEDIA))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Accept", "text/event-stream")
            .applyCustomHeaders(config)
            .build()
        return sseFlow(request) { data ->
            runCatching {
                val root = json.parseToJsonElement(data).jsonObject
                if (root["type"]?.jsonPrimitive?.contentOrNull == "content_block_delta") {
                    root["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                } else null
            }.getOrNull()
        }
    }

    private fun streamGemini(config: ModelConfig, apiKey: String, messages: List<Message>): Flow<String> {
        val payload = buildJsonObject {
            if (config.systemPrompt.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", JsonPrimitive(config.systemPrompt)) }) })
                })
            }
            put("generationConfig", buildJsonObject {
                put("temperature", JsonPrimitive(config.temperature))
            })
            put("contents", buildJsonArray {
                messages.filter { it.role != ChatRole.SYSTEM }.forEach { message ->
                    add(buildJsonObject {
                        put("role", JsonPrimitive(if (message.role == ChatRole.ASSISTANT) "model" else "user"))
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", JsonPrimitive(message.content)) })
                        })
                    })
                }
            })
        }
        val url = normalizeGeminiBase(config.baseUrl) + "models/${config.modelName}:streamGenerateContent?alt=sse"
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(JsonElement.serializer(), payload).toRequestBody(JSON_MEDIA))
            .header("x-goog-api-key", apiKey)
            .header("Accept", "text/event-stream")
            .applyCustomHeaders(config)
            .build()
        return sseFlow(request) { data ->
            runCatching {
                val root = json.parseToJsonElement(data).jsonObject
                root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        }
    }

    private fun sseFlow(request: Request, parser: (String) -> String?): Flow<String> = callbackFlow {
        val call = baseClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) close(e) else close(CancellationException("生成已停止"))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val detail = response.body?.string().orEmpty().take(500)
                    response.close()
                    close(IOException("HTTP ${response.code}: $detail"))
                    return
                }
                try {
                    response.use { res ->
                        val source = res.body?.source() ?: error("响应体为空")
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith("data:")) {
                                val data = line.removePrefix("data:").trim()
                                parser(data)?.takeIf { it.isNotEmpty() }?.let { trySend(it) }
                            }
                        }
                    }
                    close()
                } catch (t: Throwable) {
                    if (!call.isCanceled()) close(t)
                }
            }
        })
        awaitClose { call.cancel() }
    }

    private fun clientFor(config: ModelConfig, apiKey: String): OkHttpClient = baseClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .applyCustomHeaders(config)
                .build()
            chain.proceed(request)
        }
        .build()

    private fun Request.Builder.applyCustomHeaders(config: ModelConfig): Request.Builder {
        val headers = runCatching {
            val obj = json.parseToJsonElement(config.customHeadersJson.ifBlank { "{}" }).jsonObject
            obj.mapValues { (_, value) -> value.jsonPrimitive.contentOrNull.orEmpty() }
        }.getOrDefault(emptyMap())
        headers.forEach { (key, value) -> if (key.isNotBlank()) header(key, value) }
        return this
    }

    private fun normalizeBaseUrl(url: String): String {
        require(url.startsWith("https://")) { "仅允许 HTTPS API 地址" }
        return url.trim().trimEnd('/') + "/"
    }

    private fun normalizeGeminiBase(url: String): String {
        val base = normalizeBaseUrl(url)
        return if (base.endsWith("v1beta/")) base else base + "v1beta/"
    }

    private fun openAiMessage(role: String, content: String): JsonObject = buildJsonObject {
        put("role", JsonPrimitive(role))
        put("content", JsonPrimitive(content))
    }

    private fun ChatRole.toWireRole(): String = when (this) {
        ChatRole.SYSTEM -> "system"
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
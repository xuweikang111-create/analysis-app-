package com.lobsterai.app.data.remote

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.lobsterai.app.domain.model.ChatRole
import com.lobsterai.app.domain.model.Message
import com.lobsterai.app.domain.model.ModelConfig
import com.lobsterai.app.domain.model.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val baseClient: OkHttpClient,
    private val json: Json
) {
    fun stream(
        config: ModelConfig,
        apiKey: String,
        messages: List<Message>,
        thinkingEnabled: Boolean,
        visionEnabled: Boolean
    ): Flow<String> = when (config.provider) {
        ProviderType.OPENAI_COMPATIBLE -> streamOpenAi(config, apiKey, messages, thinkingEnabled, visionEnabled)
        ProviderType.ANTHROPIC -> streamAnthropic(config, apiKey, messages, thinkingEnabled, visionEnabled)
        ProviderType.GEMINI -> streamGemini(config, apiKey, messages, thinkingEnabled, visionEnabled)
    }

    suspend fun fetchModels(config: ModelConfig, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.baseUrl.startsWith("https://")) { "仅允许 HTTPS API 地址" }
            when (config.provider) {
                ProviderType.OPENAI_COMPATIBLE -> fetchOpenAiModels(config, apiKey)
                ProviderType.ANTHROPIC -> fetchAnthropicModels(config, apiKey)
                ProviderType.GEMINI -> fetchGeminiModels(config, apiKey)
            }.distinct().sorted()
        }
    }

    suspend fun test(config: ModelConfig, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.baseUrl.startsWith("https://")) { "仅允许 HTTPS API 地址" }
            val models = fetchModels(config, apiKey).getOrNull().orEmpty()
            if (models.isNotEmpty()) return@runCatching "连接成功，可见 ${models.size} 个模型"
            when (config.provider) {
                ProviderType.OPENAI_COMPATIBLE -> testOpenAiChat(config, apiKey)
                ProviderType.ANTHROPIC -> testAnthropicChat(config, apiKey)
                ProviderType.GEMINI -> "Gemini 连接成功"
            }
        }
    }

    private fun fetchOpenAiModels(config: ModelConfig, apiKey: String): List<String> {
        val request = Request.Builder()
            .url(normalizeBaseUrl(config.baseUrl) + "models")
            .get()
            .header("Authorization", "Bearer $apiKey")
            .applyCustomHeaders(config.customHeadersJson)
            .build()
        return executeJson(request) { root ->
            root["data"]?.jsonArray.orEmpty().mapNotNull { item ->
                item.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            }
        }
    }

    private fun fetchAnthropicModels(config: ModelConfig, apiKey: String): List<String> {
        val request = Request.Builder()
            .url(normalizeBaseUrl(config.baseUrl) + "models")
            .get()
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .applyCustomHeaders(config.customHeadersJson)
            .build()
        return executeJson(request) { root ->
            root["data"]?.jsonArray.orEmpty().mapNotNull { item ->
                item.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            }
        }
    }

    private fun fetchGeminiModels(config: ModelConfig, apiKey: String): List<String> {
        val request = Request.Builder()
            .url(normalizeGeminiBase(config.baseUrl) + "models")
            .get()
            .header("x-goog-api-key", apiKey)
            .applyCustomHeaders(config.customHeadersJson)
            .build()
        return executeJson(request) { root ->
            root["models"]?.jsonArray.orEmpty().mapNotNull { item ->
                item.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/")
            }
        }
    }

    private fun testOpenAiChat(config: ModelConfig, apiKey: String): String {
        require(config.modelName.isNotBlank()) { "没有获取到模型，请手动填写 Model Name" }
        val body = buildJsonObject {
            put("model", JsonPrimitive(config.modelName))
            put("stream", JsonPrimitive(false))
            put("max_tokens", JsonPrimitive(1))
            put("messages", buildJsonArray { add(openAiTextMessage("user", "ping")) })
        }
        val request = Request.Builder()
            .url(normalizeBaseUrl(config.baseUrl) + "chat/completions")
            .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer $apiKey")
            .applyCustomHeaders(config.customHeadersJson)
            .build()
        baseClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("连接失败 HTTP ${response.code}: ${response.body?.string().orEmpty().take(300)}")
        }
        return "连接成功，Chat Completions 可用"
    }

    private fun testAnthropicChat(config: ModelConfig, apiKey: String): String {
        require(config.modelName.isNotBlank()) { "没有获取到模型，请手动填写 Model Name" }
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
            .url(normalizeBaseUrl(config.baseUrl) + "messages")
            .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON_MEDIA))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .applyCustomHeaders(config.customHeadersJson)
            .build()
        baseClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("连接失败 HTTP ${response.code}: ${response.body?.string().orEmpty().take(300)}")
        }
        return "Claude 连接成功"
    }

    private fun streamOpenAi(
        config: ModelConfig,
        apiKey: String,
        messages: List<Message>,
        thinkingEnabled: Boolean,
        visionEnabled: Boolean
    ): Flow<String> = sseFlow(
        requestBuilder = {
            val system = mergedSystemPrompt(config.systemPrompt, thinkingEnabled)
            val payload = buildJsonObject {
                put("model", JsonPrimitive(config.modelName))
                put("stream", JsonPrimitive(true))
                put("temperature", JsonPrimitive(config.temperature))
                put("messages", buildJsonArray {
                    if (system.isNotBlank()) add(openAiTextMessage("system", system))
                    messages.forEach { message -> add(openAiMessage(message, visionEnabled)) }
                })
            }
            Request.Builder()
                .url(normalizeBaseUrl(config.baseUrl) + "chat/completions")
                .post(json.encodeToString(JsonElement.serializer(), payload).toRequestBody(JSON_MEDIA))
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "text/event-stream")
                .applyCustomHeaders(config.customHeadersJson)
                .build()
        },
        parser = { data ->
            if (data == "[DONE]") null else runCatching {
                val root = json.parseToJsonElement(data).jsonObject
                root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        }
    )

    private fun streamAnthropic(
        config: ModelConfig,
        apiKey: String,
        messages: List<Message>,
        thinkingEnabled: Boolean,
        visionEnabled: Boolean
    ): Flow<String> = sseFlow(
        requestBuilder = {
            val system = buildString {
                val base = mergedSystemPrompt(config.systemPrompt, thinkingEnabled)
                if (base.isNotBlank()) append(base)
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
                            if (message.role == ChatRole.USER && visionEnabled && message.imageUri != null) {
                                put("content", buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", JsonPrimitive("text"))
                                        put("text", JsonPrimitive(message.content))
                                    })
                                    readImage(message.imageUri)?.let { image ->
                                        add(buildJsonObject {
                                            put("type", JsonPrimitive("image"))
                                            put("source", buildJsonObject {
                                                put("type", JsonPrimitive("base64"))
                                                put("media_type", JsonPrimitive(image.mimeType))
                                                put("data", JsonPrimitive(image.base64))
                                            })
                                        })
                                    }
                                })
                            } else {
                                put("content", JsonPrimitive(message.content))
                            }
                        })
                    }
                })
            }
            Request.Builder()
                .url(normalizeBaseUrl(config.baseUrl) + "messages")
                .post(json.encodeToString(JsonElement.serializer(), payload).toRequestBody(JSON_MEDIA))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Accept", "text/event-stream")
                .applyCustomHeaders(config.customHeadersJson)
                .build()
        },
        parser = { data ->
            runCatching {
                val root = json.parseToJsonElement(data).jsonObject
                if (root["type"]?.jsonPrimitive?.contentOrNull == "content_block_delta") {
                    root["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                } else null
            }.getOrNull()
        }
    )

    private fun streamGemini(
        config: ModelConfig,
        apiKey: String,
        messages: List<Message>,
        thinkingEnabled: Boolean,
        visionEnabled: Boolean
    ): Flow<String> = sseFlow(
        requestBuilder = {
            val system = mergedSystemPrompt(config.systemPrompt, thinkingEnabled)
            val payload = buildJsonObject {
                if (system.isNotBlank()) {
                    put("systemInstruction", buildJsonObject {
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", JsonPrimitive(system)) })
                        })
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
                                if (message.role == ChatRole.USER && visionEnabled && message.imageUri != null) {
                                    readImage(message.imageUri)?.let { image ->
                                        add(buildJsonObject {
                                            put("inlineData", buildJsonObject {
                                                put("mimeType", JsonPrimitive(image.mimeType))
                                                put("data", JsonPrimitive(image.base64))
                                            })
                                        })
                                    }
                                }
                            })
                        })
                    }
                })
            }
            Request.Builder()
                .url(normalizeGeminiBase(config.baseUrl) + "models/${config.modelName}:streamGenerateContent?alt=sse")
                .post(json.encodeToString(JsonElement.serializer(), payload).toRequestBody(JSON_MEDIA))
                .header("x-goog-api-key", apiKey)
                .header("Accept", "text/event-stream")
                .applyCustomHeaders(config.customHeadersJson)
                .build()
        },
        parser = { data ->
            runCatching {
                val root = json.parseToJsonElement(data).jsonObject
                root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        }
    )

    private fun openAiMessage(message: Message, visionEnabled: Boolean): JsonObject = buildJsonObject {
        put("role", JsonPrimitive(message.role.toWireRole()))
        if (message.role == ChatRole.USER && visionEnabled && message.imageUri != null) {
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(message.content))
                })
                readImage(message.imageUri)?.let { image ->
                    add(buildJsonObject {
                        put("type", JsonPrimitive("image_url"))
                        put("image_url", buildJsonObject {
                            put("url", JsonPrimitive("data:${image.mimeType};base64,${image.base64}"))
                        })
                    })
                }
            })
        } else {
            put("content", JsonPrimitive(message.content))
        }
    }

    private fun openAiTextMessage(role: String, content: String): JsonObject = buildJsonObject {
        put("role", JsonPrimitive(role))
        put("content", JsonPrimitive(content))
    }

    private fun mergedSystemPrompt(base: String, thinkingEnabled: Boolean): String = buildString {
        append(base.trim())
        if (thinkingEnabled) {
            if (isNotEmpty()) append("\n\n")
            append("深度思考模式已开启：回答前请充分检查条件、步骤与潜在反例；只输出对用户有帮助的结论、必要推理摘要与可验证依据，不输出隐藏思维链。")
        }
    }

    private fun readImage(uriText: String): EncodedImage? = runCatching {
        val uri = Uri.parse(uriText)
        val mime = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(MAX_IMAGE_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = stream.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            require(total <= MAX_IMAGE_BYTES) { "图片过大，请选择 10MB 以内图片" }
            buffer.copyOf(total)
        } ?: return@runCatching null
        EncodedImage(mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
    }.getOrNull()

    private fun executeJson(request: Request, transform: (JsonObject) -> List<String>): List<String> {
        baseClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${body.take(400)}")
            val root = json.parseToJsonElement(body).jsonObject
            return transform(root)
        }
    }

    private fun sseFlow(
        requestBuilder: suspend () -> Request,
        parser: (String) -> String?
    ): Flow<String> = callbackFlow {
        val request = try {
            withContext(Dispatchers.IO) { requestBuilder() }
        } catch (t: Throwable) {
            close(t)
            return@callbackFlow
        }
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

    private fun Request.Builder.applyCustomHeaders(headersJson: String): Request.Builder {
        val headers = runCatching {
            val obj = json.parseToJsonElement(headersJson.ifBlank { "{}" }).jsonObject
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
        val clean = url.trim().trimEnd('/')
        require(clean.startsWith("https://")) { "仅允许 HTTPS API 地址" }
        return when {
            clean.endsWith("/v1beta") -> "$clean/"
            clean.endsWith("/v1") -> clean.removeSuffix("/v1") + "/v1beta/"
            else -> "$clean/v1beta/"
        }
    }

    private fun ChatRole.toWireRole(): String = when (this) {
        ChatRole.SYSTEM -> "system"
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
    }

    private data class EncodedImage(val mimeType: String, val base64: String)

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
    }
}

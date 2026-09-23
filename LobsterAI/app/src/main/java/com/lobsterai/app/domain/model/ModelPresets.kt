package com.lobsterai.app.domain.model

data class ModelPreset(
    val label: String,
    val provider: ProviderType,
    val baseUrl: String
)

val BuiltInModelPresets: List<ModelPreset> = listOf(
    ModelPreset("OpenAI", ProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1"),
    ModelPreset("Claude", ProviderType.ANTHROPIC, "https://api.anthropic.com/v1"),
    ModelPreset("Gemini", ProviderType.GEMINI, "https://generativelanguage.googleapis.com"),
    ModelPreset("DeepSeek", ProviderType.OPENAI_COMPATIBLE, "https://api.deepseek.com"),
    ModelPreset("Kimi", ProviderType.OPENAI_COMPATIBLE, "https://api.moonshot.cn/v1"),
    ModelPreset("Qwen", ProviderType.OPENAI_COMPATIBLE, "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    ModelPreset("GLM", ProviderType.OPENAI_COMPATIBLE, "https://open.bigmodel.cn/api/paas/v4")
)

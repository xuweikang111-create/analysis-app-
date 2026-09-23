package com.lobsterai.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lobsterai.app.data.preferences.SettingsStore
import com.lobsterai.app.domain.model.ModelConfig
import com.lobsterai.app.domain.model.ProviderType
import com.lobsterai.app.domain.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {
    private val operationMessage = MutableStateFlow<String?>(null)
    private val availableModels = MutableStateFlow<List<String>>(emptyList())
    private val fetchingModels = MutableStateFlow(false)

    private val preferences = combine(
        settingsStore.darkMode,
        settingsStore.clipboardDetection,
        settingsStore.thinkingEnabled,
        settingsStore.visionEnabled,
        settingsStore.autoKnowledgeEnabled
    ) { dark, clipboard, thinking, vision, autoKnowledge ->
        PreferenceState(dark, clipboard, thinking, vision, autoKnowledge)
    }

    private val modelProbe = combine(availableModels, fetchingModels) { models, fetching ->
        ModelProbeState(models, fetching)
    }

    val state: StateFlow<SettingsUiState> = combine(
        repository.observeModelConfigs(),
        settingsStore.activeModelId,
        preferences,
        modelProbe,
        operationMessage
    ) { configs, activeId, prefs, probe, message ->
        SettingsUiState(
            configs = configs,
            activeModelId = activeId,
            darkMode = prefs.darkMode,
            clipboardDetection = prefs.clipboardDetection,
            thinkingEnabled = prefs.thinkingEnabled,
            visionEnabled = prefs.visionEnabled,
            autoKnowledgeEnabled = prefs.autoKnowledgeEnabled,
            availableModels = probe.models,
            fetchingModels = probe.fetching,
            message = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch { settingsStore.setDarkMode(enabled) }
    fun setClipboardDetection(enabled: Boolean) = viewModelScope.launch { settingsStore.setClipboardDetection(enabled) }
    fun setThinkingEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setThinkingEnabled(enabled) }
    fun setVisionEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setVisionEnabled(enabled) }
    fun setAutoKnowledgeEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setAutoKnowledgeEnabled(enabled) }
    fun selectModel(id: Long) = viewModelScope.launch { settingsStore.setActiveModel(id) }
    fun clearMessage() { operationMessage.value = null }
    fun clearAvailableModels() { availableModels.value = emptyList() }

    fun save(draft: ModelConfigDraft) = viewModelScope.launch {
        runCatching {
            val id = repository.saveModelConfig(
                existingId = draft.id,
                name = draft.name,
                provider = draft.provider,
                baseUrl = draft.baseUrl,
                modelName = draft.modelName,
                apiKey = draft.apiKey,
                customHeadersJson = draft.customHeadersJson,
                systemPrompt = draft.systemPrompt,
                temperature = draft.temperature
            )
            settingsStore.setActiveModel(id)
            operationMessage.value = "模型配置已保存"
        }.onFailure { operationMessage.value = "保存失败：${it.message}" }
    }

    fun delete(config: ModelConfig) = viewModelScope.launch {
        repository.deleteModelConfig(config)
        if (state.value.activeModelId == config.id) settingsStore.setActiveModel(null)
        operationMessage.value = "模型配置已删除"
    }

    fun test(config: ModelConfig, apiKeyOverride: String? = null) = viewModelScope.launch {
        operationMessage.value = "正在测试连接…"
        val result = repository.testModelConnection(config, apiKeyOverride)
        operationMessage.value = result.fold(
            onSuccess = { it },
            onFailure = { "连接失败：${it.message ?: it.javaClass.simpleName}" }
        )
    }

    fun testDraft(draft: ModelConfigDraft) {
        val existing = draft.id?.let { id -> state.value.configs.firstOrNull { it.id == id } }
        val temp = ModelConfig(
            id = draft.id ?: 0,
            name = draft.name.ifBlank { "临时配置" },
            provider = draft.provider,
            baseUrl = draft.baseUrl,
            modelName = draft.modelName,
            apiKeyRef = existing?.apiKeyRef.orEmpty(),
            customHeadersJson = draft.customHeadersJson,
            systemPrompt = draft.systemPrompt,
            temperature = draft.temperature,
            enabled = true,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        test(temp, draft.apiKey.takeIf { it.isNotBlank() })
    }

    fun fetchModels(draft: ModelConfigDraft, announce: Boolean = true) {
        if (fetchingModels.value) return
        if (!draft.baseUrl.startsWith("https://")) return
        if (draft.apiKey.isBlank() && draft.id == null) return
        viewModelScope.launch {
            fetchingModels.value = true
            if (announce) operationMessage.value = "正在自动获取模型列表…"
            try {
                val result = repository.fetchModels(
                    existingConfigId = draft.id,
                    provider = draft.provider,
                    baseUrl = draft.baseUrl,
                    apiKeyOverride = draft.apiKey.takeIf { it.isNotBlank() },
                    customHeadersJson = draft.customHeadersJson
                )
                result.fold(
                    onSuccess = { models ->
                        availableModels.value = models
                        if (announce) {
                            operationMessage.value = if (models.isEmpty()) "连接成功，但没有返回模型列表" else "已获取 ${models.size} 个模型"
                        }
                    },
                    onFailure = {
                        availableModels.value = emptyList()
                        if (announce) operationMessage.value = "获取模型失败：${it.message ?: it.javaClass.simpleName}"
                    }
                )
            } finally {
                fetchingModels.value = false
            }
        }
    }
}

data class SettingsUiState(
    val configs: List<ModelConfig> = emptyList(),
    val activeModelId: Long? = null,
    val darkMode: Boolean = false,
    val clipboardDetection: Boolean = true,
    val thinkingEnabled: Boolean = false,
    val visionEnabled: Boolean = true,
    val autoKnowledgeEnabled: Boolean = true,
    val availableModels: List<String> = emptyList(),
    val fetchingModels: Boolean = false,
    val message: String? = null
)

data class ModelConfigDraft(
    val id: Long? = null,
    val name: String = "",
    val provider: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val baseUrl: String = "https://api.openai.com/v1",
    val modelName: String = "",
    val apiKey: String = "",
    val customHeadersJson: String = "{}",
    val systemPrompt: String = "",
    val temperature: Double = 0.7
)

private data class PreferenceState(
    val darkMode: Boolean,
    val clipboardDetection: Boolean,
    val thinkingEnabled: Boolean,
    val visionEnabled: Boolean,
    val autoKnowledgeEnabled: Boolean
)

private data class ModelProbeState(
    val models: List<String>,
    val fetching: Boolean
)

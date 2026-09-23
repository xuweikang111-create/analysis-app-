package com.lobsterai.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lobsterai.app.domain.model.BuiltInModelPresets
import com.lobsterai.app.domain.model.ModelConfig
import com.lobsterai.app.domain.model.ProviderType
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editor by remember { mutableStateOf<ModelConfigDraft?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("米奇设置", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { viewModel.clearAvailableModels(); editor = ModelConfigDraft() }) {
                        Icon(Icons.Outlined.Add, "添加模型")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        ListItem(
                            headlineContent = { Text("视觉") },
                            supportingContent = { Text("默认开启；允许在聊天中上传图片给支持视觉的模型") },
                            trailingContent = { Switch(state.visionEnabled, viewModel::setVisionEnabled) }
                        )
                        ListItem(
                            headlineContent = { Text("思考模式") },
                            supportingContent = { Text("让模型在回答前做更充分的检查与推理摘要") },
                            trailingContent = { Switch(state.thinkingEnabled, viewModel::setThinkingEnabled) }
                        )
                        ListItem(
                            headlineContent = { Text("自动智识库") },
                            supportingContent = { Text("每轮聊天自动沉淀记忆，并在后续对话按相关性调用") },
                            trailingContent = { Switch(state.autoKnowledgeEnabled, viewModel::setAutoKnowledgeEnabled) }
                        )
                        ListItem(
                            headlineContent = { Text("深色模式") },
                            trailingContent = { Switch(state.darkMode, viewModel::setDarkMode) }
                        )
                        ListItem(
                            headlineContent = { Text("剪贴板 URL 识别") },
                            supportingContent = { Text("仅在 APP 回到前台时检查") },
                            trailingContent = { Switch(state.clipboardDetection, viewModel::setClipboardDetection) }
                        )
                    }
                }
            }

            item {
                Text("模型配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "填入 API Base URL 和 API Key 后，米奇会自动获取模型列表；若中转站不提供 /models，仍可手动填写。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.configs.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Key, null)
                            Text("还没有模型配置", fontWeight = FontWeight.SemiBold)
                            Button(onClick = { viewModel.clearAvailableModels(); editor = ModelConfigDraft() }) {
                                Text("添加模型")
                            }
                        }
                    }
                }
            }

            items(state.configs, key = { it.id }) { config ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = {
                            Text(if (config.id == state.activeModelId) "${config.name} · 当前" else config.name, fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = { Text("${config.modelName}\n${config.baseUrl}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.test(config) }) {
                                    Icon(Icons.Outlined.Key, "测试")
                                }
                                IconButton(onClick = {
                                    viewModel.clearAvailableModels()
                                    editor = config.toDraft()
                                }) {
                                    Icon(Icons.Outlined.Edit, "编辑")
                                }
                                IconButton(onClick = { viewModel.delete(config) }) {
                                    Icon(Icons.Outlined.DeleteOutline, "删除")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = { viewModel.selectModel(config.id) },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(if (config.id == state.activeModelId) "正在使用" else "设为当前模型")
                    }
                }
            }
        }
    }

    editor?.let { draft ->
        ModelEditorDialog(
            initial = draft,
            models = state.availableModels,
            fetching = state.fetchingModels,
            onDismiss = { editor = null },
            onFetchModels = viewModel::fetchModels,
            onSave = { viewModel.save(it); editor = null },
            onTest = viewModel::testDraft
        )
    }
}

@Composable
private fun ModelEditorDialog(
    initial: ModelConfigDraft,
    models: List<String>,
    fetching: Boolean,
    onDismiss: () -> Unit,
    onFetchModels: (ModelConfigDraft, Boolean) -> Unit,
    onSave: (ModelConfigDraft) -> Unit,
    onTest: (ModelConfigDraft) -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }

    LaunchedEffect(draft.provider, draft.baseUrl, draft.apiKey, draft.customHeadersJson, draft.id) {
        val canProbe = draft.baseUrl.startsWith("https://") && (draft.apiKey.length >= 6 || draft.id != null)
        if (canProbe) {
            delay(800)
            onFetchModels(draft, false)
        }
    }
    LaunchedEffect(models) {
        if (draft.modelName.isBlank() && models.isNotEmpty()) draft = draft.copy(modelName = models.first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "添加模型" else "编辑模型") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("厂商预设", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(BuiltInModelPresets, key = { it.label }) { preset ->
                            AssistChip(
                                onClick = {
                                    draft = draft.copy(
                                        name = if (draft.name.isBlank()) preset.label else draft.name,
                                        provider = preset.provider,
                                        baseUrl = preset.baseUrl,
                                        modelName = ""
                                    )
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("协议 · ${draft.provider.name}")
                        }
                        DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                            ProviderType.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.name) },
                                    onClick = {
                                        draft = draft.copy(provider = provider, modelName = "")
                                        providerMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        draft.name,
                        { draft = draft.copy(name = it) },
                        label = { Text("配置名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        draft.baseUrl,
                        { draft = draft.copy(baseUrl = it, modelName = "") },
                        label = { Text("API Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        draft.apiKey,
                        { draft = draft.copy(apiKey = it, modelName = "") },
                        label = { Text(if (draft.id == null) "API Key" else "API Key（留空沿用原 Key）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { modelMenu = true },
                                enabled = models.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (models.isEmpty()) {
                                        if (fetching) "正在获取模型…" else "暂无自动模型列表"
                                    } else {
                                        draft.modelName.ifBlank { "选择模型" }
                                    },
                                    maxLines = 1
                                )
                            }
                            DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                                models.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = { draft = draft.copy(modelName = model); modelMenu = false }
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { onFetchModels(draft, true) },
                            enabled = !fetching && draft.baseUrl.startsWith("https://") && (draft.apiKey.isNotBlank() || draft.id != null)
                        ) {
                            if (fetching) CircularProgressIndicator(Modifier.size(22.dp)) else Icon(Icons.Outlined.Refresh, "刷新模型")
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        draft.modelName,
                        { draft = draft.copy(modelName = it) },
                        label = { Text("Model Name（可手动填写）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        draft.customHeadersJson,
                        { draft = draft.copy(customHeadersJson = it) },
                        label = { Text("自定义 Header JSON") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        draft.systemPrompt,
                        { draft = draft.copy(systemPrompt = it) },
                        label = { Text("系统 Prompt") },
                        minLines = 3,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        draft.temperature.toString(),
                        { value -> value.toDoubleOrNull()?.let { draft = draft.copy(temperature = it.coerceIn(0.0, 2.0)) } },
                        label = { Text("Temperature 0~2") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onTest(draft) },
                    enabled = draft.baseUrl.startsWith("https://") && draft.modelName.isNotBlank() && (draft.apiKey.isNotBlank() || draft.id != null)
                ) { Text("测试") }
                Button(
                    onClick = { onSave(draft) },
                    enabled = draft.name.isNotBlank() && draft.baseUrl.startsWith("https://") && draft.modelName.isNotBlank() && (draft.apiKey.isNotBlank() || draft.id != null)
                ) { Text("保存") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun ModelConfig.toDraft() = ModelConfigDraft(
    id = id,
    name = name,
    provider = provider,
    baseUrl = baseUrl,
    modelName = modelName,
    apiKey = "",
    customHeadersJson = customHeadersJson,
    systemPrompt = systemPrompt,
    temperature = temperature
)

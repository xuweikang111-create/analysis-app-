package com.lobsterai.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                actions = { IconButton(onClick = { editor = ModelConfigDraft() }) { Icon(Icons.Outlined.Add, contentDescription = "添加模型") } }
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
                            headlineContent = { Text("深色模式") },
                            supportingContent = { Text("使用 Material 3 深色配色") },
                            trailingContent = { Switch(state.darkMode, viewModel::setDarkMode) }
                        )
                        ListItem(
                            headlineContent = { Text("剪贴板 URL 识别") },
                            supportingContent = { Text("仅在 APP 回到前台时检查剪贴板，不后台监听") },
                            trailingContent = { Switch(state.clipboardDetection, viewModel::setClipboardDetection) }
                        )
                    }
                }
            }
            item {
                Text("模型配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("API Key 不写入数据库，使用 Android Keystore AES-GCM 加密后本地保存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.configs.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Key, contentDescription = null)
                            Text("还没有模型配置", fontWeight = FontWeight.SemiBold)
                            Text("添加 OpenAI 兼容、Claude 或 Gemini 配置后即可聊天。")
                            Button(onClick = { editor = ModelConfigDraft() }) { Text("添加模型") }
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
                        supportingContent = {
                            Text("${config.provider.name} · ${config.modelName}\n${config.baseUrl}")
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.selectModel(config.id) }) { Icon(Icons.Outlined.Science, contentDescription = "设为当前") }
                                IconButton(onClick = { viewModel.test(config) }) { Icon(Icons.Outlined.Key, contentDescription = "测试") }
                                IconButton(onClick = { editor = config.toDraft() }) { Icon(Icons.Outlined.Edit, contentDescription = "编辑") }
                                IconButton(onClick = { viewModel.delete(config) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除") }
                            }
                        }
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("兼容说明", fontWeight = FontWeight.SemiBold)
                        Text("OpenAI、DeepSeek、Kimi、Qwen、GLM 以及第三方中转站可使用 OpenAI Compatible；Claude 与 Gemini 也支持各自原生协议。自定义 Header 使用 JSON 对象格式。")
                    }
                }
            }
        }
    }

    editor?.let { draft ->
        ModelEditorDialog(
            initial = draft,
            onDismiss = { editor = null },
            onSave = { viewModel.save(it); editor = null },
            onTest = viewModel::testDraft
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelEditorDialog(
    initial: ModelConfigDraft,
    onDismiss: () -> Unit,
    onSave: (ModelConfigDraft) -> Unit,
    onTest: (ModelConfigDraft) -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var providerMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "添加模型" else "编辑模型") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("厂商预设", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(BuiltInModelPresets, key = { it.label }) { preset ->
                                AssistChip(
                                    onClick = {
                                        draft = draft.copy(
                                            name = if (draft.name.isBlank()) preset.label else draft.name,
                                            provider = preset.provider,
                                            baseUrl = preset.baseUrl
                                        )
                                    },
                                    label = { Text(preset.label) }
                                )
                            }
                        }
                        Text("预设只填写协议与官方 Base URL；Model Name 仍由你指定，避免内置过时模型名。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    ExposedDropdownMenuBox(expanded = providerMenu, onExpandedChange = { providerMenu = it }) {
                        OutlinedTextField(
                            value = draft.provider.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("协议") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenu) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                            ProviderType.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.name) },
                                    onClick = {
                                        draft = draft.copy(
                                            provider = provider,
                                            baseUrl = when (provider) {
                                                ProviderType.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
                                                ProviderType.ANTHROPIC -> "https://api.anthropic.com/v1"
                                                ProviderType.GEMINI -> "https://generativelanguage.googleapis.com"
                                            }
                                        )
                                        providerMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                item { OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text("配置名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(draft.baseUrl, { draft = draft.copy(baseUrl = it) }, label = { Text("API Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(draft.modelName, { draft = draft.copy(modelName = it) }, label = { Text("Model Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    OutlinedTextField(
                        draft.apiKey,
                        { draft = draft.copy(apiKey = it) },
                        label = { Text(if (draft.id == null) "API Key" else "API Key（留空保持原 Key）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { OutlinedTextField(draft.customHeadersJson, { draft = draft.copy(customHeadersJson = it) }, label = { Text("自定义 Header JSON") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(draft.systemPrompt, { draft = draft.copy(systemPrompt = it) }, label = { Text("系统 Prompt") }, minLines = 3, maxLines = 7, modifier = Modifier.fillMaxWidth()) }
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
                TextButton(onClick = { onTest(draft) }, enabled = draft.baseUrl.startsWith("https://") && draft.modelName.isNotBlank() && (draft.apiKey.isNotBlank() || draft.id != null)) { Text("测试") }
                Button(onClick = { onSave(draft) }, enabled = draft.name.isNotBlank() && draft.baseUrl.startsWith("https://") && draft.modelName.isNotBlank() && (draft.apiKey.isNotBlank() || draft.id != null)) { Text("保存") }
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
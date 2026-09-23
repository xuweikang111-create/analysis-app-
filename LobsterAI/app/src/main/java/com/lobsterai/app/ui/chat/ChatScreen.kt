package com.lobsterai.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lobsterai.app.domain.model.ChatRole
import com.lobsterai.app.domain.model.Message
import com.lobsterai.app.ui.components.MarkdownContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var conversationMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Message?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val text = viewModel.exportConversation()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            viewModel.importConversation(text)
        }
    }

    LaunchedEffect(state.messages.size, state.streamingText.length) {
        val count = state.messages.size + if (state.streamingText.isNotBlank()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.lobster?.name?.let { "与 $it 对话" } ?: "AI 对话", fontWeight = FontWeight.Bold)
                        Text(
                            state.selectedConfig?.let { "${it.name} · ${it.modelName}" } ?: "还没有配置模型",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::newConversation) {
                        Icon(Icons.Outlined.Add, contentDescription = "新会话")
                    }
                    Box {
                        IconButton(onClick = { overflowMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = overflowMenu, onDismissRequest = { overflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("保存到知识库") },
                                leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null) },
                                enabled = state.messages.isNotEmpty(),
                                onClick = {
                                    overflowMenu = false
                                    viewModel.saveConversationToKnowledge()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导入会话") },
                                leadingIcon = { Icon(Icons.Outlined.Upload, contentDescription = null) },
                                onClick = {
                                    overflowMenu = false
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出会话") },
                                leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                                enabled = state.selectedConversationId != null,
                                onClick = {
                                    overflowMenu = false
                                    exportLauncher.launch("lobster-chat.json")
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            ConversationToolbar(
                conversationTitle = state.conversations.firstOrNull { it.id == state.selectedConversationId }?.title ?: "新会话",
                conversations = state.conversations.map { it.id to it.title },
                selectedModel = state.selectedConfig?.name ?: "选择模型",
                models = state.configs.map { it.id to it.name },
                tokenCount = state.messages.sumOf { it.inputTokens + it.outputTokens },
                conversationMenu = conversationMenu,
                modelMenu = modelMenu,
                onConversationMenuChange = { conversationMenu = it },
                onModelMenuChange = { modelMenu = it },
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                    conversationMenu = false
                },
                onDeleteConversation = { id ->
                    state.conversations.firstOrNull { it.id == id }?.let(viewModel::deleteConversation)
                },
                onSelectModel = { id ->
                    viewModel.selectModel(id)
                    modelMenu = false
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (state.messages.isEmpty() && state.streamingText.isBlank()) {
                    item {
                        EmptyConversation(
                            hasModel = state.configs.isNotEmpty(),
                            lobsterName = state.lobster?.name ?: "你的伙伴",
                            onOpenSettings = onOpenSettings,
                            onPrompt = { input = it }
                        )
                    }
                }

                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onEdit = { editTarget = message },
                        onDelete = { viewModel.deleteMessage(message) },
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                        }
                    )
                }

                if (state.streamingText.isNotBlank()) {
                    item("streaming") {
                        AssistantMessageSurface(text = state.streamingText, streaming = true)
                    }
                }
            }

            if (state.messages.any { it.role == ChatRole.ASSISTANT } && !state.generating) {
                TextButton(onClick = viewModel::regenerate, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("重新生成")
                }
            }

            MessageComposer(
                value = input,
                generating = state.generating,
                enabled = state.configs.isNotEmpty(),
                onValueChange = { input = it },
                onSend = {
                    if (state.generating) {
                        viewModel.stop()
                    } else {
                        viewModel.send(input)
                        input = ""
                    }
                },
                onOpenSettings = onOpenSettings
            )
        }
    }

    editTarget?.let { message ->
        var editText by remember(message.id) { mutableStateOf(message.content) }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("编辑消息") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    minLines = 4,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.editMessage(message, editText)
                    editTarget = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun ConversationToolbar(
    conversationTitle: String,
    conversations: List<Pair<Long, String>>,
    selectedModel: String,
    models: List<Pair<Long, String>>,
    tokenCount: Int,
    conversationMenu: Boolean,
    modelMenu: Boolean,
    onConversationMenuChange: (Boolean) -> Unit,
    onModelMenuChange: (Boolean) -> Unit,
    onSelectConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onSelectModel: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AssistChip(onClick = { onConversationMenuChange(true) }, label = { Text(conversationTitle, maxLines = 1) })
            DropdownMenu(expanded = conversationMenu, onDismissRequest = { onConversationMenuChange(false) }) {
                if (conversations.isEmpty()) {
                    DropdownMenuItem(text = { Text("暂无历史会话") }, enabled = false, onClick = {})
                }
                conversations.forEach { (id, title) ->
                    DropdownMenuItem(
                        text = { Text(title, maxLines = 1) },
                        onClick = { onSelectConversation(id) },
                        trailingIcon = {
                            IconButton(onClick = { onDeleteConversation(id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除会话")
                            }
                        }
                    )
                }
            }
        }

        Box {
            AssistChip(onClick = { onModelMenuChange(true) }, label = { Text(selectedModel, maxLines = 1) })
            DropdownMenu(expanded = modelMenu, onDismissRequest = { onModelMenuChange(false) }) {
                if (models.isEmpty()) {
                    DropdownMenuItem(text = { Text("请先配置模型") }, enabled = false, onClick = {})
                }
                models.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { onSelectModel(id) })
                }
            }
        }

        Text(
            "≈ $tokenCount tok",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyConversation(
    hasModel: Boolean,
    lobsterName: String,
    onOpenSettings: () -> Unit,
    onPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Text("🦞", modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.headlineMedium)
        }
        Text("和 $lobsterName 开始工作", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            if (hasModel) "直接提问，或从下面选一个开始。" else "先配置一个模型，就可以开始流式对话。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!hasModel) {
            OutlinedButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("配置模型")
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    listOf(
                        "帮我规划今天最重要的三件事",
                        "总结我刚分享的网页",
                        "把这个问题拆成可执行步骤"
                    )
                ) { prompt ->
                    AssistChip(onClick = { onPrompt(prompt) }, label = { Text(prompt) })
                }
            }
        }
    }
}

@Composable
private fun MessageComposer(
    value: String,
    generating: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (!enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("未配置 AI 模型", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onOpenSettings) { Text("去设置") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text("给你的 AI 伙伴发消息…") },
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 7,
                    shape = RoundedCornerShape(24.dp),
                    enabled = enabled || generating
                )
                FilledIconButton(
                    onClick = onSend,
                    enabled = generating || (enabled && value.isNotBlank()),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        if (generating) Icons.Outlined.Stop else Icons.Outlined.Send,
                        contentDescription = if (generating) "停止" else "发送"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    val isUser = message.role == ChatRole.USER
    if (!isUser) {
        AssistantMessageSurface(
            text = message.content,
            streaming = false,
            footer = {
                MessageFooter(
                    label = "assistant · ${message.inputTokens + message.outputTokens} tok",
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            },
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onCopy)
        )
        return
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .combinedClickable(onClick = {}, onLongClick = onCopy),
            shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkdownContent(message.content)
                MessageFooter(
                    label = "you · ${message.inputTokens + message.outputTokens} tok",
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageSurface(
    text: String,
    streaming: Boolean,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(30.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("🦞", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.size(9.dp))
        Surface(
            modifier = modifier.weight(1f),
            shape = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
            color = Color.Transparent
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkdownContent(text)
                if (streaming) {
                    Text("正在生成…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                footer?.invoke()
            }
        }
    }
}

@Composable
private fun MessageFooter(
    label: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = "编辑", modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", modifier = Modifier.size(17.dp))
        }
    }
}
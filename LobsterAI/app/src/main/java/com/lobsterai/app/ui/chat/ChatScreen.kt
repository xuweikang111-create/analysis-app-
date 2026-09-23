package com.lobsterai.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lobsterai.app.domain.model.ChatRole
import com.lobsterai.app.domain.model.Message
import com.lobsterai.app.ui.components.MarkdownContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var conversationMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Message?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selectedImage = uri
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            viewModel.importConversation(text)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = viewModel.exportConversation()
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
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
    LaunchedEffect(state.visionEnabled) {
        if (!state.visionEnabled) selectedImage = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("米奇", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${state.lobster?.name ?: "AI 伙伴"} · ${state.selectedConfig?.name ?: "尚未配置模型"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.selectedConfig == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::newConversation) {
                        Icon(Icons.Outlined.Add, contentDescription = "新会话")
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("保存完整会话到智识库") },
                                leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, null) },
                                enabled = state.messages.isNotEmpty(),
                                onClick = { moreMenu = false; viewModel.saveConversationToKnowledge() }
                            )
                            DropdownMenuItem(
                                text = { Text("导入会话") },
                                leadingIcon = { Icon(Icons.Outlined.Upload, null) },
                                onClick = { moreMenu = false; importLauncher.launch(arrayOf("application/json", "text/plain")) }
                            )
                            DropdownMenuItem(
                                text = { Text("导出会话") },
                                leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                enabled = state.selectedConversationId != null,
                                onClick = { moreMenu = false; exportLauncher.launch("miqi-chat.json") }
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { conversationMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(state.conversations.firstOrNull { it.id == state.selectedConversationId }?.title ?: "新会话", maxLines = 1)
                    }
                    DropdownMenu(expanded = conversationMenu, onDismissRequest = { conversationMenu = false }) {
                        if (state.conversations.isEmpty()) {
                            DropdownMenuItem(text = { Text("暂无历史会话") }, enabled = false, onClick = {})
                        } else {
                            state.conversations.forEach { conversation ->
                                DropdownMenuItem(
                                    text = { Text(conversation.title, maxLines = 1) },
                                    onClick = { viewModel.selectConversation(conversation.id); conversationMenu = false }
                                )
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { if (state.configs.isEmpty()) onOpenSettings() else modelMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(state.selectedConfig?.name ?: "配置模型", maxLines = 1)
                    }
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        state.configs.forEach { config ->
                            DropdownMenuItem(
                                text = { Text(config.name, maxLines = 1) },
                                onClick = { viewModel.selectModel(config.id); modelMenu = false }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.thinkingEnabled,
                    onClick = { viewModel.setThinkingEnabled(!state.thinkingEnabled) },
                    label = { Text("思考") }
                )
                FilterChip(
                    selected = state.visionEnabled,
                    onClick = { viewModel.setVisionEnabled(!state.visionEnabled) },
                    label = { Text("视觉") }
                )
                FilterChip(
                    selected = state.autoKnowledgeEnabled,
                    onClick = { viewModel.setAutoKnowledgeEnabled(!state.autoKnowledgeEnabled) },
                    label = { Text("自动智识") }
                )
            }

            HorizontalDivider()

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.messages.isEmpty() && state.streamingText.isBlank()) {
                    item {
                        EmptyChatCard(
                            lobsterName = state.lobster?.name,
                            hasModel = state.selectedConfig != null,
                            visionEnabled = state.visionEnabled,
                            autoKnowledge = state.autoKnowledgeEnabled,
                            onOpenSettings = onOpenSettings
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
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.widthIn(max = 680.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                if (state.thinkingEnabled) {
                                    Text("思考模式", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                MarkdownContent(state.streamingText)
                            }
                        }
                    }
                }
            }

            if (state.messages.any { it.role == ChatRole.ASSISTANT } && !state.generating) {
                TextButton(onClick = viewModel::regenerate, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Outlined.Refresh, null)
                    Spacer(Modifier.size(6.dp))
                    Text("重新生成")
                }
            }

            selectedImage?.let { uri ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "待发送图片",
                            modifier = Modifier.size(72.dp),
                            contentScale = ContentScale.Crop
                        )
                        Column(Modifier.weight(1f)) {
                            Text("图片已附加", fontWeight = FontWeight.SemiBold)
                            Text("将使用当前模型视觉能力", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { selectedImage = null }) {
                            Icon(Icons.Outlined.Close, contentDescription = "移除图片")
                        }
                    }
                }
            }

            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { imageLauncher.launch(arrayOf("image/*")) },
                        enabled = state.visionEnabled && !state.generating
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = "添加图片")
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(if (state.selectedConfig == null) "先配置模型" else "问米奇任何问题…") },
                        enabled = !state.generating,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        minLines = 1,
                        maxLines = 6,
                        shape = RoundedCornerShape(20.dp)
                    )
                    FilledIconButton(
                        onClick = {
                            when {
                                state.generating -> viewModel.stop()
                                state.selectedConfig == null -> onOpenSettings()
                                else -> {
                                    viewModel.send(input, selectedImage?.toString())
                                    input = ""
                                    selectedImage = null
                                }
                            }
                        },
                        enabled = state.generating || state.selectedConfig == null || input.isNotBlank() || selectedImage != null
                    ) {
                        Icon(
                            when {
                                state.generating -> Icons.Outlined.Stop
                                state.selectedConfig == null -> Icons.Outlined.Settings
                                else -> Icons.Outlined.Send
                            },
                            contentDescription = null
                        )
                    }
                }
            }
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
                    maxLines = 12
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
private fun EmptyChatCard(
    lobsterName: String?,
    hasModel: Boolean,
    visionEnabled: Boolean,
    autoKnowledge: Boolean,
    onOpenSettings: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                if (lobsterName == null) "开始一段新对话" else "和 $lobsterName 开始聊天",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text("米奇会保留上下文，并把长期聊天自动沉淀为本地智识。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(if (visionEnabled) "视觉已开启" else "视觉已关闭") })
                AssistChip(onClick = {}, label = { Text(if (autoKnowledge) "自动智识开启" else "自动智识暂停") })
            }
            if (!hasModel) {
                Button(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, null)
                    Spacer(Modifier.size(6.dp))
                    Text("配置 AI 模型")
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 680.dp).combinedClickable(onClick = {}, onLongClick = onCopy),
            shape = RoundedCornerShape(18.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message.imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "聊天图片",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                MarkdownContent(message.content)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (isUser) "你" else "米奇"} · ${message.inputTokens + message.outputTokens} tok",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Edit, "编辑")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, "删除")
                    }
                }
            }
        }
    }
}

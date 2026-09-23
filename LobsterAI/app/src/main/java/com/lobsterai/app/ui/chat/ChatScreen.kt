package com.lobsterai.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.DisposableEffect
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenDrawer: () -> Unit = {},
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
    var modelMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Message?>(null) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.SIMPLIFIED_CHINESE
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts = null
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedImage = uri
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.attachDocument(uri)
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                input = listOf(input.trim(), spoken.trim())
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            viewModel.importConversation(text)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = viewModel.exportConversation()
                context.contentResolver.openOutputStream(uri)
                    ?.bufferedWriter()
                    ?.use { it.write(text) }
            }
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
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = "菜单")
                    }
                },
                title = {
                    Column {
                        Text("米奇", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                state.privacyMode -> "临时隐私会话 · 不保存"
                                state.selectedConfig == null -> "尚未配置模型"
                                else -> "${state.selectedConfig?.name} · ${state.responseStyle}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.selectedConfig == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                actions = {
                    Box {
                        TextButton(
                            onClick = {
                                if (state.configs.isEmpty()) onOpenSettings() else modelMenu = true
                            }
                        ) {
                            Text(state.selectedConfig?.modelName?.take(18) ?: "选择模型")
                        }
                        DropdownMenu(
                            expanded = modelMenu,
                            onDismissRequest = { modelMenu = false }
                        ) {
                            state.configs.forEach { config ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(config.name)
                                            Text(
                                                config.modelName,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectModel(config.id)
                                        modelMenu = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = viewModel::newConversation) {
                        Icon(Icons.Outlined.Add, contentDescription = "新对话")
                    }

                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = moreMenu,
                            onDismissRequest = { moreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("保存会话到智识库") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
                                },
                                enabled = state.messages.isNotEmpty() && !state.privacyMode,
                                onClick = {
                                    moreMenu = false
                                    viewModel.saveConversationToKnowledge()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导入会话") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Upload, contentDescription = null)
                                },
                                enabled = !state.privacyMode,
                                onClick = {
                                    moreMenu = false
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出当前会话") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Download, contentDescription = null)
                                },
                                enabled = state.messages.isNotEmpty(),
                                onClick = {
                                    moreMenu = false
                                    exportLauncher.launch("miqi-chat.json")
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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.messages.isEmpty() && state.streamingText.isBlank()) {
                    item {
                        EmptyChat(
                            hasModel = state.selectedConfig != null,
                            privacyMode = state.privacyMode,
                            autoKnowledge = state.autoKnowledgeEnabled,
                            onOpenSettings = onOpenSettings
                        )
                    }
                }

                items(
                    items = state.messages,
                    key = { message ->
                        if (message.id != 0L) message.id else message.createdAt
                    }
                ) { message ->
                    MessageBubble(
                        message = message,
                        onEdit = { editTarget = message },
                        onDelete = { viewModel.deleteMessage(message) },
                        onCopy = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("米奇消息", message.content)
                            )
                        },
                        onSpeak = {
                            tts?.speak(
                                message.content,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "miqi-${message.createdAt}"
                            )
                        },
                        onShare = {
                            shareText(context, message.content)
                        }
                    )
                }

                if (state.streamingText.isNotBlank()) {
                    item("streaming") {
                        AssistantSurface {
                            if (state.thinkingEnabled) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "思考模式",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            MarkdownContent(state.streamingText)
                        }
                    }
                }
            }

            state.pendingDocument?.let { document ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text(document.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${document.text.length} 字符已提取",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = viewModel::clearPendingDocument) {
                            Icon(Icons.Outlined.Close, contentDescription = "移除附件")
                        }
                    }
                }
            }

            selectedImage?.let { uri ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "待发送图片",
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Crop
                        )
                        Text("图片已附加", modifier = Modifier.weight(1f))
                        IconButton(onClick = { selectedImage = null }) {
                            Icon(Icons.Outlined.Close, contentDescription = "移除图片")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.thinkingEnabled,
                    onClick = { viewModel.setThinkingEnabled(!state.thinkingEnabled) },
                    label = { Text("思考") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                )
                FilterChip(
                    selected = state.visionEnabled,
                    onClick = { viewModel.setVisionEnabled(!state.visionEnabled) },
                    label = { Text("视觉") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                )
                FilterChip(
                    selected = state.privacyMode,
                    onClick = { viewModel.setPrivacyMode(!state.privacyMode) },
                    label = { Text("隐私") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                )
                AssistChip(
                    onClick = { viewModel.setAutoKnowledgeEnabled(!state.autoKnowledgeEnabled) },
                    label = {
                        Text(if (state.autoKnowledgeEnabled) "智识开启" else "智识暂停")
                    }
                )
            }

            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box {
                        IconButton(onClick = { addMenu = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "添加")
                        }
                        DropdownMenu(
                            expanded = addMenu,
                            onDismissRequest = { addMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("图片") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Image, contentDescription = null)
                                },
                                enabled = state.visionEnabled && !state.generating,
                                onClick = {
                                    addMenu = false
                                    imageLauncher.launch(arrayOf("image/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("文件 / PDF / DOCX") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.AttachFile, contentDescription = null)
                                },
                                enabled = !state.generating && !state.documentBusy,
                                onClick = {
                                    addMenu = false
                                    documentLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "text/*",
                                            "application/json",
                                            "application/xml"
                                        )
                                    )
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = {
                            Text(
                                if (state.selectedConfig == null) {
                                    "先配置 API，再开始聊天"
                                } else {
                                    "问米奇任何问题…"
                                }
                            )
                        },
                        enabled = !state.generating,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                        minLines = 1,
                        maxLines = 7,
                        shape = RoundedCornerShape(22.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                        .putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                        )
                                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "说给米奇听")
                                    voiceLauncher.launch(intent)
                                },
                                enabled = !state.generating
                            ) {
                                Icon(Icons.Outlined.Mic, contentDescription = "语音输入")
                            }
                        }
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
                        enabled = state.generating ||
                            state.selectedConfig == null ||
                            input.isNotBlank() ||
                            selectedImage != null ||
                            state.pendingDocument != null
                    ) {
                        Icon(
                            imageVector = when {
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
        var editText by remember(message.createdAt) {
            mutableStateOf(message.content)
        }
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
                Button(
                    onClick = {
                        viewModel.editMessage(message, editText)
                        editTarget = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun EmptyChat(
    hasModel: Boolean,
    privacyMode: Boolean,
    autoKnowledge: Boolean,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, start = 18.dp, end = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "米奇",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (privacyMode) {
                "当前是临时隐私会话，消息、附件和智识都不会落库。"
            } else {
                "打开即聊。视觉、文件、语音和长期智识都在一个输入框里。"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text(if (autoKnowledge) "长期智识开启" else "长期智识暂停") }
            )
            AssistChip(
                onClick = {},
                label = { Text(if (privacyMode) "不保存" else "本地保存") }
            )
        }
        if (!hasModel) {
            Button(onClick = onOpenSettings) {
                Text("配置 API")
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
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onShare: () -> Unit
) {
    val user = message.role == ChatRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onCopy
                ),
            shape = RoundedCornerShape(20.dp),
            color = if (user) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                message.imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "聊天图片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                MarkdownContent(message.content)

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (user) "你" else "米奇",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.weight(1f))

                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "复制",
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    if (!user) {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.VolumeUp,
                                contentDescription = "朗读",
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "分享",
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "删除",
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantSurface(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.widthIn(max = 720.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(
        Intent.createChooser(intent, "分享米奇回答")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

package com.lobsterai.app.ui.knowledge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lobsterai.app.domain.model.KnowledgeItem
import com.lobsterai.app.domain.model.KnowledgeType
import com.lobsterai.app.domain.model.Memory
import com.lobsterai.app.domain.model.MemoryType
import com.lobsterai.app.ui.components.MarkdownContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    onOpenDrawer: () -> Unit = {},
    viewModel: KnowledgeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var url by remember { mutableStateOf("") }
    var noteDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<KnowledgeItem?>(null) }
    var selectedMemory by remember { mutableStateOf<Memory?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "导入文件"
            val content = runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
            }.getOrDefault("")
            viewModel.importFile(name, content)
        }
    }

    LaunchedEffect(state.incomingText) {
        if (state.incomingText.isNotBlank()) {
            val incomingUrl = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
                .find(state.incomingText)
                ?.value
            if (incomingUrl != null) {
                url = incomingUrl
            } else {
                viewModel.addNote("分享内容", state.incomingText)
            }
            viewModel.clearIncoming()
        }
    }

    LaunchedEffect(state.status) {
        state.status?.let {
            snackbar.showSnackbar(it)
            viewModel.clearStatus()
        }
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
                        Text("智识库", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${state.memories.size} 条长期记忆 · ${state.items.size} 份资料",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            fileLauncher.launch(
                                arrayOf(
                                    "text/*",
                                    "application/json",
                                    "text/html",
                                    "application/xml"
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = "导入文本文件")
                    }
                    IconButton(onClick = { noteDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建笔记")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "网页资料",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("粘贴网页链接") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { viewModel.fetch(url, save = true) },
                                enabled = url.isNotBlank() && !state.busy
                            ) {
                                Icon(Icons.Outlined.Language, contentDescription = null)
                                Text("抓取收藏")
                            }
                            Button(
                                onClick = { viewModel.summarizeUrl(url) },
                                enabled = url.isNotBlank() && !state.busy
                            ) {
                                Text("AI 总结")
                            }
                            if (state.busy) {
                                IconButton(onClick = viewModel::stopAi) {
                                    Icon(Icons.Outlined.Stop, contentDescription = "停止")
                                }
                            }
                        }
                        if (state.busy) CircularProgressIndicator()
                    }
                }
            }

            if (state.aiOutput.isNotBlank()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "AI 输出",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            MarkdownContent(state.aiOutput)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "长期记忆",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "由聊天自动提取，并参与本地 RAG 检索",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.memories.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearMemories) {
                            Text("清空")
                        }
                    }
                }
            }

            if (state.memories.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Outlined.AutoStories, contentDescription = null)
                            Text(
                                "继续和米奇聊天后，有长期价值的信息会自动沉淀在这里。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(
                items = state.memories,
                key = { "memory-${it.id}" }
            ) { memory ->
                Card(
                    onClick = { selectedMemory = memory },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.AutoStories, contentDescription = null)
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(memory.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${memory.type.displayName()} · 重要度 ${memory.importance}/5",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                memory.content,
                                maxLines = 3,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.deleteMemory(memory) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除")
                        }
                    }
                }
            }

            item {
                Text(
                    "资料库",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (state.items.isEmpty()) {
                item {
                    Text(
                        "网页、笔记、文件和手动保存的聊天会出现在这里。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(
                items = state.items,
                key = { "item-${it.id}" }
            ) { item ->
                Card(
                    onClick = { selectedItem = item },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (item.type == KnowledgeType.WEB) {
                                Icons.Outlined.Language
                            } else {
                                Icons.Outlined.Description
                            },
                            contentDescription = null
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(item.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                item.type.displayName(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                item.content.take(180),
                                maxLines = 3,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.delete(item) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }

    if (noteDialog) {
        var title by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteDialog = false },
            title = { Text("新建笔记") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("标题") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("内容") },
                        minLines = 6,
                        maxLines = 14
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addNote(title, note)
                        noteDialog = false
                    },
                    enabled = note.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    selectedItem?.let { item ->
        DetailDialog(
            title = item.title,
            content = item.content,
            onDismiss = { selectedItem = null },
            onAsk = { question ->
                viewModel.ask(item, question)
                selectedItem = null
            }
        )
    }

    selectedMemory?.let { memory ->
        DetailDialog(
            title = memory.title,
            content = memory.content,
            onDismiss = { selectedMemory = null },
            onAsk = { question ->
                viewModel.askMemory(memory, question)
                selectedMemory = null
            }
        )
    }
}

@Composable
private fun DetailDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit,
    onAsk: (String) -> Unit
) {
    var question by remember(title) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(content.take(12_000))
                }
                item {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("基于这条智识提问") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAsk(question) },
                enabled = question.isNotBlank()
            ) {
                Text("向 AI 提问")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

private fun KnowledgeType.displayName(): String = when (this) {
    KnowledgeType.MEMORY -> "旧版记忆"
    KnowledgeType.WEB -> "网页"
    KnowledgeType.NOTE -> "笔记"
    KnowledgeType.FILE -> "文件"
    KnowledgeType.CHAT -> "聊天记录"
}

private fun MemoryType.displayName(): String = when (this) {
    MemoryType.PROFILE -> "用户资料"
    MemoryType.PREFERENCE -> "偏好"
    MemoryType.PROJECT -> "项目"
    MemoryType.DECISION -> "决定"
    MemoryType.GOAL -> "目标"
    MemoryType.FACT -> "事实"
    MemoryType.TODO -> "待办"
    MemoryType.RELATIONSHIP -> "关系"
    MemoryType.OTHER -> "其他"
}

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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
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
import com.lobsterai.app.ui.components.MarkdownContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(viewModel: KnowledgeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var url by remember { mutableStateOf("") }
    var noteDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<KnowledgeItem?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "导入文件"
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            viewModel.importFile(name, content)
        }
    }

    LaunchedEffect(state.incomingText) {
        if (state.incomingText.isNotBlank()) {
            val incomingUrl = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(state.incomingText)?.value
            if (incomingUrl != null) url = incomingUrl else {
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
                title = { Text("知识库", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { fileLauncher.launch(arrayOf("text/*", "application/json", "text/html", "application/xml")) }) { Icon(Icons.Outlined.UploadFile, contentDescription = "导入文件") }
                    IconButton(onClick = { noteDialog = true }) { Icon(Icons.Outlined.Add, contentDescription = "新建笔记") }
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("网页处理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("粘贴微信 / QQ / 浏览器网页链接") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { viewModel.fetch(url, save = true) }, enabled = url.isNotBlank() && !state.busy) {
                                Icon(Icons.Outlined.Language, contentDescription = null)
                                Text("抓取并收藏")
                            }
                            Button(onClick = { viewModel.summarizeUrl(url) }, enabled = url.isNotBlank() && !state.busy) { Text("AI 总结") }
                            if (state.busy) {
                                IconButton(onClick = viewModel::stopAi) { Icon(Icons.Outlined.Stop, contentDescription = "停止") }
                            }
                        }
                        if (state.busy) CircularProgressIndicator()
                        state.currentWeb?.let { page ->
                            Text(page.title, fontWeight = FontWeight.SemiBold)
                            if (page.description.isNotBlank()) Text(page.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("已提取正文 ${page.text.length} 字符", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (state.aiOutput.isNotBlank()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("AI 输出", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            MarkdownContent(state.aiOutput)
                        }
                    }
                }
            }

            item { Text("本地知识", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (state.items.isEmpty()) {
                item { Text("还没有收藏。网页、笔记、文本文件和聊天资料都会保存在本机。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.items, key = { it.id }) { item ->
                Card(onClick = { selected = item }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (item.type == KnowledgeType.WEB) Icons.Outlined.Language else Icons.Outlined.Description, contentDescription = null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold)
                            Text(item.type.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(item.content.take(160), maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.delete(item) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除") }
                    }
                }
            }
        }
    }

    if (noteDialog) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteDialog = false },
            title = { Text("新建文本笔记") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true)
                    OutlinedTextField(content, { content = it }, label = { Text("内容") }, minLines = 6, maxLines = 14)
                }
            },
            confirmButton = { Button(onClick = { viewModel.addNote(title, content); noteDialog = false }, enabled = content.isNotBlank()) { Text("保存") } },
            dismissButton = { TextButton(onClick = { noteDialog = false }) { Text("取消") } }
        )
    }

    selected?.let { item ->
        KnowledgeDetailDialog(knowledgeItem = item, onDismiss = { selected = null }, onAsk = { question -> viewModel.ask(item, question) })
    }
}

@Composable
private fun KnowledgeDetailDialog(
    knowledgeItem: KnowledgeItem,
    onDismiss: () -> Unit,
    onAsk: (String) -> Unit
) {
    var question by remember(knowledgeItem.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(knowledgeItem.title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(knowledgeItem.content.take(12_000)) }
                item { OutlinedTextField(question, { question = it }, label = { Text("基于此内容提问") }, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = { Button(onClick = { onAsk(question); onDismiss() }, enabled = question.isNotBlank()) { Text("向 AI 提问") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
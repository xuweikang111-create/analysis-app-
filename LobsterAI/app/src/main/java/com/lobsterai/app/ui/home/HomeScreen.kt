package com.lobsterai.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lobsterai.app.domain.model.Lobster
import com.lobsterai.app.ui.components.LobsterAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenChat: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val daily by viewModel.daily.collectAsStateWithLifecycle()
    var addDialog by remember { mutableStateOf(false) }
    var editPrompt by remember { mutableStateOf(false) }
    val lobster = state.selected

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("养龙虾 AI", fontWeight = FontWeight.SemiBold)
                        Text(
                            "你的本地 AI 伙伴",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { addDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新增龙虾")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.lobsters, key = { it.id }) { item ->
                        AssistChip(
                            onClick = { viewModel.select(item.id) },
                            label = { Text(if (item.id == lobster?.id) "${item.name} · 当前" else item.name) }
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LobsterAvatar(
                            modifier = Modifier
                                .fillMaxWidth(0.48f)
                                .aspectRatio(1f)
                        )
                        Text(
                            lobster?.name ?: "正在唤醒…",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Lv.${lobster?.level ?: 1}  ·  亲密度 ${lobster?.intimacy ?: 0}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Button(
                            onClick = onOpenChat,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("开始聊天")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { editPrompt = true },
                                modifier = Modifier.weight(1f),
                                enabled = lobster != null
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("编辑人设")
                            }
                            FilledTonalButton(
                                onClick = viewModel::deleteSelected,
                                modifier = Modifier.weight(1f),
                                enabled = state.lobsters.size > 1
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("删除角色")
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("伙伴状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "今日 ${daily.completedCount}/4",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        val expProgress = lobster?.let(::experienceProgress) ?: 0
                        StatusBar("经验", lobster?.experience ?: 0, expProgress)
                        StatusBar("心情", lobster?.mood ?: 0, lobster?.mood ?: 0)
                        StatusBar("饱食度", lobster?.satiety ?: 0, lobster?.satiety ?: 0)
                        StatusBar("亲密度", lobster?.intimacy ?: 0, (lobster?.intimacy ?: 0).coerceAtMost(100))
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("今日互动", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.perform("checkin") },
                                modifier = Modifier.weight(1f),
                                enabled = !daily.checkedIn && !state.busy
                            ) { Text(if (daily.checkedIn) "已签到" else "签到") }
                            FilledTonalButton(
                                onClick = { viewModel.perform("feed") },
                                modifier = Modifier.weight(1f),
                                enabled = !state.busy
                            ) { Text(if (daily.fed) "再喂食" else "喂食") }
                            FilledTonalButton(
                                onClick = { viewModel.perform("interact") },
                                modifier = Modifier.weight(1f),
                                enabled = !state.busy
                            ) { Text(if (daily.interacted) "再互动" else "互动") }
                        }
                        Text(
                            if (daily.chatted) "聊天任务已完成" else "发送一条消息即可完成今日聊天任务",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (daily.chatted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.busy) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("角色 Prompt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            lobster?.prompt?.ifBlank { "未设置" } ?: "未设置",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (addDialog) {
        LobsterEditorDialog(
            title = "创建新龙虾",
            initialName = "",
            initialPrompt = "你是一只成熟、可靠、自然的 AI 龙虾伙伴。",
            onDismiss = { addDialog = false },
            onSave = { name, prompt ->
                viewModel.addLobster(name, prompt)
                addDialog = false
            }
        )
    }

    if (editPrompt && lobster != null) {
        LobsterEditorDialog(
            title = "编辑角色 Prompt",
            initialName = lobster.name,
            initialPrompt = lobster.prompt,
            nameEnabled = false,
            onDismiss = { editPrompt = false },
            onSave = { _, prompt ->
                viewModel.updatePrompt(prompt)
                editPrompt = false
            }
        )
    }
}

@Composable
private fun StatusBar(label: String, valueText: Int, progress: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Text(valueText.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LobsterEditorDialog(
    title: String,
    initialName: String,
    initialPrompt: String,
    nameEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var prompt by remember(initialPrompt) { mutableStateOf(initialPrompt) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = nameEnabled,
                    label = { Text("名称") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("角色 Prompt") },
                    minLines = 4,
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, prompt) },
                enabled = name.isNotBlank() || !nameEnabled
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun experienceProgress(lobster: Lobster): Int {
    val completedLevels = (lobster.level - 1).coerceAtLeast(0)
    val levelStartExp = completedLevels * 100 + 20 * completedLevels * (completedLevels - 1)
    val levelNeed = 100 + completedLevels * 40
    val inLevel = (lobster.experience - levelStartExp).coerceAtLeast(0)
    return ((inLevel.toDouble() / levelNeed.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)
}

package com.lobsterai.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lobsterai.app.domain.model.Lobster
import com.lobsterai.app.ui.components.LobsterAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
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
                        Text("养龙虾 AI", fontWeight = FontWeight.Bold)
                        Text(
                            "你的本地 AI 伙伴",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { addDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新增龙虾")
                    }
                    IconButton(onClick = { editPrompt = true }, enabled = lobster != null) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑角色")
                    }
                    IconButton(onClick = viewModel::deleteSelected, enabled = state.lobsters.size > 1) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.lobsters.size > 1) {
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
            }

            item {
                CompanionHero(lobster = lobster)
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onOpenChat, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("开始对话")
                    }
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("模型设置")
                    }
                }
            }

            item {
                Text("伙伴状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("等级", "Lv.${lobster?.level ?: 1}", Modifier.weight(1f))
                    StatTile("心情", "${lobster?.mood ?: 0}%", Modifier.weight(1f))
                    StatTile("饱食", "${lobster?.satiety ?: 0}%", Modifier.weight(1f))
                    StatTile("亲密", "${lobster?.intimacy ?: 0}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusBar("本级经验", lobster?.experience ?: 0, lobster?.let(::experienceProgress) ?: 0)
                        StatusBar("心情", lobster?.mood ?: 0, lobster?.mood ?: 0)
                        StatusBar("饱食度", lobster?.satiety ?: 0, lobster?.satiety ?: 0)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("今日养成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("完成 ${daily.completedCount}/4", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("${daily.completedCount}/4", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DailyAction(
                        label = if (daily.checkedIn) "已签到" else "签到",
                        icon = Icons.Outlined.CheckCircleOutline,
                        enabled = !daily.checkedIn && !state.busy,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.perform("checkin") }
                    )
                    DailyAction(
                        label = if (daily.fed) "再喂食" else "喂食",
                        icon = Icons.Outlined.Restaurant,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.perform("feed") }
                    )
                    DailyAction(
                        label = if (daily.interacted) "再互动" else "互动",
                        icon = Icons.Outlined.TouchApp,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.perform("interact") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (daily.chatted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (daily.chatted) "✓ 今日聊天任务已完成" else "和伙伴聊一句，就能完成今日聊天任务",
                        modifier = Modifier.padding(14.dp),
                        color = if (daily.chatted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.busy) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("伙伴设定", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            lobster?.prompt?.ifBlank { "未设置角色 Prompt" } ?: "正在初始化伙伴…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { editPrompt = true }, enabled = lobster != null) {
                            Text("编辑 Identity / Prompt")
                        }
                    }
                }
            }
        }
    }

    if (addDialog) {
        LobsterEditorDialog(
            title = "创建新伙伴",
            initialName = "",
            initialPrompt = "你是一只成熟、可靠、克制的 AI 龙虾伙伴。回答准确、自然，不使用廉价卖萌语气。",
            onDismiss = { addDialog = false },
            onSave = { name, prompt ->
                viewModel.addLobster(name, prompt)
                addDialog = false
            }
        )
    }

    if (editPrompt && lobster != null) {
        LobsterEditorDialog(
            title = "编辑伙伴设定",
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
private fun CompanionHero(lobster: Lobster?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LobsterAvatar(modifier = Modifier.size(142.dp).aspectRatio(1f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
                        Text("● 本地伙伴在线", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(lobster?.name ?: "正在唤醒…", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Lv.${lobster?.level ?: 1} · 亲密度 ${lobster?.intimacy ?: 0}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "聊天、记忆、网页总结都留在同一个伙伴工作流里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DailyAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text(label)
        }
    }
}

@Composable
private fun StatusBar(label: String, valueText: Int, progress: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator(progress = { progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Identity / Prompt") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim(), prompt.trim()) }, enabled = name.isNotBlank() || !nameEnabled) {
                Text("保存")
            }
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
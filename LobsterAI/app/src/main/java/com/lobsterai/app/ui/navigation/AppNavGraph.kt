package com.lobsterai.app.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lobsterai.app.R
import com.lobsterai.app.ui.chat.ChatScreen
import com.lobsterai.app.ui.chat.ChatViewModel
import com.lobsterai.app.ui.home.HomeScreen
import com.lobsterai.app.ui.knowledge.KnowledgeScreen
import com.lobsterai.app.ui.settings.SettingsScreen
import com.lobsterai.app.ui.share.SharedContentManager
import com.lobsterai.app.ui.theme.LobsterAiTheme
import kotlinx.coroutines.launch

@Composable
fun LobsterApp(
    sharedContentManager: SharedContentManager,
    appViewModel: AppViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val dark by appViewModel.darkMode.collectAsStateWithLifecycle()
    val chatState by chatViewModel.state.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }

    LaunchedEffect(sharedContentManager) {
        sharedContentManager.incoming.collect {
            navController.navigate("knowledge") {
                launchSingleTop = true
            }
        }
    }

    LobsterAiTheme(darkTheme = dark) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.crow_icon),
                            contentDescription = "米奇",
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            "米奇",
                            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (chatState.privacyMode) "临时隐私会话" else "私人 AI 工作台",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    NavigationDrawerItem(
                        label = { Text("新对话") },
                        selected = false,
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        onClick = {
                            chatViewModel.newConversation()
                            navController.navigate("chat") {
                                launchSingleTop = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                        },
                        placeholder = { Text("搜索历史") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    val filtered = remember(chatState.conversations, search) {
                        val q = search.trim()
                        if (q.isBlank()) {
                            chatState.conversations
                        } else {
                            chatState.conversations.filter {
                                it.title.contains(q, ignoreCase = true)
                            }
                        }
                    }

                    Text(
                        "历史会话",
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        if (chatState.privacyMode) {
                            item {
                                NavigationDrawerItem(
                                    label = { Text("临时会话 · 不保存") },
                                    selected = true,
                                    icon = {
                                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                                    },
                                    onClick = {
                                        navController.navigate("chat") {
                                            launchSingleTop = true
                                        }
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }

                        items(
                            items = filtered,
                            key = { it.id }
                        ) { conversation ->
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        conversation.title,
                                        maxLines = 1
                                    )
                                },
                                selected = conversation.id == chatState.selectedConversationId,
                                icon = {
                                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                                },
                                onClick = {
                                    chatViewModel.selectConversation(conversation.id)
                                    navController.navigate("chat") {
                                        launchSingleTop = true
                                    }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }

                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))

                    NavigationDrawerItem(
                        label = { Text("智识库") },
                        selected = false,
                        icon = { Icon(Icons.Outlined.AutoStories, contentDescription = null) },
                        onClick = {
                            navController.navigate("knowledge") {
                                launchSingleTop = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavigationDrawerItem(
                        label = { Text("角色与成长") },
                        selected = false,
                        icon = { Icon(Icons.Outlined.Pets, contentDescription = null) },
                        onClick = {
                            navController.navigate("home") {
                                launchSingleTop = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavigationDrawerItem(
                        label = { Text("设置") },
                        selected = false,
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        onClick = {
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    TextButton(
                        onClick = {
                            chatViewModel.setPrivacyMode(!chatState.privacyMode)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            if (chatState.privacyMode) "退出隐私模式" else "进入临时隐私模式"
                        )
                    }
                }
            }
        ) {
            NavHost(
                navController = navController,
                startDestination = "chat"
            ) {
                composable("chat") {
                    ChatScreen(
                        onOpenDrawer = {
                            scope.launch { drawerState.open() }
                        },
                        onOpenSettings = {
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                        },
                        viewModel = chatViewModel
                    )
                }

                composable("knowledge") {
                    KnowledgeScreen(
                        onOpenDrawer = {
                            scope.launch { drawerState.open() }
                        }
                    )
                }

                composable("home") {
                    HomeScreen(
                        onOpenDrawer = {
                            scope.launch { drawerState.open() }
                        },
                        onOpenChat = {
                            navController.navigate("chat") {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        onOpenDrawer = {
                            scope.launch { drawerState.open() }
                        }
                    )
                }
            }
        }
    }
}

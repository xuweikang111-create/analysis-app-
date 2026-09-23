package com.lobsterai.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lobsterai.app.ui.chat.ChatScreen
import com.lobsterai.app.ui.home.HomeScreen
import com.lobsterai.app.ui.knowledge.KnowledgeScreen
import com.lobsterai.app.ui.settings.SettingsScreen
import com.lobsterai.app.ui.share.SharedContentManager
import com.lobsterai.app.ui.theme.LobsterAiTheme

private data class Tab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val tabs = listOf(
    Tab("home", "首页", Icons.Outlined.Home),
    Tab("chat", "聊天", Icons.Outlined.ChatBubbleOutline),
    Tab("knowledge", "知识库", Icons.Outlined.Storage),
    Tab("settings", "设置", Icons.Outlined.Settings)
)

@Composable
fun LobsterApp(
    sharedContentManager: SharedContentManager,
    viewModel: AppViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val dark by viewModel.darkMode.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination

    LaunchedEffect(sharedContentManager) {
        sharedContentManager.incoming.collect {
            navController.navigate("knowledge") {
                launchSingleTop = true
            }
        }
    }

    LobsterAiTheme(darkTheme = dark) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
            ) {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") { HomeScreen(onOpenChat = { navController.navigate("chat") { launchSingleTop = true } }) }
                    composable("chat") { ChatScreen(onOpenSettings = { navController.navigate("settings") { launchSingleTop = true } }) }
                    composable("knowledge") { KnowledgeScreen() }
                    composable("settings") { SettingsScreen() }
                }
            }
        }
    }
}

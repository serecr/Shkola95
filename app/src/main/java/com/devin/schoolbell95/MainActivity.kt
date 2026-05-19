package com.devin.schoolbell95

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.devin.schoolbell95.assistant.AssistantViewModel
import com.devin.schoolbell95.ui.screens.AssistantScreen
import com.devin.schoolbell95.ui.screens.BellsScreen
import com.devin.schoolbell95.ui.screens.HomeScreen
import com.devin.schoolbell95.ui.screens.NotificationsScreen
import com.devin.schoolbell95.ui.screens.SettingsScreen
import com.devin.schoolbell95.ui.screens.SubjectsScreen
import com.devin.schoolbell95.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private val assistantVm: AssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark by vm.darkMode.collectAsState()
            val accent by vm.accentColor.collectAsState()
            val dynamic by vm.dynamicColor.collectAsState()
            AppTheme(darkMode = dark, accentKey = accent, dynamicColor = dynamic) {
                App(vm, assistantVm)
            }
        }
    }
}

private data class NavItem(val title: String, val icon: ImageVector)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun App(vm: AppViewModel, assistantVm: AssistantViewModel) {
    val items = listOf(
        NavItem("Глав.", Icons.Filled.Home),
        NavItem("Звонки", Icons.Outlined.AccessTime),
        NavItem("Уроки", Icons.Filled.MenuBook),
        NavItem("Чат", Icons.Filled.AutoAwesome),
        NavItem("Увед.", Icons.Filled.Notifications),
        NavItem("Настр.", Icons.Filled.Settings),
    )
    val pagerState = rememberPagerState(initialPage = 0) { items.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        icon = { Icon(item.icon, null) },
                        label = { Text(item.title) },
                    )
                }
            }
        },
    ) { inner ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            beyondBoundsPageCount = 1,
        ) { page ->
            when (page) {
                0 -> HomeScreen(vm)
                1 -> BellsScreen(vm)
                2 -> SubjectsScreen(vm)
                3 -> AssistantScreen(assistantVm)
                4 -> NotificationsScreen(vm)
                5 -> SettingsScreen(vm)
            }
        }
    }

    // Keep page-changes (from swipes) in sync — nothing to do for the NavigationBar,
    // because selection is read directly from pagerState.currentPage above.
    LaunchedEffect(pagerState.currentPage) { /* no-op, here for future hooks */ }
}

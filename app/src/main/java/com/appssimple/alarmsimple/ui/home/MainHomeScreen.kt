package com.appssimple.alarmsimple.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appssimple.alarmsimple.ui.alarmlist.AlarmListScreen
import com.appssimple.alarmsimple.ui.history.SleepHistoryScreen
import com.appssimple.alarmsimple.ui.theme.BrandPrimary
import kotlinx.coroutines.launch

/**
 * Главный экран приложения с двумя вкладками и свайпом:
 * Страница 0: Будильники
 * Страница 1: Сон и аналитика (свайп вправо)
 */
@Composable
fun MainHomeScreen(
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSleepTracking: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSession: (Long) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A).copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                modifier = Modifier.height(64.dp)
            ) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    icon = {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = "Будильники",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            "Будильники",
                            fontSize = 12.sp,
                            fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandPrimary,
                        selectedTextColor = BrandPrimary,
                        indicatorColor = BrandPrimary.copy(alpha = 0.15f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    icon = {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = "Сон и аналитика",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            "Сон и аналитика",
                            fontSize = 12.sp,
                            fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandPrimary,
                        selectedTextColor = BrandPrimary,
                        indicatorColor = BrandPrimary.copy(alpha = 0.15f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            when (page) {
                0 -> {
                    AlarmListScreen(
                        onAddAlarm = onAddAlarm,
                        onEditAlarm = onEditAlarm,
                        onOpenSettings = onOpenSettings,
                        onOpenSleepTracking = onOpenSleepTracking,
                        onOpenHelp = onOpenHelp
                    )
                }
                1 -> {
                    SleepHistoryScreen(
                        onOpenSession = onOpenSession,
                        showBackButton = false
                    )
                }
            }
        }
    }
}

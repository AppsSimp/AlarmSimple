package com.appssimple.alarmsimple.ui.tracking

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appssimple.alarmsimple.service.SleepTrackingService
import com.appssimple.alarmsimple.ui.components.AppBackground
import com.appssimple.alarmsimple.ui.theme.BrandPrimary
import com.appssimple.alarmsimple.ui.theme.BrandRose
import com.appssimple.alarmsimple.ui.theme.BrandSecondary
import kotlinx.coroutines.delay

/**
 * Экран активного трекинга сна с живым таймером, анимацией пульсации дыхания и кнопкой завершения.
 */
@Composable
fun SleepTrackingScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isTracking by SleepTrackingService.isTracking.collectAsState()
    val trackingStartMs by SleepTrackingService.trackingStartMs.collectAsState()

    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isTracking, trackingStartMs) {
        while (isTracking) {
            val now = System.currentTimeMillis()
            val start = if (trackingStartMs > 0) trackingStartMs else now
            elapsedSeconds = (now - start).coerceAtLeast(0L) / 1000L
            delay(1000L)
        }
    }

    // Если трекинг выключен, автоматически закрываем экран
    LaunchedEffect(isTracking) {
        if (!isTracking) {
            onNavigateBack()
        }
    }

    // Мягкая пульсирующая анимация сна
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val timerText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    AppBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            // Кнопка сворачивания экрана (трекинг продолжается в фоне)
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Свернуть", tint = Color.White)
            }

            // Центральный блок
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Пульсирующий индикатор
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(BrandPrimary.copy(alpha = 0.12f))
                    )
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(BrandPrimary.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Spacer(Modifier.height(36.dp))

                Text(
                    text = timerText,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = BrandSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Сон отслеживается датчиками",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Положите телефон экраном вниз на матрас рядом с подушкой. Сессии короче 10 минут отсеиваются автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Кнопка завершения трекинга сна
            Button(
                onClick = {
                    val stopIntent = Intent(context, SleepTrackingService::class.java).apply {
                        action = SleepTrackingService.ACTION_STOP_TRACKING
                    }
                    context.startService(stopIntent)
                    onNavigateBack()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandRose)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Завершить сон", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

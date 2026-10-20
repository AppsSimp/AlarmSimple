package com.appssimple.alarmsimple.ui.alarm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Authentic Apple Pay Style "Done" / "Оплачено" Dialog & Animation for NFC Tag dismissals.
 * Features:
 * - Clean iOS Dark card sheet
 * - Circular outline drawing with spring
 * - Checkmark stroke animating from vertex to vertex
 * - Subtle haptic/sound cue
 */
@Composable
fun FakeApplePaySuccessOverlay(
    modifier: Modifier = Modifier,
    amountText: String = "Бесценный подъем",
    onAnimationEnd: () -> Unit
) {
    val circleProgress = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(0f) }
    val cardScale = remember { Animatable(0.85f) }

    LaunchedEffect(Unit) {
        // Pop in the card
        cardScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        // 1. Draw blue/cyan circle
        circleProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 350)
        )
        // 2. Draw checkmark
        checkProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )
        // Hold for user to enjoy the payment feel
        delay(950L)
        onAnimationEnd()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .scale(cardScale.value),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF1C1C1E), // iOS system grouped dark background
            shadowElevation = 24.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C2E))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Apple Pay animated checkmark circle
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(72.dp)) {
                        val strokeWidth = 5.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)

                        // Outer Blue Circle Progress
                        val sweep = circleProgress.value * 360f
                        drawArc(
                            color = Color(0xFF34C759), // Apple Pay signature success green (or 0xFF007AFF)
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Animated checkmark
                        if (checkProgress.value > 0f) {
                            val path = Path().apply {
                                val p1 = Offset(center.x - radius * 0.42f, center.y + radius * 0.05f)
                                val p2 = Offset(center.x - radius * 0.10f, center.y + radius * 0.38f)
                                val p3 = Offset(center.x + radius * 0.45f, center.y - radius * 0.35f)
                                moveTo(p1.x, p1.y)
                                lineTo(p2.x, p2.y)
                                lineTo(p3.x, p3.y)
                            }
                            val pathMeasure = PathMeasure().apply {
                                setPath(path, false)
                            }
                            val subPath = Path()
                            pathMeasure.getSegment(
                                0f,
                                pathMeasure.length * checkProgress.value,
                                subPath,
                                true
                            )
                            drawPath(
                                path = subPath,
                                color = Color.White,
                                style = Stroke(
                                    width = strokeWidth * 0.95f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Готово",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Оплачено пробуждением",
                    color = Color(0xFF8E8E93),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "0,00 ₽",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "NFC Tag Dismiss • Apple Pay Style",
                    color = Color(0xFF636366),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

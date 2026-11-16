package com.appssimple.alarmsimple.ui.alarm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val SuccessGreen = Color(0xFF2EE59D)
private val SuccessGreenDeep = Color(0xFF12B981)
private val AccentViolet = Color(0xFF6366F1)
private val CardTop = Color(0xFF182740)
private val CardBottom = Color(0xFF0A1220)
private val TextMuted = Color(0xFF94A3B8)

/**
 * Подтверждение «Оплачено» при отключении будильника NFC-меткой.
 *
 * Сценарий (~1.8 с): карточка всплывает на пружине, фон дышит зелёным и фиолетовым
 * свечением, контент проявляется со сдвигом, кольцо обводит круг, дорисовывается
 * галочка, из неё разлетаются искры, по карточке проходит блик.
 */
@Composable
fun PaymentSuccessOverlay(
    modifier: Modifier = Modifier,
    amountText: String = "0,00 ₽",
    onAnimationEnd: () -> Unit
) {
    val appear = remember { Animatable(0f) }
    val content = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val sheen = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                appear.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
                )
            }
            launch {
                delay(120)
                content.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            }
            launch {
                delay(250)
                sheen.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
            }
            launch {
                delay(150)
                ring.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            }
            delay(520)
            launch { burst.animateTo(1f, tween(900, easing = LinearOutSlowInEasing)) }
            check.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        }
        delay(820L)
        onAnimationEnd()
    }

    val breathe by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val cardShape = RoundedCornerShape(32.dp)
    val glowStrength = (0.35f + 0.65f * appear.value) * breathe

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF04070D).copy(alpha = 0.90f)),
        contentAlignment = Alignment.Center
    ) {
        // Дышащее свечение: зелёное сверху, фиолетовое снизу
        Canvas(modifier = Modifier.fillMaxSize()) {
            val greenCenter = Offset(size.width * 0.5f, size.height * 0.38f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SuccessGreenDeep.copy(alpha = 0.30f * glowStrength),
                        SuccessGreenDeep.copy(alpha = 0.08f * glowStrength),
                        Color.Transparent
                    ),
                    center = greenCenter,
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = greenCenter
            )
            val violetCenter = Offset(size.width * 0.5f, size.height * 0.72f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentViolet.copy(alpha = 0.22f * glowStrength),
                        Color.Transparent
                    ),
                    center = violetCenter,
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = violetCenter
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.87f)
                .scale(0.88f + 0.12f * appear.value)
                .shadow(34.dp, cardShape, ambientColor = SuccessGreenDeep, spotColor = AccentViolet)
                .clip(cardShape)
                .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f))
                        )
                    ),
                    cardShape
                )
        ) {
            // Блик, проходящий по карточке
            Canvas(modifier = Modifier.matchParentSize()) {
                if (sheen.value <= 0f) return@Canvas
                val x = -size.width * 0.6f + sheen.value * size.width * 2.2f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.13f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        start = Offset(x - size.width * 0.22f, 0f),
                        end = Offset(x + size.width * 0.22f, size.height)
                    )
                )
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 30.dp)
                    .graphicsLayer {
                        alpha = content.value
                        translationY = (1f - content.value) * 26.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SuccessBadge(ring = ring.value, check = check.value, burst = burst.value)

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Готово",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Будильник отключён",
                    color = TextMuted,
                    fontSize = 15.sp
                )

                Spacer(Modifier.height(22.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Оплата",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "NFC · бесконтактно",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = amountText,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "ПЛАТЁЖ ПОДТВЕРЖДЁН",
                    color = Color(0xFF5B6B84),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.1.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SuccessBadge(
    ring: Float,
    check: Float,
    burst: Float
) {
    val sparkles = remember {
        val random = Random(42)
        List(20) { index ->
            val angle = index * (2f * Math.PI.toFloat() / 20f) + random.nextFloat() * 0.25f
            Sparkle(
                angle = angle,
                distance = 0.9f + random.nextFloat() * 0.55f,
                size = 1.6f + random.nextFloat() * 1.8f,
                tint = when {
                    index % 3 == 0 -> Color.White
                    index % 3 == 1 -> SuccessGreen
                    else -> Color(0xFF9AF7D2)
                }
            )
        }
    }

    Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = 36.dp.toPx()

            // Волны от галочки
            if (burst > 0f) {
                repeat(3) { i ->
                    drawCircle(
                        color = SuccessGreen.copy(alpha = (1f - burst) * 0.20f),
                        radius = baseRadius + (burst * (26f + i * 22f)).dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // Мягкая заливка внутри кольца
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SuccessGreen.copy(alpha = 0.22f * check),
                        SuccessGreenDeep.copy(alpha = 0.05f * check)
                    ),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )

            // Кольцо
            drawArc(
                color = SuccessGreen,
                startAngle = -90f,
                sweepAngle = ring * 360f,
                useCenter = false,
                topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                size = Size(baseRadius * 2f, baseRadius * 2f),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            // Искры
            if (burst > 0f) {
                sparkles.forEach { sparkle ->
                    val distance = (baseRadius * 0.9f + burst * baseRadius * 1.55f * sparkle.distance)
                    val dx = center.x + cos(sparkle.angle) * distance
                    val dy = center.y + sin(sparkle.angle) * distance
                    val radius = sparkle.size.dp.toPx() * (1f - burst * 0.75f)
                    if (radius > 0.2f) {
                        drawCircle(
                            color = sparkle.tint.copy(alpha = (1f - burst).coerceIn(0f, 1f)),
                            radius = radius,
                            center = Offset(dx, dy)
                        )
                    }
                }
            }

            // Галочка
            if (check > 0f) {
                val radius = baseRadius
                val path = Path().apply {
                    val p1 = Offset(center.x - radius * 0.42f, center.y + radius * 0.02f)
                    val p2 = Offset(center.x - radius * 0.10f, center.y + radius * 0.34f)
                    val p3 = Offset(center.x + radius * 0.46f, center.y - radius * 0.32f)
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val subPath = Path()
                measure.getSegment(0f, measure.length * check, subPath, true)
                drawPath(
                    path = subPath,
                    color = Color.White,
                    style = Stroke(
                        width = 4.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

private data class Sparkle(
    val angle: Float,
    val distance: Float,
    val size: Float,
    val tint: Color
)

package com.lobsterai.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun LobsterAvatar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "lobster")
    val bob by transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(
        modifier = modifier.graphicsLayer {
            translationY = bob
            scaleX = pulse
            scaleY = pulse
        }
    ) {
        val w = size.width
        val h = size.height
        val center = Offset(w * 0.5f, h * 0.52f)
        val shell = Brush.radialGradient(
            colors = listOf(Color(0xFFFF7A6E), Color(0xFFD62F45), Color(0xFF7D1026)),
            center = Offset(w * 0.45f, h * 0.38f),
            radius = w * 0.55f
        )
        val shadow = Color.Black.copy(alpha = 0.18f)
        drawOval(shadow, topLeft = Offset(w * 0.23f, h * 0.84f), size = Size(w * 0.54f, h * 0.08f))

        drawLine(Color(0xFF90182B), Offset(w * 0.42f, h * 0.34f), Offset(w * 0.24f, h * 0.1f), strokeWidth = w * 0.014f, cap = StrokeCap.Round)
        drawLine(Color(0xFF90182B), Offset(w * 0.58f, h * 0.34f), Offset(w * 0.76f, h * 0.1f), strokeWidth = w * 0.014f, cap = StrokeCap.Round)

        drawOval(shell, topLeft = Offset(w * 0.34f, h * 0.2f), size = Size(w * 0.32f, h * 0.26f))
        drawOval(shell, topLeft = Offset(w * 0.36f, h * 0.38f), size = Size(w * 0.28f, h * 0.42f))

        repeat(4) { index ->
            val y = h * (0.46f + index * 0.075f)
            drawArc(
                color = Color.White.copy(alpha = 0.15f),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(w * 0.385f, y),
                size = Size(w * 0.23f, h * 0.09f),
                style = Stroke(width = w * 0.008f)
            )
        }

        drawCircle(Color(0xFF20121A), radius = w * 0.025f, center = Offset(w * 0.43f, h * 0.28f))
        drawCircle(Color(0xFF20121A), radius = w * 0.025f, center = Offset(w * 0.57f, h * 0.28f))
        drawCircle(Color.White.copy(alpha = 0.9f), radius = w * 0.008f, center = Offset(w * 0.422f, h * 0.272f))
        drawCircle(Color.White.copy(alpha = 0.9f), radius = w * 0.008f, center = Offset(w * 0.562f, h * 0.272f))

        drawLine(Color(0xFFB7233C), Offset(w * 0.38f, h * 0.46f), Offset(w * 0.2f, h * 0.58f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
        drawLine(Color(0xFFB7233C), Offset(w * 0.62f, h * 0.46f), Offset(w * 0.8f, h * 0.58f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)

        drawOval(shell, topLeft = Offset(w * 0.07f, h * 0.49f), size = Size(w * 0.2f, h * 0.18f))
        drawOval(shell, topLeft = Offset(w * 0.73f, h * 0.49f), size = Size(w * 0.2f, h * 0.18f))
        drawLine(Color(0xFF6F1020), Offset(w * 0.14f, h * 0.56f), Offset(w * 0.23f, h * 0.62f), strokeWidth = w * 0.018f, cap = StrokeCap.Round)
        drawLine(Color(0xFF6F1020), Offset(w * 0.86f, h * 0.56f), Offset(w * 0.77f, h * 0.62f), strokeWidth = w * 0.018f, cap = StrokeCap.Round)

        listOf(0.47f, 0.54f, 0.61f, 0.68f).forEach { y ->
            drawLine(Color(0xFF8D1529), Offset(w * 0.37f, h * y), Offset(w * 0.25f, h * (y + 0.07f)), strokeWidth = w * 0.018f, cap = StrokeCap.Round)
            drawLine(Color(0xFF8D1529), Offset(w * 0.63f, h * y), Offset(w * 0.75f, h * (y + 0.07f)), strokeWidth = w * 0.018f, cap = StrokeCap.Round)
        }

        drawCircle(Color.White.copy(alpha = 0.12f), radius = w * 0.08f, center = Offset(center.x - w * 0.07f, center.y - h * 0.18f))
    }
}

package com.caipan.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    var startExit by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible && !startExit) 1f else 0f,
        animationSpec = tween(if (startExit) 260 else 500),
        label = "splashAlpha"
    )
    val contentScale by animateFloatAsState(
        targetValue = if (isVisible && !startExit) 1f else 0.92f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "splashScale"
    )
    val exitAlpha by animateFloatAsState(
        targetValue = if (startExit) 0f else 1f,
        animationSpec = tween(260),
        label = "splashOut"
    )
    val pulse by rememberInfiniteTransition(label = "splashPulse").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "logoPulse"
    )

    LaunchedEffect(Unit) {
        isVisible = true
        delay(1350)
        startExit = true
        delay(280)
        onFinished()
    }

    val background = MaterialTheme.colorScheme.background
    val foreground = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize().alpha(exitAlpha).background(background)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.dp.toPx()
            drawLine(
                color = accent.copy(alpha = 0.13f),
                start = androidx.compose.ui.geometry.Offset(size.width * .08f, size.height * .23f),
                end = androidx.compose.ui.geometry.Offset(size.width * .92f, size.height * .23f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = androidx.compose.ui.geometry.Offset(size.width * .08f, size.height * .77f),
                end = androidx.compose.ui.geometry.Offset(size.width * .92f, size.height * .77f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        Column(
            Modifier.fillMaxSize().alpha(contentAlpha).scale(contentScale).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("M U S E", color = foreground.copy(alpha = .62f), fontSize = 11.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.size(128.dp).scale(pulse).clip(RoundedCornerShape(36.dp))
                    .border(1.dp, Color.White.copy(alpha = .24f), RoundedCornerShape(36.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.app_icon),
                    contentDescription = "Muse",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(28.dp))
            Text("Muse", color = foreground, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Text("让每一段旋律，回到你身边", color = foreground.copy(alpha = 0.68f), fontSize = 15.sp, textAlign = TextAlign.Center)
        }
        Text(
            "YOUR MUSIC. YOUR MOMENT.",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp),
            color = foreground.copy(alpha = .42f),
            fontSize = 11.sp,
            letterSpacing = 2.sp
        )
    }
}

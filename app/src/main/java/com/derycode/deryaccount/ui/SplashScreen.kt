package com.derycode.deryaccount.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.R
import kotlinx.coroutines.delay

/** Branded launch splash — matches the official DeryAccount mockup:
 * navy gradient, tagline, "D" mark, feature strip, loading bar and
 * a green wave footer. Shown while the app resolves session/onboarding
 * state in the background, then fades into Login/Home. */
@Composable
fun DeryAccountSplashScreen() {
    val navy = Color(0xFF0B1F4D)
    val navyDeep = Color(0xFF07132E)
    val green = Color(0xFF22C55E)

    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        // ~1.5s animated fill, purely cosmetic — real loading happens behind it
        val steps = 30
        for (i in 1..steps) {
            progress = i / steps.toFloat()
            delay(45)
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress, animationSpec = tween(120, easing = LinearEasing), label = "splash-progress"
    )

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(navy, navyDeep)))
    ) {
        // top-right tagline
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 20.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text("Smart Accounting", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, letterSpacing = 1.sp)
            Text("Better Decisions", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(46.dp).height(2.dp).background(green))
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = "DeryAccount",
                modifier = Modifier.size(150.dp)
            )
            Spacer(Modifier.height(18.dp))
            Row {
                Text("Dery", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Text("Account", color = green, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(6.dp))
            Text("ACCOUNTING MADE SIMPLE", color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SplashFeature("Sales", Color(0xFF22C55E))
                SplashDivider()
                SplashFeature("Purchases", Color(0xFFF59E0B))
                SplashDivider()
                SplashFeature("Stock", Color(0xFF3B82F6))
                SplashDivider()
                SplashFeature("Reports", Color(0xFFA855F7))
            }

            Spacer(Modifier.height(20.dp))
            Text("Track  •  Manage  •  Grow", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }

        // bottom wave + loading bar
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val w = size.width; val h = size.height
                val wave = Path().apply {
                    moveTo(0f, h * 0.55f)
                    cubicTo(w * 0.25f, h * 0.15f, w * 0.55f, h * 0.85f, w, h * 0.35f)
                    lineTo(w, h); lineTo(0f, h); close()
                }
                drawPath(wave, Brush.horizontalGradient(listOf(green, Color(0xFF16A34A))), alpha = 0.9f)
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 60.dp).padding(bottom = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
                Spacer(Modifier.height(8.dp))
                Text("Loading...", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SplashFeature(label: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(tint),
        )
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SplashDivider() {
    Box(Modifier.width(1.dp).height(34.dp).background(Color.White.copy(alpha = 0.25f)))
}

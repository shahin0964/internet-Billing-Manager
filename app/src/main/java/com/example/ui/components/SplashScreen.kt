package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val SplashNavyBackground = Color(0xFF020826)
val SplashCyan = Color(0xFF00C8FF)
val SplashBlue = Color(0xFF005BF8)
val SplashWhite = Color(0xFFFFFFFF)

@Composable
fun SplashScreenOverlay(
    isVisible: Boolean,
    onSplashFinished: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        exit = fadeOut(animationSpec = tween(durationMillis = 300))
    ) {
        SplashScreen(onSplashFinished = onSplashFinished)
    }
}

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val logoScale = remember { Animatable(0.85f) }
    val logoAlpha = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }
    val appNameAlpha = remember { Animatable(0f) }
    val appNameOffsetY = remember { Animatable(16f) }
    val taglineAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Step 1: Scale up logo smoothly & fade in logo + glow
        launch {
            logoScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 400)
            )
        }
        launch {
            glowAlpha.animateTo(
                targetValue = 0.75f,
                animationSpec = tween(durationMillis = 500)
            )
        }

        // Step 2: Fade in app name text + slight upward motion
        delay(220)
        launch {
            appNameAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 400)
            )
        }
        launch {
            appNameOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }

        // Step 3: Fade in tagline
        delay(200)
        launch {
            taglineAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 400)
            )
        }

        // Hold briefly and complete splash screen
        delay(550)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashNavyBackground),
        contentAlignment = Alignment.Center
    ) {
        // Subtle Cyan/Blue Glow behind logo
        Box(
            modifier = Modifier
                .size(280.dp)
                .alpha(glowAlpha.value)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SplashCyan.copy(alpha = 0.35f),
                            SplashBlue.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .scale(logoScale.value)
                .alpha(logoAlpha.value)
        ) {
            // Attached Official Logo Canvas Drawing
            IbmOfficialLogo(size = 180.dp)

            Spacer(modifier = Modifier.height(24.dp))

            // App Name "Internet Billing Manager"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(y = appNameOffsetY.value.dp)
                    .alpha(appNameAlpha.value)
            ) {
                Text(
                    text = "Internet Billing Manager",
                    style = androidx.compose.ui.text.TextStyle(
                        color = SplashCyan,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tagline "Seamless Connection. Smarter Billing."
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(taglineAlpha.value)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, SplashWhite.copy(alpha = 0.6f))
                                )
                            )
                    )

                    Text(
                        text = "Seamless Connection. Smarter Billing.",
                        style = androidx.compose.ui.text.TextStyle(
                            color = SplashWhite.copy(alpha = 0.9f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 0.8.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(SplashWhite.copy(alpha = 0.6f), Color.Transparent)
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * Custom Canvas Rendering of the Official IBM Logo matching the attached image
 */
@Composable
fun IbmOfficialLogo(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Wi-Fi Signal Arcs above 'i'
        val arcCenterX = w * 0.28f
        val arcCenterY = h * 0.26f

        // Outer Arc (White)
        drawArc(
            color = SplashWhite,
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(arcCenterX - w * 0.18f, arcCenterY - h * 0.18f),
            size = Size(w * 0.36f, h * 0.36f),
            style = Stroke(width = w * 0.042f, cap = StrokeCap.Round)
        )

        // Middle Arc (Cyan)
        drawArc(
            color = SplashCyan,
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(arcCenterX - w * 0.13f, arcCenterY - h * 0.13f),
            size = Size(w * 0.26f, h * 0.26f),
            style = Stroke(width = w * 0.038f, cap = StrokeCap.Round)
        )

        // Inner Arc (Blue)
        drawArc(
            color = SplashBlue,
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(arcCenterX - w * 0.08f, arcCenterY - h * 0.08f),
            size = Size(w * 0.16f, h * 0.16f),
            style = Stroke(width = w * 0.034f, cap = StrokeCap.Round)
        )

        // Dot of 'i' (Blue)
        drawCircle(
            color = SplashBlue,
            radius = w * 0.038f,
            center = Offset(arcCenterX, h * 0.34f)
        )

        // Stem of 'i' (White Bar)
        val stemLeft = w * 0.23f
        val stemTop = h * 0.42f
        val stemWidth = w * 0.10f
        val stemHeight = h * 0.28f
        drawRoundRect(
            color = SplashWhite,
            topLeft = Offset(stemLeft, stemTop),
            size = Size(stemWidth, stemHeight),
            cornerRadius = CornerRadius(w * 0.015f, w * 0.015f)
        )

        // B Pixel Pattern (left side of B)
        val pixelSize = w * 0.028f
        val pixelStartX = w * 0.35f
        val pixelStartY = h * 0.42f
        val pixelColors = listOf(
            listOf(SplashCyan, SplashWhite, SplashCyan),
            listOf(SplashWhite, SplashCyan, SplashBlue),
            listOf(SplashCyan, SplashBlue, SplashWhite),
            listOf(SplashBlue, SplashWhite, SplashCyan),
            listOf(SplashWhite, SplashCyan, SplashWhite),
            listOf(SplashCyan, SplashBlue, SplashCyan),
            listOf(SplashWhite, SplashCyan, SplashBlue),
            listOf(SplashCyan, SplashWhite, SplashCyan),
            listOf(SplashBlue, SplashCyan, SplashWhite),
            listOf(SplashWhite, SplashBlue, SplashCyan)
        )

        for (row in pixelColors.indices) {
            for (col in pixelColors[row].indices) {
                drawRect(
                    color = pixelColors[row][col],
                    topLeft = Offset(pixelStartX + col * pixelSize, pixelStartY + row * pixelSize),
                    size = Size(pixelSize * 0.88f, pixelSize * 0.88f)
                )
            }
        }

        // Letter B (Cyan/Blue gradient stylized B)
        val bPath = Path().apply {
            val bLeft = w * 0.42f
            val bTop = h * 0.42f
            moveTo(bLeft, bTop)
            lineTo(bLeft + w * 0.12f, bTop)
            cubicTo(
                bLeft + w * 0.20f, bTop,
                bLeft + w * 0.20f, bTop + h * 0.14f,
                bLeft + w * 0.12f, bTop + h * 0.14f
            )
            lineTo(bLeft, bTop + h * 0.14f)
            close()

            moveTo(bLeft, bTop + h * 0.14f)
            lineTo(bLeft + w * 0.14f, bTop + h * 0.14f)
            cubicTo(
                bLeft + w * 0.22f, bTop + h * 0.14f,
                bLeft + w * 0.22f, bTop + h * 0.28f,
                bLeft + w * 0.14f, bTop + h * 0.28f
            )
            lineTo(bLeft, bTop + h * 0.28f)
            close()
        }

        drawPath(
            path = bPath,
            brush = Brush.verticalGradient(
                colors = listOf(SplashCyan, SplashBlue),
                startY = h * 0.42f,
                endY = h * 0.70f
            )
        )

        // Letter M (Solid White)
        val mPath = Path().apply {
            val mLeft = w * 0.58f
            val mTop = h * 0.42f
            val mWidth = w * 0.26f
            val mHeight = h * 0.28f

            moveTo(mLeft, mTop + mHeight)
            lineTo(mLeft, mTop)
            lineTo(mLeft + mWidth * 0.08f, mTop)
            lineTo(mLeft + mWidth * 0.50f, mTop + mHeight * 0.70f)
            lineTo(mLeft + mWidth * 0.92f, mTop)
            lineTo(mLeft + mWidth, mTop)
            lineTo(mLeft + mWidth, mTop + mHeight)
            lineTo(mLeft + mWidth * 0.82f, mTop + mHeight)
            lineTo(mLeft + mWidth * 0.82f, mTop + mHeight * 0.30f)
            lineTo(mLeft + mWidth * 0.50f, mTop + mHeight * 0.82f)
            lineTo(mLeft + mWidth * 0.18f, mTop + mHeight * 0.30f)
            lineTo(mLeft + mWidth * 0.18f, mTop + mHeight)
            close()
        }

        drawPath(
            path = mPath,
            color = SplashWhite
        )

        // Swoosh curve under B & M (Blue curved stroke ending with dot)
        val swooshPath = Path().apply {
            moveTo(w * 0.42f, h * 0.69f)
            cubicTo(
                w * 0.40f, h * 0.78f,
                w * 0.50f, h * 0.78f,
                w * 0.68f, h * 0.64f
            )
            cubicTo(
                w * 0.78f, h * 0.56f,
                w * 0.86f, h * 0.48f,
                w * 0.90f, h * 0.44f
            )
        }

        drawPath(
            path = swooshPath,
            color = SplashBlue,
            style = Stroke(width = w * 0.038f, cap = StrokeCap.Round)
        )

        // Swoosh end circle dot
        drawCircle(
            color = SplashBlue,
            radius = w * 0.032f,
            center = Offset(w * 0.90f, h * 0.44f)
        )
    }
}

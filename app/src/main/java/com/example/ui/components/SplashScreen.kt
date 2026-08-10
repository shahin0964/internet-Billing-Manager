package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SplashNavyBackground = Color(0xFF010726)
val SplashCyan = Color(0xFF00E5FF)
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
    // 1.5 seconds real loading progress animation
    val progress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    LaunchedEffect(Unit) {
        // Animate progress smoothly from 0.0 to 1.0 in exactly 1500ms (1.5 seconds)
        progress.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
        )
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF01051B),
                        SplashNavyBackground,
                        Color(0xFF020E3D),
                        Color(0xFF010518)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Background Ambient Glows & Tech Grid Lines
        BackgroundTechDecoration()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 36.dp, horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Main Visual Header Group
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Official IBM Logo
                IbmOfficialLogo(size = 140.dp)

                Spacer(modifier = Modifier.height(16.dp))

                // English App Title
                Text(
                    text = "Internet Billing Management",
                    style = androidx.compose.ui.text.TextStyle(
                        color = SplashCyan,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Tagline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(0.88f)
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.6.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
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

                Spacer(modifier = Modifier.height(28.dp))

                // Glowing Banner: "স্মার্ট ইন্টারনেট বিল ব্যবস্থাপনার সহজ সমাধান"
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF003882).copy(alpha = 0.5f),
                                    Color(0xFF0066FF).copy(alpha = 0.35f),
                                    Color(0xFF003882).copy(alpha = 0.5f)
                                )
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    SplashCyan.copy(alpha = 0.8f),
                                    SplashBlue.copy(alpha = 0.9f),
                                    SplashCyan.copy(alpha = 0.8f)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "স্মার্ট ইন্টারনেট বিল\nব্যবস্থাপনার সহজ সমাধান",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 26.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 4 Feature Circles Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    FeatureIconCircle(
                        icon = Icons.Filled.People,
                        title = "গ্রাহক\nব্যবস্থাপনা"
                    )
                    FeatureIconCircle(
                        icon = Icons.Filled.ReceiptLong,
                        title = "বিলিং ও\nসংগ্রহ"
                    )
                    FeatureIconCircle(
                        icon = Icons.Filled.BarChart,
                        title = "রিপোর্ট ও\nবিশ্লেষণ"
                    )
                    FeatureIconCircle(
                        icon = Icons.Filled.Cloud,
                        title = "ক্লাউড ব্যাকআপ\nও সিঙ্ক"
                    )
                }
            }

            // Bottom Section: Real Progress Bar & "অ্যাপ চালু হচ্ছে..."
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Real Progress Bar (1.5s loading animation)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0A1B4D))
                        .border(
                            width = 1.dp,
                            color = SplashCyan.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(4.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.value)
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        SplashBlue,
                                        SplashCyan,
                                        Color.White
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "অ্যাপ চালু হচ্ছে...",
                    style = androidx.compose.ui.text.TextStyle(
                        color = SplashCyan.copy(alpha = pulseAlpha),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FeatureIconCircle(
    icon: ImageVector,
    title: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0066FF).copy(alpha = 0.45f),
                            Color(0xFF002266).copy(alpha = 0.8f)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SplashCyan,
                            SplashBlue.copy(alpha = 0.6f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = androidx.compose.ui.text.TextStyle(
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
        )
    }
}

@Composable
private fun BackgroundTechDecoration() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Top Light Ray/Glows
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SplashCyan.copy(alpha = 0.15f),
                    SplashBlue.copy(alpha = 0.08f),
                    Color.Transparent
                )
            ),
            radius = w * 0.7f,
            center = Offset(w * 0.5f, h * 0.2f)
        )

        // Bottom Globe Arc Drawing
        val globeCenterY = h * 1.05f
        val globeRadius = w * 0.9f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0088FF).copy(alpha = 0.25f),
                    Color(0xFF0033AA).copy(alpha = 0.15f),
                    Color.Transparent
                )
            ),
            radius = globeRadius * 1.1f,
            center = Offset(w * 0.5f, globeCenterY)
        )

        drawArc(
            color = SplashCyan.copy(alpha = 0.7f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.5f - globeRadius, globeCenterY - globeRadius),
            size = Size(globeRadius * 2f, globeRadius * 2f),
            style = Stroke(width = 2.dp.toPx())
        )
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

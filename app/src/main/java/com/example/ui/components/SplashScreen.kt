package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

val SplashNavyBackground = Color(0xFF010726)
val SplashCyan = Color(0xFF00E5FF)
val SplashBlue = Color(0xFF005BF8)
val SplashWhite = Color(0xFFFFFFFF)

@Composable
fun SplashScreenOverlay(
    isLoading: Boolean,
    onSplashFinished: () -> Unit
) {
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1500) // Exact 1.5 seconds (1500 milliseconds) display duration
        isVisible = false
        delay(300) // allow smooth fadeOut animation to complete
        onSplashFinished()
    }

    AnimatedVisibility(
        visible = isVisible,
        exit = fadeOut(animationSpec = tween(durationMillis = 400))
    ) {
        SplashScreen()
    }
}

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splashAnimations")

    // 1. Subtle Logo Glow Pulse
    val logoGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoGlowAlpha"
    )

    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    // 2. Loading Ring Smooth 360 Rotation (60 FPS)
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    // 3. Loading Text Opacity Breathing
    val loadingTextAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loadingTextAlpha"
    )

    // 4. Subtle Network Particle Wave Phase
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particlePhase"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF010515),
                        Color(0xFF010A2B),
                        Color(0xFF020E3D),
                        Color(0xFF010518)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Background Light Rays & Animated Tech Particle Mesh Grid
        BackgroundTechDecoration(particlePhase = particlePhase)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp, horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Upper Group: Logo + Title + Tagline
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Outer Glow & Official Logo
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.scale(logoScale)
                ) {
                    // Soft Ambient Glow behind Logo
                    Canvas(modifier = Modifier.size(200.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    SplashCyan.copy(alpha = 0.25f * logoGlowAlpha),
                                    SplashBlue.copy(alpha = 0.15f * logoGlowAlpha),
                                    Color.Transparent
                                )
                            ),
                            radius = size.width * 0.48f
                        )
                    }

                    // Exact Artwork iBM Official Logo
                    IbmOfficialLogo(size = 150.dp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // App Title: Internet Billing Management
                Text(
                    text = "Internet Billing",
                    style = androidx.compose.ui.text.TextStyle(
                        color = SplashCyan,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.6.sp,
                        textAlign = TextAlign.Center
                    )
                )
                Text(
                    text = "Management",
                    style = androidx.compose.ui.text.TextStyle(
                        color = SplashCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 2.0.sp,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tagline with Gradient Accent Lines
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(0.90f)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, SplashWhite.copy(alpha = 0.7f))
                                )
                            )
                    )
                    Text(
                        text = "Seamless Connection. Smarter Billing.",
                        style = androidx.compose.ui.text.TextStyle(
                            color = SplashWhite.copy(alpha = 0.92f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.5.sp,
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
                                    colors = listOf(SplashWhite.copy(alpha = 0.7f), Color.Transparent)
                                )
                            )
                    )
                }
            }

            // Center-Bottom Group: Smooth Rotating Loading Ring & LOADING... Text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Circular Ring with Smooth Rotating Arc
                Box(
                    modifier = Modifier.size(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val strokeWidth = 2.5.dp.toPx()

                        // Base Circle Track
                        drawCircle(
                            color = Color(0xFF0A255C).copy(alpha = 0.6f),
                            radius = (w - strokeWidth) / 2f,
                            style = Stroke(width = strokeWidth)
                        )

                        // Outer Glow Ring
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    SplashCyan.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            ),
                            radius = w / 2f
                        )

                        // Smooth Rotating Loading Arc (60 FPS)
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    SplashBlue,
                                    SplashCyan,
                                    Color.White
                                )
                            ),
                            startAngle = ringRotation,
                            sweepAngle = 280f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth * 1.2f, cap = StrokeCap.Round)
                        )
                    }

                    // Inside "LOADING..." Text
                    Text(
                        text = "LOADING...",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White.copy(alpha = loadingTextAlpha),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundTechDecoration(particlePhase: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Top Radial Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SplashCyan.copy(alpha = 0.12f),
                    SplashBlue.copy(alpha = 0.06f),
                    Color.Transparent
                )
            ),
            radius = w * 0.75f,
            center = Offset(w * 0.5f, h * 0.25f)
        )

        // Bottom Tech Wave Mesh Grid & Particles
        val particleCount = 28
        val baseParticleY = h * 0.82f
        val maxWaveHeight = h * 0.12f

        for (i in 0 until particleCount) {
            val progress = (i.toFloat() / particleCount + particlePhase) % 1.0f
            val px = w * (i.toFloat() / (particleCount - 1))
            val waveOffset = kotlin.math.sin((progress * 2 * Math.PI) + (i * 0.4)).toFloat() * maxWaveHeight * 0.25f
            val py = baseParticleY + waveOffset + (progress * 15f)

            val alpha = (0.2f + 0.6f * kotlin.math.sin(progress * Math.PI).toFloat()).coerceIn(0f, 1f)
            val pRadius = (1.5.dp.toPx() + (progress * 1.5.dp.toPx()))

            // Draw glowing particle node
            drawCircle(
                color = SplashCyan.copy(alpha = alpha * 0.85f),
                radius = pRadius,
                center = Offset(px, py)
            )

            // Connecting lines to adjacent nodes
            if (i < particleCount - 1) {
                val nextProgress = ((i + 1).toFloat() / particleCount + particlePhase) % 1.0f
                val nextPx = w * ((i + 1).toFloat() / (particleCount - 1))
                val nextWaveOffset = kotlin.math.sin((nextProgress * 2 * Math.PI) + ((i + 1) * 0.4)).toFloat() * maxWaveHeight * 0.25f
                val nextPy = baseParticleY + nextWaveOffset + (nextProgress * 15f)

                drawLine(
                    color = SplashBlue.copy(alpha = alpha * 0.35f),
                    start = Offset(px, py),
                    end = Offset(nextPx, nextPy),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

/**
 * Custom Canvas Rendering of the Official iBM Logo matching the uploaded image artwork
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

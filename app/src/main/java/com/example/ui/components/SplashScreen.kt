package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

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

    // 1. Entrance Fade & Scale Animation (0.0s to 0.5s)
    var isEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isEntered = true
    }

    val introAlpha by animateFloatAsState(
        targetValue = if (isEntered) 1f else 0f,
        animationSpec = tween(durationMillis = 550, easing = LinearEasing),
        label = "introAlpha"
    )

    val introScale by animateFloatAsState(
        targetValue = if (isEntered) 1f else 0.84f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "introScale"
    )

    // 2. Wi-Fi Arc Pulse Sequence (0.1s to 1.5s)
    val wifiPulseTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wifiPulseTime"
    )

    // 3. Swoosh Glow Travels (0.3s to 1.5s)
    val swooshPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1.3f, // Travels slightly past the end of the line for a natural gap
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "swooshPhase"
    )

    // 4. Background Star/Particle Twinkle
    val twinkleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "twinkleTime"
    )

    // 5. Circular Loader Arc Rotation (60 FPS)
    val loaderRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loaderRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF000511),
                        Color(0xFF010A2B),
                        Color(0xFF02103F),
                        Color(0xFF000511)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Futuristic Cyber Globe & Constellation Twinkly Background
        BackgroundTechDecoration(
            twinkleTime = twinkleTime
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp, horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Upper/Center Group: Animated Logo + Dynamic Texts
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(introAlpha)
                    .scale(introScale)
            ) {
                // Glow Halo behind the Logo
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(240.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    SplashCyan.copy(alpha = 0.32f),
                                    SplashBlue.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.width * 0.48f
                        )
                    }

                    // Render Official Logo with active internal wave and swoosh glow animations
                    IbmOfficialLogo(
                        size = 160.dp,
                        wifiPulseTime = wifiPulseTime,
                        swooshPhase = swooshPhase
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // App Title: Internet Billing Management
                Text(
                    text = "Internet Billing",
                    style = androidx.compose.ui.text.TextStyle(
                        color = SplashWhite,
                        fontSize = 25.sp,
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
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 2.4.sp,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Premium Tagline with Side Accent Lines
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, SplashCyan.copy(alpha = 0.6f))
                                )
                            )
                    )
                    Text(
                        text = "Seamless Connection. Smarter Billing.",
                        style = androidx.compose.ui.text.TextStyle(
                            color = SplashWhite.copy(alpha = 0.90f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.5.sp,
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
                                    colors = listOf(SplashCyan.copy(alpha = 0.6f), Color.Transparent)
                                )
                            )
                    )
                }
            }

            // Bottom Group: Real Glowing Tech Circular Loading Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp)
            ) {
                CircularTechLoader(
                    rotation = loaderRotation
                )
            }
        }
    }
}

@Composable
private fun BackgroundTechDecoration(twinkleTime: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Center Radiant Digital Globe Aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SplashCyan.copy(alpha = 0.18f),
                    SplashBlue.copy(alpha = 0.06f),
                    Color.Transparent
                )
            ),
            radius = w * 0.75f,
            center = Offset(w * 0.5f, h * 0.45f)
        )

        // 2. Futuristic Cyber Globe Lines (Latitude and Longitude Rings behind logo)
        val globeCenter = Offset(w * 0.5f, h * 0.45f)
        val latRings = 4
        for (i in 0 until latRings) {
            val radiusX = w * 0.42f
            val radiusY = h * 0.18f * (i.toFloat() / (latRings - 1) * 2f - 1f)
            drawContext.canvas.save()
            drawContext.canvas.rotate(12f, globeCenter.x, globeCenter.y)
            drawArc(
                color = SplashCyan.copy(alpha = 0.05f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(globeCenter.x - radiusX, globeCenter.y - radiusY.absoluteValue),
                size = Size(radiusX * 2f, radiusY.absoluteValue * 2f),
                style = Stroke(width = 0.8.dp.toPx())
            )
            drawContext.canvas.restore()
        }

        val longRings = 4
        for (i in 0 until longRings) {
            val radiusX = w * 0.42f * (i.toFloat() / (longRings - 1) * 2f - 1f)
            val radiusY = h * 0.18f
            drawContext.canvas.save()
            drawContext.canvas.rotate(12f, globeCenter.x, globeCenter.y)
            drawArc(
                color = SplashCyan.copy(alpha = 0.05f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(globeCenter.x - radiusX.absoluteValue, globeCenter.y - radiusY),
                size = Size(radiusX.absoluteValue * 2f, radiusY * 2f),
                style = Stroke(width = 0.8.dp.toPx())
            )
            drawContext.canvas.restore()
        }

        // 3. Static responsive twinkle star positions
        val starPositions = listOf(
            Offset(0.12f, 0.18f), Offset(0.28f, 0.10f), Offset(0.48f, 0.08f), Offset(0.68f, 0.12f), Offset(0.85f, 0.16f),
            Offset(0.18f, 0.28f), Offset(0.35f, 0.22f), Offset(0.58f, 0.19f), Offset(0.74f, 0.26f), Offset(0.88f, 0.32f),
            Offset(0.08f, 0.44f), Offset(0.22f, 0.38f), Offset(0.78f, 0.40f), Offset(0.92f, 0.46f),
            Offset(0.14f, 0.60f), Offset(0.30f, 0.55f), Offset(0.72f, 0.58f), Offset(0.86f, 0.64f),
            Offset(0.10f, 0.76f), Offset(0.34f, 0.72f), Offset(0.50f, 0.75f), Offset(0.66f, 0.74f), Offset(0.82f, 0.80f),
            Offset(0.22f, 0.88f), Offset(0.45f, 0.90f), Offset(0.60f, 0.89f), Offset(0.76f, 0.86f)
        )

        // Draw connections web (subtle digital constellation grid)
        for (i in starPositions.indices) {
            val p1 = Offset(starPositions[i].x * w, starPositions[i].y * h)
            for (j in i + 1 until starPositions.size) {
                val p2 = Offset(starPositions[j].x * w, starPositions[j].y * h)
                val distSq = (p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y)
                val maxDist = w * 0.18f
                if (distSq < maxDist * maxDist) {
                    val dist = kotlin.math.sqrt(distSq)
                    val alphaFactor = (1f - dist / maxDist) * 0.05f
                    drawLine(
                        color = SplashBlue.copy(alpha = alphaFactor),
                        start = p1,
                        end = p2,
                        strokeWidth = 0.5.dp.toPx()
                    )
                }
            }
        }

        // Draw Twinkling nodes
        for (i in starPositions.indices) {
            val px = starPositions[i].x * w
            val py = starPositions[i].y * h
            val pulseOffset = i * 0.4f
            val starAlpha = (0.15f + 0.85f * kotlin.math.sin(twinkleTime * 3.5f + pulseOffset).absoluteValue).coerceIn(0f, 1f)
            val radius = if (i % 3 == 0) 2.2.dp.toPx() else 1.2.dp.toPx()

            // Draw glowing halo around every third node
            if (i % 3 == 0) {
                drawCircle(
                    color = SplashCyan.copy(alpha = starAlpha * 0.18f),
                    radius = radius * 3f,
                    center = Offset(px, py)
                )
            }
            // Core node
            drawCircle(
                color = (if (i % 2 == 0) SplashCyan else SplashWhite).copy(alpha = starAlpha),
                radius = radius,
                center = Offset(px, py)
            )
        }
    }
}

@Composable
fun IbmOfficialLogo(
    size: Dp,
    wifiPulseTime: Float,
    swooshPhase: Float
) {
    val pathMeasure = remember { PathMeasure() }
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Wi-Fi Signal Arcs above 'i' stem
        val arcCenterX = w * 0.28f
        val arcCenterY = h * 0.26f

        // Outward pulse alpha calculations
        val innerAlpha = (1f - kotlin.math.abs(wifiPulseTime - 1.0f)).coerceIn(0.25f, 1.0f)
        val middleAlpha = (1f - kotlin.math.abs(wifiPulseTime - 2.0f)).coerceIn(0.25f, 1.0f)
        val outerAlpha = (1f - kotlin.math.abs(wifiPulseTime - 3.0f)).coerceIn(0.25f, 1.0f)

        // Outer Arc (White)
        drawArc(
            color = SplashWhite.copy(alpha = outerAlpha),
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(arcCenterX - w * 0.18f, arcCenterY - h * 0.18f),
            size = Size(w * 0.36f, h * 0.36f),
            style = Stroke(width = w * 0.042f, cap = StrokeCap.Round)
        )

        // Middle Arc (Cyan)
        drawArc(
            color = SplashCyan.copy(alpha = middleAlpha),
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(arcCenterX - w * 0.13f, arcCenterY - h * 0.13f),
            size = Size(w * 0.26f, h * 0.26f),
            style = Stroke(width = w * 0.038f, cap = StrokeCap.Round)
        )

        // Inner Arc (Blue)
        drawArc(
            color = SplashBlue.copy(alpha = innerAlpha),
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

        // Dynamic Swoosh Glow Animation
        try {
            pathMeasure.setPath(swooshPath, false)
            val pathLen = pathMeasure.length
            if (pathLen > 0f) {
                val segmentPath = Path()
                val startDist = (swooshPhase - 0.25f).coerceAtLeast(0f) * pathLen
                val endDist = swooshPhase.coerceAtMost(1f) * pathLen
                if (endDist > startDist) {
                    pathMeasure.getSegment(startDist, endDist, segmentPath, true)
                    drawPath(
                        path = segmentPath,
                        color = SplashCyan,
                        style = Stroke(width = w * 0.042f, cap = StrokeCap.Round)
                    )
                }
            }
        } catch (e: Throwable) {
            // Graceful fallback
        }

        // Swoosh end circle dot
        drawCircle(
            color = SplashBlue,
            radius = w * 0.032f,
            center = Offset(w * 0.90f, h * 0.44f)
        )
    }
}

@Composable
fun CircularTechLoader(rotation: Float) {
    Canvas(modifier = Modifier.size(80.dp)) {
        val w = size.width
        val strokeW = 3.dp.toPx()

        // 1. Soft Radial Cyan Central Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SplashCyan.copy(alpha = 0.16f), Color.Transparent)
            ),
            radius = w * 0.6f
        )

        // 2. Outer Dotted Orbiting Track
        drawCircle(
            color = SplashCyan.copy(alpha = 0.15f),
            radius = w * 0.44f,
            style = Stroke(width = 1.dp.toPx())
        )

        // 3. Primary Rotating Loader Arc
        drawArc(
            color = SplashCyan,
            startAngle = rotation,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(w * 0.08f, w * 0.08f),
            size = Size(w * 0.84f, w * 0.84f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // 4. Secondary Counter-Rotating Darker Blue Arc
        drawArc(
            color = SplashBlue,
            startAngle = -rotation * 0.8f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(w * 0.14f, w * 0.14f),
            size = Size(w * 0.72f, w * 0.72f),
            style = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round)
        )

        // 5. Orbiting Nodes on the dotted track
        val nodes = 6
        for (i in 0 until nodes) {
            val baseAngle = (i * 360f / nodes) + rotation * 1.3f
            val rad = Math.toRadians(baseAngle.toDouble())
            val r = w * 0.44f
            val nx = (w / 2f + r * kotlin.math.cos(rad)).toFloat()
            val ny = (w / 2f + r * kotlin.math.sin(rad)).toFloat()

            val nodeAlpha = 0.25f + 0.75f * kotlin.math.sin(Math.toRadians((rotation + i * 60).toDouble())).toFloat().absoluteValue

            drawCircle(
                color = SplashCyan.copy(alpha = nodeAlpha),
                radius = 2.dp.toPx(),
                center = Offset(nx, ny)
            )
        }
    }
}

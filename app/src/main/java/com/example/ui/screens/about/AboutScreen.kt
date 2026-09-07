package com.example.ui.screens.about

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ReadMateBrandLogo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// Strict Monochrome Obsidian Palette
private val DeepObsidian = Color(0xFF0B0B0E)
private val ZincSurface = Color(0xFF141418)
private val SubduedZinc = Color(0xFF101013)
private val SlateBorders = Color(0xFF27272F)
private val CtaBorder = Color(0xFF3F3F46)
private val CrispWhite = Color(0xFFFFFFFF)
private val ZincMuted = Color(0xFF71717A)
private val TextBodyPrimary = Color(0xFFD4D4D8)
private val TextBodyMuted = Color(0xFFA1A1AA)

@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DeepObsidian)
            .testTag("about_screen_scaffold"),
        containerColor = DeepObsidian,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepObsidian)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("about_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = CrispWhite
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    ReadMateBrandLogo(
                        size = 28.dp,
                        showWordmark = false
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "ABOUT & ARCHITECTURE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = CrispWhite
                    )
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = SlateBorders
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Hero Identity Card with Breathing Kinetic Aperture
            StaggeredEntranceContainer(index = 0) {
                HeroIdentityCard()
            }

            // 2. Philosophy & Mission Section
            StaggeredEntranceContainer(index = 1) {
                PhilosophySection()
            }

            // 3. Lead Architect & Tech Stack Architecture Card
            StaggeredEntranceContainer(index = 2) {
                ArchitectCopyrightCard()
            }

            // 4. Commission & Privacy-First CTA Deep Links
            StaggeredEntranceContainer(index = 3) {
                CommissionCtaCard(
                    onWhatsAppClick = {
                        val message = Uri.encode("Hello Faizan, reaching out regarding ReadMate development and inquiries...")
                        val url = "https://wa.me/923164438828?text=$message"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Fallback to browser intent
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=923164438828&text=$message"))
                                context.startActivity(browserIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Unable to launch WhatsApp", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onEmailClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:Faizankhan18022004@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "ReadMate Architecture & Inquiries")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Unable to open email client", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Generous clearance above navigation bar / gesture pill
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * Reusable Hardware-Accelerated Staggered Entrance Container
 */
@Composable
private fun StaggeredEntranceContainer(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(18f) }

    LaunchedEffect(Unit) {
        delay(index * 80L)
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }
        launch {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha.value
                this.translationY = offsetY.value * density
            }
    ) {
        content()
    }
}

/**
 * 1. Hero Identity Card
 * Surface #141418, border 1.dp solid #27272F, shape 14.dp, padding 22.dp.
 */
@Composable
private fun HeroIdentityCard(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_aperture_kinetic")

    // Breathing scale animation oscillating between 0.97f and 1.03f
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aperture_scale"
    )

    // Ultra-slow continuous rotation
    val logoRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 45000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aperture_rotation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("about_hero_card"),
        shape = RoundedCornerShape(14.dp),
        color = ZincSurface,
        border = BorderStroke(1.dp, SlateBorders)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 26.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Center showcase emblem (size = 80dp)
            ReadMateBrandLogo(
                size = 80.dp,
                showWordmark = false,
                modifier = Modifier.graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // App Name: ReadMate (styled strictly with clean tracking: letter-spacing 0.5sp, bold, #FFFFFF)
            Text(
                text = "ReadMate",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = CrispWhite
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle: EXECUTIVE KNOWLEDGE ACCELERATION ENGINE (10.sp, FontWeight.SemiBold, letterSpacing = 2.sp, #71717A)
            Text(
                text = "EXECUTIVE KNOWLEDGE ACCELERATION ENGINE",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = ZincMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Version Pill: v1.0.0 • PRODUCTION READY in a clean capsule chip
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = SubduedZinc,
                border = BorderStroke(1.dp, SlateBorders)
            ) {
                Text(
                    text = "v1.0.0 • PRODUCTION READY",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    color = TextBodyMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Precision Geometric Monochrome Aperture Glyph
 */
@Composable
private fun BrandApertureGlyph(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = this.center
        val radius = size.minDimension / 2f
        val strokeWidthPx = 1.8.dp.toPx()

        // Outer aperture ring
        drawCircle(
            color = CrispWhite,
            radius = radius - strokeWidthPx,
            style = Stroke(width = strokeWidthPx)
        )

        // 6 Aperture blades
        val bladeCount = 6
        val innerRadius = radius * 0.38f
        for (i in 0 until bladeCount) {
            val angle = (i * 360f / bladeCount) * (Math.PI / 180f)
            val outerX = center.x + (radius - strokeWidthPx * 1.5f) * cos(angle).toFloat()
            val outerY = center.y + (radius - strokeWidthPx * 1.5f) * sin(angle).toFloat()

            val tangentAngle = angle + (Math.PI / 2.7)
            val innerX = center.x + innerRadius * cos(tangentAngle).toFloat()
            val innerY = center.y + innerRadius * sin(tangentAngle).toFloat()

            drawLine(
                color = CrispWhite,
                start = Offset(outerX, outerY),
                end = Offset(innerX, innerY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }

        // Inner core iris
        drawCircle(
            color = CrispWhite,
            radius = radius * 0.16f,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

/**
 * 2. Philosophy & Mission Section
 * Surface #141418, border 1.dp solid #27272F, shape 14.dp, padding 20.dp.
 */
@Composable
private fun PhilosophySection(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("about_philosophy_card"),
        shape = RoundedCornerShape(14.dp),
        color = ZincSurface,
        border = BorderStroke(1.dp, SlateBorders)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: THE PHILOSOPHY (11.sp, letterSpacing = 1.5.sp, #71717A, FontWeight.Bold)
            Text(
                text = "THE PHILOSOPHY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = ZincMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Scannable Value Propositions
            PhilosophyPoint(
                headline = "Cognitive Retention Engine",
                description = "Conceived and engineered to solve cognitive decay in the digital age. Transforms passive reading into permanent, systematic knowledge recall."
            )

            Spacer(modifier = Modifier.height(12.dp))

            PhilosophyPoint(
                headline = "Spaced Repetition & Recall",
                description = "Automates revision cycles across books and terms, enforcing active retrieval exactly when memory traces begin to fade."
            )

            Spacer(modifier = Modifier.height(12.dp))

            PhilosophyPoint(
                headline = "Distraction-Free Ergonomics",
                description = "Hardware-grade Obsidian monochrome aesthetic crafted for sustained focus and maximum reading longevity."
            )
        }
    }
}

@Composable
private fun PhilosophyPoint(
    headline: String,
    description: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = headline,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = CrispWhite
        )
        Text(
            text = description,
            fontSize = 12.sp,
            color = TextBodyPrimary,
            lineHeight = 18.sp
        )
    }
}

/**
 * 3. Lead Architect & Architecture Stack Card
 * Surface #141418, border 1.dp solid #27272F, shape 14.dp, padding 20.dp.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchitectCopyrightCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("about_architect_card"),
        shape = RoundedCornerShape(14.dp),
        color = ZincSurface,
        border = BorderStroke(1.dp, SlateBorders)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: LEAD ARCHITECT & INTELLECTUAL PROPERTY
            Text(
                text = "LEAD ARCHITECT & INTELLECTUAL PROPERTY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = ZincMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Author Attribution Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Designed & Engineered By",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = ZincMuted
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Faizan Khan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrispWhite
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SubduedZinc,
                    border = BorderStroke(1.dp, SlateBorders)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = ZincMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "OFFICIAL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZincMuted,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Copyright notice: © 2026 Faizan Khan. All Rights Reserved.
            Text(
                text = "© 2026 Faizan Khan. All Rights Reserved.",
                fontSize = 12.sp,
                color = ZincMuted
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Obsidian Micro-Pill Badge: OTA Engine Active • Verified
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ZincSurface,
                border = BorderStroke(1.dp, SlateBorders),
                modifier = Modifier.testTag("about_ota_engine_badge")
            ) {
                Text(
                    text = "OTA Engine Active • Verified",
                    color = CrispWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(
                thickness = 0.8.dp,
                color = SlateBorders
            )

            Spacer(modifier = Modifier.height(14.dp))

            // System Architecture Section
            Text(
                text = "TECH ARCHITECTURE",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = ZincMuted
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Compact Technology Stack Chips
            val techTags = listOf(
                "Local-First",
                "Jetpack Compose",
                "Kotlin Coroutines",
                "Room Database",
                "Hardware Keystore",
                "Offline-Ready",
                "Zero Telemetry",
                "Monochrome Design"
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                techTags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SubduedZinc,
                        border = BorderStroke(1.dp, SlateBorders)
                    ) {
                        Text(
                            text = tag,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextBodyMuted,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4. Commission & Privacy-First CTA Deep Links
 * Surface #101013, border 1.dp solid #3F3F46, shape 14.dp, padding 20.dp.
 */
@Composable
private fun CommissionCtaCard(
    onWhatsAppClick: () -> Unit,
    onEmailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("about_commission_card"),
        shape = RoundedCornerShape(14.dp),
        color = SubduedZinc,
        border = BorderStroke(1.dp, CtaBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: COMMISSION & CUSTOM DEVELOPMENT
            Text(
                text = "COMMISSION & CUSTOM DEVELOPMENT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = CrispWhite
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Pitch Copy
            Text(
                text = "Need a high-performance native Android application or an enterprise-grade web platform? Let's engineer your vision with modern speed, precision, and production-ready architecture.",
                fontSize = 12.5.sp,
                color = TextBodyMuted,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Contact Action Buttons (Privacy-first: clean action labels, no exposed credentials)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary WhatsApp Action CTA
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("about_whatsapp_button"),
                    shape = RoundedCornerShape(8.dp),
                    color = CrispWhite,
                    onClick = onWhatsAppClick
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Chat,
                            contentDescription = "WhatsApp",
                            tint = DeepObsidian,
                            modifier = Modifier.size(19.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "Connect via WhatsApp",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepObsidian
                        )
                    }
                }

                // Secondary Direct Email Action CTA
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("about_email_button"),
                    shape = RoundedCornerShape(8.dp),
                    color = ZincSurface,
                    border = BorderStroke(1.dp, SlateBorders),
                    onClick = onEmailClick
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Email,
                            contentDescription = "Email",
                            tint = CrispWhite,
                            modifier = Modifier.size(19.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "Inquire via Email",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CrispWhite
                        )
                    }
                }
            }
        }
    }
}

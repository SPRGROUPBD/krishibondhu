package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.HarvestGold
import com.example.ui.theme.LightGreen
import com.example.ui.theme.MintSurface
import com.example.ui.theme.PrimaryGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    isAppLoading: Boolean,
    onSplashComplete: () -> Unit,
    appVersion: String = BuildConfig.VERSION_NAME,
    buildVersion: Int = BuildConfig.VERSION_CODE
) {
    val logoScale = remember { Animatable(0.78f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        delay(140L)
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
        )
        delay(1300L)
        var waited = 0
        while (isAppLoading && waited < 10) {
            delay(100L)
            waited++
        }
        onSplashComplete()
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            DarkGreen,
            PrimaryGreen,
            DarkGreen
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .drawBehind {
                drawCircle(
                    color = MintSurface.copy(alpha = 0.08f),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.15f, size.height * 0.18f)
                )
                drawCircle(
                    color = HarvestGold.copy(alpha = 0.08f),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * 0.9f, size.height * 0.82f)
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("splash_screen")
    ) {
        // Center: App Logo -> App Name -> App Version
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(132.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
                    .testTag("splash_logo"),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 12.dp,
                border = BorderStroke(3.dp, MintSurface.copy(alpha = 0.85f))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.krishi_bondhu_logo),
                    contentDescription = stringResource(id = R.string.splash_logo_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .testTag("splash_app_name")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .testTag("splash_app_version"),
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, LightGreen.copy(alpha = 0.35f))
            ) {
                Text(
                    text = stringResource(id = R.string.splash_app_version, appVersion),
                    style = MaterialTheme.typography.labelLarge,
                    color = LightGreen,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }

        // Bottom Right: Build Version
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .alpha(textAlpha.value),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.testTag("splash_build_version"),
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.18f),
                border = BorderStroke(0.75.dp, LightGreen.copy(alpha = 0.28f))
            ) {
                Text(
                    text = stringResource(id = R.string.splash_build_version, buildVersion),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = LightGreen.copy(alpha = 0.92f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

package com.moi.lumine.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.moi.lumine.ui.ConfigViewModel
import com.moi.lumine.ui.Screen
import com.moi.lumine.ui.theme.GreenConnect
import com.moi.lumine.ui.theme.LumineSpacing

private val StatusCardHeight = 108.dp
private val MenuCardHeight = 88.dp

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: ConfigViewModel,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val isConnected by viewModel.isVpnActive.collectAsState()
    val selectedConfig by viewModel.selectedConfigDisplayName.collectAsState()
    val vpnStatus by viewModel.vpnStatus.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LumineSpacing.pageHorizontal),
        verticalArrangement = Arrangement.spacedBy(LumineSpacing.cardGap),
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        // ── Brand header ──────────────────────────────────
        item {
            Text(
                text = "Lumine",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "for Android",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // ── Status card ───────────────────────────────────
        item {
            StatusCard(
                isConnected = isConnected,
                vpnPhase = vpnStatus.phase,
                statusMessage = vpnStatus.message,
                isBusy = vpnStatus.phase == "authorizing" || vpnStatus.phase == "starting" || vpnStatus.phase == "stopping"
            ) {
                if (vpnStatus.phase == "authorizing" || vpnStatus.phase == "starting" || vpnStatus.phase == "stopping") {
                    return@StatusCard
                }
                if (isConnected) onStop() else onStart()
            }
        }

        // ── Primary menu: config ──────────────────────────
        item {
            MenuCard(
                title = "配置",
                subtitle = "当前使用：$selectedConfig",
                icon = Icons.Default.Description,
                onClick = { navController.navigate(Screen.Subscriptions.route) }
            )
        }

        // ── Grouped secondary items ───────────────────────
        item {
            SecondaryMenuGroup(
                items = listOf(
                    SecondaryMenuItem(Icons.Default.Tune, "规则") { navController.navigate(Screen.Rules.route) },
                    SecondaryMenuItem(Icons.AutoMirrored.Filled.Assignment, "日志") { navController.navigate(Screen.Logs.route) },
                    SecondaryMenuItem(Icons.Default.QueryStats, "统计") { navController.navigate(Screen.Stats.route) },
                    SecondaryMenuItem(Icons.Default.Settings, "设置") { navController.navigate(Screen.Settings.route) },
                    SecondaryMenuItem(Icons.Default.Info, "关于") { navController.navigate(Screen.About.route) },
                )
            )
        }
    }
}

// ────────────────────────── Status Card ──────────────────────

@Composable
fun StatusCard(
    isConnected: Boolean,
    vpnPhase: String,
    statusMessage: String,
    isBusy: Boolean,
    onClick: () -> Unit
) {
    val titleText = when {
        vpnPhase == "authorizing" -> "等待授权"
        vpnPhase == "starting" -> "启动中"
        vpnPhase == "stopping" -> "停止中"
        vpnPhase == "error" -> "运行异常"
        isConnected -> "已启动"
        else -> "已停止"
    }
    val summaryText = when {
        statusMessage.isNotBlank() && (isConnected || isBusy) -> statusMessage
        isConnected -> "服务运行中"
        else -> "点此启动服务"
    }
    val detailText = statusMessage.takeUnless {
        it.isBlank() || it == summaryText || isConnected || isBusy
    }

    val isActive = isConnected || isBusy

    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(220),
        label = "status_container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "status_content"
    )
    val summaryColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        animationSpec = tween(320),
        label = "status_summary"
    )
    val cardElevation by animateDpAsState(
        targetValue = if (isActive) 6.dp else 1.dp,
        animationSpec = tween(320),
        label = "status_elevation"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1f,
        animationSpec = tween(320),
        label = "status_icon_scale"
    )

    // Pulse animation for connected state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1800
                0f at 0
                0.35f at 600
                0.15f at 1200
                0f at 1800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    val statusIcon = when {
        isConnected -> Icons.Default.CheckCircle
        isBusy -> Icons.Default.Sync
        vpnPhase == "error" -> Icons.Default.Error
        else -> Icons.Default.Cancel
    }

    val cardShape = RoundedCornerShape(LumineSpacing.statusCardCornerSize)
    val gradientAlpha = if (isActive) 0.12f + pulseAlpha * 0.08f else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(StatusCardHeight)
            .clip(cardShape)
            .clickable { onClick() },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle gradient overlay when active
            if (gradientAlpha > 0.01f) {
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = gradientAlpha),
                                    Color.White.copy(alpha = gradientAlpha * 0.3f)
                                )
                            ),
                            shape = cardShape
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LumineSpacing.cardContentPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    tint = if (isConnected) GreenConnect else contentColor
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = titleText,
                        transitionSpec = { statusContentTransform() },
                        label = "status_title"
                    ) { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AnimatedContent(
                        targetState = summaryText,
                        transitionSpec = { statusContentTransform() },
                        label = "status_summary_text"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = summaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (detailText != null) {
                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.bodySmall,
                            color = summaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (isBusy) {
                    Spacer(modifier = Modifier.width(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                }
            }
        }
    }
}

// ────────────────────── Menu Card (primary) ──────────────────

@Composable
fun MenuCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(LumineSpacing.cardCornerSize)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(MenuCardHeight)
            .clip(shape)
            .clickable { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
            supportingContent = {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ──────────────────── Secondary menu group ───────────────────

private data class SecondaryMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@Composable
private fun SecondaryMenuGroup(items: List<SecondaryMenuItem>) {
    val shape = RoundedCornerShape(LumineSpacing.cardCornerSize)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                ListItem(
                    headlineContent = { Text(item.label) },
                    leadingContent = {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { item.onClick() }
                )
            }
        }
    }
}

// ────────────────────── Animation helpers ────────────────────

private fun statusContentTransform(): ContentTransform {
    val duration = 260
    return (fadeIn(animationSpec = tween(durationMillis = duration)) +
        slideInVertically(animationSpec = tween(durationMillis = duration)) { it / 3 }) togetherWith
        (fadeOut(animationSpec = tween(durationMillis = duration)) +
            slideOutVertically(animationSpec = tween(durationMillis = duration)) { -it / 4 })
}

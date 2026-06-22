package com.moi.lumine.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.moi.lumine.RuntimeStatEvent
import com.moi.lumine.ui.ConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavController, viewModel: ConfigViewModel) {
    val stats by viewModel.runtimeStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接统计") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("TCP", stats.tcpConnections, Icons.Default.SwapHoriz, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        StatCard("UDP", stats.udpFlows, Icons.Default.Timeline, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("DNS", stats.dnsQueries, Icons.Default.Dns, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                        StatCard("阻断", stats.blockedRequests, Icons.Default.Block, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    }
                    StatCard("失败", stats.failedRequests, Icons.Default.ErrorOutline, MaterialTheme.colorScheme.error, Modifier.fillMaxWidth())
                }
            }

            item {
                Text(
                    "最近事件",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (stats.recentEvents.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            "VPN 运行并产生连接后，这里会显示 DNS 与连接事件。",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(stats.recentEvents.asReversed()) {
                    StatEventItem(it)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Long, icon: ImageVector, accentTint: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accentTint)
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatEventItem(event: RuntimeStatEvent) {
    val accent = when (event.outcome) {
        "blocked" -> Color(0xFFB86E00)
        "failed" -> Color(0xFFB3261E)
        else -> MaterialTheme.colorScheme.primary
    }
    ListItem(
        headlineContent = {
            Text(
                "${event.type} ${event.outcomeLabel()}",
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Column {
                Text(event.target, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = listOfNotNull(event.mode, event.detail).joinToString("  ")
                if (detail.isNotBlank()) {
                    Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        trailingContent = {
            Text(event.time, style = MaterialTheme.typography.labelSmall)
        }
    )
}

private fun RuntimeStatEvent.outcomeLabel(): String {
    return when (outcome) {
        "blocked" -> "已阻断"
        "failed" -> "失败"
        else -> "通过"
    }
}

package com.moi.lumine.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.moi.lumine.BuildConfig
import com.moi.lumine.ui.theme.LumineSpacing
import mobile.Mobile

private const val UpstreamUrl = "https://github.com/moi-si/lumine"
private const val AndroidInspirationUrl = "https://github.com/SniShaper/lumine-for-android"
private const val LicenseUrl = "https://github.com/FaQxD233/lumine-android/blob/main/LICENSE"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = LumineSpacing.pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(LumineSpacing.cardGap),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            item {
                AboutHeader()
            }
            item {
                VersionCard()
            }
            item {
                LinkSection(
                    rows = listOf(
                        LinkRowData(Icons.Default.Code, "Lumine 原项目", "核心代理实现来源", UpstreamUrl),
                        LinkRowData(Icons.Default.Description, "开源许可", "查看当前仓库许可证", LicenseUrl)
                    ),
                    onOpen = { uriHandler.openUri(it) }
                )
            }
            item {
                AcknowledgementSection(
                    rows = listOf(
                        LinkRowData(Icons.Default.Code, "感谢 Lumine 原项目", "感谢 moi-si/lumine 提供核心代理实现基础", UpstreamUrl),
                        LinkRowData(Icons.Default.Info, "感谢 Android 版灵感来源", "感谢 SniShaper/lumine-for-android 提供 Android 版实现灵感", AndroidInspirationUrl)
                    ),
                    onOpen = { uriHandler.openUri(it) }
                )
            }
        }
    }
}

@Composable
private fun AboutHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Lumine",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "for Android",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
            )
        }
    }
}

@Composable
private fun VersionCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            InfoRow(Icons.Default.Tag, "应用版本", BuildConfig.VERSION_NAME)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(Icons.Default.Info, "版本代码", BuildConfig.VERSION_CODE.toString())
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(Icons.Default.Security, "核心版本", Mobile.getVersion())
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

private data class LinkRowData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val url: String
)

@Composable
private fun LinkSection(rows: List<LinkRowData>, onOpen: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                ListItem(
                    headlineContent = { Text(row.title) },
                    supportingContent = {
                        Text(
                            row.subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = {
                        Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        IconButton(onClick = { onOpen(row.url) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "打开链接", modifier = Modifier.size(20.dp))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    }
}

@Composable
private fun AcknowledgementSection(rows: List<LinkRowData>, onOpen: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Text(
                "鸣谢",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
            )
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                ListItem(
                    headlineContent = { Text(row.title) },
                    supportingContent = {
                        Text(
                            row.subtitle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = {
                        Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        IconButton(onClick = { onOpen(row.url) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "打开链接", modifier = Modifier.size(20.dp))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    }
}

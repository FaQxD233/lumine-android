package com.moi.lumine.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.moi.lumine.model.AppProxyMode
import com.moi.lumine.model.InstalledAppInfo
import com.moi.lumine.ui.ConfigViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppProxyScreen(navController: NavController, viewModel: ConfigViewModel) {
    val mode by viewModel.appProxyMode.collectAsState()
    val selectedPackages by viewModel.selectedAppPackages.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isLoading by viewModel.isLoadingInstalledApps.collectAsState()
    var query by remember { mutableStateOf("") }
    var showSelectedOnly by remember { mutableStateOf(false) }

    val filteredApps = remember(installedApps, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            installedApps
        } else {
            installedApps.filter {
                it.label.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle)
            }
        }
    }.let { apps ->
        remember(apps, selectedPackages, showSelectedOnly) {
            if (showSelectedOnly) {
                apps.filter { it.packageName in selectedPackages }
            } else {
                apps
            }
        }
    }
    val visiblePackages = remember(filteredApps) { filteredApps.map { it.packageName }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用代理") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshInstalledApps() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Apps")
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
                AppProxyModeCard(
                    mode = mode,
                    selectedCount = selectedPackages.size,
                    onModeChange = viewModel::setAppProxyMode
                )
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("搜索应用") }
                )
            }

            item {
                AppProxyBulkActions(
                    visibleCount = filteredApps.size,
                    selectedCount = selectedPackages.size,
                    showSelectedOnly = showSelectedOnly,
                    onToggleSelectedOnly = { showSelectedOnly = !showSelectedOnly },
                    onSelectVisible = {
                        viewModel.setSelectedAppPackages(selectedPackages + visiblePackages)
                    },
                    onClearSelected = {
                        viewModel.setSelectedAppPackages(emptySet())
                    }
                )
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                }
            }

            items(filteredApps, key = { it.packageName }) { app ->
                AppPackageItem(
                    app = app,
                    selected = app.packageName in selectedPackages,
                    onToggle = { viewModel.toggleAppPackage(app.packageName) }
                )
            }
        }
    }
}

@Composable
private fun AppProxyBulkActions(
    visibleCount: Int,
    selectedCount: Int,
    showSelectedOnly: Boolean,
    onToggleSelectedOnly: () -> Unit,
    onSelectVisible: () -> Unit,
    onClearSelected: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "显示 $visibleCount 个应用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilterChip(
                selected = showSelectedOnly,
                onClick = onToggleSelectedOnly,
                label = { Text("只看已选") }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onSelectVisible,
                enabled = visibleCount > 0
            ) {
                Text("全选结果")
            }
            TextButton(
                onClick = onClearSelected,
                enabled = selectedCount > 0
            ) {
                Text("清空选择")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AppProxyModeCard(
    mode: AppProxyMode,
    selectedCount: Int,
    onModeChange: (AppProxyMode) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Apps, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("代理范围", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "已选择 $selectedCount 个应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = mode == AppProxyMode.All,
                    onClick = { onModeChange(AppProxyMode.All) },
                    label = { Text("全部") }
                )
                FilterChip(
                    selected = mode == AppProxyMode.BypassSelected,
                    onClick = { onModeChange(AppProxyMode.BypassSelected) },
                    label = { Text("绕过选中") }
                )
                FilterChip(
                    selected = mode == AppProxyMode.OnlySelected,
                    onClick = { onModeChange(AppProxyMode.OnlySelected) },
                    label = { Text("仅选中") }
                )
            }
        }
    }
}

@Composable
private fun AppPackageItem(
    app: InstalledAppInfo,
    selected: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    )
}

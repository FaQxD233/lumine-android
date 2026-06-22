package com.moi.lumine.ui.screens

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.moi.lumine.ui.ConfigViewModel
import com.moi.lumine.ui.Screen
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(navController: NavController, viewModel: ConfigViewModel) {
    val config by viewModel.currentConfig.collectAsState()
    val selectedConfigName by viewModel.selectedConfigName.collectAsState()
    val blockQuic by viewModel.blockQuic.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val externalEditorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.reloadSelectedConfig()
        coroutineScope.launch {
            snackbarHostState.showSnackbar("已重新读取配置：$selectedConfigName")
        }
    }

    var dnsAddr by remember { mutableStateOf(config.dnsAddr) }
    var logLevel by remember { mutableStateOf(sanitizeLogLevel(config.logLevel)) }
    LaunchedEffect(config.dnsAddr, config.logLevel) {
        dnsAddr = config.dnsAddr
        logLevel = sanitizeLogLevel(config.logLevel)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全局设置") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val normalizedDnsAddr = dnsAddr.trim()
                        if (normalizedDnsAddr.isBlank()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("上游 DNS 不能为空")
                            }
                            return@IconButton
                        }
                        val updatedConfig = config.copy(
                            dnsAddr = normalizedDnsAddr,
                            logLevel = sanitizeLogLevel(logLevel)
                        )
                        viewModel.updateConfig(updatedConfig)
                        viewModel.saveConfig()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("已保存，重启 VPN 后生效")
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text("核心设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = dnsAddr,
                onValueChange = { dnsAddr = it },
                label = { Text("上游 DNS") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://... 或 1.1.1.1:53") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = { navController.navigate(Screen.AppProxy.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Apps, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("应用代理范围")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("阻止 QUIC (UDP/443)", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = blockQuic,
                    onCheckedChange = { viewModel.setBlockQuic(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    runCatching {
                        val uri = viewModel.getSelectedConfigEditUri()
                        val editIntent = Intent(Intent.ACTION_EDIT).apply {
                            setDataAndType(uri, "text/plain")
                            clipData = ClipData.newUri(
                                context.contentResolver,
                                "$selectedConfigName.json",
                                uri
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            putExtra(Intent.EXTRA_TITLE, "$selectedConfigName.json")
                            putExtra(
                                Intent.EXTRA_MIME_TYPES,
                                arrayOf("application/json", "text/plain")
                            )
                        }
                        externalEditorLauncher.launch(
                            Intent.createChooser(editIntent, "选择文本编辑器")
                        )
                    }.onFailure { error ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(error.message ?: "没有找到可用的文本编辑器")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("用文本编辑器修改 JSON")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("日志级别", style = MaterialTheme.typography.labelLarge)
            SupportedLogLevels.forEach { level ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = (logLevel == level), onClick = { logLevel = level })
                    Text(level, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private val SupportedLogLevels = listOf("DEBUG", "INFO", "ERROR")

private fun sanitizeLogLevel(value: String): String {
    val normalized = value.trim().uppercase(Locale.US)
    return if (normalized in SupportedLogLevels) normalized else "INFO"
}

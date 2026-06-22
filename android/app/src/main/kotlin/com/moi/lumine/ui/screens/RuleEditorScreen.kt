package com.moi.lumine.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.moi.lumine.ui.ConfigViewModel
import com.moi.lumine.model.Policy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(navController: NavController, viewModel: ConfigViewModel, type: String) {
    val config by viewModel.currentConfig.collectAsState()
    val key by viewModel.editingRuleKey.collectAsState()
    val ruleKey = key
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setEditingRule(null)
        }
    }

    if (ruleKey == null) {
        LaunchedEffect(Unit) {
            navController.popBackStack()
        }
        return
    }

    val existingPolicy = if (type == "domain") {
        config.domainPolicies[ruleKey]
    } else {
        config.ipPolicies[ruleKey]
    }
    val isNewRule = existingPolicy == null
    val initialPolicy = existingPolicy ?: Policy()

    var mode by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.mode ?: "tls-rf") }
    var host by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.host ?: "") }
    var mapTo by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.mapTo ?: "") }
    var port by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.port?.toString().orEmpty()) }
    var dnsMode by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.dnsMode ?: "") }
    var connectTimeout by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.connectTimeout ?: "") }
    var tls13Only by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.tls13Only ?: false) }
    var fakeTtl by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.fakeTtl?.toString().orEmpty()) }
    var maxTtl by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.maxTtl?.toString().orEmpty()) }
    var attempts by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.attempts?.toString().orEmpty()) }
    var fakeSleep by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.fakeSleep ?: "") }
    var singleTimeout by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.singleTimeout ?: "") }
    var numRecords by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.numRecords?.toString().orEmpty()) }
    var numSegs by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.numSegs?.toString().orEmpty()) }
    var sendInterval by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.sendInterval ?: "") }
    var waitForAck by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.waitForAck ?: false) }
    var modMinorVer by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.modMinorVer ?: false) }
    var oob by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.oob ?: false) }
    var oobEx by remember(ruleKey, initialPolicy) { mutableStateOf(initialPolicy.oobEx ?: false) }

    val portError = port.trim().toOptionalIntOrNull()?.let { it !in 0..65535 } == true
    val fakeTtlError = fakeTtl.trim().toOptionalIntOrNull()?.let { it !in 0..255 } == true
    val maxTtlError = maxTtl.trim().toOptionalIntOrNull()?.let { it !in 2..255 } == true
    val attemptsError = attempts.trim().toOptionalIntOrNull()?.let { it < 1 } == true
    val numRecordsError = numRecords.trim().toOptionalIntOrNull()?.let { it <= 0 } == true
    val numSegsError = numSegs.trim().toOptionalIntOrNull()?.let { it == 0 || it < -1 } == true
    val hasNumberError = listOf(portError, fakeTtlError, maxTtlError, attemptsError, numRecordsError, numSegsError).any { it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNewRule) "新建规则" else "编辑规则",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !hasNumberError,
                        onClick = {
                        val updatedPolicy = initialPolicy.copy(
                            mode = mode,
                            host = host.ifEmpty { null },
                            mapTo = mapTo.ifEmpty { null },
                            port = port.trim().toOptionalIntOrNull(),
                            dnsMode = dnsMode.ifEmpty { null },
                            connectTimeout = connectTimeout.ifEmpty { null },
                            tls13Only = tls13Only,
                            fakeTtl = fakeTtl.trim().toOptionalIntOrNull(),
                            maxTtl = maxTtl.trim().toOptionalIntOrNull(),
                            attempts = attempts.trim().toOptionalIntOrNull(),
                            fakeSleep = fakeSleep.ifEmpty { null },
                            singleTimeout = singleTimeout.ifEmpty { null },
                            numRecords = numRecords.trim().toOptionalIntOrNull(),
                            numSegs = numSegs.trim().toOptionalIntOrNull(),
                            sendInterval = sendInterval.ifEmpty { null },
                            waitForAck = waitForAck,
                            modMinorVer = modMinorVer,
                            oob = oob,
                            oobEx = oobEx
                        )
                        val updatedConfig = if (type == "domain") {
                            config.copy(domainPolicies = config.domainPolicies + (ruleKey to updatedPolicy))
                        } else {
                            config.copy(ipPolicies = config.ipPolicies + (ruleKey to updatedPolicy))
                        }
                        viewModel.updateConfig(updatedConfig)
                        viewModel.saveConfig()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("已保存，重启 VPN 后生效")
                            delay(350)
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("规则路径", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(shortRuleKeyForEditor(ruleKey), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text("代理模式 (Mode)", style = MaterialTheme.typography.labelLarge)
            }

            val modes = listOf("tls-rf", "raw", "direct", "block", "tls-alert", "ttl-d")
            items(modes.size, key = { modes[it] }) { index ->
                val m = modes[index]
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = (mode == m), onClick = { mode = m })
                    Text(m, modifier = Modifier.padding(start = 8.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("目标主机 (Host Overwrite)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如 1.1.1.1、2607:... 或 self") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = mapTo,
                    onValueChange = { mapTo = it.trim() },
                    label = { Text("映射到 (Map To)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如 127.0.0.1:8080") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("端口 (Port)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("留空使用原始端口") },
                    isError = portError,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = connectTimeout,
                    onValueChange = { connectTimeout = it.trim() },
                    label = { Text("连接超时 (connect_timeout)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如 10s") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                DnsModeSelector(
                    dnsMode = dnsMode,
                    onChange = { dnsMode = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = tls13Only, onCheckedChange = { tls13Only = it })
                    Text("仅限 TLS 1.3")
                }
                if (mode == "ttl-d") {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("TTL-D 参数", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    NumberField(fakeTtl, { fakeTtl = it }, "fake_ttl", "留空自动探测", fakeTtlError)
                    NumberField(maxTtl, { maxTtl = it }, "max_ttl", "默认 64", maxTtlError)
                    NumberField(attempts, { attempts = it }, "attempts", "默认 2", attemptsError)
                    OutlinedTextField(
                        value = fakeSleep,
                        onValueChange = { fakeSleep = it.trim() },
                        label = { Text("fake_sleep") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如 200ms") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = singleTimeout,
                        onValueChange = { singleTimeout = it.trim() },
                        label = { Text("single_timeout") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如 500ms") },
                        singleLine = true
                    )
                }
                if (mode == "tls-rf") {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("TLS-RF 参数", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    NumberField(numRecords, { numRecords = it }, "num_records", "默认 1", numRecordsError)
                    NumberField(numSegs, { numSegs = it }, "num_segs", "默认 1，-1 自动", numSegsError)
                    OutlinedTextField(
                        value = sendInterval,
                        onValueChange = { sendInterval = it.trim() },
                        label = { Text("send_interval") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如 20ms") },
                        singleLine = true
                    )
                    ToggleRow("wait_for_ack", waitForAck) { waitForAck = it }
                    ToggleRow("mod_minor_ver", modMinorVer) { modMinorVer = it }
                    ToggleRow("oob", oob) { oob = it }
                    ToggleRow("oob_ex", oobEx) { oobEx = it }
                }
            }
        }
    }
}

@Composable
private fun DnsModeSelector(dnsMode: String, onChange: (String) -> Unit) {
    Text("DNS 模式 (dns_mode)", style = MaterialTheme.typography.labelLarge)
    val modes = listOf("" to "默认", "prefer_ipv4" to "prefer_ipv4", "prefer_ipv6" to "prefer_ipv6", "ipv4_only" to "ipv4_only", "ipv6_only" to "ipv6_only")
    modes.forEach { (value, label) ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected = dnsMode == value, onClick = { onChange(value) })
            Text(label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isError: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() || char == '-' }.take(5)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        isError = isError,
        singleLine = true
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun shortRuleKeyForEditor(key: String, limit: Int = 600): String {
    return if (key.length <= limit) {
        key
    } else {
        key.take(limit) + "\n... (${key.length} chars)"
    }
}

private fun String.toOptionalIntOrNull(): Int? {
    if (isBlank()) {
        return null
    }
    return toIntOrNull()
}

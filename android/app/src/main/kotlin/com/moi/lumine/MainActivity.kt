package com.moi.lumine

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.moi.lumine.ui.ConfigViewModel
import com.moi.lumine.ui.Screen
import com.moi.lumine.ui.screens.*
import com.moi.lumine.ui.theme.LumineTheme
import mobile.Mobile

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // Light/dark status bar will be driven by the composable below
        setContent {
            LumineTheme {
                val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
                val view = window.decorView
                SideEffect {
                    val insetsController = WindowInsetsControllerCompat(window, view)
                    insetsController.isAppearanceLightStatusBars = !darkTheme
                    insetsController.isAppearanceLightNavigationBars = !darkTheme
                }
                MainContainer()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 1001
    }
}

@Composable
fun MainContainer() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val viewModel: ConfigViewModel = viewModel()
    val vpnStatus by viewModel.vpnStatus.collectAsState()
    var toggleLocked by rememberSaveable { mutableStateOf(false) }
    var lastToggleAt by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(vpnStatus.phase) {
        if (vpnStatus.phase != "authorizing" && vpnStatus.phase != "starting" && vpnStatus.phase != "stopping") {
            kotlinx.coroutines.delay(650)
            toggleLocked = false
        }
    }
    
    val vpnRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            VpnRuntimeState.setStatus("starting", "权限已授予，正在启动服务")
            VpnServiceController.start(context, viewModel.selectedConfigName.value)
        } else {
            VpnRuntimeState.setStatus("idle", "VPN 权限未授予")
            toggleLocked = false
        }
    }

    fun startVpn() {
        val now = SystemClock.elapsedRealtime()
        if (toggleLocked || now - lastToggleAt < 650L) {
            return
        }
        toggleLocked = true
        lastToggleAt = now
        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            VpnRuntimeState.setStatus("authorizing", "等待 VPN 权限授权")
            vpnRequestLauncher.launch(vpnIntent)
        } else {
            VpnRuntimeState.setStatus("starting", "正在启动服务")
            VpnServiceController.start(context, viewModel.selectedConfigName.value)
        }
    }

    fun stopVpn() {
        val now = SystemClock.elapsedRealtime()
        if (toggleLocked || now - lastToggleAt < 650L) {
            return
        }
        toggleLocked = true
        lastToggleAt = now
        VpnRuntimeState.setStatus("stopping", "正在停止代理")
        runCatching {
            VpnServiceController.stop(context)
        }.onFailure {
            VpnRuntimeState.setStatus("error", "停止服务失败")
            toggleLocked = false
        }
    }

    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { drawerEnterFromRight() },
            exitTransition = { drawerExitToLeft() },
            popEnterTransition = { drawerEnterFromLeft() },
            popExitTransition = { drawerExitToRight() }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    navController = navController,
                    viewModel = viewModel,
                    onStart = { startVpn() },
                    onStop = { stopVpn() }
                )
            }
            composable(Screen.Subscriptions.route) { SubscriptionScreen(navController, viewModel) }
            composable(Screen.Rules.route) { RuleListScreen(navController, viewModel) }
            composable(
                Screen.RuleDetail.route,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: ""
                RuleEditorScreen(navController, viewModel, type)
            }
            composable(Screen.Settings.route) { GlobalSettingsScreen(navController, viewModel) }
            composable(Screen.AppProxy.route) { AppProxyScreen(navController, viewModel) }
            composable(Screen.Logs.route) { LogScreen(navController, viewModel) }
            composable(Screen.Stats.route) { StatsScreen(navController, viewModel) }
            composable(Screen.About.route) { AboutScreen(navController) }
        }
    }
}

private const val NAV_DRAWER_ANIMATION_MS = 260

private fun drawerEnterFromRight(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = NAV_DRAWER_ANIMATION_MS,
            easing = FastOutSlowInEasing
        ),
        initialOffsetX = { width -> width }
    )
}

private fun drawerExitToLeft(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = NAV_DRAWER_ANIMATION_MS,
            easing = FastOutSlowInEasing
        ),
        targetOffsetX = { width -> -width / 4 }
    )
}

private fun drawerEnterFromLeft(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = NAV_DRAWER_ANIMATION_MS,
            easing = FastOutSlowInEasing
        ),
        initialOffsetX = { width -> -width / 4 }
    )
}

private fun drawerExitToRight(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = NAV_DRAWER_ANIMATION_MS,
            easing = FastOutSlowInEasing
        ),
        targetOffsetX = { width -> width }
    )
}


@Composable
fun TestScreen(onStart: () -> Unit) {
    var version by remember { mutableStateOf("Loading...") }
    val spliceMsg = remember { Mobile.helloSplice() }

    LaunchedEffect(Unit) {
        version = Mobile.getVersion()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Lumine Mobile", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Version: $version")
        Text(text = "Syscall Test: $spliceMsg")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onStart) {
            Text("Start VPN")
        }
    }
}

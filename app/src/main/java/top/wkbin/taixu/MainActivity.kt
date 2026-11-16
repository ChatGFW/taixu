package top.wkbin.taixu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.ui.onboarding.OnboardingScreen
import top.wkbin.taixu.ui.onboarding.OnboardingViewModel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.datastore.AppearancePreferences
import top.wkbin.taixu.core.model.AppUpdateInfo
import top.wkbin.taixu.core.network.AppUpdateManager
import top.wkbin.taixu.runtime.service.RuntimeServiceController
import top.wkbin.taixu.ui.components.ProvideClassicToolStatusDots
import top.wkbin.taixu.ui.components.ProvideRuntimeWidthClass
import top.wkbin.taixu.ui.navigation.TaiXuNavHost
import top.wkbin.taixu.ui.navigation.openOnboardingAutostartSettings
import top.wkbin.taixu.ui.InterfaceScale
import top.wkbin.taixu.ui.theme.TaiXuTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import top.wkbin.taixu.core.common.navigation.AppNavigationTarget
import top.wkbin.taixu.core.common.navigation.GlobalNavigationBus
import top.wkbin.taixu.harness.mcp.oauth.McpOAuthCoordinator
import top.wkbin.taixu.harness.mcp.McpManager
import top.wkbin.taixu.service.adb.AdbNotificationManager
import top.wkbin.taixu.navigation.ShareIntentExtractor
import top.wkbin.taixu.service.WorkflowForegroundService
import top.wkbin.taixu.ui.chat.floating.FloatingChatService
import org.koin.android.ext.android.getKoin
import top.wkbin.taixu.core.database.repository.BotGroupSessionRepository
import top.wkbin.taixu.navigation.AgentNotificationIntentExtractor
import top.wkbin.taixu.core.model.ThemePalette
import top.wkbin.taixu.runtime.virtualdisplay.TwinPhonePresence

class MainActivity : AppCompatActivity() {
    val settingsDataStore: AppearancePreferences by inject()
    val appUpdateManager: AppUpdateManager by inject()
    val runtimeServiceController: RuntimeServiceController by inject()
    val globalNavigationBus: GlobalNavigationBus by inject()
    val adbNotificationManager: AdbNotificationManager by inject()
    val mcpOAuthCoordinator: McpOAuthCoordinator by inject()
    val mcpManager: McpManager by inject()
    private val botGroupSessions: BotGroupSessionRepository? get() =
        runCatching { getKoin().getOrNull<BotGroupSessionRepository>() }.getOrNull()

    /** Splash 等到 onboarding 偏好就绪。 */
    private val keepSplashOnScreen: MutableState<Boolean> = mutableStateOf(true)
    private var createdUptimeMs: Long = 0L
    private val twinPipHost = TwinPhonePipHost(this)
    private val interfaceScale by lazy { InterfaceScale(resources) }
    private val densityEpoch = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        createdUptimeMs = SystemClock.uptimeMillis()
        val splashScreen = installSplashScreen()
        splashScreen.setupTaiXuSplashAnimation { keepSplashOnScreen.value }
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(twinPipHost)
        handleNavigationIntent(intent)
        enableEdgeToEdge()
        // 缩放写 Resources 必须在组合外完成：Dialog / Popup 的新 ComposeView 只认 Context 密度。
        lifecycleScope.launch {
            settingsDataStore.appFontScale.distinctUntilChanged().collect { requested ->
                if (interfaceScale.apply(requested)) densityEpoch.intValue++
            }
        }
        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val dynamicColorEnabled by settingsDataStore.dynamicColorEnabled.collectAsStateWithLifecycle(initialValue = true)
            val themePalette by settingsDataStore.themePalette.collectAsStateWithLifecycle(initialValue = ThemePalette())
            val classicToolStatusDots by settingsDataStore.classicToolStatusDots.collectAsStateWithLifecycle(initialValue = false)
            val systemDark = isSystemInDarkTheme()
            val fontScale = LocalDensity.current.fontScale
            // densityEpoch 只作为重组句柄：系统 dpi 变化发生在组合外，LocalDensity 不能自动感知。
            densityEpoch.intValue
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }

            CompositionLocalProvider(
                LocalDensity provides interfaceScale.densityFor(fontScale),
            ) {
                TaiXuTheme(
                    darkTheme = isDark,
                    dynamicColor = dynamicColorEnabled,
                    palette = themePalette,
                ) {
                    ProvideClassicToolStatusDots(enabled = classicToolStatusDots) {
                        val onboardingViewModel: OnboardingViewModel = koinViewModel()
                        val onboarding by onboardingViewModel.status.collectAsStateWithLifecycle()

                        LaunchedEffect(onboarding.loaded) {
                            if (onboarding.loaded && keepSplashOnScreen.value) {
                                val elapsed = SystemClock.uptimeMillis() - createdUptimeMs
                                val remaining = (420L - elapsed).coerceAtLeast(0L)
                                if (remaining > 0L) delay(remaining)
                                keepSplashOnScreen.value = false
                                Log.i(
                                    "TaiXuStartup",
                                    "splash dismissed in ${SystemClock.uptimeMillis() - createdUptimeMs}ms",
                                )
                            }
                        }

                        var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
                        val currentVersionName = rememberAppVersionName()

                        LaunchedEffect(onboarding.completed) {
                            if (onboarding.completed) {
                                val autoCheck = settingsDataStore.autoCheckUpdates.first()
                                if (autoCheck) {
                                    appUpdateManager.checkUpdate(currentVersionName).onSuccess { info ->
                                        if (info.hasUpdate) updateInfo = info
                                    }
                                }
                            }
                        }

                        AppUpdatePrompt(
                            updateInfo = updateInfo,
                            onDismiss = { updateInfo = null },
                            appUpdateManager = appUpdateManager,
                            startActivity = { startActivity(it) },
                        )

                        ProvideRuntimeWidthClass {
                            when {
                                !onboarding.loaded -> Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = RuntimeTheme.colorScheme.primary)
                                }
                                onboarding.completed -> TaiXuNavHost(globalNavigationBus = globalNavigationBus)
                                else -> OnboardingScreen(
                                    onboardingViewModel,
                                    onOpenAutostartSettings = { openOnboardingAutostartSettings() },
                                    onNotificationGranted = { startRuntimeServiceWhenNotificationAllowed() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (interfaceScale.acceptSystemConfiguration(newConfig)) densityEpoch.intValue++
    }

    override fun onResume() {
        super.onResume()
        // 系统可能在后台重置 Resources；回来时按已保存的缩放重写并触发 Density 重算。
        if (interfaceScale.apply(interfaceScale.scale)) densityEpoch.intValue++
        runCatching { FloatingChatService.stop(this) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        twinPipHost.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        twinPipHost.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(targetIntent: Intent?) {
        val intentToHandle = targetIntent ?: intent ?: return
        val oauthUri = intentToHandle.data
        if (intentToHandle.action == Intent.ACTION_VIEW && oauthUri != null &&
            oauthUri.scheme == "taixu" && oauthUri.host == "oauth" && oauthUri.path == "/mcp"
        ) {
            lifecycleScope.launch {
                runCatching { mcpOAuthCoordinator.callback(oauthUri) }
                    .onSuccess { result ->
                        if (result is McpOAuthCoordinator.CallbackResult.Authorized) {
                            lifecycleScope.launch { mcpManager.invalidateServer(result.serverId) }
                        }
                    }
                    .onFailure { /* UI observes server auth state; never log callback code/token. */ }
            }
            return
        }
        val action = intentToHandle.action
        TwinPhonePresence.consumeOpenTwinIntent(intentToHandle)
        ShareIntentExtractor.extractSharedText(intentToHandle)?.let { sharedText ->
            globalNavigationBus.navigateTo(AppNavigationTarget.SharedText(sharedText))
            return
        }
        val navigateTo = intentToHandle.getStringExtra("navigate_to")
        val isAdbLogcat = action == "top.wkbin.taixu.action.OPEN_ADB_LOGCAT" || navigateTo == "adb_logcat"
        if (isAdbLogcat) globalNavigationBus.navigateTo(AppNavigationTarget.AdbLogcat)
        if (action == WorkflowForegroundService.ACTION_OPEN_RUN) {
            globalNavigationBus.navigateTo(
                AppNavigationTarget.WorkflowRun(
                    intentToHandle.getStringExtra(WorkflowForegroundService.EXTRA_EXECUTION_ID),
                ),
            )
        }
        if (intentToHandle.data?.scheme == "taixu" && intentToHandle.data?.authority == "agent-notification") {
            lifecycleScope.launch {
                AgentNotificationIntentExtractor.extractTarget(intentToHandle, botGroupSessions)
                    ?.let(globalNavigationBus::navigateTo)
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        window.decorView.post {
            if (!isFinishing && !isDestroyed) startRuntimeServiceWhenNotificationAllowed()
        }
    }

    private fun startRuntimeServiceWhenNotificationAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runtimeServiceController.start()
            (application as? TaiXuApplication)?.refreshAgentNotifications()
        }
    }
}

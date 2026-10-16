package top.wkbin.taixu.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.feature.onboarding.R
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf

/** Welcome is local UI state; installation and completion use the existing workflow. */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel? = previewOrNull { koinViewModel() },
    previewPage: Int = -1,
) {
    val page by viewModel?.page?.collectAsStateWithLifecycle() ?: previewStateOf(previewPage)
    val runtime by viewModel?.runtimeState?.collectAsStateWithLifecycle() ?: previewStateOf(RuntimeState.Ready)
    val importing by viewModel?.isImporting?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    var started by rememberSaveable { mutableStateOf(previewPage >= 0) }
    val stage = if (!started && page <= 0) 0 else if (page <= 0) 1 else 2
    val busy = runtime is RuntimeState.Initializing || importing
    BackHandler(enabled = stage == 1 && !busy) { started = false }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (stage == 1 && !busy) {
                    RuntimeIconButton(onClick = { started = false }, contentDescription = stringResource(R.string.onboarding_back)) {
                        RuntimeIcon(RuntimeIconName.Back, Modifier.size(24.dp))
                    }
                } else {
                    Box(Modifier.size(48.dp))
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    repeat(3) { index ->
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (index == stage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(width = if (index == stage) 32.dp else 8.dp, height = 8.dp),
                        ) {}
                    }
                }
                Text(stringResource(R.string.onboarding_step, stage + 1), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            }
        },
        bottomBar = {
            if (stage == 0) {
                Box(Modifier.fillMaxWidth().padding(WindowInsets.navigationBars.asPaddingValues())
                    .padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                    RuntimeButton(onClick = { started = true }, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                        Text(stringResource(R.string.onboarding_start), modifier = Modifier.padding(4.dp))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(targetState = stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboardingStage") { current ->
                when (current) {
                    0 -> OnboardingWelcomePage(Modifier.fillMaxSize())
                    1 -> SystemSetupPage(viewModel, Modifier.widthIn(max = 840.dp).fillMaxSize())
                    else -> ModelSetupPage(viewModel, Modifier.widthIn(max = 840.dp).fillMaxSize())
                }
            }
        }
    }
}

package top.wkbin.taixu.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.onboarding.R
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName

@Composable
internal fun OnboardingWelcomePage(modifier: Modifier = Modifier) {
    Box(modifier.verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.onboarding_welcome), style = RuntimeTheme.typography.headlineLarge,
                    color = RuntimeTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                Text(stringResource(R.string.onboarding_welcome_description), style = RuntimeTheme.typography.bodyLarge,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            RuntimeCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                WelcomeFeature(RuntimeIconName.Terminal, stringResource(R.string.onboarding_feature_linux),
                    stringResource(R.string.onboarding_feature_linux_description))
                WelcomeFeature(RuntimeIconName.Sparkles, stringResource(R.string.onboarding_feature_agent),
                    stringResource(R.string.onboarding_feature_agent_description))
                WelcomeFeature(RuntimeIconName.Folder, stringResource(R.string.onboarding_feature_workspace),
                    stringResource(R.string.onboarding_feature_workspace_description))
            }
        }
    }
}

@Composable
private fun WelcomeFeature(icon: RuntimeIconName, title: String, description: String) {
    BasicComponent(
        title = title,
        summary = description,
        startAction = {
            RuntimeIcon(icon, Modifier.padding(end = 16.dp).size(26.dp), tint = RuntimeTheme.colorScheme.primary)
        },
    )
}

@Composable
internal fun OnboardingPageHeading(
    title: String,
    description: String,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = RuntimeTheme.typography.headlineLarge)
        Text(description, style = RuntimeTheme.typography.bodyLarge, color = RuntimeTheme.colorScheme.onSurfaceVariant)
    }
}

package top.wkbin.taixu.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import top.wkbin.taixu.ui.components.RuntimeButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import top.wkbin.taixu.ui.components.RuntimeCheckboxPreference
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.onboarding.R
import top.wkbin.taixu.feature.components.R as ComponentsR
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeUsageNoticeContent
import top.wkbin.taixu.ui.components.runtimeContentPadding

@Composable
internal fun OnboardingDisclaimerPage(
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = runtimeContentPadding(horizontal = 12.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(ComponentsR.string.runtime_notice_title), style = RuntimeTheme.typography.headlineMedium)
                Text(stringResource(R.string.onboarding_notice_intro), style = RuntimeTheme.typography.bodyMedium,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { RuntimeUsageNoticeContent() }
    }
}

/** The acknowledgement and primary action stay together outside the scrollable notice. */
@Composable
internal fun OnboardingDisclaimerActions(
    acknowledged: Boolean,
    onAcknowledge: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().background(RuntimeTheme.colorScheme.surface)
        .padding(WindowInsets.navigationBars.asPaddingValues()), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RuntimeCard(contentPadding = PaddingValues(0.dp)) {
                top.wkbin.taixu.ui.components.RuntimeCheckboxPreference(
                    title = stringResource(R.string.onboarding_notice_acknowledge),
                    checked = acknowledged,
                    onCheckedChange = onAcknowledge,
                )
            }
            RuntimeButton(onClick = onContinue, enabled = acknowledged, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_notice_continue))
            }
        }
    }
}

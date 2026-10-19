package top.wkbin.taixu.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.onboarding.R
import top.wkbin.taixu.feature.components.R as ComponentsR
import top.wkbin.taixu.ui.components.RuntimeCheckbox
import top.wkbin.taixu.ui.components.RuntimeIllustration
import top.wkbin.taixu.ui.components.RuntimeIllustrationScene
import top.wkbin.taixu.ui.components.RuntimeUsageNoticeContent
import top.wkbin.taixu.ui.components.runtimeContentPadding

@Composable
internal fun OnboardingDisclaimerPage(
    acknowledged: Boolean,
    onAcknowledge: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = runtimeContentPadding(horizontal = 24.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RuntimeIllustration(RuntimeIllustrationScene.Notice, Modifier.height(140.dp))
                Text(stringResource(ComponentsR.string.runtime_notice_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.onboarding_notice_intro), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { RuntimeUsageNoticeContent() }
        item {
            Row(
                Modifier.fillMaxWidth().toggleable(acknowledged, role = Role.Checkbox, onValueChange = onAcknowledge)
                    .heightIn(min = 48.dp).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeCheckbox(acknowledged, onCheckedChange = null)
                Text(stringResource(R.string.onboarding_notice_acknowledge),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

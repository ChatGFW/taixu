package top.wkbin.taixu.ui.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.git.R
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.TaixuGitEmptyIllustration

// ----------------------------------------------------------------------
// Git 空态：非仓库整屏缺省页 + 各页签空提示，统一使用主题自适应插图
// ----------------------------------------------------------------------

@Composable
internal fun NotARepoContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            TaixuGitEmptyIllustration()
            Text(
                stringResource(R.string.fgit_not_repo_title),
                style = RuntimeTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(R.string.fgit_not_repo_hint),
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
internal fun EmptyHint(text: String) {
    RuntimeCard(
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TaixuGitEmptyIllustration()
            Text(
                text,
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

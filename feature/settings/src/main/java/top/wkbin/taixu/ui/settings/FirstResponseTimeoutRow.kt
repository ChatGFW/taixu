package top.wkbin.taixu.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.core.model.FirstResponseTimeoutPolicy
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf

/** 全局「等待模型首个响应」上限。0 为关闭，其余为 90–600 秒。 */
class FirstResponseTimeoutViewModel(private val preferences: AgentPreferences) : ViewModel() {
    val seconds = preferences.firstResponseTimeoutSeconds.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        FirstResponseTimeoutPolicy.DEFAULT_SECONDS,
    )

    fun setSeconds(value: Int) {
        viewModelScope.launch { preferences.setFirstResponseTimeoutSeconds(value) }
    }
}

@Composable
internal fun FirstResponseTimeoutRow(
    viewModel: FirstResponseTimeoutViewModel? = previewOrNull { koinViewModel() },
) {
    val seconds by viewModel?.seconds?.collectAsStateWithLifecycle()
        ?: previewStateOf(FirstResponseTimeoutPolicy.DEFAULT_SECONDS)
    val enabled = seconds > FirstResponseTimeoutPolicy.DISABLED
    var lastEnabled by remember { mutableIntStateOf(FirstResponseTimeoutPolicy.DEFAULT_SECONDS) }
    if (enabled) lastEnabled = seconds
    var sliderSeconds by remember(seconds) {
        mutableFloatStateOf(seconds.coerceAtLeast(FirstResponseTimeoutPolicy.MIN_ENABLED_SECONDS).toFloat())
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsDensity.rowHorizontalPadding, vertical = SettingsDensity.rowVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_first_response_timeout_title),
                modifier = Modifier.weight(1f),
                style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (enabled) {
                    stringResource(R.string.settings_first_response_timeout_value, sliderSeconds.toInt())
                } else {
                    stringResource(R.string.settings_first_response_timeout_off)
                },
                style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace),
                color = RuntimeTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RuntimeSwitch(
                checked = enabled,
                onCheckedChange = { turnOn ->
                    val restored = lastEnabled.coerceIn(
                        FirstResponseTimeoutPolicy.MIN_ENABLED_SECONDS,
                        FirstResponseTimeoutPolicy.MAX_SECONDS,
                    )
                    viewModel?.setSeconds(if (turnOn) restored else FirstResponseTimeoutPolicy.DISABLED)
                },
            )
        }
        Text(
            text = stringResource(R.string.settings_first_response_timeout_summary),
            style = settingsSupportingStyle(),
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (enabled) {
            Slider(
                value = sliderSeconds,
                onValueChange = { sliderSeconds = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
                valueRange = FirstResponseTimeoutPolicy.MIN_ENABLED_SECONDS.toFloat()..
                    FirstResponseTimeoutPolicy.MAX_SECONDS.toFloat(),
                onValueChangeFinished = {
                    viewModel?.setSeconds(
                        sliderSeconds.toInt().coerceIn(
                            FirstResponseTimeoutPolicy.MIN_ENABLED_SECONDS,
                            FirstResponseTimeoutPolicy.MAX_SECONDS,
                        ),
                    )
                },
            )
        }
    }
}

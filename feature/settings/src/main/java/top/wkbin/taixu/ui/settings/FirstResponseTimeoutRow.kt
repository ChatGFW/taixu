package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import top.wkbin.taixu.ui.miuix.Slider
import top.wkbin.taixu.ui.miuix.Switch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val onEnabledChange by rememberUpdatedState { turnOn: Boolean ->
        val restored = lastEnabled.coerceIn(
            FirstResponseTimeoutPolicy.MIN_ENABLED_SECONDS,
            FirstResponseTimeoutPolicy.MAX_SECONDS,
        )
        viewModel?.setSeconds(if (turnOn) restored else FirstResponseTimeoutPolicy.DISABLED)
    }

    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        endActions = {
            Switch(
                checked = enabled,
                onCheckedChange = { onEnabledChange(it) },
            )
        },
        onClick = { onEnabledChange(!enabled) },
        role = Role.Switch,
        bottomAction = if (enabled) {
            {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.settings_first_response_timeout_value,
                            sliderSeconds.toInt(),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Slider(
                        value = sliderSeconds,
                        onValueChange = { sliderSeconds = it },
                        modifier = Modifier.fillMaxWidth(),
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
        } else {
            null
        },
    ) {
        Text(
            text = stringResource(R.string.settings_first_response_timeout_title),
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.settings_first_response_timeout_summary),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

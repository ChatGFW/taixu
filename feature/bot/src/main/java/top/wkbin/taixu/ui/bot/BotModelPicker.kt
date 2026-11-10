package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.RuntimeDropdownField

@Composable
internal fun BotModelPicker(
    profiles: List<AiModelEntity>, profileId: String?, variant: String?, enabled: Boolean,
    onChange: (String?, String?) -> Unit,
) {
    val profile = profiles.firstOrNull { it.id == profileId }
    val defaultLabel = stringResource(R.string.fbot_model_default)
    val invalidLabel = stringResource(R.string.fbot_model_invalid)
    val profileDefaultLabel = stringResource(R.string.fbot_profile_default)
    Column(Modifier.fillMaxWidth()) {
        val profileOptions = remember(profiles, defaultLabel) {
            listOf(null as String? to defaultLabel) + profiles.map { it.id to "${it.name} · ${it.provider}" }
        }
        val profileIndex = when {
            profileId == null -> 0
            profiles.any { it.id == profileId } -> profileOptions.indexOfFirst { it.first == profileId }.coerceAtLeast(0)
            else -> 0
        }
        RuntimeDropdownField(
            title = stringResource(R.string.fbot_provider_profile),
            items = if (profileId != null && profile == null) {
                profileOptions.map { it.second } + invalidLabel
            } else {
                profileOptions.map { it.second }
            },
            selectedIndex = if (profileId != null && profile == null) profileOptions.size else profileIndex,
            onSelectedIndexChange = { index ->
                val id = profileOptions.getOrNull(index)?.first
                onChange(id, null)
            },
            enabled = enabled,
        )
        if (profile != null) {
            val variants = remember(profile.model) {
                profile.model.split(',').map(String::trim).filter(String::isNotBlank).distinct()
            }
            val modelOptions = remember(variants, profileDefaultLabel) {
                listOf(null as String? to profileDefaultLabel) + variants.map { it to it }
            }
            val modelIndex = modelOptions.indexOfFirst { it.first == variant }.let { if (it < 0) 0 else it }
            RuntimeDropdownField(
                title = stringResource(R.string.fbot_concrete_model),
                items = modelOptions.map { it.second },
                selectedIndex = modelIndex,
                onSelectedIndexChange = { index -> onChange(profile.id, modelOptions[index].first) },
                enabled = enabled,
            )
        }
        Text(
            stringResource(R.string.fbot_model_scope_hint),
            style = RuntimeTheme.typography.bodySmall,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
        )
    }
}

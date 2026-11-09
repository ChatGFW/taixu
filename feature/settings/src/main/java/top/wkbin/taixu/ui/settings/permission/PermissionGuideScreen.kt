package top.wkbin.taixu.ui.settings.permission

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.settings.SettingsDetailCard
import top.wkbin.taixu.ui.theme.semanticColors
import androidx.compose.foundation.layout.FlowRow
import top.wkbin.taixu.ui.components.RuntimeDropdownField
import top.wkbin.taixu.ui.components.RuntimeFilterChip
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.settings.successStatusColor
import top.wkbin.taixu.ui.settings.warningStatusColor
import top.wkbin.taixu.ui.components.RuntimeSettingsScaffold

@Composable
fun PermissionGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 品牌/主题选择迁移 rememberSaveable，旋转后不重置
    var selectedBrand by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(OemBrand.detect()) }
    var selectedTopic by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(KeepaliveTopic.AUTOSTART) }

    // 状态刷新
    var statusMap by remember { mutableStateOf(mapOf<KeepaliveTopic, Boolean>()) }
    fun refreshStatuses() {
        statusMap = KeepaliveTopic.entries.associateWith { topic ->
            PermissionGuideRepository.checkStatus(context, topic)
        }
    }

    LaunchedEffect(Unit) {
        refreshStatuses()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshStatuses()
    }

    val steps = remember(selectedBrand, selectedTopic) {
        PermissionGuideRepository.getSteps(selectedBrand, selectedTopic)
    }

    RuntimeSettingsScaffold(
        title = "后台保活与权限向导", onBack = onBack, modifier = modifier,
    ) {
        // 1. 顶部当前品牌选择与提示卡片
        item {
            SettingsDetailCard(
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column {
                            Text(
                                text = "当前适配厂商机型",
                                style = RuntimeTheme.typography.labelMedium,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${selectedBrand.label} (${selectedBrand.osName})",
                                style = RuntimeTheme.typography.titleMedium,
                                color = RuntimeTheme.colorScheme.primary,
                            )
                        }
                        RuntimeDropdownField(
                            title = "切换品牌",
                            items = OemBrand.entries.map { brand -> "${brand.label} (${brand.osName})" },
                            selectedIndex = OemBrand.entries.indexOf(selectedBrand),
                            onSelectedIndexChange = { index ->
                                OemBrand.entries.getOrNull(index)?.let { selectedBrand = it }
                            },
                        )
                    }

                    Text(
                        text = "国产定制系统后台清理机制较为激进，为了保证 PRoot 沙箱、后台编译任务与 Agent 长时间思考不被冻结杀掉，建议根据指引完成配置。",
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        // 2. 权限专题选择滑动条 (Horizontal Tabs)
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(KeepaliveTopic.entries) { topic ->
                    val isSelected = topic == selectedTopic
                    val isGranted = statusMap[topic] == true
                    val hasRealStatus = topic == KeepaliveTopic.BATTERY_UNRESTRICTED ||
                        topic == KeepaliveTopic.NOTIFICATION ||
                        topic == KeepaliveTopic.FLOATING_OVERLAY

                    RuntimeFilterChip(
                        selected = isSelected, onClick = { selectedTopic = topic },
                        shape = CircleShape,
                        label = { Text(topic.title, style = RuntimeTheme.typography.labelLarge) },
                        leadingIcon = { RuntimeIcon(topic.icon, Modifier.size(18.dp)) },
                        trailingIcon = if (hasRealStatus) ({
                            Box(Modifier.size(8.dp).clip(CircleShape)
                                .background(if (isGranted) successStatusColor() else warningStatusColor())
                                .semantics { contentDescription = if (isGranted) "权限已授权" else "权限未授权" })
                        }) else null,
                    )
                }
            }
        }

        // 3. 当前专题说明与直达系统设置入口
        item {
            val isGranted = statusMap[selectedTopic] == true
            val hasRealStatus = selectedTopic == KeepaliveTopic.BATTERY_UNRESTRICTED ||
                selectedTopic == KeepaliveTopic.NOTIFICATION ||
                selectedTopic == KeepaliveTopic.FLOATING_OVERLAY

            SettingsDetailCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RuntimeIcon(
                                name = selectedTopic.icon,
                                modifier = Modifier.size(20.dp),
                                tint = RuntimeTheme.colorScheme.primary,
                            )
                            Text(
                                text = selectedTopic.title,
                                style = RuntimeTheme.typography.titleMedium,
                            )
                        }

                        if (hasRealStatus) {
                            Surface(
                                color = if (isGranted) semanticColors().successContainer else semanticColors().warningContainer,
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = if (isGranted) "已放行 / 已就绪" else "待配置 / 未豁免",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = RuntimeTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = if (isGranted) semanticColors().onSuccessContainer else semanticColors().onWarningContainer,
                                )
                            }
                        }
                    }

                    Text(
                        text = selectedTopic.subtitle,
                        style = RuntimeTheme.typography.bodyMedium,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )

                    RuntimeButton(
                        onClick = {
                            launchSettingIntents(context, selectedBrand, selectedTopic)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RuntimeIcon(RuntimeIconName.OpenInNew, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("直达系统对应设置页")
                    }
                }
            }
        }

        // 4. 分步指引卡片列表
        item {
            Text(
                text = "配置步骤详解",
                style = RuntimeTheme.typography.labelLarge,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }

        items(steps.indices.toList()) { index ->
            val step = steps[index]
            SettingsDetailCard(
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // 序号小圆球
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(RuntimeTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = RuntimeTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = step.title,
                            style = RuntimeTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = RuntimeTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = step.description,
                            style = RuntimeTheme.typography.bodyMedium,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                        )
                        if (step.tip != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "💡 ${step.tip}",
                                style = RuntimeTheme.typography.bodySmall,
                                color = RuntimeTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun launchSettingIntents(context: Context, brand: OemBrand, topic: KeepaliveTopic) {
    val intents = PermissionGuideRepository.buildLaunchIntents(context, brand, topic)
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (_: Throwable) {
            // 继续尝试降级 intent
        }
    }
    Toast.makeText(context, "无法直接跳转，请在系统设置的应用管理中手动配置", Toast.LENGTH_LONG).show()
}

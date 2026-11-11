package top.wkbin.taixu.ui.settings.skill

import androidx.compose.foundation.background
import top.wkbin.taixu.ui.theme.semanticColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.ButtonDefaults
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.skill.AuditLevel
import top.wkbin.taixu.core.model.skill.CompatibilityLevel
import top.wkbin.taixu.core.tools.skill.SkillInstallInspection
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
/**
 * 技能端侧静态安全审计与权限审查弹窗（基于 RuntimeAlertDialog 与 Miuix 设计规范）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillSecurityAuditDialog(
    inspection: SkillInstallInspection,
    isCommitting: Boolean,
    onConfirmInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val audit = inspection.auditReport
    val compat = inspection.compatibilityResult
    val pkg = inspection.pkg
    val isBlocked = inspection.isBlocked

    val badgeColor = when (audit.level) {
        AuditLevel.SAFE -> semanticColors().success
        AuditLevel.INFO -> RuntimeTheme.colorScheme.primary
        AuditLevel.WARNING -> semanticColors().warning
        AuditLevel.DANGER -> semanticColors().warning
        AuditLevel.BLOCKED -> RuntimeTheme.colorScheme.error
    }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                RuntimeIcon(
                    name = if (isBlocked) RuntimeIconName.Alert else RuntimeIconName.Shield,
                    tint = badgeColor,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = if (isBlocked) "安全阻断：禁止安装" else "技能安装审查",
                    style = RuntimeTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "端侧静态安全与权限边界",
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            // AlertDialog 的 text 槽包在 Box 里：多个平级子项会叠在同一位置。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 1. 技能基本信息卡片
                RuntimeCard(
                    containerColor = RuntimeTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RuntimeTheme.shapes.small,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = pkg.manifest.name,
                                style = RuntimeTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                color = badgeColor.copy(alpha = 0.15f),
                                shape = RuntimeTheme.shapes.small,
                            ) {
                                Text(
                                    text = audit.level.label,
                                    color = badgeColor,
                                    style = RuntimeTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (pkg.manifest.description.isNotBlank()) {
                            Text(
                                text = pkg.manifest.description,
                                style = RuntimeTheme.typography.bodySmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = buildString {
                                append("版本 v${pkg.manifest.version}")
                                if (pkg.manifest.author.isNotBlank()) {
                                    append(" · 作者 ")
                                    append(pkg.manifest.author)
                                }
                            },
                            style = RuntimeTheme.typography.labelSmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 2. 权限边界申请
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "申请权限与操作边界",
                        style = RuntimeTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val allPerms = (audit.declaredPermissions + audit.detectedPermissions).toList()
                    if (allPerms.isEmpty()) {
                        Text(
                            text = "纯只读提示词扩展，无需申请外部系统权限",
                            style = RuntimeTheme.typography.bodySmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            allPerms.forEach { perm ->
                                val isHighRisk = perm.isHighRisk
                                Surface(
                                    color = if (isHighRisk) {
                                        RuntimeTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                    } else {
                                        RuntimeTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RuntimeTheme.shapes.chip,
                                ) {
                                    Text(
                                        text = perm.label,
                                        style = RuntimeTheme.typography.labelSmall,
                                        fontWeight = if (isHighRisk) FontWeight.Medium else FontWeight.Normal,
                                        color = if (isHighRisk) {
                                            RuntimeTheme.colorScheme.error
                                        } else {
                                            RuntimeTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 兼容性评估结果
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "环境兼容性判定",
                        style = RuntimeTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = compat.level.label,
                        style = RuntimeTheme.typography.bodySmall,
                        color = when (compat.level) {
                            CompatibilityLevel.COMPATIBLE -> semanticColors().success
                            CompatibilityLevel.PARTIALLY_COMPATIBLE -> semanticColors().warning
                            CompatibilityLevel.INCOMPATIBLE -> RuntimeTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.Medium,
                    )
                    if (compat.missingTools.isNotEmpty()) {
                        Text(
                            text = "缺少依赖工具: ${compat.missingTools.joinToString()}",
                            style = RuntimeTheme.typography.bodySmall,
                            color = RuntimeTheme.colorScheme.error,
                        )
                    }
                }

                // 4. 静态审计发现项
                if (audit.findings.isNotEmpty()) {
                    Text(
                        text = "审计发现项 (${audit.findings.size})",
                        style = RuntimeTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isBlocked) RuntimeTheme.colorScheme.error else RuntimeTheme.colorScheme.onSurface,
                    )

                    audit.findings.take(6).forEach { finding ->
                        val itemColor = when (finding.level) {
                            AuditLevel.BLOCKED, AuditLevel.DANGER -> RuntimeTheme.colorScheme.error
                            AuditLevel.WARNING -> semanticColors().warning
                            else -> RuntimeTheme.colorScheme.onSurfaceVariant
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RuntimeTheme.shapes.chip)
                                .background(itemColor.copy(alpha = 0.08f))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "[${finding.ruleId}] ${finding.title}",
                                style = RuntimeTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = itemColor,
                            )
                            Text(
                                text = finding.detail,
                                style = RuntimeTheme.typography.bodySmall,
                                color = RuntimeTheme.colorScheme.onSurface,
                            )
                            finding.snippet?.let { snippet ->
                                Text(
                                    text = snippet,
                                    style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RuntimeTheme.shapes.chip)
                                        .background(Color.Black.copy(alpha = 0.05f))
                                        .padding(6.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "静态审计仅为初步防线，无法识别所有混淆与恶意行为；安装后该技能脚本的实际执行仍会逐条申请审批。",
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (isBlocked) {
                RuntimeTextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RuntimeTheme.colorScheme.error,
                    ),
                ) {
                    Text("已阻断")
                }
            } else {
                RuntimeTextButton(
                    onClick = onConfirmInstall,
                    enabled = !isCommitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (audit.hasWarnings) {
                            semanticColors().warning
                        } else {
                            RuntimeTheme.colorScheme.primary
                        },
                    ),
                ) {
                    if (isCommitting) {
                        RuntimeCircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = RuntimeTheme.colorScheme.onPrimary,
                        )
                        Text("安装中…")
                    } else {
                        Text(if (audit.hasWarnings) "确认安装" else "通过并安装")
                    }
                }
            }
        },
        dismissButton = {
            RuntimeTextButton(onClick = onDismiss) {
                Text(if (isBlocked) "关闭" else "放弃")
            }
        },
    )
}

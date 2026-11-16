package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.FileArtifactOrigin
import top.wkbin.taixu.harness.TaskDelivery
import top.wkbin.taixu.harness.task.DeliveryArtifactKind
import top.wkbin.taixu.harness.task.DeliveryGap
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.miuix.HorizontalDivider
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.theme.semanticColors
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.basic.Text

/** A transcript-bound delivery record, rendered as one compact change summary. */
@Composable
internal fun TaskDeliveryCard(
    message: TaskDelivery,
    workspace: String,
) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(true) }
    val artifacts = remember(message.report.artifacts) { message.report.artifacts.filter {
        it.kind != DeliveryArtifactKind.OUTPUT_LOG && it.origin != FileArtifactOrigin.IMAGE_READ
    }.asReversed().distinctBy { it.path }.asReversed() }
    if (artifacts.isEmpty()) return

    val edits = remember(artifacts) { artifacts.mapNotNull { artifact ->
        artifact.lineChanges?.takeIf { artifact.kind == DeliveryArtifactKind.FILE }
    } }
    val added = remember(edits) { edits.sumOf { it.added } }
    val removed = remember(edits) { edits.sumOf { it.removed } }
    val apkCount = remember(artifacts) { artifacts.count { it.kind == DeliveryArtifactKind.APK } }
    val fileCount = artifacts.size - apkCount

    RuntimeCard(
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .squircleBackground(RuntimeTheme.colorScheme.primaryContainer, 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(
                    RuntimeIconName.Edit,
                    modifier = Modifier.size(18.dp),
                    tint = RuntimeTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        fileCount > 0 && apkCount > 0 -> stringResource(R.string.delivery_counts, fileCount, apkCount)
                        fileCount > 0 -> stringResource(R.string.delivery_files, fileCount)
                        else -> stringResource(R.string.delivery_apks, apkCount)
                    },
                    style = RuntimeTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (edits.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.delivery_line_changes), style = RuntimeTheme.typography.labelSmall)
                        Text(
                            text = "+$added",
                            style = RuntimeTheme.typography.labelSmall,
                            color = semanticColors().success,
                            maxLines = 1,
                        )
                        Text(
                            text = "−$removed",
                            style = RuntimeTheme.typography.labelSmall,
                            color = RuntimeTheme.colorScheme.error,
                            maxLines = 1,
                        )
                    }
                }
            }
            RuntimeTextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(if (expanded) R.string.delivery_collapse else R.string.delivery_view_changes),
                    style = RuntimeTheme.typography.labelMedium,
                    color = RuntimeTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
        message.report.gaps.forEach { gap ->
            Text(
                text = stringResource(gapLabel(gap)),
                style = RuntimeTheme.typography.labelSmall,
                color = if (gap == DeliveryGap.GOAL_NOT_VERIFIED) RuntimeTheme.colorScheme.onSurfaceVariant
                    else semanticColors().warning,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (expanded) {
            HorizontalDivider(
                color = RuntimeTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            artifacts.forEach { artifact ->
                DeliveryArtifactRow(artifact, workspace)
            }
        }
    }
}

internal fun kindLabel(kind: DeliveryArtifactKind): Int = when (kind) {
    DeliveryArtifactKind.APK -> R.string.delivery_kind_apk
    DeliveryArtifactKind.IMAGE -> R.string.delivery_kind_image
    else -> R.string.delivery_kind_file
}

internal fun originLabel(origin: FileArtifactOrigin): Int = when (origin) {
    FileArtifactOrigin.NEW_AFTER_COMMAND -> R.string.delivery_origin_new
    FileArtifactOrigin.CHANGED_AFTER_COMMAND -> R.string.delivery_origin_changed
    FileArtifactOrigin.UNCHANGED_AFTER_COMMAND -> R.string.delivery_origin_unchanged
    FileArtifactOrigin.OBSERVED_AFTER_COMMAND -> R.string.delivery_origin_observed
    FileArtifactOrigin.AFTER_DOWNLOAD -> R.string.delivery_origin_download
    FileArtifactOrigin.IMAGE_READ -> R.string.delivery_origin_image_read
    FileArtifactOrigin.AFTER_WRITE -> R.string.delivery_origin_write
    FileArtifactOrigin.AFTER_EDIT -> R.string.delivery_origin_edit
}

private fun gapLabel(gap: DeliveryGap): Int = when (gap) {
    DeliveryGap.GOAL_NOT_VERIFIED -> R.string.delivery_unverified
    DeliveryGap.NO_TOOL_EVIDENCE -> R.string.delivery_no_evidence
    DeliveryGap.HISTORY_INCOMPLETE -> R.string.delivery_history_incomplete
    DeliveryGap.UNKNOWN_OUTCOME -> R.string.delivery_unknown_outcome
    DeliveryGap.PENDING_RESULT -> R.string.delivery_pending_result
    DeliveryGap.FAILED_TOOL -> R.string.delivery_failed_tool
    DeliveryGap.INCOMPLETE_ARTIFACT -> R.string.delivery_incomplete_artifact
    DeliveryGap.INVALID_ARTIFACT -> R.string.delivery_invalid_artifact
}

package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotAssignmentStatus
import top.wkbin.taixu.core.model.bot.BotAvatarState
import top.wkbin.taixu.core.model.bot.GroupMessage
import top.wkbin.taixu.core.model.bot.GroupSenderType
import top.wkbin.taixu.core.model.bot.PixelAvatarRecipe
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton

/**
 * Full-screen pixel office. Members walk to a desk, the whiteboard, the racks or the sofa
 * as their run state changes. Tap a character to mention them or open their profile.
 */
@Composable
internal fun GroupCyberStudio(
    members: List<Bot>,
    messages: List<GroupMessage>,
    assignments: List<BotAssignment>,
    onSelectBot: (Bot) -> Unit,
    onOpenBot: (String) -> Unit,
    onCancelAssignment: (String) -> Unit,
    onContinueAssignment: suspend (String, String, String) -> Unit,
    onBackToChat: () -> Unit,
    modifier: Modifier = Modifier,
    waitingSessions: List<OfficeWait> = emptyList(),
    footer: @Composable () -> Unit = {},
) {
    var continueTarget by rememberSaveable { mutableStateOf<String?>(null) }
    assignments.firstOrNull { it.id == continueTarget }?.let { task ->
        GroupAssignmentContinueDialog(task, { continueTarget = null }, onContinueAssignment)
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedId, members) {
        if (selectedId != null && members.none { it.id == selectedId }) selectedId = null
    }
    val streamingBySender = remember(messages) { messages.filter { it.isStreaming }.associateBy { it.senderId } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val assignmentByBot = remember(assignments, now) {
        assignments.groupBy { it.botId }.mapValues { (_, tasks) -> displayAssignment(tasks, now) }
    }
    val states = remember(members, streamingBySender, assignmentByBot) {
        members.associate { bot ->
            bot.id to computeMemberAvatarState(bot, streamingBySender[bot.id], assignmentByBot[bot.id])
        }
    }
    val actors = remember(members, states, waitingSessions, now) {
        PixelOfficeLayout.applyWaiting(
            PixelOfficeLayout.place(members, states),
            members.associate { it.id to it.sessionId },
            waitingSessions,
            now,
        )
    }
    val recipes = remember(members) {
        members.associate { bot ->
            bot.id to (PixelAvatarRecipe.decode(bot.avatarSeed) ?: PixelAvatarRecipe.fromName(bot.id))
        }
    }
    val speakers = buildList {
        actors.filter { it.waitSeconds != null }.forEach { add(it.botId) }
        PixelOfficeLayout.speakerIds(actors, selectedId).forEach { if (it !in this) add(it) }
    }.take(2)
    val idleLine = stringResource(R.string.fbot_pixel_office_idle_line)
    val waitingLine = stringResource(R.string.fbot_pixel_office_waiting)
    val spoken = members.associate { bot ->
        val state = states[bot.id] ?: BotAvatarState.IDLE
        val waiting = actors.any { it.botId == bot.id && it.waitSeconds != null }
        bot.id to when {
            waiting -> waitingLine
            state == BotAvatarState.BLOCKED -> assignmentByBot[bot.id]?.error?.takeIf { it.isNotBlank() }
                ?: assignmentByBot[bot.id]?.summary?.takeIf { it.isNotBlank() }
                ?: officeStatus(state)
            state == BotAvatarState.IDLE -> idleLine
            else -> liveStatusText(streamingBySender[bot.id], assignmentByBot[bot.id]) ?: officeStatus(state)
        }
    }

    val selected = members.firstOrNull { it.id == selectedId }
    val selectedTask = selected?.let { bot ->
        assignments.firstOrNull { it.botId == bot.id && it.status.isActive }
            ?: assignments.firstOrNull { it.botId == bot.id }
    }

    Box(modifier.fillMaxSize().testTag("group-cyber-studio")) {
        Column(Modifier.fillMaxSize()) {
            GroupPixelOffice(
                actors = actors,
                recipes = recipes,
                bubbles = speakers.filter { states[it] != BotAvatarState.IDLE || it == selectedId }
                    .associateWith { spoken[it].orEmpty() },
                objective = assignments.filter { it.status.isActive }.maxByOrNull { it.startedAt }?.title
                    ?: messages.filter { it.senderType == GroupSenderType.USER && it.content.isNotBlank() }
                        .maxByOrNull { it.timestamp }?.content,
                screens = members.associate { bot ->
                    val step = streamingBySender[bot.id]?.steps?.lastOrNull { it.success == null }
                    bot.id to step?.name.orEmpty()
                }.filterValues { it.isNotBlank() },
                selectedId = selectedId,
                emptyHint = if (members.isEmpty()) stringResource(R.string.fbot_pixel_office_empty) else null,
                onSelect = { id -> selectedId = if (selectedId == id) null else id },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            if (selected != null) {
                OfficeActorCard(
                    bot = selected,
                    state = states[selected.id] ?: BotAvatarState.IDLE,
                    task = selectedTask,
                    liveText = spoken[selected.id],
                    onMention = { onSelectBot(selected) },
                    onProfile = { onOpenBot(selected.id) },
                    onCancel = { selectedTask?.let { onCancelAssignment(it.id) } },
                    onContinue = { selectedTask?.let { continueTarget = it.id } },
                    onDismiss = { selectedId = null },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            footer()
        }
        PixelBackSign(onBackToChat)
    }
}

@Composable
private fun PixelBackSign(onBack: () -> Unit) {
    val ink = Color(0xFF3A2A1E)
    val wood = Color(PixelOfficeRoom.WOOD)
    val paper = Color(0xFFF6F0E4)
    val backLabel = stringResource(R.string.fbot_cyber_studio_back_chat)
    Box(
        Modifier
            .statusBarsPadding()
            .padding(start = 12.dp, top = 8.dp)
            .size(42.dp)
            .testTag("studio-back-to-chat-fab")
            .semantics { contentDescription = backLabel }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.minDimension / 12f
            fun block(x: Int, y: Int, w: Int, h: Int, color: Color) {
                drawRect(color, Offset(x * cell, y * cell), Size(w * cell, h * cell))
            }
            block(0, 1, 12, 10, ink)
            block(1, 2, 10, 8, wood)
            block(2, 3, 8, 6, paper)
            block(3, 5, 6, 2, ink)
            block(3, 4, 2, 4, ink)
            block(4, 3, 2, 2, ink)
            block(4, 7, 2, 2, ink)
        }
    }
}

@Composable
private fun OfficeActorCard(
    bot: Bot,
    state: BotAvatarState,
    task: BotAssignment?,
    liveText: String?,
    onMention: () -> Unit,
    onProfile: () -> Unit,
    onCancel: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RuntimeCard(
        modifier = modifier.fillMaxWidth().testTag("studio-select-card"),
        containerColor = Color(0xFFF6F0E4),
        borderColor = Color(PixelOfficeRoom.TRIM),
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bot.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = bot.role.ifBlank { officeStatus(state) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RuntimeIconButton(onClick = onDismiss, contentDescription = stringResource(R.string.fbot_cancel)) {
                RuntimeIcon(RuntimeIconName.Close, Modifier.size(18.dp))
            }
        }
        if (task != null) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!liveText.isNullOrBlank() && state != BotAvatarState.IDLE) {
            Text(
                text = liveText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RuntimeTextButton(
                onClick = onMention,
                modifier = Modifier.testTag("studio-actor-mention"),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) { Text(stringResource(R.string.fbot_cyber_studio_mention_action), maxLines = 1) }
            RuntimeTextButton(
                onClick = onProfile,
                modifier = Modifier.testTag("studio-actor-profile"),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) { Text(stringResource(R.string.fbot_cyber_studio_profile_action), maxLines = 1) }
            if (task != null && task.status.isActive) {
                RuntimeTextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) { Text(stringResource(R.string.fbot_work_cancel), maxLines = 1) }
            }
            if (task?.independent == true) {
                RuntimeTextButton(
                    onClick = onContinue,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(
                            if (task.status.isActive) R.string.fbot_assignment_add_instruction
                            else R.string.fbot_assignment_continue,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private const val SUCCESS_POSE_MS = 45_000L

/** Active work stays visible. A finished task celebrates briefly, then the bot goes back to idle. */
private fun displayAssignment(tasks: List<BotAssignment>, now: Long): BotAssignment? {
    tasks.firstOrNull { it.status.isActive }?.let { return it }
    val latest = tasks.maxByOrNull { it.finishedAt ?: it.startedAt } ?: return null
    return when (latest.status) {
        BotAssignmentStatus.BLOCKED, BotAssignmentStatus.FAILED -> latest
        BotAssignmentStatus.COMPLETED -> {
            val finished = latest.finishedAt ?: latest.startedAt
            if (now - finished <= SUCCESS_POSE_MS) latest else null
        }
        else -> null
    }
}

@Composable
private fun officeStatus(state: BotAvatarState): String = when (state) {
    BotAvatarState.THINKING -> stringResource(R.string.fbot_desk_status_thinking)
    BotAvatarState.EXECUTING -> stringResource(R.string.fbot_desk_status_executing)
    BotAvatarState.ACTIVE -> stringResource(R.string.fbot_desk_status_active)
    BotAvatarState.BLOCKED -> stringResource(R.string.fbot_desk_status_blocked)
    BotAvatarState.SUCCESS -> stringResource(R.string.fbot_desk_status_success)
    BotAvatarState.FAILED -> stringResource(R.string.fbot_desk_status_failed)
    BotAvatarState.IDLE -> stringResource(R.string.fbot_desk_status_idle)
}

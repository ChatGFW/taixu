package top.wkbin.taixu.ui.bot

import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.BotAvatarState

/** Where a bot stands in the pixel office, in room pixels. Feet sit at [x], [y]. */
internal enum class OfficePose { STAND, SIT, TYPE, THINK }

internal data class OfficeSeat(val id: String, val x: Int, val y: Int, val pose: OfficePose)

internal data class OfficeActor(
    val botId: String,
    val name: String,
    val x: Int,
    val y: Int,
    val pose: OfficePose,
    val state: BotAvatarState,
    val waitSeconds: Int? = null,
)


internal data class OfficeWait(val sessionId: String, val createdAt: Long, val expiresAt: Long)

/** Shifts each bot onto its own beat so the room does not breathe in unison. */
internal fun officeFrameOffset(botId: String): Int = (botId.hashCode() and Int.MAX_VALUE) % 3

/**
 * Assigns each member a stable spot in the office.
 * Busy bots walk to the whiteboard, desk or server racks; idle bots take the sofa.
 */
internal object PixelOfficeLayout {
    private val desks = listOf(
        OfficeSeat("desk", 28, 224, OfficePose.TYPE),
        OfficeSeat("desk-b", 72, 224, OfficePose.TYPE),
        OfficeSeat("desk-c", 28, 272, OfficePose.TYPE),
        OfficeSeat("desk-d", 72, 272, OfficePose.TYPE),
    )
    private val board = listOf(OfficeSeat("board", 92, 164, OfficePose.THINK))
    private val sofas = listOf(
        OfficeSeat("sofa-l", 40, 376, OfficePose.SIT),
        OfficeSeat("sofa-r", 116, 378, OfficePose.SIT),
        OfficeSeat("sofa-c", 140, 138, OfficePose.SIT),
    )
    private val trouble = listOf(OfficeSeat("coffee", 150, 252, OfficePose.STAND))
    private val floor = listOf(
        OfficeSeat("meet", 112, 138, OfficePose.STAND),
        OfficeSeat("hall", 70, 410, OfficePose.STAND),
        OfficeSeat("window", 174, 144, OfficePose.STAND),
        OfficeSeat("poster", 164, 380, OfficePose.STAND),
    )

    fun place(members: List<Bot>, states: Map<String, BotAvatarState>): List<OfficeActor> {
        val used = mutableSetOf<String>()
        return members.sortedBy { it.id }.map { bot ->
            val state = states[bot.id] ?: BotAvatarState.IDLE
            val wanted = poseFor(state)
            val preferred = when (state) {
                BotAvatarState.THINKING -> board
                BotAvatarState.EXECUTING, BotAvatarState.ACTIVE -> desks
                BotAvatarState.BLOCKED, BotAvatarState.FAILED -> trouble
                BotAvatarState.IDLE, BotAvatarState.SUCCESS -> sofas
                else -> floor
            }
            val seat = claim(used, preferred, floor)
            OfficeActor(bot.id, bot.name, seat.x, seat.y, resolvePose(wanted, seat.id), state)
        }
    }

    /** Marks bots whose session is waiting on an approval or a user question. */
    fun applyWaiting(
        actors: List<OfficeActor>,
        sessionOf: Map<String, String>,
        waits: List<OfficeWait>,
        now: Long,
    ): List<OfficeActor> {
        if (waits.isEmpty()) return actors.map { it.copy(waitSeconds = null) }
        return actors.map { actor ->
            val session = sessionOf[actor.botId]
            val matched = waits.filter { it.sessionId == session }
            if (session == null || matched.isEmpty()) actor.copy(waitSeconds = null)
            else actor.copy(waitSeconds = matched.minOf { waitSeconds(it, now) })
        }
    }

    fun waitSeconds(wait: OfficeWait, now: Long): Int {
        val remaining = ((wait.expiresAt - now) / 1000L).toInt()
        if (wait.expiresAt == Long.MAX_VALUE || remaining > 24 * 3600) {
            return (60 - ((now - wait.createdAt) / 1000L).toInt()).coerceIn(0, 60)
        }
        return remaining.coerceAtLeast(0)
    }

    /** At most two speech bubbles: the selected bot, then whoever is busiest. */
    fun speakerIds(actors: List<OfficeActor>, selectedId: String?): List<String> {
        if (actors.isEmpty()) return emptyList()
        val busy = actors.filter { it.state != BotAvatarState.IDLE }
            .sortedBy { stateRank(it.state) }
            .map { it.botId }
        val ordered = buildList {
            if (selectedId != null && actors.any { it.botId == selectedId }) add(selectedId)
            busy.forEach { if (it !in this) add(it) }
            if (isEmpty()) add(actors.first().botId)
        }
        return ordered.take(2)
    }

    private fun poseFor(state: BotAvatarState): OfficePose = when (state) {
        BotAvatarState.THINKING -> OfficePose.THINK
        BotAvatarState.EXECUTING, BotAvatarState.ACTIVE -> OfficePose.TYPE
        BotAvatarState.IDLE, BotAvatarState.SUCCESS -> OfficePose.SIT
        else -> OfficePose.STAND
    }

    private fun resolvePose(wanted: OfficePose, seatId: String): OfficePose = when (wanted) {
        OfficePose.TYPE -> if (seatId.startsWith("desk")) OfficePose.TYPE else OfficePose.STAND
        OfficePose.THINK -> if (seatId == "board") OfficePose.THINK else OfficePose.STAND
        OfficePose.SIT -> if (seatId.startsWith("sofa")) OfficePose.SIT else OfficePose.STAND
        OfficePose.STAND -> OfficePose.STAND
    }

    private fun claim(used: MutableSet<String>, preferred: List<OfficeSeat>, fallback: List<OfficeSeat>): OfficeSeat {
        val seat = (preferred + fallback).firstOrNull { it.id !in used }
        if (seat != null) {
            used += seat.id
            return seat
        }
        val index = used.size
        val overflow = index - (used.count { !it.startsWith("extra-") })
        val rows = listOf(148, 288, 408)
        val extra = OfficeSeat(
            "extra-$index", 20 + (overflow % 6) * 29,
            rows[(overflow / 6) % rows.size] + (overflow / 18) * 26, OfficePose.STAND,
        )
        used += extra.id
        return extra
    }

    private fun stateRank(state: BotAvatarState): Int = when (state) {
        BotAvatarState.ACTIVE -> 0
        BotAvatarState.EXECUTING -> 1
        BotAvatarState.THINKING -> 2
        BotAvatarState.BLOCKED -> 3
        BotAvatarState.FAILED -> 4
        BotAvatarState.SUCCESS -> 5
        BotAvatarState.IDLE -> 6
    }
}

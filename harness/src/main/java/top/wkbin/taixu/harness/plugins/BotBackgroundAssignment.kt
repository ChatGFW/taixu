package top.wkbin.taixu.harness.plugins

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotAssignmentStatus
import top.wkbin.taixu.harness.bot.BotAssignmentTracker
import top.wkbin.taixu.harness.subagent.SubagentLaneResult

/** 先持久化受理，再执行；完成状态落盘后才允许唤醒主控检查依赖。 */
internal suspend fun CoroutineScope.launchBotAssignment(
    tracker: BotAssignmentTracker,
    assignment: BotAssignment,
    run: suspend () -> SubagentLaneResult,
    report: suspend (String, Boolean) -> Unit,
) {
    val job = launch(start = CoroutineStart.LAZY) {
        var status = BotAssignmentStatus.FAILED
        var summary: String? = null
        var error: String? = null
        var notification = ""
        try {
            val result = run()
            summary = result.summary
            status = if (result.success) BotAssignmentStatus.COMPLETED else BotAssignmentStatus.FAILED
            if (!result.success) error = "子任务未成功结束（${result.termination}）"
            notification = formatBackgroundReport(assignment, result)
        } catch (cancelled: CancellationException) {
            status = BotAssignmentStatus.CANCELLED
            error = "已取消"
            notification = "🛑 **${assignment.botName}** 的任务已取消（任务 ${assignment.id}）"
            throw cancelled
        } catch (failure: Throwable) {
            error = failure.message ?: "未知错误"
            notification = "⚠️ **${assignment.botName}** 的委派执行失败（任务 ${assignment.id}）：$error"
        } finally {
            withContext(NonCancellable) {
                tracker.finish(assignment.id, status, summary, error)
                report(notification, status != BotAssignmentStatus.CANCELLED)
            }
        }
    }
    try {
        tracker.start(assignment, job)
    } catch (failure: Throwable) {
        job.cancel()
        throw failure
    }
    job.start()
}

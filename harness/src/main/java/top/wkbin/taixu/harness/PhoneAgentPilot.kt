package top.wkbin.taixu.harness

import java.util.Base64
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.core.model.workflow.PhoneWorkflowOperation
import top.wkbin.taixu.runtime.gui.GuiKey
import top.wkbin.taixu.runtime.gui.GuiPrimitive
import top.wkbin.taixu.runtime.virtualdisplay.VirtualDisplayCoordinator
import top.wkbin.taixu.runtime.virtualdisplay.VirtualScreenToolkit
import top.wkbin.taixu.runtime.virtualdisplay.PhoneTaskControl
import top.wkbin.taixu.runtime.virtualdisplay.PhoneTaskState
import top.wkbin.taixu.runtime.virtualdisplay.TwinPhonePresence
import top.wkbin.taixu.runtime.virtualdisplay.VirtualDisplayHud

/**
 * 看虚拟屏截图并按模型输出的动作去点、滑、输入。
 * 优先用设置里的专用手机操作模型；未配置时回退当前聊天主模型（关工具、开视觉）。
 * 主对话模型只负责下达 virtual_screen_task，不再自己一轮轮截图猜坐标。
 */
internal fun busyPhoneTaskMessage(
    requestedSession: String,
    owner: String? = null,
): String {
    val active = owner?.takeIf { it.isNotBlank() }
    return if (active != null && active != requestedSession) {
        "已有手机任务在执行（session=$active）。请先停止它；全局同时只能跑一个，避免抢屏黑屏。"
    } else {
        "已有手机任务在执行（session=${active ?: requestedSession}）。请先停止它；全局同时只能跑一个。"
    }
}

internal class PhoneAgentPilot(
    private val providerClient: ProviderClient,
    private val preferences: AgentPreferences,
    private val coordinator: VirtualDisplayCoordinator,
    private val toolkit: VirtualScreenToolkit,
    private val services: PhoneAgentServices? = null,
    private val fallbackMain: (suspend () -> ModelConfig)? = null,
) {
    suspend fun run(
        session: String,
        goal: String,
        packageName: String,
        maxSteps: Int,
        workflowName: String = "",
        allow: Set<String> = emptySet(),
    ): Pair<Boolean, String> = coroutineScope {
        val task = coordinator.phoneTasks.start(session, currentCoroutineContext()[Job]!!)
            ?: return@coroutineScope false to busyPhoneTaskMessage(
                session,
                coordinator.phoneTasks.activeSession(),
            )
        try {
            services?.log(session, "RunStart executor=phone-agent maxSteps=$maxSteps record=${workflowName.isNotBlank()} allow=$allow")
            val result = runTask(session, goal, packageName, maxSteps.coerceIn(1, 30), task, workflowName, allow)
            when (task.state.value) {
                PhoneTaskState.CANCELLED -> Unit
                PhoneTaskState.COMPLETED -> Unit
                else -> {
                    task.finish(if (result.first) PhoneTaskState.COMPLETED else PhoneTaskState.FAILED)
                    if (result.first) {
                        coordinator.publishPhoneTaskResult("任务完成", success = true)
                    } else {
                        coordinator.publishPhoneTaskResult("任务未完成 · 查看聊天中的原因", success = false)
                    }
                }
            }
            services?.log(session, "RunEnd success=${result.first} state=${task.state.value}")
            result
        } catch (e: CancellationException) {
            if (task.state.value != PhoneTaskState.CANCELLED) {
                task.finish(PhoneTaskState.CANCELLED)
                coordinator.publishPhoneTaskResult("任务已停止", success = false, cancelled = true)
            }
            throw e
        } catch (e: Exception) {
            task.finish(PhoneTaskState.FAILED)
            services?.log(session, "RunError kind=${e::class.java.simpleName}")
            coordinator.publishPhoneTaskResult("任务执行失败", success = false)
            throw e
        } finally {
            coordinator.phoneTasks.release(session, task)
        }
    }

    private suspend fun runTask(
        session: String,
        goal: String,
        packageName: String,
        maxSteps: Int,
        task: PhoneTaskControl,
        workflowName: String,
        allow: Set<String>,
    ): Pair<Boolean, String> {
        val (model, modelSource) = try {
            resolvePhoneRunModel(preferences, fallbackMain)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false to (e.message ?: "手机操作模型不可用")
        }
        services?.log(
            session,
            "ModelConfig source=$modelSource model=${model.model} max_tokens=${model.maxTokens} vision=${model.visionEnabled}",
        )
        val executorLabel = when (modelSource) {
            "main-fallback" -> "执行者：聊天主模型代跑（未配置专用手机操作模型）。"
            else -> "执行者：手机操作模型。"
        }
        val recording = mutableListOf<PhoneWorkflowOperation>()
        var recordingClean = true
        var lastVersion = task.awaitReady()
        val notes = ArrayList<String>(maxSteps)
        // 先打开常驻分身 Tab，再 ensure/launch；进度通知标题用任务目标（
        TwinPhonePresence.requestOpen()
        coordinator.setPhoneTaskTitle(goal.take(40))
        coordinator.reveal(session, "准备分身画面", minimized = true)?.let { notes += it }
        if (coordinator.getDisplayId(session) == null && coordinator.ensureVirtualDisplay(session) == null) {
            return false to "虚拟屏创建失败，手机操作模型无法开始。"
        }
        if (packageName.isNotBlank()) {
            val ready = task.awaitReady()
            if (ready != lastVersion) recordingClean = false
            lastVersion = ready
            val pkg = resolveLaunchablePackage(packageName, coordinator.installedApps())
                ?: return false to "找不到应用「$packageName」。请改用包名，或不要传 package，让手机操作模型自己打开。"
            if (!task.isCurrent(lastVersion)) return false to "启动应用前人工介入，任务已停止；请核对页面后重新运行。"
            coordinator.publishPhoneTaskProgress("打开 $pkg", stepIndex = 0)
            if (!coordinator.launchApp(session, pkg)) return false to "虚拟屏启动应用失败：$pkg"
            recording += PhoneWorkflowOperation("virtual_screen_launch", mapOf("package" to pkg), 900)
            if (!task.isCurrent(lastVersion)) recordingClean = false
            delay(900)
        }
        // 给分身预览一点挂载/出帧时间，后续截图更容易走实时 PixelCopy。
        delay(450)
        val progress = PhoneAgentProgress()
        var invalidReplies = 0
        var step = 0
        var request = 0
        while (step < maxSteps) {
            val version = task.awaitReady()
            if (version != lastVersion) recordingClean = false
            lastVersion = version
            val shot = coordinator.requestScreenshot(session)
                ?: return false to finish(notes, "第 ${step + 1} 步截图失败，虚拟屏没有画面。", executorLabel)
            val size = screenSize(session, shot)
                ?: return false to finish(notes, "第 ${step + 1} 步不知道虚拟屏尺寸，无法把相对坐标换成像素。", executorLabel)
            val history = if (notes.isEmpty()) "这是第一步。" else "已经做过：\n" + notes.joinToString("\n")
            val recordingHint = if (workflowName.isNotBlank() && recording.isEmpty() && packageName.isBlank())
                "\n需要录制可复用工作流，请先用 Launch 打开目标应用，再操作。" else ""
            if (!task.isCurrent(version)) { recordingClean = false; continue }
            val mime = VirtualScreenHostActions.imageMimeType(shot)
            val image = ImagePayloadCompressor.downscaleDataUrl(
                "data:$mime;base64," + Base64.getEncoder().encodeToString(shot),
            )
            val started = System.nanoTime()
            services?.log(session, "ModelRequest request=${++request} step=${step + 1} display=${coordinator.getDisplayId(session)} frame=${size.first}x${size.second} shotBytes=${shot.size} mime=$mime imageChars=${image.length}")
            val reply = try {
                providerClient.chat(
                    model,
                    listOf(
                        ApiMessage(role = "system", content = phoneAgentSystemPrompt(allow = allow)),
                        ApiMessage(
                            role = "user",
                            content = "任务：$goal\n$history$recordingHint\n请根据截图输出下一个动作。",
                            imageUrls = listOf(image),
                        ),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                services?.log(session, "ModelError request=$request elapsedMs=${(System.nanoTime() - started) / 1_000_000} kind=${e::class.java.simpleName} outputBudgetRejected=${e.message?.contains("max_tokens", true) == true}")
                return false to finish(notes, "手机操作模型请求失败：${e.message}", executorLabel)
            }
            services?.log(session, "ModelResponse request=$request elapsedMs=${(System.nanoTime() - started) / 1_000_000} contentChars=${reply.content?.length ?: 0} reasoningChars=${reply.reasoningContent?.length ?: 0} inputTokens=${reply.usage.inputTokens} outputTokens=${reply.usage.outputTokens}")
            currentCoroutineContext().ensureActive()
            // 人工触摸或暂停/继续后，这次推理基于旧截图，必须重新观察。
            if (!task.isCurrent(version)) { recordingClean = false; continue }
            val text = reply.content.orEmpty()
            val action = parsePhoneAgentAction(text)
            if (action == null) {
                services?.log(session, "ActionParseFailed request=$request")
                invalidReplies++
                if (invalidReplies >= 2) {
                    return false to finish(notes, "连续两次无法解析正式动作，请检查手机模型协议。", executorLabel)
                }
                notes += "格式错误：请仅在 <answer> 内输出一条完整 do(...) 或 finish(message=...)，使用定义的动作和参数。"
                continue
            }
            invalidReplies = 0
            val frame = MessageDigest.getInstance("SHA-256").digest(shot).joinToString("") { "%02x".format(it) }
            progress.beforeAction(action, frame)?.let { return false to finish(notes, it, executorLabel) }
            step++
            val stepTitle = phoneAgentStepTitle(text, action)
            services?.log(session, "Action step=$step ${phoneActionLog(action)}")
            if (!task.isCurrent(version)) { recordingClean = false; continue }
            when (action) {
                is PhoneAgentAction.Finish -> {
                    task.finish(PhoneTaskState.COMPLETED)
                    coordinator.publishPhoneTaskResult(action.message.take(40).ifBlank { "任务完成" }, success = true)
                    val saved = when {
                        workflowName.isBlank() -> ""
                        !recordingClean -> "\n存在人工介入或失败步骤，未自动保存工作流；请在编辑器中手动整理。"
                        phoneRecordingIssue(recording) != null -> "\n${phoneRecordingIssue(recording)}"
                        services == null -> "\n工作流保存服务未连接，未保存。"
                        else -> try { "\n" + services.save(workflowName, recording) }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { "\n操作结束，但保存工作流失败：${e.message}" }
                    }
                    return true to finish(notes, action.message + saved, executorLabel)
                }
                is PhoneAgentAction.TakeOver -> {

                    val reason = action.message.ifBlank {
                        if (action.confirm) "请确认是否继续" else "登录、验证码或支付"
                    }
                    notes += if (action.confirm) "$step. 请确认：$reason" else "$step. 请你接手：$reason"
                    recordingClean = false
                    if (action.confirm) {
                        coordinator.publishPhoneTaskConfirm(session, reason.take(40))
                    } else {
                        coordinator.publishPhoneTaskHandoff(session, reason.take(40))
                    }
                    services?.log(session, "UserWait step=$step confirm=${action.confirm}")
                    continue
                }
                is PhoneAgentAction.Note -> {
                    notes += "$step. 记录：${action.message.ifBlank { "当前页面" }}"
                    coordinator.publishPhoneTaskProgress(stepTitle, step)
                }
                else -> {
                    coordinator.publishPhoneTaskProgress(stepTitle, step)
                    val actionStarted = System.nanoTime()
                    val performed = perform(session, action, size, task, version)
                    services?.log(session, "Injection step=$step success=${performed.first} elapsedMs=${(System.nanoTime() - actionStarted) / 1_000_000}")
                    if (!task.isCurrent(version)) {
                        recordingClean = false
                        notes += "$step. ${actionLabel(action)} 期间人工介入，部分输入可能已发出；重新截图核对。"
                        continue
                    }
                    notes += "$step. $stepTitle：${if (performed.first) "已发出，等待画面核验" else "执行失败"}；${performed.second}"
                    if (!performed.first) {
                        coordinator.publishPhoneTaskProgress(performed.second.take(40), step)
                    }
                    progress.afterAction(performed.first)?.let { return false to finish(notes, it, executorLabel) }
                    val waitMs = when (action) {
                        is PhoneAgentAction.Wait -> action.durationMs
                        is PhoneAgentAction.Launch -> 900
                        else -> 700
                    }
                    if (performed.first) action.workflowOperation(waitMs, performed.second.removePrefix("启动 "))?.let { recording += it }
                    else recordingClean = false
                    delay(waitMs)
                }
            }
        }
        return false to finish(
            notes,
            "已达到 $maxSteps 步，任务还没有完成。可以把下一步再交给手机操作模型。",
            executorLabel,
        )
    }

    private fun screenSize(session: String, shot: ByteArray): Pair<Int, Int>? {
        // 注入坐标必须以 VD 分辨率为准；预览图可能是 View 尺寸或被缩放。
        coordinator.getVideoSize(session)?.takeIf { it.first > 1 && it.second > 1 }?.let { return it }
        val (width, height) = VirtualScreenHostActions.imageDimensions(shot)
        return if (width > 1 && height > 1) width to height else null
    }

    private suspend fun perform(
        session: String, action: PhoneAgentAction, size: Pair<Int, Int>, task: PhoneTaskControl, version: Long,
    ): Pair<Boolean, String> {
        val canInject = { task.isCurrent(version) }
        suspend fun execute(primitive: GuiPrimitive) = toolkit.execute(session, primitive, canInject)
        val result = when (action) {
            is PhoneAgentAction.Tap -> execute(GuiPrimitive.Tap(phoneAgentPoint(action.x, size.first), phoneAgentPoint(action.y, size.second)))
            is PhoneAgentAction.DoubleTap -> execute(
                GuiPrimitive.DoubleTap(phoneAgentPoint(action.x, size.first), phoneAgentPoint(action.y, size.second)),
            )
            is PhoneAgentAction.LongPress -> execute(
                GuiPrimitive.LongPress(phoneAgentPoint(action.x, size.first), phoneAgentPoint(action.y, size.second)),
            )
            is PhoneAgentAction.Swipe -> execute(
                GuiPrimitive.Swipe(
                    phoneAgentPoint(action.x1, size.first),
                    phoneAgentPoint(action.y1, size.second),
                    phoneAgentPoint(action.x2, size.first),
                    phoneAgentPoint(action.y2, size.second),
                ),
            )
            is PhoneAgentAction.Type -> toolkit.setText(session, action.text, canInject)
            is PhoneAgentAction.Launch -> {
                val requested = action.app.trim()
                val pkg = resolveLaunchablePackage(requested, coordinator.installedApps())
                    ?: return false to "找不到应用「$requested」。请点击桌面上的图标，或改用包名。"
                if (!canInject()) return false to "应用启动已中止：任务暂停或画面失效"
                return if (coordinator.launchApp(session, pkg)) true to "启动 $pkg" else false to "启动失败 $pkg"
            }
            PhoneAgentAction.Back -> execute(GuiPrimitive.Key(GuiKey.BACK))
            PhoneAgentAction.Home -> execute(GuiPrimitive.Key(GuiKey.HOME))
            is PhoneAgentAction.Wait -> return true to "等待页面 ${action.durationMs}ms"
            is PhoneAgentAction.Note -> return true to "记录：${action.message}"
            is PhoneAgentAction.Finish, is PhoneAgentAction.TakeOver -> return false to "这个动作不能注入"
        }
        return result.success to result.message
    }

    private fun finish(notes: List<String>, outcome: String, executorLabel: String = "执行者：手机操作模型。"): String =
        buildString {
            append(executorLabel)
            append('\n')
            append(outcome)
            if (notes.isNotEmpty()) {
                append("\n操作记录：\n")
                append(notes.joinToString("\n"))
            }
        }

}

private fun actionLabel(action: PhoneAgentAction): String = when (action) {
    is PhoneAgentAction.Tap -> "点击"
    is PhoneAgentAction.DoubleTap -> "双击"
    is PhoneAgentAction.LongPress -> "长按"
    is PhoneAgentAction.Swipe -> "滑动"
    is PhoneAgentAction.Type -> "输入「${action.text.take(16)}」"
    is PhoneAgentAction.Launch -> "打开 ${action.app}"
    PhoneAgentAction.Back -> "返回"
    PhoneAgentAction.Home -> "回桌面"
    is PhoneAgentAction.Wait -> "等待页面"
    is PhoneAgentAction.Note -> "记录"
    is PhoneAgentAction.Finish -> "完成"
    is PhoneAgentAction.TakeOver -> "请你接手"
}

/**
 * 优先用手机模型 <think> 里的一句人话当步骤标题，
 * 没有再退回动作中文标签。
 */
internal fun phoneAgentStepTitle(raw: String, action: PhoneAgentAction): String {
    val think = Regex("(?s)<think>(.*?)</think>").find(raw)?.groupValues?.get(1)
        ?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotBlank() }
        ?.take(40)
    return think?.takeIf { it.isNotBlank() } ?: actionLabel(action)
}

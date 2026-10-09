package top.wkbin.taixu.harness

import java.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.runtime.gui.GuiKey
import top.wkbin.taixu.runtime.gui.GuiPrimitive
import top.wkbin.taixu.runtime.virtualdisplay.VirtualDisplayCoordinator
import top.wkbin.taixu.runtime.virtualdisplay.VirtualScreenToolkit

/**
 * 用设置里选定的手机操作模型看虚拟屏截图，并按它输出的动作去点、滑、输入。
 * 主模型只负责下达任务，不再自己一轮轮截图猜坐标。
 */
internal class PhoneAgentPilot(
    private val providerClient: ProviderClient,
    private val preferences: AgentPreferences,
    private val coordinator: VirtualDisplayCoordinator,
    private val toolkit: VirtualScreenToolkit,
) {
    suspend fun run(session: String, goal: String, packageName: String, maxSteps: Int): Pair<Boolean, String> {
        val endpoint = preferences.phoneAgentConfig.first()
        if (!endpoint.ready) {
            return false to "还没有配置手机操作模型。请到设置 → 智能体与模型 → 虚拟屏 → 手机操作模型，单独填写接口。这和聊天主模型不是同一个地方。智谱 Base URL 是 https://open.bigmodel.cn/api/paas/v4 ，模型名是 autoglm-phone。"
        }
        val model = ModelConfig(
            name = "手机操作",
            provider = "phone-agent",
            model = endpoint.model.trim(),
            baseUrl = endpoint.baseUrl.trim(),
            apiKey = endpoint.apiKey,
            pureChatMode = true,
            toolCallMode = ToolCallMode.DISABLED,
            visionEnabled = true,
            reasoningMode = ReasoningMode.DISABLED,
        )
        if (coordinator.getDisplayId(session) == null && coordinator.ensureVirtualDisplay(session) == null) {
            return false to "虚拟屏创建失败，手机操作模型无法开始。"
        }
        if (packageName.isNotBlank()) {
            val pkg = resolveLaunchablePackage(packageName, coordinator.installedApps())
                ?: return false to "找不到应用「$packageName」。请改用包名，或不要传 package，让手机操作模型自己打开。"
            if (!coordinator.launchApp(session, pkg)) return false to "虚拟屏启动应用失败：$pkg"
            delay(900)
        }
        coordinator.reveal(session, "手机模型接手")
        val notes = ArrayList<String>(maxSteps)
        repeat(maxSteps) { index ->
            val step = index + 1
            val png = coordinator.requestScreenshot(session)
                ?: return false to finish(notes, "第 $step 步截图失败，虚拟屏没有画面。")
            val size = screenSize(session, png)
                ?: return false to finish(notes, "第 $step 步不知道虚拟屏尺寸，无法把相对坐标换成像素。")
            val history = if (notes.isEmpty()) "这是第一步。" else "已经做过：\n" + notes.joinToString("\n")
            val reply = runCatching {
                providerClient.chat(
                    model,
                    listOf(
                        ApiMessage(role = "system", content = SYSTEM_PROMPT),
                        ApiMessage(
                            role = "user",
                            content = "任务：$goal\n$history\n请根据截图输出下一个动作。",
                            imageUrls = listOf(ImagePayloadCompressor.downscaleDataUrl("data:image/png;base64," + Base64.getEncoder().encodeToString(png))),
                        ),
                    ),
                )
            }.getOrElse { return false to finish(notes, "手机操作模型请求失败：${it.message}") }
            val text = listOfNotNull(reply.content, reply.reasoningContent).joinToString("\n")
            val action = parsePhoneAgentAction(text)
                ?: return false to finish(notes, "第 $step 步没有解析出动作。模型原文：${text.take(400)}")
            when (action) {
                is PhoneAgentAction.Finish -> {
                    coordinator.reveal(session, "任务完成")
                    return true to finish(notes, action.message)
                }
                is PhoneAgentAction.TakeOver -> {
                    coordinator.reveal(session, "需要你接手")
                    return false to finish(notes, "需要人工接手：${action.message.ifBlank { "登录、验证码或支付" }}。请在悬浮窗上继续操作。")
                }
                else -> {
                    val performed = perform(session, action, size)
                    notes += "$step. $performed"
                    coordinator.reveal(session, performed.take(24))
                    delay(when (action) {
                        is PhoneAgentAction.Wait -> 1_200
                        is PhoneAgentAction.Launch -> 900
                        else -> 700
                    })
                }
            }
        }
        return false to finish(notes, "已达到 $maxSteps 步，任务还没有完成。可以把下一步再交给手机操作模型。")
    }

    private fun screenSize(session: String, png: ByteArray): Pair<Int, Int>? {
        val (width, height) = VirtualScreenHostActions.pngDimensions(png)
        if (width > 1 && height > 1) return width to height
        return coordinator.getVideoSize(session)?.takeIf { it.first > 1 && it.second > 1 }
    }

    private suspend fun perform(session: String, action: PhoneAgentAction, size: Pair<Int, Int>): String {
        val result = when (action) {
            is PhoneAgentAction.Tap -> toolkit.execute(session, GuiPrimitive.Tap(phoneAgentPoint(action.x, size.first), phoneAgentPoint(action.y, size.second)))
            is PhoneAgentAction.DoubleTap -> toolkit.execute(
                session,
                GuiPrimitive.DoubleTap(phoneAgentPoint(action.x, size.first), phoneAgentPoint(action.y, size.second)),
            )
            is PhoneAgentAction.LongPress -> toolkit.execute(
                session,
                GuiPrimitive.LongPress(phoneAgentPoint(action.x, size.first), phoneAgentPoint(action.y, size.second)),
            )
            is PhoneAgentAction.Swipe -> toolkit.execute(
                session,
                GuiPrimitive.Swipe(
                    phoneAgentPoint(action.x1, size.first),
                    phoneAgentPoint(action.y1, size.second),
                    phoneAgentPoint(action.x2, size.first),
                    phoneAgentPoint(action.y2, size.second),
                ),
            )
            is PhoneAgentAction.Type -> toolkit.execute(session, GuiPrimitive.PasteText(action.text))
            is PhoneAgentAction.Launch -> {
                val requested = action.app.trim()
                val pkg = resolveLaunchablePackage(requested, coordinator.installedApps())
                    ?: return "找不到应用「$requested」。请点击桌面上的图标，或改用包名。"
                return if (coordinator.launchApp(session, pkg)) "启动 $pkg" else "启动失败 $pkg"
            }
            PhoneAgentAction.Back -> toolkit.execute(session, GuiPrimitive.Key(GuiKey.BACK))
            PhoneAgentAction.Home -> toolkit.execute(session, GuiPrimitive.Key(GuiKey.HOME))
            PhoneAgentAction.Wait -> return "等待页面"
            is PhoneAgentAction.Finish, is PhoneAgentAction.TakeOver -> return action.toString()
        }
        return result.message
    }

    private fun finish(notes: List<String>, outcome: String): String = buildString {
        append(outcome)
        if (notes.isNotEmpty()) {
            append("\n操作记录：\n")
            append(notes.joinToString("\n"))
        }
    }

    private companion object {
        val SYSTEM_PROMPT = """
            你是手机界面操作模型。只根据当前截图决定下一步，不要自己假设已经点到。
            只输出一个动作，不要输出其它解释。坐标是 0 到 1000 的相对位置，左上角是 [0, 0]。
            do(action="Tap", element=[x, y])
            do(action="Double Tap", element=[x, y])
            do(action="Long Press", element=[x, y])
            do(action="Swipe", start=[x1, y1], end=[x2, y2])
            do(action="Type", text="要输入的文字")
            do(action="Back")
            do(action="Home")
            do(action="Wait")
            do(action="Launch", app="桌面上的应用名或包名")
            do(action="Take_over", message="需要用户接手的原因")
            finish(message="任务完成时的说明")
        """.trimIndent()
    }
}

package top.wkbin.taixu.runtime.bridge.adb

import top.wkbin.taixu.core.common.shell.ShellQuote

/**
 * Logcat 命令拼接的唯一实现。
 *
 * 同一条命令有两个执行通道：内置无线 ADB（`EmbeddedAdbManager`）与宿主特权
 * shell（Root/Shizuku）。两条通道必须产出完全一致的 argv，否则同一个请求在
 * 通道切换后会得到不同结果，排查时无从判断差异来自通道还是来自命令本身。
 *
 * [LogcatRequest] 的字段在 [EmbeddedAdbManager.captureLogcat] 已被校验过，这里
 * 只做拼装，不再重复校验。
 */
object LogcatCommand {

    /** 关键词在调用方进程内过滤，不拼进 shell，因此这里只处理 pkg/tag/priority/lines。 */
    fun build(request: EmbeddedAdbManager.LogcatRequest, lines: Int): String {
        val priority = request.priority.uppercaseChar()
        val tagArgs = if (request.tag.isBlank()) {
            ShellQuote.of("*:$priority")
        } else {
            "${ShellQuote.of("${request.tag}:$priority")} ${ShellQuote.of("*:S")}"
        }
        val logcat = "/system/bin/logcat -d -v threadtime -t $lines"
        if (request.packageName.isBlank()) return "$logcat $tagArgs"

        // pidof 缺失时不能静默退化成全量日志：明确回 exit 3，让上层按"目标未运行"处理。
        return "pid=\$(/system/bin/pidof ${ShellQuote.of(request.packageName)} | /system/bin/cut -d' ' -f1); " +
            "if [ -z \"\$pid\" ]; then echo '目标应用未运行：${request.packageName}'; exit 3; fi; " +
            "$logcat --pid=\"\$pid\" $tagArgs"
    }

    /** 关键词过滤；空关键词原样返回，避免多一次全量遍历。 */
    fun filter(output: String, keyword: String): String {
        if (keyword.isBlank()) return output
        return output.lineSequence()
            .filter { it.contains(keyword, ignoreCase = true) }
            .joinToString("\n")
    }
}

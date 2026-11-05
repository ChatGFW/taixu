package top.wkbin.taixu.harness

/**
 * 识别命令里的 bash 专有语法。
 *
 * 沙箱的 `/bin/sh` 是 dash（Ubuntu）或 busybox ash（Alpine），模型写顺手了 bash 语法时
 * 往往会得到 "not found" / "unexpected token" 之类的间接报错，然后按参数错误去修，
 * 越修越偏。这里只在命令失败后给出定向提示，不改变执行语义、也不做自动改写。
 *
 * 规则刻意保守：命令里已经出现 `bash` 时整体跳过（作者已经显式用了 bash），
 * `$(( ))` 这类 POSIX 算术展开不算 bashism，避免把正常命令判成方言错误。
 */
internal object BashismDetector {

    internal data class Finding(val syntax: String, val hint: String)

    private data class Rule(val syntax: String, val pattern: Regex, val hint: String)

    private val RULES = listOf(
        Rule("[[ ... ]]", Regex("""\[\[(?!:)"""), "改用 `[ ... ]`，操作数记得加引号"),
        Rule("$'...'", Regex("""\$'"""), "ANSI-C 转义不是 POSIX；改用普通引号或 printf"),
        Rule("<<<", Regex("""<<<"""), "here-string 不是 POSIX；改用 `echo ... |` 或 heredoc"),
        Rule("(( ... ))", Regex("""(?<![$])\(\([^)]"""), "改用 `\$(( ... ))` 或 `test`/`[ ]`"),
        Rule("\${var//...}", Regex("""\$\{[A-Za-z_][A-Za-z0-9_]*/"""), "改用 sed/tr 做替换"),
        Rule("\${var^^}/\${var,,}", Regex("""\$\{[A-Za-z_][A-Za-z0-9_]*(\^\^|,,)\}"""), "改用 tr 转换大小写"),
        Rule("function", Regex("""\bfunction\s+[A-Za-z_]"""), "改用 `name() { ... }` 形式"),
        Rule("set -o pipefail", Regex("""set\s+-o\s+pipefail"""), "dash/ash 不支持 pipefail；改用显式检查退出码"),
        Rule("&>", Regex("""&>"""), "改用 `>file 2>&1`"),
        Rule("shopt", Regex("""\bshopt\b"""), "shopt 是 bash 内建；改用 set 或直接判断"),
        Rule("declare", Regex("""\bdeclare\s+-"""), "改用普通变量赋值"),
        Rule("read -p", Regex("""\bread\s+-[a-z]*p"""), "改用 `printf` 提示后再 read"),
        Rule("source", Regex("""(^|[;&|]\s*)source\s"""), "POSIX 写法是 `. file`"),
        Rule("数组", Regex("""\b[A-Za-z_][A-Za-z0-9_]*=\("""), "dash/ash 没有数组；改用空格分隔的字符串"),
        Rule("==", Regex("""\[\s[^\]]*=="""), "POSIX `[ ]` 用单个 `=` 比较字符串"),
        Rule("let", Regex("""\blet\s+[A-Za-z_]"""), "改用 `\$(( ))` 算术展开"),
        Rule("\$RANDOM", Regex("\\\$RANDOM\\b"), "改用 `od -An -N2 -tu2 /dev/urandom`"),
    )

    private const val MAX_FINDINGS = 4

    /** 返回可直接拼进工具结果的提示；没有命中或已经显式用 bash 时返回 null。 */
    fun explain(command: String): String? {
        val findings = detect(command)
        if (findings.isEmpty()) return null
        return buildString {
            append("【Shell 方言提示】当前 /bin/sh 不是 bash（dash / busybox ash），命令里出现了 bash 专有语法：")
            findings.forEach { append("\n- `").append(it.syntax).append("`：").append(it.hint) }
            append("\n确实需要 bash 时显式 `bash -c '<命令>'`（沙箱需已安装 bash），否则请改成 POSIX 写法。")
        }
    }

    fun detect(command: String): List<Finding> {
        val normalized = command.trim()
        if (normalized.isEmpty() || hasExplicitBash(normalized)) return emptyList()
        return RULES
            .filter { it.pattern.containsMatchIn(normalized) }
            .distinctBy { it.syntax }
            .take(MAX_FINDINGS)
            .map { Finding(it.syntax, it.hint) }
    }

    /** 作者已经把命令交给 bash 时不再提示：`bash -c`、`bash script.sh`、`#!/bin/bash`。 */
    private fun hasExplicitBash(command: String): Boolean =
        Regex("""(^|[\s;&|(])(/usr/bin/|/bin/)?bash(\s|$)""").containsMatchIn(command)
}

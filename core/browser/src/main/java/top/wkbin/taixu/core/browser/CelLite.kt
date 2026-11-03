package top.wkbin.taixu.core.browser

/**
 * CEL 极简子集（对齐 OpenBot 动作策略表达，非完整 CEL）。
 *
 * 支持：`true`/`false`、字符串字面量、点号字段、`==`/`!=`、
 * `contains(a,b)` / `matches(a,b)`（大小写不敏感）、`&&`/`||`/`!`、括号。
 * 字段值一律按字符串解析；缺字段视为空串。
 */
object CelLite {
    fun eval(expression: String, vars: Map<String, String>): Boolean {
        val parser = Parser(expression, vars)
        val value = parser.parseExpr()
        parser.expectEnd()
        return value
    }

    private class Parser(
        source: String,
        private val vars: Map<String, String>,
    ) {
        private val s = source.trim()
        private var i = 0

        fun parseExpr(): Boolean = parseOr()

        fun expectEnd() {
            skipWs()
            if (i < s.length) error("意外输入：${s.substring(i)}")
        }

        private fun parseOr(): Boolean {
            var left = parseAnd()
            // 两侧都必须解析完，不能用语言级短路跳过右操作数（否则 expectEnd 会把未消费的尾巴当非法输入）。
            while (matchOp("||")) {
                val right = parseAnd()
                left = left || right
            }
            return left
        }

        private fun parseAnd(): Boolean {
            var left = parseNot()
            while (matchOp("&&")) {
                val right = parseNot()
                left = left && right
            }
            return left
        }

        private fun parseNot(): Boolean {
            if (matchOp("!")) return !parseNot()
            return parsePrimary()
        }

        private fun parsePrimary(): Boolean {
            skipWs()
            if (match('(')) {
                val v = parseExpr()
                expect(')')
                return v
            }
            if (matchKeyword("true")) return true
            if (matchKeyword("false")) return false
            if (matchKeyword("contains")) {
                expect('(')
                val a = parseAtom()
                expect(',')
                val b = parseAtom()
                expect(')')
                return a.contains(b, ignoreCase = true)
            }
            if (matchKeyword("matches")) {
                expect('(')
                val a = parseAtom()
                expect(',')
                val b = parseAtom()
                expect(')')
                return runCatching { Regex(b, RegexOption.IGNORE_CASE).containsMatchIn(a) }
                    .getOrElse { error("非法正则：$b") }
            }
            // comparison or bare (not allowed as bool — force ==)
            val left = parseAtom()
            skipWs()
            return when {
                matchOp("==") -> left.equals(parseAtom(), ignoreCase = false)
                matchOp("!=") -> !left.equals(parseAtom(), ignoreCase = false)
                else -> error("期望比较或布尔值，得到字段/字面量「$left」")
            }
        }

        private fun parseAtom(): String {
            skipWs()
            if (i >= s.length) error("意外结束")
            val c = s[i]
            if (c == '\'' || c == '"') {
                i++
                val start = i
                while (i < s.length && s[i] != c) {
                    if (s[i] == '\\' && i + 1 < s.length) i += 2 else i++
                }
                if (i >= s.length) error("未闭合字符串")
                val raw = s.substring(start, i)
                i++ // closing quote
                return unescape(raw)
            }
            if (c.isLetter() || c == '_') {
                val start = i
                i++
                while (i < s.length) {
                    val ch = s[i]
                    if (ch.isLetterOrDigit() || ch == '_' || ch == '.') i++ else break
                }
                val name = s.substring(start, i)
                // keyword misuse as atom
                if (name == "true" || name == "false" || name == "contains" || name == "matches") {
                    error("「$name」不能作为原子值")
                }
                return vars[name].orEmpty()
            }
            error("非法原子：${s.substring(i).take(16)}")
        }

        private fun unescape(raw: String): String = buildString(raw.length) {
            var j = 0
            while (j < raw.length) {
                val ch = raw[j]
                if (ch == '\\' && j + 1 < raw.length) {
                    append(raw[j + 1])
                    j += 2
                } else {
                    append(ch)
                    j++
                }
            }
        }

        private fun matchOp(op: String): Boolean {
            skipWs()
            if (s.startsWith(op, i)) {
                i += op.length
                return true
            }
            return false
        }

        private fun matchKeyword(word: String): Boolean {
            skipWs()
            if (!s.startsWith(word, i)) return false
            val end = i + word.length
            if (end < s.length) {
                val next = s[end]
                if (next.isLetterOrDigit() || next == '_' || next == '.') return false
            }
            i = end
            return true
        }

        private fun match(ch: Char): Boolean {
            skipWs()
            if (i < s.length && s[i] == ch) {
                i++
                return true
            }
            return false
        }

        private fun expect(ch: Char) {
            if (!match(ch)) error("期望 '$ch'")
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
    }
}

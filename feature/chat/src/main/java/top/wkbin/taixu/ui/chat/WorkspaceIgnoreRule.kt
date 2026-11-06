package top.wkbin.taixu.ui.chat

/** Common gitignore wildcards, nested scopes and ordered negation, independent of host OS. */
internal data class WorkspaceIgnoreRule(val base: String, val pattern: WorkspaceGlob, val directory: Boolean, val include: Boolean) {
    fun matches(path: String, isDirectory: Boolean): Boolean {
        val relative = if (base.isEmpty()) path else path.removePrefix("$base/").takeIf { it != path } ?: return false
        return (!directory || isDirectory) && pattern.matches(relative)
    }

    companion object {
        fun ignored(path: String, directory: Boolean, rules: List<WorkspaceIgnoreRule>): Boolean =
            rules.lastOrNull { it.matches(path, directory) }?.let { !it.include } ?: false

        fun parse(base: String, text: String): List<WorkspaceIgnoreRule> = text.lineSequence().take(512).mapNotNull { line ->
            var value = line.trimEnd()
            if (value.isEmpty() || value.startsWith('#') || value.length > 1_024) return@mapNotNull null
            val include = value.startsWith('!')
            if (include) value = value.drop(1)
            val directory = value.endsWith('/')
            value = value.trimEnd('/')
            val anchored = value.startsWith('/') || '/' in value
            value = value.removePrefix("/")
            if (value.isEmpty()) return@mapNotNull null
            WorkspaceIgnoreRule(base, WorkspaceGlob(value, anchored), directory, include)
        }.toList()
    }
}

/** Small NFA: O(pattern length * path length), with no regex backtracking on project input. */
internal class WorkspaceGlob(value: String, anchored: Boolean) {
    private sealed interface Token {
        data class Literal(val char: Char) : Token
        data object AnyChar : Token
        data object Star : Token
        data object Recursive : Token
        // Optional **/ compiles to a split over Recursive + '/'.
        data object Split : Token
    }
    private val tokens = buildList {
        fun prefix() { add(Token.Split); add(Token.Recursive); add(Token.Literal('/')) }
        if (!anchored) prefix()
        var i = 0
        while (i < value.length) {
            when (val c = value[i++]) {
                '*' -> if (i < value.length && value[i] == '*') {
                    i++
                    if (i < value.length && value[i] == '/') { i++; prefix() } else add(Token.Recursive)
                } else add(Token.Star)
                '?' -> add(Token.AnyChar)
                '\\' -> if (i < value.length) add(Token.Literal(value[i++]))
                else -> add(Token.Literal(c))
            }
        }
    }

    fun matches(path: String): Boolean {
        fun closure(states: BooleanArray) {
            for (i in tokens.indices) if (states[i]) when (tokens[i]) {
                Token.Star, Token.Recursive -> states[i + 1] = true
                Token.Split -> { states[i + 1] = true; states[i + 3] = true }
                else -> Unit
            }
        }
        var active = BooleanArray(tokens.size + 1).apply { this[0] = true }
        for (char in path) {
            closure(active)
            val next = BooleanArray(active.size)
            for (i in tokens.indices) if (active[i]) when (val token = tokens[i]) {
                is Token.Literal -> if (char == token.char) next[i + 1] = true
                Token.AnyChar -> if (char != '/') next[i + 1] = true
                Token.Star -> if (char != '/') next[i] = true
                Token.Recursive -> next[i] = true
                Token.Split -> Unit
            }
            active = next
            if (active.none { it }) return false
        }
        closure(active)
        return active.last()
    }
}

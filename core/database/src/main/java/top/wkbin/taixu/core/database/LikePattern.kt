package top.wkbin.taixu.core.database

/**
 * 把用户输入转成 LIKE 模式串，配合 SQL 里的 `ESCAPE '\'` 使用。
 *
 * 不转义时 `%` / `_` 会被当成通配符：搜「30%」会把整库命中，搜 `a_b` 会匹配 `axb`。
 * 顺序不能变：先转义反斜杠本身，否则会把后面转义出来的 `\%` 再转一次。
 */
internal fun likePattern(input: String): String =
    input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
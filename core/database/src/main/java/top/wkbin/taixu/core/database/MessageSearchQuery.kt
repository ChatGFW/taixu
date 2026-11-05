package top.wkbin.taixu.core.database

/** Keep raw text separate from the escaped LIKE pattern so excerpt offsets remain literal. */
internal const val MESSAGE_SEARCH_SQL = """
    SELECT sessionId, sessionTitle, messageId, isUser, createdAt,
           substr(fullText, MAX(1, instr(lower(fullText), lower(:query)) - 16), 220) AS excerpt
    FROM (
        SELECT e.sessionId AS sessionId, s.title AS sessionTitle, e.id AS messageId,
               e.customType = 'user' AS isUser, e.createdAt AS createdAt,
               CASE WHEN json_valid(e.payloadJson) THEN
                   CASE WHEN json_type(e.payloadJson, '$.text') = 'text'
                        THEN json_extract(e.payloadJson, '$.text') END
               END AS fullText
        FROM harness_entries e
        LEFT JOIN harness_sessions s ON s.id = e.sessionId
        WHERE e.entryType = 'message' AND e.customType IN ('user', 'assistant')
    )
    WHERE fullText LIKE '%' || :pattern || '%' ESCAPE '\'
    ORDER BY createdAt DESC, messageId DESC LIMIT :limit
"""

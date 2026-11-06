package top.wkbin.taixu.harness.session

import top.wkbin.taixu.harness.QueuedPrompt

interface QueuedPromptEditingControl {
    fun removeQueuedPromptById(sessionId: String, itemId: String) {}
    suspend fun convertQueuedPromptToSteer(sessionId: String, itemId: String): Boolean = false
    suspend fun beginQueuedPromptEdit(sessionId: String, itemId: String, token: String): QueuedPrompt? = null
    suspend fun saveQueuedPromptEdit(sessionId: String, itemId: String, token: String, text: String): Boolean = false
    fun cancelQueuedPromptEdit(sessionId: String, itemId: String, token: String) {}
}

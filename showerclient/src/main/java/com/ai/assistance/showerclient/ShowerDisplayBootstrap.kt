package com.ai.assistance.showerclient

import android.view.KeyEvent
import com.ai.assistance.shower.IShowerService

/** 仅用于新屏：先初始化桌面再交付 ID，失败时释放本次创建的屏幕。 */
internal fun IShowerService.createDisplayWithHome(
    width: Int,
    height: Int,
    dpi: Int,
    bitrateKbps: Int,
): Int {
    val id = ensureDisplay(width, height, dpi, bitrateKbps)
    if (id < 0) return id
    // 建屏的 launchBackdrop 可能选到厂商 SECONDARY_HOME 空白页；与 Soft Home 统一落点。
    // 不用于复用已有屏幕，避免把正在操作的应用切回桌面。
    try {
        injectKey(id, KeyEvent.KEYCODE_HOME)
    } catch (e: Exception) {
        runCatching { destroyDisplay(id) }
        throw e
    }
    return id
}

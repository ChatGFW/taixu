package top.wkbin.taixu.runtime.virtualdisplay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 空 VirtualDisplay 合成纯黑时的保底包。
 * 注意：分身桌面 [TwinLauncherActivity] 已由 shower ensureDisplay 启动，不要再把宿主包丢进 warm。
 */
internal fun resolveVirtualDisplayWarmPackages(context: Context): List<String> {
    val out = LinkedHashSet<String>()
    val secondary = "com.android.launcher3.secondarydisplay"
    if (runCatching { context.packageManager.getPackageInfo(secondary, 0) }.isSuccess) {
        out += secondary
    }
    out += "com.android.settings"
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
    resolved?.activityInfo?.packageName
        ?.takeIf { it.isNotBlank() && it != context.packageName }
        ?.let { out += it }
    out += listOf(
        "com.miui.home",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
    ).filter {
        runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess
    }
    return out.toList()
}

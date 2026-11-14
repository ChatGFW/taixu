package top.wkbin.taixu.runtime.virtualdisplay

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 分身副屏默认桌面（
 * 网格列出可启动 App；点击用 [ActivityOptions.setLaunchDisplayId] 在本虚拟屏启动。
 */
class TwinLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (windowManager.defaultDisplay.displayId == 0) {
            finish()
            return
        }
        val pad = dp(16)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF12141A.toInt())
            setPadding(pad, pad, pad, pad)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF12141A.toInt())
        }
        root.addView(
            TextView(this).apply {
                text = "分身桌面"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(0, 0, 0, dp(4))
            },
        )
        root.addView(
            TextView(this).apply {
                text = "点选应用在此虚拟屏打开"
                setTextColor(0x99FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, 0, 0, dp(16))
            },
        )
        val grid = GridLayout(this).apply {
            columnCount = 4
            useDefaultMargins = true
        }
        val apps = launchableApps()
        for (app in apps) {
            grid.addView(appTile(app))
        }
        if (apps.isEmpty()) {
            root.addView(
                TextView(this).apply {
                    text = "未找到可启动应用"
                    setTextColor(0x99FFFFFF.toInt())
                },
            )
        } else {
            root.addView(grid)
        }
        scroll.addView(root)
        setContentView(scroll)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 副屏桌面吞掉返回，避免误关（
    }

    private data class LaunchApp(val label: CharSequence, val packageName: String, val activityName: String)

    private fun launchableApps(): List<LaunchApp> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolve = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val self = packageName
        return resolve
            .asSequence()
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val act = info.activityInfo?.name ?: return@mapNotNull null
                if (pkg == self) return@mapNotNull null
                LaunchApp(info.loadLabel(pm), pkg, act)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.toString() }
            .take(80)
            .toList()
    }

    private fun appTile(app: LaunchApp): LinearLayout {
        val iconSize = dp(48)
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnClickListener { launchOnThisDisplay(app) }
        }
        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            setImageDrawable(
                runCatching { packageManager.getApplicationIcon(app.packageName) }.getOrNull(),
            )
        }
        val label = TextView(this).apply {
            text = app.label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER_HORIZONTAL
            maxLines = 2
            setPadding(0, dp(6), 0, 0)
        }
        tile.addView(icon)
        tile.addView(label)
        return tile
    }

    private fun launchOnThisDisplay(app: LaunchApp) {
        val displayId = windowManager.defaultDisplay.displayId
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        // 同屏 ActivityOptions 比 app 进程里裸 am 可靠。
        val opts = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        runCatching { startActivity(intent, opts.toBundle()) }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
}

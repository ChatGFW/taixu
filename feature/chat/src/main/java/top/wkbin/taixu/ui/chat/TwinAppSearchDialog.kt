package top.wkbin.taixu.ui.chat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.miuix.RuntimeDialog
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

/** 可启动应用条目；图标随查询一次性取好，滚动时不再回查 PackageManager。 */
private class TwinSearchApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

/**
 * 底栏「搜索应用」弹窗：按应用名或包名过滤，选中后交给调用方在虚拟屏打开。
 * 列表口径与副屏桌面 [top.wkbin.taixu.runtime.virtualdisplay.TwinLauncherActivity] 一致。
 */
@Composable
internal fun TwinAppSearchDialog(
    onDismiss: () -> Unit,
    onPick: (packageName: String) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<TwinSearchApp>>(emptyList()) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadTwinSearchApps(context) }
    }
    val filtered = remember(query, apps) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
    RuntimeDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                "搜索应用",
                color = RuntimeTheme.colorScheme.onSurface,
                style = RuntimeTheme.typography.titleMedium,
            )
            RuntimeFilledTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("应用名或包名") },
                singleLine = true,
                shape = RuntimeTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(top = 8.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app.packageName) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AndroidView(
                            modifier = Modifier.size(32.dp),
                            factory = { ctx ->
                                ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
                            },
                            update = { view -> runCatching { view.setImageDrawable(app.icon) } },
                        )
                        Text(
                            app.label,
                            color = RuntimeTheme.colorScheme.onSurface,
                            style = RuntimeTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                Text(
                    "没有匹配的应用",
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    style = RuntimeTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private fun loadTwinSearchApps(context: Context): List<TwinSearchApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolve = runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) }
        .getOrNull()
        .orEmpty()
    val self = context.packageName
    return resolve.mapNotNull { info ->
        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
        if (pkg == self) return@mapNotNull null
        TwinSearchApp(
            packageName = pkg,
            label = runCatching { info.loadLabel(pm)?.toString() }.getOrNull() ?: pkg,
            icon = runCatching { info.loadIcon(pm) }.getOrNull(),
        )
    }.distinctBy { it.packageName }.sortedBy { it.label }
}

/** 底栏右侧「搜索应用」入口：搜索图标 + 文字，字号与颜色同「操作手机」。 */
@Composable
internal fun TwinSearchAppsButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RuntimeIcon(
            RuntimeIconName.Search,
            Modifier.size(22.dp),
            RuntimeTheme.colorScheme.onSurface,
        )
        Text(
            "搜索应用",
            color = RuntimeTheme.colorScheme.onSurface,
            style = RuntimeTheme.typography.titleSmall,
            maxLines = 1,
        )
    }
}

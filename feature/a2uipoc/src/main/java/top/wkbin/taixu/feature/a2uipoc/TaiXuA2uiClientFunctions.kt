package top.wkbin.taixu.feature.a2uipoc

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.model.catalog.functions.A2uiLocaleProvider
import androidx.a2ui.model.catalog.functions.A2uiMessageFormatter
import androidx.a2ui.model.catalog.functions.A2uiPluralizeFunction
import androidx.a2ui.model.catalog.functions.A2uiUrlOpener
import androidx.a2ui.model.protocol.A2uiExecutionContext
import androidx.a2ui.model.protocol.A2uiException
import java.util.Locale

/**
 * Basic Catalog 留给宿主的两个函数。
 * openUrl 只放行 http/https，并用新任务打开，避免把来路交给外部页面。
 * MessageFormatter 接 pluralize 产出的 ICU 模板。
 */
internal object TaiXuUrlOpener : A2uiUrlOpener {

    @Volatile
    var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
    }

    override fun openUrl(url: String) {
        val allowed = TaiXuA2uiMediaPolicy.httpUrlOrNull(url)
            ?: throw A2uiException.A2uiRuntimeException("openUrl 仅允许 http 或 https 链接")
        val context = appContext
            ?: throw A2uiException.A2uiRuntimeException("openUrl 尚未绑定应用上下文")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(allowed)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            throw A2uiException.A2uiRuntimeException("无法打开链接：${error.message ?: "没有可处理的应用"}")
        }
    }
}

internal object TaiXuMessageFormatter : A2uiMessageFormatter {
    override fun format(pattern: String, locale: Locale, arguments: Map<String, Any>): String =
        TaiXuA2uiMediaPolicy.formatMessage(pattern, locale, arguments)
}

/** alpha02 collects host behavior in Functions; keep URL policy and the host ICU formatter. */
internal object TaiXuA2uiClientFunctions : A2uiBasicCatalogV1.Functions {
    private val pluralize = A2uiPluralizeFunction(TaiXuMessageFormatter, A2uiLocaleProvider.Default)

    override fun openUrl(url: String, executionContext: A2uiExecutionContext) = TaiXuUrlOpener.openUrl(url)

    override fun pluralize(
        value: Double, zero: String?, one: String?, two: String?, few: String?, many: String?,
        other: String, executionContext: A2uiExecutionContext,
    ): String = pluralize.execute(buildMap {
        put("value", value)
        put("other", other)
        zero?.let { put("zero", it) }
        one?.let { put("one", it) }
        two?.let { put("two", it) }
        few?.let { put("few", it) }
        many?.let { put("many", it) }
    }, executionContext) as String
}

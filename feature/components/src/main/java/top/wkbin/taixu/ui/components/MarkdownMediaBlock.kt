package top.wkbin.taixu.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.components.R
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import android.util.Log
import top.wkbin.taixu.core.markdown.*
@Composable
internal fun RemoteMediaBlock(block: MdRemoteMedia, cacheKey: String? = null) {
    val context = LocalContext.current
    var previewing by remember(block.url) { mutableStateOf(false) }
    var loaded by remember(cacheKey, block.url) { mutableStateOf(false) }
    var failed by remember(cacheKey, block.url) { mutableStateOf(false) }
    var aspectRatio by remember(cacheKey, block.url) { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(14.dp)
    val density = LocalDensity.current
    val decodeWidthPx = with(density) { MEDIA_MAX_WIDTH.roundToPx() }
    val decodeHeightPx = with(density) { MEDIA_MAX_HEIGHT.roundToPx() }

    val hostRoots = LocalSandboxHostRoots.current
    val dataModel: Any = remember(block.url, hostRoots) {
        when {
            block.url.startsWith("http://", ignoreCase = true) ||
                block.url.startsWith("https://", ignoreCase = true) ||
                block.url.startsWith("data:", ignoreCase = true) -> block.url
            else -> resolveMediaSource(block.url, hostRoots)
        }
    }
    val request = remember(dataModel, cacheKey, decodeWidthPx, decodeHeightPx) {
        ImageRequest.Builder(context)
            .data(dataModel)
            .size(decodeWidthPx, decodeHeightPx)
            .apply { if (cacheKey != null) memoryCacheKey(cacheKey) }
            // 列表内关闭 crossfade：淡入会给每张图片产生一个动画帧，
            // 快速滑动时多张图同时切 loaded 状态会叠加成可见掉帧。全屏预览仍保留淡入。
            .build()
    }
    val imageWidth: androidx.compose.ui.unit.Dp
    val imageHeight: androidx.compose.ui.unit.Dp
    if (aspectRatio > 0f) {
        imageWidth = minOf(MEDIA_MAX_WIDTH, MEDIA_MAX_HEIGHT * aspectRatio)
        imageHeight = imageWidth / aspectRatio
    } else {
        // 占位高度按「常见横图 4:3」估算，而非固定 180dp：
        // 旧写法给的是与真实比例无关的常数高度，图片加载完成回填 aspectRatio 后，
        // item 高度会从占位值突变到真实值 → LazyColumn 在滚动中反复重测量/位置修正。
        // 按比例占位把跳变幅度压到最小（多数图片接近该比例），显著减轻滚动抖动。
        imageWidth = MEDIA_MAX_WIDTH
        imageHeight = imageWidth / MEDIA_PLACEHOLDER_ASPECT
    }

    // AsyncImage avoids SubcomposeAsyncImage's per-item subcomposition overhead in the LazyColumn.
    Box(
        modifier = Modifier
            .size(imageWidth, imageHeight)
            .clip(shape)
            .background(RuntimeTheme.colorScheme.surfaceContainerHigh)
            .then(if (loaded) Modifier.clickable { previewing = true } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = block.description.ifBlank { stringResource(R.string.components_md_web_content) },
            contentScale = ContentScale.FillBounds,
            onLoading = {
                loaded = false
                failed = false
            },
            onSuccess = { state ->
                val src = state.painter.intrinsicSize
                if (!src.isUnspecified && src.width > 0f && src.height > 0f) {
                    aspectRatio = src.width / src.height
                }
                loaded = true
                failed = false
            },
            onError = { state ->
                loaded = false
                failed = true
                // 诊断日志：失败时输出原始 url、解析后的 data model 与异常，便于 logcat 定位
                Log.e(
                    "TaiXu",
                    "chat image load failed: url=${block.url}, data=$dataModel, error=${state.result.throwable}",
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (loaded) 1f else 0f },
        )
        when {
            failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeIcon(
                    RuntimeIconName.Image,
                    Modifier.size(32.dp),
                    tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.components_md_image_load_failed),
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
            !loaded -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }

    if (previewing) {
        ImagePreviewDialog(
            block = block,
            onDismiss = { previewing = false },
        )
    }
}

private val MEDIA_MAX_WIDTH = 260.dp
private val MEDIA_MAX_HEIGHT = 340.dp
/** 未加载完成时的占位宽高比（4:3 横图，接近常见截图/生成图比例，可把图片加载后的高度跳变压到最小）。 */
private const val MEDIA_PLACEHOLDER_ASPECT = 4f / 3f

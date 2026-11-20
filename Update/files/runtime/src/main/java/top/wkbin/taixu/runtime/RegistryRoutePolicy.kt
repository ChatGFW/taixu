package top.wkbin.taixu.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Map the persisted mirror preference to a RootFS registry route. */
suspend fun Flow<String>.toRegistryRoute(): RegistryRoute = first().toRegistryRoute()

fun String.toRegistryRoute(): RegistryRoute = when (this) {
    "official" -> RegistryRoute.OFFICIAL
    "china" -> RegistryRoute.CHINA_ACCELERATED
    else -> RegistryRoute.AUTO
}

/** 反向映射：把界面选中的线路写回镜像策略，供后续更新与检查更新沿用。 */
fun RegistryRoute.toPolicyValue(): String = when (this) {
    RegistryRoute.OFFICIAL -> "official"
    RegistryRoute.CHINA_ACCELERATED -> "china"
    RegistryRoute.AUTO -> "auto"
}

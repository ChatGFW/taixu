package top.wkbin.taixu.ui.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable

typealias TopAppBarScrollBehavior = top.yukonga.miuix.kmp.basic.ScrollBehavior

object TopAppBarDefaults {
    @Composable
    fun exitUntilCollapsedScrollBehavior() = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior()
}

package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.sync.Mutex

/**
 * 工具链补齐互斥闸。
 *
 * 说明：ToolManager 的 installMutex 是 private（且其文件受行数棘轮约束不能再加公开方法），
 * 故这里用进程级单例 Mutex 防止本面板的 apt 补齐重入。
 * 与套件安装的互斥由 [ToolchainViewModel.repairMissing] 的串行编排保证：
 * 先等套件 batchInstallComponents 的 Flow collect 完成（其内部持有 installMutex），
 * 再进入 apt 逐包安装（本闸持锁），两条路在时间上不重叠，不会并发动 dpkg。
 */
object ToolchainInstallGuard {
    val mutex: Mutex = Mutex()
}

package top.wkbin.taixu.runtime.bridge.adb

import top.wkbin.taixu.runtime.bridge.adb.EmbeddedAdbManager.ConnectionState

/**
 * `EmbeddedAdbManager` 的可见状态判据。
 *
 * 背景：`_state` 一个字段同时承载「连接状态」与「发现/配对进度」，而
 * `startDiscoveryInternal`、它的失败分支与 `onDiscoveryError` 都会直接写
 * `Discovering` / `Failed`。发现可由任何消费者（通知栏、开发者页）随时重启，
 * 于是连上 ADB 之后一次发现就能把 `Connected` 覆盖掉：`/api/logcat` 仍能执行
 * （`executeShell` 只看 client 是否为空），但 `/api/health` 报 `adb:false`。
 *
 * 判定独立成文件，是为了让 `EmbeddedAdbManager` 保持零行增长——该文件基线
 * 584 行已顶到架构棘轮上限，只许缩减不许上涨。
 */
object AdbChannelState {

    /**
     * 执行通道是否真的可用。
     *
     * 只认两种情况：已持有可用连接，或状态明确为 [ConnectionState.Connected]。
     * 不能用「状态不是 Failed」之类的弱条件——`Discovering` / `Pairing` /
     * `Connecting` 既不表示可用，也不表示不可用。
     */
    fun isLive(hasClient: Boolean, state: ConnectionState): Boolean =
        hasClient || state is ConnectionState.Connected

    /**
     * 发现流程是否可以改写可见状态。
     *
     * 只在通道尚未建立时为真：已经能执行命令了，`Discovering` / `Failed`
     * 都不该再推翻它。
     */
    fun mayRewriteVisibleState(hasClient: Boolean, state: ConnectionState): Boolean =
        !isLive(hasClient, state)
}

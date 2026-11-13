package top.wkbin.taixu.runtime.virtualdisplay

import android.app.Activity
import android.os.Bundle

/**
 * 
 * 虚拟屏建好后 `am start --display` 丢占位 Activity，避免合成器无窗口纯黑。
 * 误落到主屏（displayId=0）则立刻 finish。
 */
class TwinBackdropActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (windowManager.defaultDisplay.displayId == 0) {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        
    }
}

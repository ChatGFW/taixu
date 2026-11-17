package top.wkbin.taixu.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TaiXuSplashTest {
    @Test fun playbackGuardFollowsTheSystemAnimatorDurationScale() {
        assertEquals(540L, splashPlaybackGuardDelayMs(1f))
        assertEquals(1080L, splashPlaybackGuardDelayMs(2f))
        assertEquals(1620L, splashPlaybackGuardDelayMs(3f))
    }

    @Test fun playbackGuardKeepsTheKnownDurationWhenScaleIsUnavailable() {
        assertEquals(540L, splashPlaybackGuardDelayMs(0f))
    }
}

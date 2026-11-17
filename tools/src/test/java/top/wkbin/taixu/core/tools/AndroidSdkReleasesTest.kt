package top.wkbin.taixu.core.tools

import org.junit.Assert.*
import org.junit.Test

class AndroidSdkReleasesTest {
    @Test fun rateLimitFallbackKeepsEachAssetsOwnDigest() {
        val versions = AndroidSdkReleases.feedVersions("""<feed>
            <link href="${AndroidSdkReleases.REPOSITORY}/releases/tag/37.0.0"/>
            <link href="https://example.org/releases/tag/36.0.2"/>
            <link href="${AndroidSdkReleases.REPOSITORY}/releases/tag/../35"/>
        </feed>""")
        assertEquals(listOf("37.0.0"), versions)
        val html = """<ul>
            <li><a href="/HomuHomu833/android-sdk-custom/releases/download/37.0.0/android-sdk-aarch64-linux-musl.tar.xz">SDK</a><span>sha256:${"ab".repeat(32)}</span><span>202 MB</span></li>
            <li><span>sha256:${"cd".repeat(32)}</span></li>
        </ul>"""
        val release = AndroidSdkReleases.assetPage("37.0.0", html)!!
        assertEquals("ab".repeat(32), release.sha256)
        assertEquals(202000000L, release.size)
        assertNull(AndroidSdkReleases.assetPage("36.0.2", html))
    }

    @Test fun selectsOnlyPortableArm64AssetsAndRetainsDigest() {
        val content = """[
            {"tag_name":"37.0.0","assets":[
                {"name":"android-sdk-aarch64-linux-gnu.tar.xz","browser_download_url":"unused"},
                {"name":"android-sdk-aarch64-linux-musl.tar.xz","size":157000000,
                 "digest":"sha256:${"ab".repeat(32)}",
                 "browser_download_url":"${url("37.0.0")}"}]},
            {"tag_name":"36.0.2","assets":[{"name":"android-sdk-aarch64-linux-musl.tar.xz",
                "digest":null,"browser_download_url":"${url("36.0.2")}"}]},
            {"tag_name":"35.0.0","assets":[{"name":"android-sdk-x86_64-linux-musl.tar.xz"}]}
        ]"""
        val releases = AndroidSdkReleases.parse(content)
        assertEquals(listOf("37.0.0", "36.0.2"), releases.map { it.version })
        assertEquals("ab".repeat(32), releases.first().sha256)
        assertEquals(157000000L, releases.first().size)
        assertNull(releases.last().sha256)
    }

    @Test fun ignoresDraftTraversalAndUnexpectedDownloadHosts() {
        val content = """[
            {"tag_name":"37.0.0","draft":true,"assets":[{"name":"android-sdk-aarch64-linux-musl.tar.xz","browser_download_url":"${url("37.0.0")}"}]},
            {"tag_name":"../../37","assets":[{"name":"android-sdk-aarch64-linux-musl.tar.xz","browser_download_url":"${url("../../37")}"}]},
            {"tag_name":"36.0.2","assets":[{"name":"android-sdk-aarch64-linux-musl.tar.xz","browser_download_url":"https://example.org/tool.tar.xz"}]}
        ]"""
        assertTrue(AndroidSdkReleases.parse(content).isEmpty())
    }

    private fun url(version: String) = "${AndroidSdkReleases.REPOSITORY}/releases/download/$version/android-sdk-aarch64-linux-musl.tar.xz"
}

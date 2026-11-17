package top.wkbin.taixu.runtime.rootfs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BundledRootfsManifestTest {
    @get:Rule val temporary = TemporaryFolder()

    private val validManifest = """
        {
          "schema": 1,
          "abi": "arm64",
          "assetId": "9ea6f5d765b3c09e",
          "archive": {
            "name": "rootfs-arm64.tar.xz",
            "bytes": 17937512,
            "sha256": "2aaa8f089615e8d1cb32336f03e383aec53e62d795323293ce376010eadf27a7"
          },
          "distribution": { "id": "ubuntu", "version": "24.04.5" },
          "build": { "xzPreset": 6, "xzThreads": 4 },
          "rootfs": { "entries": 3415 }
        }
    """.trimIndent()

    @Test fun parsesShippedManifestAndIgnoresUnknownFields() {
        val manifest = parseBundledRootfsManifest(validManifest)
        requireNotNull(manifest)
        assertEquals(1, manifest.schema)
        assertEquals("arm64", manifest.abi)
        assertEquals("rootfs-arm64.tar.xz", manifest.archive.name)
        assertEquals(17_937_512L, manifest.archive.bytes)
        assertEquals("ubuntu", manifest.distribution.id)
        assertEquals("24.04.5", manifest.distribution.version)
    }

    @Test fun rejectsMalformedOrIncompleteManifests() {
        assertNull(parseBundledRootfsManifest("{"))
        assertNull(parseBundledRootfsManifest("""{"schema":1}"""))
        assertNull(parseBundledRootfsManifest("""{"schema":1,"abi":"arm64","assetId":"x"}"""))
        assertNull(parseBundledRootfsManifest(""))
    }

    @Test fun sha256MatchesKnownDigestAndChangesWithContent() {
        val file = temporary.newFile("abc.txt")
        file.writeText("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex(file))
        file.writeText("abd")
        assertEquals(64, sha256Hex(file).length)
        assertEquals(false, sha256Hex(file) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test fun emptyFileHasEmptyDigest() {
        val empty = temporary.newFile("empty.bin")
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(empty))
    }
}

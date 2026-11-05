package top.wkbin.taixu.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BashismDetectorTest {

    private fun syntaxes(command: String) = BashismDetector.detect(command).map { it.syntax }

    @Test fun flagsCommonBashOnlySyntax() {
        assertTrue(syntaxes("if [[ -f a && -f b ]]; then echo ok; fi").contains("[[ ... ]]"))
        assertTrue(syntaxes("cat <<< \"\$x\"").contains("<<<"))
        assertTrue(syntaxes("echo \$'a\\nb'").contains("\$'...'"))
        assertTrue(syntaxes("[ \"\$a\" == \"\$b\" ]").contains("=="))
        assertTrue(syntaxes("set -o pipefail; ls").contains("set -o pipefail"))
        assertTrue(syntaxes("source ~/.profile").contains("source"))
        assertTrue(syntaxes("declare -a xs").contains("declare"))
        assertTrue(syntaxes("x=\${name//a/b}").contains("\${var//...}"))
        assertTrue(syntaxes("files=( a b c )").contains("数组"))
    }

    @Test fun leavesPosixAndExplicitBashAlone() {
        assertEquals(emptyList<String>(), syntaxes("ls -la | head -20"))
        assertEquals(emptyList<String>(), syntaxes("x=\$((1 + 2)); echo \"\$x\""))
        assertEquals(emptyList<String>(), syntaxes("case \$1 in [[:alpha:]]*) echo letter;; esac"))
        assertEquals(emptyList<String>(), syntaxes("bash -c '[[ -f a ]] && echo ok'"))
        assertEquals(emptyList<String>(), syntaxes("/usr/bin/bash ./run.sh"))
        assertEquals(emptyList<String>(), syntaxes(""))
    }

    @Test fun explainProducesActionableHintOnlyWhenNeeded() {
        val hint = BashismDetector.explain("if [[ -f a ]]; then echo ok; fi")

        assertTrue(hint!!.contains("bash 专有语法"))
        assertTrue(hint.contains("[[ ... ]]"))
        assertTrue(hint.contains("bash -c"))
        assertNull(BashismDetector.explain("ls -la"))
    }

    @Test fun capsTheNumberOfFindings() {
        val noisy = "[[ a ]] && x=\$'b' && cat <<< c && [ \"\$a\" == \"\$b\" ] && shopt -s nullglob"

        assertTrue(BashismDetector.detect(noisy).size <= 4)
        assertFalse(BashismDetector.detect(noisy).isEmpty())
    }
}

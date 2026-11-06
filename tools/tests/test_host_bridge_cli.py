"""Execute the shipped CLI with a fake curl, without a device or host credentials."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
GIT_BASH = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe"
BASH = str(GIT_BASH) if GIT_BASH.is_file() else shutil.which("bash")


@unittest.skipUnless(BASH, "Bash is required to execute the POSIX CLI")
class HostBridgeCliTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="taixu-cli-test-")
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        source = (ROOT / "runtime/src/main/java/top/wkbin/taixu/runtime/bridge/HostBridgeCliScript.kt").read_text(encoding="utf-8")
        raw = source.split('private val RAW = """', 1)[1].split('""".trimIndent()', 1)[0]
        self.script = self.directory / "host.sh"
        self.script.write_text(raw.strip().replace("@@", "$") + "\n", encoding="utf-8")
        self.driver = self.directory / "driver.sh"
        self.driver.write_text('''curl() {
  while [ "$#" -gt 0 ]; do
    if [ "$1" = '-d' ]; then shift; printf '%s' "$1" > "$CAPTURE"; break; fi
    shift
  done
  printf '{"success":true,"output":"healthy bridge"}\\n200'
}
export -f curl
export TAIXU_BRIDGE_TOKEN=mock
bash "$@"
''', encoding="utf-8")

    def run_cli(self, *arguments):
        return subprocess.run([BASH, str(self.driver), str(self.script), *arguments],
                              env=dict(os.environ, CAPTURE=str(self.directory / "body.json")),
                              capture_output=True, text=True, encoding="utf-8", timeout=15)

    def test_status_commands_keep_response_and_status_code(self):
        for command in ("health", "status", "doctor"):
            with self.subTest(command=command):
                result = self.run_cli(command)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn("healthy bridge", result.stdout)

    def test_screen_numbers_cannot_rewrite_op(self):
        self.assert_rejected("screen", "tap", '1,"op":"text","text":"pwned"', "2")
        self.assert_rejected("screen", "swipe", "1", "2", "3", "4", '5,"op":"text"')
        self.assert_rejected("screen", "tap", "08", "1")
        self.assert_rejected("logcat", "pkg", "tag", "V", "kw", '1,"op":"shell"')

        tap = self.run_cli("screen", "tap", "10", "20")
        self.assertEqual(0, tap.returncode, tap.stderr)
        body = json.loads((self.directory / "body.json").read_text(encoding="utf-8"))
        self.assertEqual({"op": "tap", "x": 10, "y": 20}, body)

        swipe = self.run_cli("screen", "swipe", "1", "2", "3", "4", "50")
        self.assertEqual(0, swipe.returncode, swipe.stderr)
        body = json.loads((self.directory / "body.json").read_text(encoding="utf-8"))
        self.assertEqual("swipe", body["op"])
        self.assertEqual(50, body["duration_ms"])

    def assert_rejected(self, *arguments):
        body = self.directory / "body.json"
        body.unlink(missing_ok=True)
        result = self.run_cli(*arguments)
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertFalse(body.exists(), result.stdout)

    def test_json_text_round_trips_controls_unicode_and_trailing_newlines(self):
        text = '中文 "quote" \\ slash\nline\t tab\rreturn\bback\fform\x01control\n\n'
        for arguments, field in (
            (("clipboard", "set", text), "text"),
            (("notify", "title", text), "body"),
            (("screen", "text", text), "text"),
            (("setting", "put", "system", "key", text), "value"),
        ):
            with self.subTest(command=arguments[0]):
                result = self.run_cli(*arguments)
                self.assertEqual(0, result.returncode, result.stderr)
                body = json.loads((self.directory / "body.json").read_text(encoding="utf-8"))
                self.assertEqual(text, body[field])


if __name__ == "__main__":
    unittest.main()

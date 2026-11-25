#!/usr/bin/env python3
"""Execute SDK candidate selectors extracted from all six build entries."""
import os
from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
ENTRIES = [
    "app/src/main/assets/scripts/build_android.sh",
    "app/src/main/assets/scripts/build_flutter.sh",
    "app/src/main/assets/scripts/taixu-build.sh",
    "runtime/src/main/java/top/wkbin/taixu/runtime/build/BuildEnvironmentPreflight.kt",
    "runtime/src/main/java/top/wkbin/taixu/runtime/doctor/EnvironmentRepairer.kt",
    "assets/plugins/android-suite-offline/payload/scripts/verify-android-suite.sh",
]


def selectors():
    for entry in ENTRIES:
        text = (ROOT / entry).read_text()
        if entry.endswith("BuildEnvironmentPreflight.kt"):
            text = text.replace(r'\$', '$').replace(r'\"', '"').replace(r'\\n', r'\n')
        elif entry.endswith("EnvironmentRepairer.kt"):
            text = text.replace("${'$'}", "$")
        found = re.findall(r"(_[tT][xX]_\w+)=\$\((for _tx_candidate.*?tail -n 1)\)",
                           text, re.DOTALL)
        assert found, entry
        for variable, body in found:
            yield entry, variable, body.replace("\\\n", "").replace(
                "/opt/android-sdk/", '"$ANDROID_HOME"/')


class SdkCandidatesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="tx-sdk-")
        self.addCleanup(self.temp.cleanup)
        self.sdk = Path(self.temp.name) / "SDK with spaces"
        self.sdk.mkdir()

    def build_tools(self, version, missing=None, executable=True):
        directory = self.sdk / "build-tools" / version
        for file in ("source.properties", "lib/d8.jar", "aapt2"):
            if file == missing:
                continue
            target = directory / file
            target.parent.mkdir(parents=True, exist_ok=True)
            target.touch()
            if file == "aapt2" and executable:
                target.chmod(0o755)
        return directory

    def platform(self, version, valid=True):
        directory = self.sdk / "platforms" / ("android-" + version)
        directory.mkdir(parents=True)
        if valid:
            (directory / "android.jar").touch()

    def check(self, tools, platform):
        count = 0
        for entry, variable, body in selectors():
            with self.subTest(entry=entry, variable=variable):
                result = self.run_selector(variable, body)
                expected = platform if "/platforms/" in body else tools
                self.assertEqual(expected.rsplit("-", 1)[-1] if variable == "_tx_max_api" and expected
                                 else str(self.sdk / expected) if expected else "", result)
                count += 1
        self.assertEqual(11, count)

    def run_selector(self, variable, body, override=""):
        result = subprocess.run(
            ["/bin/sh", "-eu", "-c", f'{variable}=$({body}); printf "%s" "${variable}"'],
            env={**os.environ, "ANDROID_HOME": str(self.sdk),
                 "TAIXU_AAPT2_PATH": override},
            text=True, capture_output=True, check=True,
        )
        return result.stdout

    def test_empty_sdk(self):
        self.check("", "")

    def test_highest_complete_stable_candidate_wins(self):
        for version in ("9.0.0", "34.0.0", "35.0.0", "40.0.0-rc1"):
            self.build_tools(version)
        self.build_tools("36.0.0", missing="lib/d8.jar")
        self.build_tools("37.0.0", missing="source.properties")
        self.build_tools("38.0.0", missing="aapt2")
        self.build_tools("39.0.0", executable=False)
        for version in ("9", "34", "35", "40-preview"):
            self.platform(version)
        self.platform("39", valid=False)
        self.check("build-tools/35.0.0", "platforms/android-35")

    def test_broken_symlinks_are_not_candidates(self):
        self.build_tools("35.0.0")
        bad = self.build_tools("36.0.0", missing="lib/d8.jar")
        (bad / "lib").mkdir(exist_ok=True)
        (bad / "lib/d8.jar").symlink_to("missing.jar")
        self.platform("35")
        self.platform("36", valid=False)
        (self.sdk / "platforms/android-36/android.jar").symlink_to("missing.jar")
        self.check("build-tools/35.0.0", "platforms/android-35")

    def test_explicit_aapt2_override_is_preserved(self):
        self.build_tools("35.0.0", missing="aapt2")
        override = self.sdk / "custom aapt2"
        override.touch()
        override.chmod(0o755)
        for entry, variable, body in selectors():
            if "/platforms/" in body or "verify-android-suite" in entry:
                continue
            self.assertEqual(str(self.sdk / "build-tools/35.0.0"),
                             self.run_selector(variable, body, str(override)), entry)


class SdkValidationTest(unittest.TestCase):
    def test_generated_validate_script(self):
        source = (ROOT / "tools/src/main/java/top/wkbin/taixu/core/tools/AndroidSdkManager.kt").read_text()
        source = source.split("suspend fun validate(", 1)[1].split('val script = """', 1)[1].split('""".trimIndent()', 1)[0]
        source = source.replace("${'$'}", "$")
        for custom, missing in [(False, None), (True, None), (False, "platform"), (False, "d8")]:
            with self.subTest(custom=custom, missing=missing), tempfile.TemporaryDirectory() as temp:
                base = Path(temp) / "SDK"
                tools = base / "build-tools/35.0.0"
                (tools / "lib").mkdir(parents=True)
                (tools / "aapt2").write_bytes(bytes(18) + bytes([0xb7, 0]) + bytes(44))
                if missing != "d8":
                    (tools / "lib/d8.jar").touch()
                (base / "platform-tools").mkdir()
                platform = base / "platforms/android-35"
                platform.mkdir(parents=True)
                if missing != "platform":
                    (platform / "android.jar").touch()
                (base / "build-tools/99.0.0").mkdir()
                (base / "platforms/android-99").mkdir()
                sdk = Path(temp) / "custom SDK" if custom else base
                if custom:
                    (sdk / "build-tools").mkdir(parents=True)
                script = source.replace("/opt/android-sdk", str(base))
                # The production absolute glob is unquoted; quote its mapped fixture root.
                script = script.replace("in " + str(base) + "/", "in " + shlex.quote(str(base)) + "/")
                script = script.replace("!= " + str(base) + ";", "!= " + shlex.quote(str(base)) + ";")
                script = script.replace("$root", shlex.quote(str(sdk))).replace("$aapt2", shlex.quote(str(tools / "aapt2")))
                result = subprocess.run(["sh", "-eu", "-c", "timeout() { test \"$#\" -eq 3; }\n" + script], capture_output=True, text=True)
                if missing:
                    self.assertEqual(2, result.returncode, result.stderr)
                    self.assertIn("Android Platform" if missing == "platform" else "Build-Tools", result.stdout)
                    self.assertNotIn("parameter not set", result.stderr)
                else:
                    self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                    if custom:
                        self.assertTrue((sdk / "platforms").is_symlink())
                        self.assertTrue((sdk / "build-tools/35.0.0").is_symlink())


if __name__ == "__main__":
    unittest.main(verbosity=2)

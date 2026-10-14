"""Offline shell regression: real Node, fake npm; never contacts a registry."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


def shell_path(path):
    value = path.as_posix()
    if os.name == "nt":
        return "/" + value[0].lower() + value[2:]
    return value


class CatalogRuntimeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.bash = shutil.which("bash")
        if os.name == "nt":
            self.bash = r"C:\Program Files\Git\bin\bash.exe"
        if not self.bash or not shutil.which("node"):
            self.skipTest("bash and node required")
        self.modules = self.root / "modules"
        self.package = self.modules / "fixture-mcp"
        self.package.mkdir(parents=True)
        self.manifest = {"name": "fixture-mcp", "version": "1.2.3", "bin": {"fixture-mcp": "entry.cjs"}}
        self.write_manifest()
        (self.package / "entry.cjs").write_text("console.log(JSON.stringify(process.argv.slice(2)));", encoding="utf-8")
        self.markers = self.root / "markers"
        self.markers.mkdir()
        (self.markers / "fixture.ok").write_text("SPEC=fixture-mcp@1.2.3\nVERSION=1.2.3\n", encoding="utf-8")
        self.bin = self.root / "bin"
        self.bin.mkdir()
        npm = self.bin / "npm"
        npm.write_text("#!/bin/sh\nif [ \"$*\" = 'root -g' ]; then printf '%s\\n' '" + self.modules.as_posix() +
                       "'; else echo 'NETWORK FORBIDDEN' >&2; exit 99; fi\n", encoding="utf-8")
        npm.chmod(0o755)
        source = Path(__file__).resolve().parents[2] / "app/src/main/assets/scripts/setup_mcp_catalog.sh"
        self.script = self.root / "catalog.sh"
        self.script.write_text(source.read_text(encoding="utf-8").replace(
            'MARKER_DIR="/opt/taixu/mcp"', 'MARKER_DIR="' + shell_path(self.markers) + '"'), encoding="utf-8")

    def write_manifest(self):
        (self.package / "package.json").write_text(json.dumps(self.manifest), encoding="utf-8")

    def run_catalog(self, command, *args):
        return subprocess.run([self.bash, "-c", 'export PATH="$1:$PATH"; shift; exec /bin/sh "$@"',
            "test", shell_path(self.bin), shell_path(self.script), command, "fixture", "fixture-mcp", *args],
            capture_output=True, text=True, encoding="utf-8", timeout=10)

    def test_valid_installation_probes_and_runs_without_network(self):
        self.assertEqual(0, self.run_catalog("probe").returncode)
        result = self.run_catalog("run", "argument with spaces", "--mcp")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["argument with spaces", "--mcp"], json.loads(result.stdout))

    def test_deleted_package_does_not_pass_with_stale_marker(self):
        (self.package / "package.json").unlink()
        self.assertNotEqual(0, self.run_catalog("probe").returncode)
        self.assertNotEqual(0, self.run_catalog("run").returncode)

    def test_wrong_version_and_missing_entry_are_rejected(self):
        self.manifest["version"] = "1.2.4"
        self.write_manifest()
        self.assertNotEqual(0, self.run_catalog("probe").returncode)
        self.manifest["version"] = "1.2.3"
        self.write_manifest()
        (self.package / "entry.cjs").unlink()
        self.assertNotEqual(0, self.run_catalog("probe").returncode)

    def test_package_substitution_and_path_escape_are_rejected(self):
        (self.markers / "fixture.ok").write_text("SPEC=other-package@1.2.3\n", encoding="utf-8")
        self.assertNotEqual(0, self.run_catalog("probe").returncode)
        (self.markers / "fixture.ok").write_text("SPEC=fixture-mcp@1.2.3\n", encoding="utf-8")
        self.manifest["bin"] = "../outside.cjs"
        self.write_manifest()
        self.assertNotEqual(0, self.run_catalog("probe").returncode)


if __name__ == "__main__":
    unittest.main()

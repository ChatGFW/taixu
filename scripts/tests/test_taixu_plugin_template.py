"""Offline regression for the bundled local-plugin development project."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile


TEMPLATE = Path(__file__).resolve().parents[2] / "project-template/src/main/assets/templates/taixu-plugin/process"


class PluginTemplateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        manifest = json.loads((TEMPLATE / "template.json").read_text(encoding="utf-8"))
        self.values = {variable["name"]: variable["defaultValue"] for variable in manifest["variables"]}
        self.values.update(toolId="demo-tool", pluginId="example.demo", pluginName="演示插件")

        def render(value):
            for key, replacement in self.values.items():
                value = value.replace("{{" + key + "}}", replacement)
            return value

        for source in TEMPLATE.rglob("*"):
            if not source.is_file() or source.name == "template.json":
                continue
            relative = render(source.relative_to(TEMPLATE).as_posix()).removesuffix(".template")
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(render(source.read_text(encoding="utf-8")), encoding="utf-8", newline="\n")
        spec = importlib.util.spec_from_file_location("plugin_packager", self.root / "tools/package_plugin.py")
        self.packager = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.packager)

    def test_cli_produces_exact_payload_and_valid_hashes_without_mutating_source(self):
        original = (self.root / "manifest.json").read_bytes()
        result = subprocess.run([sys.executable, str(self.root / "tools/package_plugin.py")],
                                check=True, capture_output=True, text=True)
        output = self.root / "dist/demo-tool-1.0.0.txplugin"
        self.assertIn(output.name, result.stdout)
        with zipfile.ZipFile(output) as archive:
            self.assertEqual({"manifest.json", "payload/bin/demo-tool"}, set(archive.namelist()))
            manifest = json.loads(archive.read("manifest.json"))
            self.assertEqual(2, manifest["schemaVersion"])
            self.assertEqual("example.demo", manifest["plugin"]["id"])
            self.assertEqual("演示插件", manifest["name"])
            self.assertEqual({"bin/demo-tool": hashlib.sha256(archive.read("payload/bin/demo-tool")).hexdigest()},
                             manifest["plugin"]["integrity"])
        self.assertEqual(original, (self.root / "manifest.json").read_bytes())

    def test_packager_recalculates_hashes_after_payload_changes(self):
        output = self.packager.build()
        (self.root / "payload/bin/demo-tool").write_bytes(b"#!/bin/sh\necho changed\n")
        self.packager.build()
        with zipfile.ZipFile(output) as archive:
            expected = hashlib.sha256(b"#!/bin/sh\necho changed\n").hexdigest()
            self.assertEqual(expected, json.loads(archive.read("manifest.json"))["plugin"]["integrity"]["bin/demo-tool"])

    def test_invalid_id_does_not_replace_existing_archive(self):
        output = self.packager.build()
        original = output.read_bytes()
        manifest = json.loads((self.root / "manifest.json").read_text(encoding="utf-8"))
        manifest["id"] = "../escape"
        (self.root / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaises(ValueError):
            self.packager.build()
        self.assertEqual(original, output.read_bytes())

    def test_initial_shell_command_runs_and_reports_version(self):
        shell = r"C:\Program Files\Git\bin\bash.exe" if os.name == "nt" else shutil.which("sh")
        if not shell or not Path(shell).is_file():
            self.skipTest("POSIX shell unavailable")
        result = subprocess.run([shell, "-c", "sh payload/bin/demo-tool --version"], cwd=self.root,
                                check=True, capture_output=True, text=True)
        self.assertEqual("1.0.0", result.stdout.strip())
        result = subprocess.run([shell, "-c", "sh payload/bin/demo-tool"], cwd=self.root,
                                check=True, capture_output=True)
        self.assertIn("演示插件", result.stdout.decode("utf-8"))


if __name__ == "__main__":
    unittest.main()

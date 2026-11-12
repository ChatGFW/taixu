import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE_PATH = Path(__file__).resolve().parent.parent / "package-cc-switch-plugin.py"
spec = importlib.util.spec_from_file_location("cc_switch_pack", MODULE_PATH)
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)


class PackageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.original_project = package.PROJECT
        package.PROJECT = self.root / "project"
        for name in ("payload/bin", "payload/scripts", "payload/checksums"):
            (package.PROJECT / name).mkdir(parents=True)
        (package.PROJECT / "manifest.json").write_text(json.dumps({"id":"cc-switch", "version":package.VERSION, "launchType":"web"}))
        (package.PROJECT / "payload/bin/cc-switch-daemon").write_bytes(b'#!/bin/sh\nexit 0\n')
        self.build = self.root / "build"
        self.build.mkdir()
        (self.build / "UPSTREAM.json").write_text(json.dumps({"version":package.UPSTREAM_VERSION, "commit":package.COMMIT}))
        (self.build / "LICENSE").write_text('test fixture')
        (self.build / "Cargo.lock").write_text('test fixture')
        self.ui = self.root / "ui"
        self.ui.mkdir()
        (self.ui / "index.html").write_text('<html>offline fixture</html>')
        self.server = self.root / "server"
        header = bytearray(64)
        header[:6] = b'\x7fELF\x02\x01'
        header[18:20] = b'\xb7\x00'
        self.server.write_bytes(header + b'GLIBC_2.31\0')

    def tearDown(self):
        package.PROJECT = self.original_project
        self.temp.cleanup()

    def test_archive_is_deterministic_and_verified(self):
        first, second = self.root / "one.txplugin", self.root / "two.txplugin"
        package.pack(self.server, self.ui, self.build, first)
        package.pack(self.server, self.ui, self.build, second)
        self.assertEqual(first.read_bytes(), second.read_bytes())

    def test_rejects_new_glibc_and_missing_renderer(self):
        self.server.write_bytes(self.server.read_bytes() + b'GLIBC_2.39\0')
        with self.assertRaisesRegex(ValueError, "newer glibc"):
            package.pack(self.server, self.ui, self.build, self.root / "bad.txplugin")
        self.server.write_bytes(self.server.read_bytes().replace(b'GLIBC_2.39',b'GLIBC_2.31'))
        (self.ui / "index.html").unlink()
        with self.assertRaisesRegex(ValueError, "renderer"):
            package.pack(self.server, self.ui, self.build, self.root / "missing.txplugin")

    def test_rejects_wrong_machine(self):
        data = bytearray(self.server.read_bytes())
        data[18:20] = b'\x3e\x00'
        self.server.write_bytes(data)
        with self.assertRaisesRegex(ValueError, "ARM64"):
            package.pack(self.server, self.ui, self.build, self.root / "x86.txplugin")


if __name__ == '__main__':
    unittest.main()

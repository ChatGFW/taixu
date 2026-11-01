"""Validate term-devtools launchers and package policy without network installs."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(os.name == 'posix', 'POSIX PTY required')
class BtopLauncherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.tool = self.base / 'tool'
        (self.tool / 'libexec').mkdir(parents=True)
        binary = self.tool / 'libexec/btop'
        binary.write_text('''#!/bin/sh
if [ "${1:-}" = --version ]; then echo btop version: 1.4.8; exit 0; fi
printf 'arg=%s\\n' "${1:-}"
''')
        binary.chmod(0o755)
        source = (ROOT / 'assets/plugins/term-devtools/payload/bin/btop').read_text(encoding='utf-8')
        source = source.replace('/opt/taixu/tools/term-devtools', str(self.tool))
        self.launcher = self.base / 'launch'
        self.launcher.write_text(source)
        self.launcher.chmod(0o755)

    def test_version_and_noninteractive_rejection(self):
        version = subprocess.run(
            [str(self.launcher), '--version'], capture_output=True, text=True, check=True,
        )
        self.assertIn('1.4.8', version.stdout)
        result = subprocess.run([str(self.launcher)], capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('原生交互终端', result.stderr.decode())


class PackageTests(unittest.TestCase):
    def test_manifest_and_install_policy(self):
        source = ROOT / 'assets/plugins/term-devtools'
        manifest = json.loads((source / 'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual(manifest['launchType'], 'pty')
        self.assertTrue(manifest['offlineOnly'])
        self.assertEqual(manifest['commandLinks'], ['btop', 'fx'])
        script = (source / 'payload/scripts/install.sh').read_text(encoding='utf-8')
        self.assertNotIn('curl ', script)
        self.assertNotIn('apt-get ', script)
        fx = (source / 'payload/bin/fx').read_text(encoding='utf-8')
        self.assertNotIn('原生交互终端', fx)


if __name__ == '__main__':
    unittest.main()

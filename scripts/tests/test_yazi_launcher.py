"""Run the POSIX Yazi launcher in a disposable Linux fixture, including a real PTY."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(os.name == 'posix', 'POSIX PTY required')
class LauncherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.tool = self.base / 'tool'
        self.data = self.base / 'data'
        (self.tool / 'libexec').mkdir(parents=True)
        (self.tool / 'config').mkdir(parents=True)
        (self.tool / 'config/yazi.toml').write_text('show_hidden = false\n')
        binary = self.tool / 'libexec/yazi'
        binary.write_text('''#!/bin/sh
if [ "${1:-}" = --version ]; then echo Yazi 26.9.1; exit 0; fi
printf 'config=%s\\narg=%s\\n' "$YAZI_CONFIG_HOME" "${1:-}"
''')
        binary.chmod(0o755)
        source = (ROOT / 'assets/plugins/yazi/payload/bin/yazi').read_text(encoding='utf-8')
        source = source.replace('/opt/taixu/tools/yazi', str(self.tool))
        source = source.replace('/opt/taixu/data/yazi', str(self.data))
        self.launcher = self.base / 'launch'
        self.launcher.write_text(source)
        self.launcher.chmod(0o755)

    def test_version_and_noninteractive_rejection(self):
        version = subprocess.run(
            [str(self.launcher), '--version'], capture_output=True, text=True, check=True,
        )
        self.assertIn('26.9.1', version.stdout)
        result = subprocess.run([str(self.launcher)], capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('原生交互终端', result.stderr.decode())

    def pty_run(self, args):
        import pty
        master, slave = pty.openpty()
        process = subprocess.Popen(
            [str(self.launcher), *args], stdin=slave, stdout=slave, stderr=slave,
        )
        os.close(slave)
        output = b''
        try:
            while True:
                try:
                    block = os.read(master, 4096)
                except OSError:
                    break
                if not block:
                    break
                output += block
        finally:
            os.close(master)
        self.assertEqual(process.wait(timeout=5), 0)
        return output.decode()

    def test_pty_configuration_copy_once(self):
        first = self.pty_run(['/workspace/demo'])
        self.assertIn('config=' + str(self.data / 'config'), first)
        self.assertIn('arg=/workspace/demo', first)
        config = self.data / 'config/yazi.toml'
        self.assertTrue(config.exists())
        config.write_text('custom configuration')
        second = self.pty_run(['/workspace/demo'])
        self.assertIn('config=' + str(self.data / 'config'), second)
        self.assertEqual(config.read_text(), 'custom configuration')


class PackageTests(unittest.TestCase):
    def test_manifest_pty_and_no_network_install(self):
        source = ROOT / 'assets/plugins/yazi'
        manifest = json.loads((source / 'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual(manifest['launchType'], 'pty')
        self.assertTrue(manifest['offlineOnly'])
        self.assertEqual(manifest['commandLinks'], ['yazi', 'ya'])
        script = (source / 'payload/scripts/install.sh').read_text(encoding='utf-8')
        self.assertNotIn('curl ', script)
        self.assertNotIn('apt-get ', script)
        config = (source / 'payload/config/yazi.toml').read_text(encoding='utf-8')
        self.assertIn('image/*', config)
        self.assertIn('noop', config)


if __name__ == '__main__':
    unittest.main()

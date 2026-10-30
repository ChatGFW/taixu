"""Run the POSIX launcher in a disposable Linux fixture, including a real PTY."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(os.name == 'posix' and shutil.which('git'), 'POSIX PTY and Git required')
class LauncherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.tool = self.base / 'tool'
        self.data = self.base / 'data'
        self.github = self.base / 'github'
        (self.tool / 'libexec').mkdir(parents=True)
        (self.github / 'bin').mkdir(parents=True)
        (self.github / 'bin/gh').write_text('#!/bin/sh\nexit 0\n')
        (self.github / 'bin/gh').chmod(0o755)
        (self.tool / 'config.yml').write_text('git:\n  autoFetch: false\n')
        binary = self.tool / 'libexec/lazygit'
        binary.write_text('''#!/bin/sh
if [ "${1:-}" = --version ]; then echo version=0.66.2; exit 0; fi
printf 'config=%s\ngitconfig=%s\narg=%s\n' "$LG_CONFIG_DIR" "${GIT_CONFIG_GLOBAL:-}" "${1:-}"
''')
        binary.chmod(0o755)
        source = (ROOT / 'assets/plugins/lazygit/payload/bin/lazygit').read_text(encoding='utf-8')
        source = source.replace('/opt/taixu/tools/lazygit', str(self.tool))
        source = source.replace('/opt/taixu/data/lazygit', str(self.data))
        source = source.replace('/opt/taixu/tools/github', str(self.github))
        source = source.replace('/opt/taixu/data/github', str(self.github / 'data'))
        self.launcher = self.base / 'launch'
        self.launcher.write_text(source)
        self.launcher.chmod(0o755)

    def test_version_and_noninteractive_rejection(self):
        version = subprocess.run([str(self.launcher), '--version'], capture_output=True, text=True, check=True)
        self.assertIn('0.66.2', version.stdout)
        result = subprocess.run([str(self.launcher)], capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('原生交互终端', result.stderr.decode())

    def pty_run(self, args):
        import pty
        master, slave = pty.openpty()
        environment = os.environ.copy()
        environment.pop('GIT_CONFIG_GLOBAL', None)
        process = subprocess.Popen([str(self.launcher), *args], stdin=slave, stdout=slave,
                                   stderr=slave, env=environment)
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

    def test_pty_configuration_and_explicit_github(self):
        normal = self.pty_run(['--path=/workspace/demo'])
        self.assertIn('gitconfig=\r\n', normal)
        self.assertIn('arg=--path=/workspace/demo', normal)
        config = self.data / 'config/config.yml'
        self.assertTrue(config.exists())
        config.write_text('custom configuration')
        github = self.pty_run(['--github', '--path=/workspace/demo'])
        self.assertIn('gitconfig=' + str(self.github / 'data/gitconfig'), github)
        self.assertEqual(config.read_text(), 'custom configuration')


class PackageTests(unittest.TestCase):
    def test_manifest_pty_and_no_network_install(self):
        source = ROOT / 'assets/plugins/lazygit'
        manifest = json.loads((source / 'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual(manifest['launchType'], 'pty')
        self.assertTrue(manifest['offlineOnly'])
        script = (source / 'payload/scripts/install.sh').read_text(encoding='utf-8')
        self.assertNotIn('curl ', script)
        self.assertNotIn('apt-get ', script)


if __name__ == '__main__':
    unittest.main()

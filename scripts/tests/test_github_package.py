"""Verify the unified package's runtime entry and embedded control panel."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('github_packager', ROOT / 'tools/package-github-plugin.py')
packager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packager)


class UnifiedPackageTest(unittest.TestCase):
    def test_one_process_package_contains_cli_service_and_page(self):
        source = ROOT / 'assets/plugins/github'
        manifest = json.loads((source / 'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual(manifest['plugin']['execution'], 'PROCESS')
        self.assertEqual(manifest['launchCommand'], 'gh-ui serve')
        self.assertEqual(manifest['servicePath'], '/')
        self.assertIn({'name': 'LOCAL_WEB', 'target': 'authenticated-v1'}, manifest['plugin']['capabilities'])
        with tempfile.TemporaryDirectory() as temporary:
            project = Path(temporary) / 'github'
            shutil.copytree(source, project, ignore=shutil.ignore_patterns('vendor', '__pycache__'))
            vendor = project / 'payload/vendor'
            vendor.mkdir()
            (vendor / 'gh').write_bytes(b'offline test binary')
            (vendor / 'LICENSE').write_text('offline test license', encoding='utf-8')
            target = Path(temporary) / 'github.txplugin'
            packager.pack(project, target)
            with zipfile.ZipFile(target) as archive:
                entries = set(archive.namelist())
                self.assertEqual(entries, {'manifest.json', *('payload/' + p for p in packager.GITHUB_PAYLOAD)})
                html = archive.read('payload/web/index.html').decode('utf-8')
                self.assertIn('app.js', html)
                self.assertIn('app.css', html)
                self.assertNotIn('payload/bin/gh-token', entries)


if __name__ == '__main__':
    unittest.main()

#!/usr/bin/env python3
"""Offline packaging/security regression tests, using synthetic archives only."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('package_agent2api', ROOT / 'tools/package-agent2api-plugin.py')
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)


class Agent2ApiPackageTest(unittest.TestCase):
    def make_archive(self, directory, entries):
        path = directory / 'upstream.tar.gz'
        with tarfile.open(path, 'w:gz') as archive:
            for name, content, link in entries:
                member = tarfile.TarInfo(name)
                if link:
                    member.type = tarfile.SYMTYPE
                    member.linkname = link
                    archive.addfile(member)
                else:
                    member.size = len(content)
                    archive.addfile(member, io.BytesIO(content))
        return path

    def test_rejects_archive_traversal_links_and_duplicate_members_before_writing(self):
        for entries in ([('../escape', b'x', None)], [('ui/leak', b'', '/etc/passwd')],
                        [('ui/index.html', b'x', None), ('ui/index.html', b'y', None)]):
            with self.subTest(entries=entries), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                archive = self.make_archive(root, entries)
                with self.assertRaises(ValueError):
                    package.vendor_release(archive, root / 'LICENSE', root / 'vendor')
                self.assertFalse((root / 'vendor').exists())

    def test_rejects_non_arm64_binary(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            archive = self.make_archive(root, [('agent2api-server', b'\x7fELF\x02\x01' + bytes(60), None),
                                               ('ui/index.html', b'<html></html>', None)])
            with self.assertRaisesRegex(ValueError, 'ARM64'):
                package.vendor_release(archive, root / 'LICENSE', root / 'vendor')

    def test_existing_download_must_match_pin_without_network_fallback(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'archive'
            path.write_bytes(b'corrupt')
            with self.assertRaisesRegex(ValueError, 'checksum'):
                package.fetch(path, 'https://invalid.example/', '0' * 64)

    def test_reproducible_pack_contains_exact_payload_hashes(self):
        with tempfile.TemporaryDirectory() as temp:
            project = Path(temp) / 'project'
            (project / 'payload').mkdir(parents=True)
            (project / 'manifest.json').write_text((ROOT / 'assets/plugins/agent2api/manifest.json').read_text(encoding='utf-8'), encoding='utf-8')
            (project / 'payload/empty-account-config').write_bytes(b'{}\n')
            first = package.pack(project, Path(temp) / 'first.txplugin')
            second = package.pack(project, Path(temp) / 'second.txplugin')
            self.assertEqual(first.read_bytes(), second.read_bytes())
            with zipfile.ZipFile(first) as archive:
                manifest = json.loads(archive.read('manifest.json'))
                self.assertEqual({'empty-account-config': hashlib.sha256(b'{}\n').hexdigest()}, manifest['plugin']['integrity'])
                self.assertEqual({'manifest.json', 'payload/empty-account-config'}, set(archive.namelist()))


if __name__ == '__main__':
    unittest.main()

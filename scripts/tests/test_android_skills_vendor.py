"""A changed upstream pin must refresh an existing non-empty skill cache."""
import importlib.util
import io
import json
from pathlib import Path
import shutil
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('android_skills_vendor', ROOT / 'tools/vendor-android-skills-plugin.py')
vendor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vendor)


class SkillCacheTest(unittest.TestCase):
    def test_changed_pin_replaces_old_skills_and_matching_pin_reuses_cache(self):
        with tempfile.TemporaryDirectory() as temporary:
            project = Path(temporary) / 'plugin'
            payload = project / 'payload'
            previous = payload / 'skills/old-skill'
            previous.mkdir(parents=True)
            (previous / 'SKILL.md').write_text('old skill', encoding='utf-8')
            provenance = project / 'UPSTREAM.md'
            provenance.write_text('old upstream commit', encoding='utf-8')
            archive = Path(temporary) / 'source.tar.gz'
            entries = {
                'source/.claude-plugin/plugin.json': json.dumps({'version': '2.0.0', 'skills': ['./feature/new-skill']}).encode(),
                'source/feature/new-skill/SKILL.md': b'new skill',
                'source/LICENSE.txt': b'test license',
            }
            with tarfile.open(archive, 'w:gz') as output:
                for name, content in entries.items():
                    entry = tarfile.TarInfo(name)
                    entry.size = len(content)
                    output.addfile(entry, io.BytesIO(content))

            def download(url, destination):
                shutil.copyfile(archive, destination)

            with patch.object(vendor, 'PLUGIN_DIR', project), patch.object(vendor, 'PAYLOAD_DIR', payload):
                with patch.object(vendor, 'download', side_effect=download) as fetch:
                    self.assertEqual(vendor.vendor_skills(False), ('2.0.0', 1))
                    fetch.assert_called_once()
                self.assertFalse(previous.exists())
                self.assertEqual((payload / 'skills/new-skill/SKILL.md').read_bytes(), b'new skill')
                provenance.write_text(f'提交 `{vendor.ANDROID_SKILLS_COMMIT}`，插件版本 `2.0.0`', encoding='utf-8')
                with patch.object(vendor, 'download', side_effect=AssertionError('cache should be reused')):
                    self.assertEqual(vendor.vendor_skills(False), ('2.0.0', 1))


if __name__ == '__main__':
    unittest.main()

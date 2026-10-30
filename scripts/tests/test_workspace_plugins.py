"""Offline behavior tests: retrieval, index lifecycle and actual MCP subprocesses."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
LIB = ROOT / 'assets/plugins/local-rag/payload/lib'
sys.path.insert(0, str(LIB))
sys.dont_write_bytecode = True
import rag_cli
import rag_index


class RetrievalTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.project = self.base / 'workspace/demo'
        self.project.mkdir(parents=True)
        self.data = self.base / 'data'

    def write(self, path, text):
        target = self.project / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding='utf-8')

    def call(self, name, **kwargs):
        return rag_cli.call(name, {'project': 'demo', **kwargs}, self.project.parent, self.data)

    def test_bilingual_lines_and_fingerprint(self):
        self.write('guide.md', '# Guide\n工作区索引使用 SQLite FTS 离线检索\nNo paid provider.\n')
        self.assertTrue(self.call('index')['complete'])
        for query in ('工作区', 'sqlite', '离线检索 SQLite'):
            hit = self.call('search', query=query)['hits'][0]
            self.assertEqual(hit['path'], 'guide.md')
            self.assertEqual((hit['start_line'], hit['end_line']), (1, 3))
            self.assertEqual(len(hit['sha256']), 64)
            self.assertFalse(hit['stale'])
            self.assertIn('SQLite FTS', hit['text'])
        self.assertEqual(self.call('search', query='区工作')['hits'], [])

    def test_restart_resume_update_delete_and_new_scan(self):
        for name in ('a.md', 'b.md', 'c.md'):
            self.write(name, 'retrieval original')
        self.assertFalse(self.call('index', batch=1)['complete'])
        self.assertFalse(self.call('index', batch=1)['complete'])
        self.assertTrue(self.call('index', batch=1)['complete'])
        self.assertEqual(self.call('index')['updated'], 0)
        self.write('a.md', 'replacement')
        (self.project / 'b.md').unlink()
        stale = self.call('search', query='original')['hits']
        self.assertTrue(any(h['stale'] for h in stale))
        self.assertTrue(all(not h['text'] for h in stale if h['stale']))
        self.assertTrue(self.call('index')['complete'])
        self.assertEqual(self.call('status')['files'], 2)
        self.assertEqual([h['path'] for h in self.call('search', query='original')['hits']], ['c.md'])
        self.assertEqual(self.call('search', query='replacement')['hits'][0]['path'], 'a.md')

    def test_binary_oversize_hidden_and_invalid_utf8(self):
        self.write('normal.md', 'visible')
        self.write('.env', 'visible secret')
        self.write('node_modules/package/readme.md', 'visible dependency')
        self.write('big.txt', 'x' * (rag_index.MAX_FILE + 1))
        (self.project / 'binary.txt').write_bytes(b'visible\0binary')
        (self.project / 'invalid.md').write_bytes(b'\xff')
        result = self.call('index')
        self.assertEqual(result['skipped'], 3)
        self.assertEqual(result['files'], 1)

    @unittest.skipUnless(shutil.which('git'), 'Git needed for ignore behavior')
    def test_gitignore_negation_tracked_and_nested_project(self):
        subprocess.run(['git', 'init', '-q', str(self.project)], check=True)
        self.write('.gitignore', '*.md\n!keep.md\n')
        for name in ('ignore.md', 'keep.md', 'tracked.md', 'sub/ignore.md'):
            self.write(name, 'retrieval')
        subprocess.run(['git', '-C', str(self.project), 'add', '-f', 'tracked.md'], check=True)
        self.call('index')
        self.assertEqual({h['path'] for h in self.call('search', query='retrieval')['hits']}, {'keep.md', 'tracked.md'})
        nested = rag_cli.call('index', {'project': 'demo/sub'}, self.project.parent, self.data)
        self.assertEqual(nested['files'], 0)

    def test_project_scope_and_strict_arguments(self):
        for project in ('../demo', '/etc', '.', 'demo/../../escape', 'C:\\Windows'):
            with self.assertRaises((ValueError, OSError)):
                rag_cli.call('status', {'project': project}, self.project.parent, self.data)
        for args in ({'project': 'demo', 'batch': True}, {'project': 'demo', 'batch': 65},
                     {'project': 'demo', 'unknown': 1}):
            with self.assertRaises(ValueError):
                rag_cli.call('index', args, self.project.parent, self.data)

    def test_symlinks_and_project_separation(self):
        outside = self.base / 'outside.md'
        outside.write_text('external secret')
        try:
            (self.project / 'link.md').symlink_to(outside)
            (self.project.parent / 'alias').symlink_to(self.project, target_is_directory=True)
        except OSError:
            self.skipTest('symlink privileges unavailable')
        self.call('index')
        self.assertEqual(self.call('status')['files'], 0)
        with self.assertRaises(ValueError):
            rag_cli.call('status', {'project': 'alias'}, self.project.parent, self.data)

    def test_capacity_and_scan_failure_preserve_previous_index(self):
        self.write('a.md', 'retrieval')
        self.call('index')
        self.write('b.md', 'retrieval')
        with patch.object(rag_index, 'MAX_INDEX_FILES', 1):
            result = self.call('index')
            self.assertEqual(result['skipped'], 1)
            self.assertEqual(result['files'], 1)
        with patch.object(rag_index, 'MAX_ENTRIES', 1):
            with self.assertRaises(ValueError):
                self.call('index')
        self.assertEqual(self.call('status')['files'], 1)

    def test_long_lines_and_fts_operator_input(self):
        self.write('long.md', 'word ' * 1000 + '\nneedle')
        self.call('index')
        hits = self.call('search', query='needle')['hits']
        self.assertEqual(len(hits), 1)
        self.assertLessEqual(len(hits[0]['text']), 2402)
        self.assertEqual(self.call('search', query='" OR unknown --')['hits'], [])

    def test_actual_mcp_process_and_restart(self):
        self.write('README.md', '离线检索 sqlite')
        def request(name, arguments):
            return {'jsonrpc': '2.0', 'id': name, 'method': 'tools/call',
                    'params': {'name': name, 'arguments': {'project': 'demo', **arguments}}}
        def run(requests):
            result = subprocess.run([sys.executable, '-I', str(LIB / 'rag_cli.py'), '--mcp',
                                     '--workspace-base', str(self.project.parent), '--data', str(self.data)],
                                    input=''.join(json.dumps(r) + '\n' for r in requests),
                                    capture_output=True, text=True, encoding='utf-8', timeout=20, check=True)
            self.assertEqual(result.stderr, '')
            return [json.loads(line) for line in result.stdout.splitlines()]
        first = run([{'jsonrpc': '2.0', 'id': 1, 'method': 'initialize'},
                     {'jsonrpc': '2.0', 'method': 'notifications/initialized'},
                     {'jsonrpc': '2.0', 'id': 2, 'method': 'tools/list'}, request('index', {})])
        self.assertEqual(len(first), 3)
        self.assertEqual(len(first[1]['result']['tools']), 3)
        self.assertFalse(first[2]['result']['isError'])
        second = run([request('search', {'query': '离线检索'}), request('search', {'query': 123}), []])
        value = json.loads(second[0]['result']['content'][0]['text'])
        self.assertEqual(value['hits'][0]['path'], 'README.md')
        self.assertTrue(second[1]['result']['isError'])
        self.assertIn('error', second[2])

    def test_manifest_matches_mcp_schema(self):
        manifest = json.loads((ROOT / 'assets/plugins/local-rag/manifest.json').read_text(encoding='utf-8'))
        declarations = manifest['plugin']['harness']['tools']
        self.assertEqual({t['id']: t['schema'] for t in declarations},
                         {t['name']: t['inputSchema'] for t in rag_cli.TOOLS})

    @unittest.skipUnless(os.name == 'posix', 'POSIX installer required')
    def test_offline_install_and_installed_runtime(self):
        tool = self.base / 'installed'
        payload = LIB.parent
        environment = {**os.environ, 'TAIXU_TOOL_DIR': str(tool),
                       'TAIXU_PLUGIN_PAYLOAD': str(payload)}
        subprocess.run(['/bin/sh', str(payload / 'scripts/install.sh')],
                       env=environment, check=True, capture_output=True)
        self.assertTrue(os.access(tool / 'bin/local-rag', os.X_OK))
        self.write('README.md', '安装后检索 sqlite')
        cli = ['/usr/bin/python3', '-I', str(tool / 'lib/rag_cli.py')]
        probe = subprocess.run([*cli, '--check'], capture_output=True, text=True, check=True)
        self.assertIn('FTS5 OK', probe.stdout)
        result = subprocess.run([*cli, '--workspace-base', str(self.project.parent),
                                 '--data', str(self.data), 'index', 'demo'],
                                capture_output=True, text=True, check=True)
        self.assertTrue(json.loads(result.stdout)['complete'])


if __name__ == '__main__':
    unittest.main()

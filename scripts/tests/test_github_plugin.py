"""Offline GitHub plugin regression: no credentials or real GitHub requests."""
import http.client
import importlib.util
import json
from pathlib import Path
import tempfile
import threading
import hashlib
import hmac
import secrets
import time
import io
import sys
import unittest
from unittest.mock import patch
from http.server import ThreadingHTTPServer

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'assets/plugins/github/payload/lib/github_plugin.py'
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('github_plugin', SOURCE)
plugin = importlib.util.module_from_spec(spec)
spec.loader.exec_module(plugin)


class GitHubPluginTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.app = plugin.App(Path(self.temporary.name) / 'data', ROOT / 'assets/plugins/github/payload')

    def tearDown(self):
        self.app.pool.shutdown(wait=True, cancel_futures=True)
        self.temporary.cleanup()

    def test_repo_paths_cannot_escape_or_be_options(self):
        for name in ('../repo', 'owner/..', '/tmp/repo', '--flag', 'a/b/c', 'a/../b', 'a/b;echo', 'a/b c'):
            with self.subTest(name=name), self.assertRaises(plugin.OperationError):
                self.app.repo(name, False)
        self.assertEqual(self.app.repo('owner/project', False).name, 'project')
        root = self.app.data / 'repos'
        root.mkdir()
        try:
            (root / 'escape').symlink_to(self.temporary.name, target_is_directory=True)
        except OSError:
            return  # Windows developer mode is not assumed.
        with self.assertRaises(plugin.OperationError):
            self.app.repo('escape/outside', False)

    def test_token_is_validated_before_replacing_old_credentials(self):
        plugin.save_private(self.app.data / 'token', 'old-token-value')
        with patch.object(self.app, 'gh', side_effect=plugin.OperationError('rejected')):
            with self.assertRaises(plugin.OperationError):
                self.app.perform('auth/token', {'token': 'new-token-value'})
        self.assertEqual((self.app.data / 'token').read_text(), 'old-token-value')

    def test_token_validation_never_passes_token_in_arguments(self):
        with patch.object(self.app, 'gh', return_value='{"login":"octocat"}') as gh, patch.object(self.app, 'configure_git'):
            result = self.app.perform('auth/token', {'token': 'test-secret-token'})
        self.assertEqual(gh.call_args.args, ('api', 'user'))
        self.assertEqual(gh.call_args.kwargs['env']['GH_TOKEN'], 'test-secret-token')
        self.assertNotIn('test-secret-token', json.dumps(result))
        self.assertNotIn('test-secret-token', json.dumps(self.app.status()))
        self.assertEqual((self.app.data / 'token').read_text(), 'test-secret-token')
        self.app.perform('auth/logout', {})
        self.assertFalse((self.app.data / 'token').exists())

    def test_shell_is_never_used_and_errors_are_redacted(self):
        completed = plugin.subprocess.CompletedProcess([], 1, 'secret-token', 'secret-token')
        with patch.object(plugin.subprocess, 'run', return_value=completed) as run:
            with self.assertRaises(plugin.OperationError) as error:
                self.app.gh('api', 'user')
        self.assertNotIn('secret-token', str(error.exception))
        self.assertNotIn('shell', run.call_args.kwargs)
        self.assertIsInstance(run.call_args.args[0], list)

    def test_existing_ssh_key_is_not_overwritten(self):
        plugin.save_private(self.app.data / 'ssh/id_ed25519', 'private-key')
        with self.assertRaises(plugin.OperationError):
            self.app.perform('ssh/generate', {})
        self.assertEqual((self.app.data / 'ssh/id_ed25519').read_text(), 'private-key')
        self.assertNotIn('private-key', json.dumps(self.app.status()))

    def test_clone_uses_fixed_github_host_and_destination(self):
        with patch.object(self.app, 'git') as git, patch.object(self.app, 'configure_git'):
            result = self.app.perform('repo/clone', {'repo': 'owner/project', 'protocol': 'ssh'})
        self.assertEqual(git.call_args.args[:3], ('clone', '--', 'git@github.com:owner/project.git'))
        self.assertTrue(Path(result['path']).is_relative_to(self.app.data))

    def test_identity_only_changes_plugin_scoped_git_configuration(self):
        with patch.object(self.app, 'git') as git:
            self.app.perform('git/identity', {'name': 'Test', 'email': 'test@example.com'})
        self.assertEqual(git.call_count, 2)
        self.assertEqual(self.app.environment()['GIT_CONFIG_GLOBAL'], str(self.app.data / 'gitconfig'))

    def test_http_gate_nonce_and_no_secret_reads(self):
        key = b'k' * 32
        protocol = plugin.ServiceProtocol(key)
        server = ThreadingHTTPServer(('127.0.0.1', 0), plugin.handler(self.app, protocol))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        connection = http.client.HTTPConnection(*server.server_address, timeout=3)
        try:
            body = '{}'
            def signed(method, path, text=''):
                timestamp, nonce = str(int(time.time())), secrets.token_hex(16)
                digest = hashlib.sha256(text.encode()).hexdigest()
                message = f'request\n{timestamp}\n{nonce}\n{method}\n{path}\n{digest}'
                mac = hmac.new(key, message.encode(), hashlib.sha256).hexdigest()
                return {'X-Taixu-Service-Authorization': f'{timestamp}:{nonce}:{mac}', 'X-Taixu-Plugin-Write': '1'}
            with patch.object(self.app, 'submit', return_value={'job': 'a' * 24}) as submit:
                for headers in ({}, {'X-Taixu-Plugin-Write': '1'}, {'X-Taixu-Plugin-Write': '1', 'Origin': 'https://evil.example'}):
                    connection.request('POST', '/api/auth/logout', body, headers)
                    response = connection.getresponse()
                    self.assertEqual(response.status, 403)
                    response.read()
                self.assertFalse(submit.called)
                headers = signed('POST', '/api/auth/logout', body)
                connection.request('POST', '/api/auth/logout', body, headers)
                response = connection.getresponse()
                self.assertEqual(json.loads(response.read())['job'], 'a' * 24)
                submit.assert_called_once_with('auth/logout', {})
                connection.request('POST', '/api/auth/logout', body, headers)
                response = connection.getresponse()
                self.assertEqual(response.status, 403)
                response.read()
                self.assertEqual(submit.call_count, 1)
                connection.request('GET', '/app.js')
                response = connection.getresponse()
                self.assertEqual(response.status, 403)
                self.assertNotIn(key, response.read())
            plugin.save_private(self.app.data / 'token', 'never-return-this')
            for path in ('/token', '/../token', '/api/../token'):
                connection.request('GET', path, headers=signed('GET', path))
                response = connection.getresponse()
                self.assertEqual(response.status, 404)
                self.assertNotIn(b'never-return-this', response.read())
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            thread.join()

    def test_completed_jobs_survive_reopening_without_persisting_form_secrets(self):
        with patch.object(self.app, 'perform', return_value={'message': 'saved'}):
            identifier = self.app.submit('auth/token', {'token': 'SECRET_NOT_IN_JOB_RECORD'})['job']
            self.app.pool.shutdown(wait=True)
        stored = (self.app.data / 'last-job.json').read_text()
        self.assertNotIn('SECRET_NOT_IN_JOB_RECORD', stored)
        reopened = plugin.App(self.app.data, self.app.tool)
        try:
            self.assertEqual(reopened.jobs[identifier]['state'], 'done')
        finally:
            reopened.pool.shutdown()

    def test_interrupted_jobs_are_marked_unknown_and_never_replayed(self):
        identifier = 'a' * 24
        plugin.save_private(self.app.data / 'last-job.json', json.dumps({identifier: {'state': 'running'}}))
        cli = plugin.App(self.app.data, self.app.tool, recover_jobs=False)
        try:
            self.assertEqual(json.loads((self.app.data / 'last-job.json').read_text())[identifier]['state'], 'running')
        finally:
            cli.pool.shutdown()
        reopened = plugin.App(self.app.data, self.app.tool)
        try:
            self.assertEqual(reopened.jobs[identifier]['state'], 'unknown')
            self.assertIn('勿直接重复提交', reopened.jobs[identifier]['error'])
        finally:
            reopened.pool.shutdown()

    def test_protocol_binds_method_path_body_time_and_startup_key(self):
        protocol = plugin.ServiceProtocol.from_stdin(io.BytesIO(b'{"protocol":"taixu-service-v1","key":"' + b'6b' * 32 + b'"}\n'))
        timestamp, nonce, body = str(int(time.time())), 'a' * 32, b'{}'
        message = f'request\n{timestamp}\n{nonce}\nPOST\n/api/auth/token\n{hashlib.sha256(body).hexdigest()}'
        signed = f'{timestamp}:{nonce}:' + hmac.new(b'k' * 32, message.encode(), hashlib.sha256).hexdigest()
        self.assertIsNone(protocol.verify('GET', '/api/auth/token', body, signed))
        self.assertIsNone(protocol.verify('POST', '/api/auth/logout', body, signed))
        self.assertIsNone(protocol.verify('POST', '/api/auth/token', b'{"x":1}', signed))
        self.assertEqual(protocol.verify('POST', '/api/auth/token', body, signed), nonce)
        self.assertIsNone(protocol.verify('POST', '/api/auth/token', body, signed))
        self.assertIsNone(plugin.ServiceProtocol(b'x' * 32).verify('POST', '/api/auth/token', body, signed))
        self.assertIsNone(plugin.ServiceProtocol(b'k' * 32, clock=lambda: int(timestamp) + 32).verify('POST', '/api/auth/token', body, signed))
        for value in (b'', b'{"key":"secret"}\n', b'x' * 300):
            with self.assertRaises((ValueError, TypeError)):
                plugin.ServiceProtocol.from_stdin(io.BytesIO(value))


if __name__ == '__main__':
    unittest.main()

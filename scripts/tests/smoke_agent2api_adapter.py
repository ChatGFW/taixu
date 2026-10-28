#!/usr/bin/env python3
"""Exercise the packaged adapter's native protocol against a stub upstream.

The adapter is built for ARM64; build the same sources for the host architecture first, then run
this script under Linux. Nothing here talks to a real provider: the "upstream" is a local stub that
only records the environment it was started with, so the reset flow can be asserted without any
network traffic or real credentials.
"""
import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
AUTH_HEADER = 'X-Taixu-Service-Authorization'
RESPONSE_HEADER = 'X-Taixu-Service-Response'

STUB = '''#!/usr/bin/env python3
import json, os
from http.server import BaseHTTPRequestHandler, HTTPServer
log = os.environ['STUB_LOG']
data = os.environ['AGENT2API_PROXY_HOME']
open(os.path.join(data, 'accounts.db'), 'a').close()
with open(log, 'a') as handle:
    handle.write(json.dumps({
        'pid': os.getpid(),
        'host': os.environ.get('AGENT2API_HOST'),
        'port': os.environ.get('AGENT2API_PROXY_PORT'),
        'adminUser': os.environ.get('AGENT2API_ADMIN_USER'),
        'adminPassword': os.environ.get('AGENT2API_ADMIN_PASSWORD'),
        'selfUpdate': os.environ.get('AGENT2API_SELF_UPDATE'),
    }) + '\\n')
class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_GET(self):
        bodies = {'/health': b'ok', '/api/panel/status': b'{"data":{"registered":true}}', '/v1/models': b'{"data":[{"id":"stub-model"}]}'}
        body = bodies.get(self.path)
        if body is None:
            self.send_response(404); self.end_headers(); return
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers(); self.wfile.write(body)
HTTPServer((os.environ['AGENT2API_HOST'], int(os.environ['AGENT2API_PROXY_PORT'])), Handler).serve_forever()
'''


def digest(body):
    return hashlib.sha256(body).hexdigest()


def signed(key, method, path, body=b'', nonce=None, timestamp=None):
    timestamp = str(timestamp or int(time.time()))
    nonce = nonce or os.urandom(16).hex()
    message = '\n'.join(['request', timestamp, nonce, method, path, digest(body)]).encode()
    signature = hmac.new(key, message, hashlib.sha256).hexdigest()
    headers = {AUTH_HEADER: f'{timestamp}:{nonce}:{signature}'}
    if method == 'POST':
        headers['X-Taixu-Plugin-Write'] = '1'
        headers['Content-Type'] = 'application/json'
    return headers


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adapter', type=Path, default=ROOT / 'build/agent2api-adapter/agent2api-host-amd64')
    parser.add_argument('--web', type=Path, default=ROOT / 'assets/plugins/agent2api/payload/web')
    args = parser.parse_args()
    def port_free(port):
        # Go and Python servers set SO_REUSEADDR, so a lingering TIME_WAIT socket is not a conflict.
        with socket.socket() as probe:
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                probe.bind(('127.0.0.1', port))
            except OSError:
                return False
            return True

    for busy in (3065, 3066):
        if not port_free(busy):
            raise SystemExit(f'port {busy} must be free for the adapter smoke test')

    temp = Path(tempfile.mkdtemp(prefix='agent2api-adapter-'))
    try:
        # Mirrors the installed layout: lib/ holds both binaries and ../web holds the page assets.
        vendor = temp / 'lib'
        vendor.mkdir(parents=True)
        (temp / 'web').mkdir(parents=True)
        for item in args.web.iterdir():
            shutil.copy2(item, temp / 'web' / item.name)
        stub_log = temp / 'upstream.log'
        (vendor / 'agent2api-server').write_text(STUB)
        (vendor / 'agent2api-server').chmod(0o755)
        shutil.copy2(args.adapter, vendor / 'agent2api-host')
        (vendor / 'agent2api-host').chmod(0o755)
        key = bytes.fromhex('0123456789abcdef' * 4)
        env = {k: v for k, v in os.environ.items()
               if not k.startswith(('AGENT2API_', 'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY'))}
        env.update(HOME=str(temp), AGENT2API_PROXY_HOME=str(temp), STUB_LOG=str(stub_log))
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

        def call(path, method='GET', body=b'', headers=None, signed_request=True):
            request = urllib.request.Request(f'http://127.0.0.1:3065{path}', data=body if method == 'POST' else None,
                                             method=method)
            if signed_request:
                headers = headers or signed(key, method, path, body)
                for name, value in headers.items():
                    request.add_header(name, value)
            try:
                with opener.open(request, timeout=5) as response:
                    return response.status, response.read(), response.headers
            except urllib.error.HTTPError as error:
                return error.code, error.read(), error.headers

        process = subprocess.Popen([str(vendor / 'agent2api-host')], cwd=str(vendor), env=env,
                                   stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        try:
            process.stdin.write(json.dumps({'protocol': 'taixu-service-v1', 'key': key.hex()}).encode() + b'\n')
            process.stdin.flush()
            for _ in range(150):
                if process.poll() is not None:
                    raise RuntimeError(process.stdout.read().decode(errors='replace'))
                try:
                    status, body, headers = call('/taixu/', signed_request=False)
                    if status in (200, 403):
                        break
                except OSError:
                    pass
                time.sleep(0.1)
            else:
                raise TimeoutError('adapter readiness timeout')

            # The document is only served with a valid signature, and the page itself is present.
            status, body, headers = call('/taixu/')
            assert status == 200 and b'Agent2API' in body, (status, body[:200])
            assert headers.get(RESPONSE_HEADER), 'missing signed response header'
            unsigned, _, _ = call('/taixu/', signed_request=False)
            assert unsigned == 403, ('unsigned document served', unsigned)
            replay = signed(key, 'GET', '/taixu/')
            assert call('/taixu/', headers=replay)[0] == 200, 'fresh nonce rejected'
            assert call('/taixu/', headers=replay)[0] == 403, 'replayed nonce accepted'
            # A signature bound to one path cannot be replayed against another.
            assert call('/taixu/api/status', headers=signed(key, 'GET', '/taixu/'))[0] == 403

            status, body, _ = call('/taixu/api/status')
            payload = json.loads(body)
            assert status == 200 and payload['ready'] and payload['panel']['registered'] is True, body[:300]
            status, body, _ = call('/taixu/api/models')
            assert status == 200 and json.loads(body)['data'][0]['id'] == 'stub-model'

            # Non-adapter paths stay transparent for the real WebUI, without leaking native headers.
            status, body, _ = call('/api/panel/status', signed_request=False)
            assert status == 200 and b'registered' in body, (status, body[:200])
            assert call('/taixu/unknown', signed_request=False)[0] == 403

            # Administrator reset: generated credentials, then a restart without them in the env.
            reset_body = json.dumps({'username': 'admin', 'preserveAccounts': True}).encode()
            status, body, _ = call('/taixu/api/admin/reset', 'POST', reset_body)
            job = json.loads(body)['job']
            result = None
            for _ in range(400):
                time.sleep(0.2)
                status, body, _ = call(f'/taixu/api/job/{job}')
                state = json.loads(body)
                if state.get('state') in ('done', 'failed'):
                    result = state
                    break
            assert result and result['state'] == 'done', result
            assert result['username'] == 'admin' and len(result['password']) == 32, result

            runs = [json.loads(line) for line in stub_log.read_text().splitlines()]
            first = runs[0]
            assert first['host'] == '127.0.0.1' and first['port'] == '3066', first
            assert first['adminUser'] is None and first['selfUpdate'] == '0', first
            with_secret = [run for run in runs if run['adminUser']]
            assert with_secret and with_secret[0]['adminUser'] == 'admin', runs
            assert with_secret[0]['adminPassword'] == result['password'], 'generated password never reached upstream'
            last = runs[-1]
            assert last['adminUser'] is None and last['adminPassword'] is None, 'plaintext credentials retained'
            assert last['pid'] != first['pid'], 'upstream was not restarted after reset'
            assert (temp / 'accounts.db').exists(), 'account store not preserved'

            # The busy flag is released after a finished job, so a later reset is accepted again.
            status, body, _ = call('/taixu/api/admin/reset', 'POST', reset_body)
            assert status == 200 and 'job' in json.loads(body), body[:300]
            print(json.dumps({'status': 'ok', 'upstream_runs': len(runs), 'reset_job': job,
                              'no_default_admin_env': first['adminUser'] is None}, ensure_ascii=False))
        finally:
            process.send_signal(signal.SIGTERM)
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
            subprocess.run(['pkill', '-f', str(vendor / 'agent2api-server')], capture_output=True)
    finally:
        shutil.rmtree(temp, ignore_errors=True)


if __name__ == '__main__':
    main()

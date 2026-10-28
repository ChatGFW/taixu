#!/usr/bin/env python3
"""Run the packaged ARM64 server through a supplied QEMU; only local unauthenticated probes."""
import argparse
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--qemu', required=True, type=Path)
    parser.add_argument('--sysroot', required=True, type=Path)
    args = parser.parse_args()
    vendor = ROOT / 'assets/plugins/agent2api/payload/vendor'
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    with tempfile.TemporaryDirectory(prefix='agent2api-smoke-') as temp:
        env = {k: v for k, v in os.environ.items()
               if not k.startswith(('AGENT2API_', 'WORKBUDDY_', 'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY'))}
        env.update(HOME=temp, AGENT2API_PROXY_HOME=temp, AGENT2API_HOST='127.0.0.1',
                   AGENT2API_PROXY_PORT=str(port), AGENT2API_UI_DIR=str(vendor / 'ui'),
                   AGENT2API_SELF_UPDATE='0', AGENT2API_ALLOW_NO_KEY='0')
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

        def get(path, data=None):
            try:
                request = urllib.request.Request(f'http://127.0.0.1:{port}{path}', data=data,
                                                 headers={'Content-Type': 'application/json'})
                with opener.open(request, timeout=3) as response:
                    return response.status, response.read()
            except urllib.error.HTTPError as error:
                return error.code, error.read()

        # Restart the same database to exercise empty-state migration and recovery.
        for run in range(2):
            with open(Path(temp) / f'run-{run}.log', 'wb') as log:
                process = subprocess.Popen([str(args.qemu.resolve()), '-L', str(args.sysroot.resolve()),
                                            str(vendor / 'agent2api-server')], env=env, stdout=log, stderr=log)
                try:
                    for _ in range(100):
                        if process.poll() is not None:
                            raise RuntimeError((Path(temp) / f'run-{run}.log').read_text())
                        try:
                            status, body = get('/health')
                            if status == 200:
                                break
                        except (OSError, urllib.error.URLError):
                            pass
                        time.sleep(0.2)
                    else:
                        raise TimeoutError('ARM64 gateway readiness timeout')
                    assert status == 200, (status, body)
                    print('health:', body.decode()[:300])
                    status, page = get('/')
                    assert status == 200 and b'<html' in page.lower(), (status, page[:200])
                    status, body = get('/v1/models')
                    assert status == 200 and json.loads(body)['data'] == [], (status, body)
                    # Empty request cannot select an upstream model, even if authentication regresses.
                    status, body = get('/v1/chat/completions', b'{}')
                    assert status in (401, 403, 503), (status, body)
                    print(json.dumps({'run': run + 1, 'ui': 'OK', 'unauthenticated_inference_status': status}))
                finally:
                    process.send_signal(signal.SIGTERM)
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=5)
        print('ARM64 startup / health / UI / fail-closed / restart passed; no upstream model requests sent.')


if __name__ == '__main__':
    main()

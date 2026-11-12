"""Run against an isolated HOME with no inherited credentials or real providers."""
import argparse
import base64
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def run(binary: Path, ui: Path, qemu=None, sysroot=None):
    with tempfile.TemporaryDirectory(prefix="taixu-cc-switch-test-") as directory:
        home = Path(directory)
        data = home / "data"
        data.mkdir()
        (data / "web_password").write_text("isolated-test-password")
        port = free_port()
        env = {"PATH": os.defpath, "HOME": str(home), "CC_SWITCH_DATA_DIR": str(data),
               "CC_SWITCH_PORT": str(port), "CC_SWITCH_UI_DIR": str(ui), "RUST_LOG": "warn"}
        auth = "Basic " + base64.b64encode(b"admin:isolated-test-password").decode()
        log = (home / "server.log").open("w+")
        command = ([str(qemu), "-L", str(sysroot)] if qemu else []) + [str(binary)]
        process = subprocess.Popen(command, env=env, stdout=log, stderr=log)
        captured = []
        class MockProvider(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"
            def do_POST(self):
                captured.append(json.loads(self.rfile.read(int(self.headers['Content-Length']))))
                body = json.dumps({"id":"msg_mock", "type":"message", "role":"assistant",
                    "content":[{"type":"text","text":"offline mock reply"}], "model":"claude-test",
                    "stop_reason":"end_turn", "usage":{"input_tokens":3,"output_tokens":1}}).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            def log_message(self, *args):
                pass
        upstream = ThreadingHTTPServer(("127.0.0.1", 0), MockProvider)
        threading.Thread(target=upstream.serve_forever, daemon=True).start()

        def request(path, payload=None, authorized=True, extra=None):
            conn = http.client.HTTPConnection("127.0.0.1", port, timeout=20)
            headers = {"Content-Type": "application/json"}
            if authorized:
                headers["Authorization"] = auth
            headers.update(extra or {})
            conn.request("POST" if payload is not None else "GET", path, None if payload is None else json.dumps(payload), headers)
            response = conn.getresponse()
            result = response.status, response.read()
            conn.close()
            return result

        def invoke(name, args=None):
            status, body = request("/api/invoke/" + name, args or {})
            assert status == 200, (name, status, body.decode()[:500])
            return json.loads(body)["result"]

        try:
            for _ in range(240):
                if process.poll() is not None:
                    log.seek(0)
                    raise AssertionError("Server exited: " + log.read()[-3000:])
                try:
                    if request("/")[0] == 200:
                        break
                except (OSError, http.client.HTTPException):
                    pass
                time.sleep(.25)
            else:
                raise AssertionError("Server startup timed out")
            assert request("/", authorized=False)[0] == 401
            assert request("/", extra={"Host": "attacker.example"})[0] == 403
            assert request("/api/invoke/get_settings", {}, extra={"Origin": "https://attacker.example"})[0] == 403
            assert request("/api/invoke/not_a_command", {})[0] == 404
            assert request("/api/invoke/get_providers", {"app": 123})[0] == 400
            assert b'<div id="root"' in request("/")[1]
            assert invoke("get_init_error") is None
            assert isinstance(invoke("get_settings"), dict)
            assert isinstance(invoke("get_providers", {"app": "claude"}), dict)
            provider = {"id": "web-smoke", "name": "Offline mock", "settingsConfig": {
                "env": {"ANTHROPIC_BASE_URL": f"http://127.0.0.1:{upstream.server_port}", "ANTHROPIC_AUTH_TOKEN": "not-a-real-key"}}}
            invoke("add_provider", {"app": "claude", "provider": provider, "addToLive": False})
            assert "web-smoke" in invoke("get_providers", {"app": "claude"})
            provider["name"] = "Edited offline mock"
            invoke("update_provider", {"app": "claude", "provider": provider})
            assert invoke("get_providers", {"app": "claude"})["web-smoke"]["name"] == provider["name"]
            assert isinstance(invoke("list_sessions"), list)
            assert isinstance(invoke("get_privacy_filter_config"), dict)
            invoke("switch_provider", {"app":"claude", "id":"web-smoke"})
            config = invoke("get_proxy_config")
            config["listen_port"] = free_port()
            invoke("update_proxy_config", {"config":config})
            invoke("set_privacy_filter_config", {"config":{"enabled":True,"filterToolResults":False}})
            invoke("set_proxy_takeover_for_app", {"appType":"claude","enabled":True,"stack":False,"route":"web-smoke"})
            proxy = http.client.HTTPConnection("127.0.0.1", config["listen_port"], timeout=120 if qemu else 20)
            proxy.request("POST", "/v1/messages", json.dumps({"model":"claude-test","max_tokens":16,
                "messages":[{"role":"user","content":"Email me at smoke@example.com"}]}),
                {"Content-Type":"application/json", "x-api-key":"not-a-real-key", "anthropic-version":"2023-06-01"})
            result = proxy.getresponse()
            body = result.read()
            assert result.status == 200, (result.status, body[:500])
            assert b'offline mock reply' in body
            assert captured and "smoke@example.com" not in json.dumps(captured)
            proxy.close()
            status, body = request("/api/invoke/restart_app", {})
            assert status == 400 and "桌面" in body.decode()
            process.terminate()
            process.wait(timeout=12)
            cli = json.loads((home / ".claude/settings.json").read_text())
            assert cli["env"]["ANTHROPIC_BASE_URL"] == f"http://127.0.0.1:{upstream.server_port}", "Shutdown did not restore direct config"
            print("PASS: isolated startup, UI/auth, origin/host rejection, command admission, provider CRUD, sessions, mock proxy roundtrip, privacy redaction, graceful direct-config restore")
        except Exception:
            log.flush()
            log.seek(0)
            print(log.read()[-5000:])
            print(f"Mock provider requests: {len(captured)}")
            raise
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
            log.close()
            upstream.shutdown()
            upstream.server_close()


def run_first_run(binary: Path, ui: Path, qemu=None, sysroot=None):
    """A fresh data directory must be usable without anyone reading a password file."""
    with tempfile.TemporaryDirectory(prefix="taixu-cc-switch-first-run-") as directory:
        home = Path(directory)
        data = home / "data"
        data.mkdir()
        port = free_port()
        env = {"PATH": os.defpath, "HOME": str(home), "CC_SWITCH_DATA_DIR": str(data),
               "CC_SWITCH_PORT": str(port), "CC_SWITCH_UI_DIR": str(ui), "RUST_LOG": "warn"}
        log = (home / "server.log").open("w+")
        command = ([str(qemu), "-L", str(sysroot)] if qemu else []) + [str(binary)]

        def start():
            return subprocess.Popen(command, env=env, stdout=log, stderr=log)

        def call(method, path, payload=None, password=None, extra=None):
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=30)
            headers = dict(extra or {})
            if password is not None:
                headers["Authorization"] = "Basic " + base64.b64encode(f"admin:{password}".encode()).decode()
            body = None
            if payload is not None:
                body = json.dumps(payload)
                headers["Content-Type"] = "application/json"
            connection.request(method, path, body, headers)
            response = connection.getresponse()
            result = response.status, {k.lower(): v for k, v in response.getheaders()}, response.read()
            connection.close()
            return result

        def wait_ready(process):
            for _ in range(240):
                if process.poll() is not None:
                    log.flush(); log.seek(0)
                    raise AssertionError("Server exited: " + log.read()[-3000:])
                try:
                    if call("GET", "/password")[0] == 200:
                        return
                except (OSError, http.client.HTTPException):
                    pass
                time.sleep(.25)
            raise AssertionError("Server startup timed out")

        process = start()
        try:
            wait_ready(process)
            status, headers, body = call("GET", "/password")
            assert status == 200 and "设置密码" in body.decode(), (status, body[:200])
            assert headers.get("cache-control") == "no-store"
            assert call("GET", "/password", extra={"Host": "attacker.example"})[0] == 403
            status, headers, _ = call("GET", "/")
            assert status in (302, 303) and headers.get("location") == "/password", (status, headers)
            assert call("GET", "/api/events")[0] == 403
            assert call("POST", "/api/invoke/get_settings", {})[0] == 403
            assert call("GET", "/api/password")[2] == b'{"configured":false,"authorized":false,"password":null}'
            assert call("GET", "/api/password")[1].get("cache-control") == "no-store"
            assert call("POST", "/api/password", {"password": "short"})[0] == 400
            assert call("POST", "/api/password", {"password": "带中文的密码12345"})[0] == 400
            assert call("POST", "/api/password", {"password": " " * 12})[0] == 400
            status, _, body = call("POST", "/api/password", {"password": "isolated-test-password"})
            assert status == 200 and json.loads(body) == {"ok": True}, (status, body)
            stored = data / "web_password"
            assert stored.read_text() == "isolated-test-password"
            assert stored.stat().st_mode & 0o777 == 0o600, oct(stored.stat().st_mode)
            assert not list(data.glob("*.tmp")), "password write left a temporary file"
            # Once set, every other route closes again and the panel keeps working.
            assert call("GET", "/password")[0] == 200
            assert call("POST", "/api/password", {"password": "isolated-test-password"})[0] == 401
            assert call("GET", "/")[0] == 401
            assert call("GET", "/", password="wrong-password")[0] == 401
            assert b'<div id="root"' in call("GET", "/", password="isolated-test-password")[2]
            assert json.loads(call("GET", "/api/password", password="isolated-test-password")[2])["password"] == "isolated-test-password"
            # Rotation accepts either the authenticated browser or the current password.
            assert call("POST", "/api/password", {"password": "rotated-test-password"})[0] == 401
            assert call("POST", "/api/password", {"password": "rotated-test-password", "current": "wrong"})[0] == 401
            assert call("POST", "/api/password", {"password": "rotated-test-password", "current": "isolated-test-password"})[0] == 200
            assert stored.read_text() == "rotated-test-password"
            assert call("GET", "/", password="isolated-test-password")[0] == 401
            assert call("GET", "/", password="rotated-test-password")[0] == 200
            assert "result" in json.loads(call("POST", "/api/invoke/get_settings", {}, password="rotated-test-password")[2])
            process.terminate()
            process.wait(timeout=12)
            # The choice survives a restart, so nothing is regenerated behind the user.
            process = start()
            wait_ready(process)
            assert call("GET", "/", password="rotated-test-password")[0] == 200
            print("PASS: first-run password setup, pre-setup lockout, rotation, restart persistence")
        except Exception:
            log.flush(); log.seek(0)
            print(log.read()[-5000:])
            raise
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
            log.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("ui", type=Path)
    parser.add_argument("--qemu", type=Path)
    parser.add_argument("--sysroot", type=Path, default=Path("/usr/aarch64-linux-gnu"))
    args = parser.parse_args()
    run(args.binary.resolve(), args.ui.resolve(), args.qemu, args.sysroot)
    run_first_run(args.binary.resolve(), args.ui.resolve(), args.qemu, args.sysroot)

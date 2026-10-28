#!/usr/bin/env python3
"""TaiXu GitHub plugin: allowlisted local operations; no shell or token echo."""
import concurrent.futures
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
sys.path.insert(0, str(Path(__file__).resolve().parent))
from service_protocol import ServiceProtocol, HEADER, RESPONSE_HEADER

DATA = Path('/opt/taixu/data/github')
TOOL = Path('/opt/taixu/tools/github')
REPO = re.compile(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z')
BRANCH = re.compile(r'[A-Za-z0-9][A-Za-z0-9_/-]{0,99}\Z')


class OperationError(Exception):
    pass


def save_private(path, value):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_name(path.name + '.new')
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as stream:
        stream.write(value)
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)


class App:
    def __init__(self, data=DATA, tool=TOOL, recover_jobs=True):
        self.data, self.tool = Path(data), Path(tool)
        self.data.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.jobs = {}
        self.lock = threading.Lock()
        self.pool = concurrent.futures.ThreadPoolExecutor(max_workers=1)
        record = self.data / 'last-job.json'
        if recover_jobs and record.exists():
            try:
                saved = json.loads(record.read_text(encoding='utf-8'))
                if not isinstance(saved, dict) or len(saved) != 1 or not re.fullmatch('[a-f0-9]{24}', next(iter(saved))):
                    raise ValueError()
                self.jobs = saved
                if next(iter(saved.values())).get('state') == 'running':
                    self.jobs[next(iter(saved))] = dict(state='unknown', error='服务已重启，任务执行结果待核对。请先检查本地仓库和 GitHub，勿直接重复提交。')
                    self.persist_jobs()
            except (ValueError, OSError, TypeError, AttributeError):
                raise OperationError('任务记录损坏，请先在终端核对仓库和远端状态') from None

    def persist_jobs(self):
        save_private(self.data / 'last-job.json', json.dumps(self.jobs, ensure_ascii=False))

    def environment(self):
        env = dict(os.environ)
        for key in ('GH_TOKEN', 'GITHUB_TOKEN', 'GH_ENTERPRISE_TOKEN', 'GITHUB_ENTERPRISE_TOKEN',
                    'GH_DEBUG', 'GIT_TRACE', 'GIT_TRACE_CURL', 'GIT_CURL_VERBOSE'):
            env.pop(key, None)
        token_file = self.data / 'token'
        if token_file.exists():
            env['GH_TOKEN'] = token_file.read_text(encoding='utf-8').strip()
        env.update(GH_HOST='github.com', GH_CONFIG_DIR=str(self.data / 'gh'),
                   GH_PROMPT_DISABLED='1', GH_PAGER='cat', GIT_TERMINAL_PROMPT='0',
                   GIT_CONFIG_GLOBAL=str(self.data / 'gitconfig'),
                   GIT_SSH_COMMAND='ssh -i ' + str(self.data / 'ssh/id_ed25519') +
                   ' -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=yes' +
                   ' -o UserKnownHostsFile=' + str(self.data / 'ssh/known_hosts'))
        return env

    def run(self, args, *, cwd=None, env=None, stdin=None, timeout=90):
        try:
            result = subprocess.run(args, input=stdin, text=True, encoding='utf-8',
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                    env=env or self.environment(), cwd=cwd, timeout=timeout,
                                    check=False, stdin=subprocess.DEVNULL if stdin is None else None)
        except (OSError, subprocess.TimeoutExpired):
            raise OperationError('命令不可用或超时，请检查依赖和网络') from None
        if result.returncode:
            # CLI errors may contain tokens, remotes or credential-helper output.
            raise OperationError('操作失败，请检查 Token 权限、网络、仓库和分支状态')
        if len(result.stdout) > 262144:
            raise OperationError('响应过大')
        return result.stdout

    def gh(self, *args, **kwargs):
        return self.run([str(self.tool / 'lib/gh'), *args], **kwargs)

    def repo(self, name, require_exists=True):
        if not isinstance(name, str) or not REPO.fullmatch(name) or any(p in ('.', '..') for p in name.split('/')):
            raise OperationError('仓库格式应为 owner/repo')
        root = (self.data / 'repos').resolve()
        path = root.joinpath(*name.split('/'))
        if not path.resolve().is_relative_to(root):
            raise OperationError('仓库路径越界')
        if require_exists and not (path / '.git').exists():
            raise OperationError('请先克隆此仓库')
        return path

    def git(self, *args, **kwargs):
        return self.run(['git', *args], **kwargs)

    def configure_git(self):
        self.git('config', '--global', 'credential.https://github.com.helper',
                 '!' + str(self.tool / 'bin/gh') + ' auth git-credential')

    def status(self):
        user = None
        account = self.data / 'account.json'
        if (self.data / 'token').exists() and account.exists():
            user = json.loads(account.read_text())
        public = self.data / 'ssh/id_ed25519.pub'
        return dict(user=user, tokenSaved=(self.data / 'token').exists(),
                    publicKey=public.read_text() if public.exists() else '',
                    dependencies={name: shutil.which(name) is not None for name in ('git', 'ssh', 'ssh-keygen')},
                    repositoryRoot=str(self.data / 'repos'))

    def perform(self, action, body):
        if action == 'auth/token':
            token = body.get('token', '')
            if not isinstance(token, str) or not 10 <= len(token) <= 512 or any(c.isspace() for c in token):
                raise OperationError('Token 格式无效')
            env = self.environment()
            env['GH_TOKEN'] = token
            info = json.loads(self.gh('api', 'user', env=env))
            self.configure_git()
            save_private(self.data / 'token', token)
            save_private(self.data / 'account.json', json.dumps({key: info.get(key) for key in ('login', 'name', 'html_url')}))
            return dict(message='已连接 GitHub', login=info['login'])
        if action == 'auth/logout':
            (self.data / 'token').unlink(missing_ok=True)
            (self.data / 'account.json').unlink(missing_ok=True)
            return dict(message='已删除本地 Token；如需吊销，请前往 GitHub Token 设置')
        if action == 'git/identity':
            name, email = body.get('name', ''), body.get('email', '')
            if not all(isinstance(v, str) and 0 < len(v) <= 150 and not any(ord(c) < 32 for c in v) for v in (name, email)):
                raise OperationError('请输入有效的 Git 用户名和邮箱')
            self.git('config', '--global', 'user.name', name)
            self.git('config', '--global', 'user.email', email)
            return dict(message='已保存插件专用 Git 提交身份')
        if action == 'ssh/generate':
            directory = self.data / 'ssh'
            directory.mkdir(mode=0o700, exist_ok=True)
            key = directory / 'id_ed25519'
            if key.exists() or key.with_suffix('.pub').exists():
                raise OperationError('密钥已存在，不会覆盖')
            self.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-C', 'taixu-github', '-f', str(key)])
            os.chmod(key, 0o600)
            return dict(message='已生成无口令 Ed25519 密钥。请把公钥添加到 GitHub，并同步主机公钥。',
                        publicKey=key.with_suffix('.pub').read_text())
        if action == 'ssh/hostkeys':
            directory = self.data / 'ssh'
            # Official GitHub host keys fetched over authenticated HTTPS, never ssh-keyscan trust-on-first-use.
            meta = json.loads(self.gh('api', 'meta'))
            keys = meta.get('ssh_keys', [])
            if not keys or any(not re.fullmatch(r'ssh-(?:ed25519|rsa) [A-Za-z0-9+/=]+|ecdsa-sha2-nistp256 [A-Za-z0-9+/=]+', k) for k in keys):
                raise OperationError('密钥已生成，但 GitHub 主机公钥获取失败；请在终端核验后配置 known_hosts')
            save_private(directory / 'known_hosts', ''.join('github.com ' + k + '\n' for k in keys))
            return dict(message='已通过 GitHub HTTPS API 同步 SSH 主机公钥')
        if action == 'ssh/upload':
            public = self.data / 'ssh/id_ed25519.pub'
            if not public.exists():
                raise OperationError('请先生成密钥')
            self.gh('ssh-key', 'add', str(public), '--title', 'TaiXu Android')
            return dict(message='公钥已添加到 GitHub')
        if action == 'repos/list':
            return dict(repos=json.loads(self.gh('repo', 'list', '--limit', '30', '--json', 'nameWithOwner,isPrivate,description')))
        name = body.get('repo', '')
        path = self.repo(name, require_exists=action not in ('repo/clone', 'pr/list'))
        if action == 'pr/list':
            return dict(prs=json.loads(self.gh('pr', 'list', '--repo', name, '--limit', '20', '--json', 'number,title,url,state')))
        if action == 'repo/clone':
            if path.exists():
                raise OperationError('目标目录已存在，不会覆盖')
            path.parent.mkdir(parents=True, exist_ok=True)
            protocol = body.get('protocol', 'https')
            if protocol not in ('https', 'ssh'):
                raise OperationError('不支持的传输协议')
            url = 'https://github.com/' + name + '.git' if protocol == 'https' else 'git@github.com:' + name + '.git'
            self.configure_git()
            self.git('clone', '--', url, str(path))
            return dict(message='仓库已克隆', path=str(path))
        if action == 'repo/pull':
            self.git('pull', '--ff-only', cwd=path)
            return dict(message='已拉取远端更新')
        if action == 'repo/commit-push':
            branch, message = body.get('branch', ''), body.get('message', '')
            if not isinstance(branch, str) or not BRANCH.fullmatch(branch) or '..' in branch or branch.endswith('/'):
                raise OperationError('分支名称无效')
            if not isinstance(message, str) or not message.strip() or len(message) > 200:
                raise OperationError('请输入 1–200 字提交说明')
            self.git('switch', '-c', branch, cwd=path)
            self.git('add', '-A', cwd=path)
            self.git('commit', '-m', message, cwd=path)
            self.git('push', '-u', 'origin', branch, cwd=path)
            return dict(message='已创建分支、提交全部修改并推送；失败时本地提交会保留')
        if action == 'pr/create':
            title, text = body.get('title', ''), body.get('body', '')
            if not isinstance(title, str) or not title.strip() or len(title) > 150 or not isinstance(text, str):
                raise OperationError('PR 标题无效')
            url = self.gh('pr', 'create', '--repo', name, '--title', title, '--body', text, cwd=path).strip()
            return dict(message='PR 已创建', url=url if url.startswith('https://github.com/') else '')
        raise OperationError('操作不支持')

    def submit(self, action, body):
        allowed = {'auth/token', 'auth/logout', 'git/identity', 'ssh/generate', 'ssh/upload', 'ssh/hostkeys', 'repos/list',
                   'repo/clone', 'repo/pull', 'repo/commit-push', 'pr/create', 'pr/list'}
        if action not in allowed:
            raise OperationError('操作不支持')
        with self.lock:
            if any(job['state'] == 'running' for job in self.jobs.values()):
                raise OperationError('已有操作在执行')
            self.jobs.clear()
            identifier = secrets.token_hex(12)
            self.jobs[identifier] = dict(state='running')
            self.persist_jobs()
        def work():
            try:
                result = dict(state='done', result=self.perform(action, body))
            except OperationError as error:
                result = dict(state='failed', error=str(error))
            except Exception:
                result = dict(state='failed', error='操作失败，请检查依赖、网络和配置')
            finally:
                body.clear()
            with self.lock:
                self.jobs[identifier] = result
                self.persist_jobs()
        self.pool.submit(work)
        return dict(job=identifier)


def handler(app, protocol):
    class Handler(BaseHTTPRequestHandler):
        auth_nonce = None

        def setup(self):
            super().setup()
            self.connection.settimeout(3)

        def authenticate(self, body=b''):
            headers = self.headers.get_all(HEADER, [])
            self.auth_nonce = protocol.verify(self.command, self.path, body, headers[0]) if len(headers) == 1 else None
            if self.auth_nonce is None:
                self.reply(dict(error='unauthorized'), status=403)
                return False
            return True

        def log_message(self, *args):
            pass  # No URL, body, token or subprocess output in HTTP logs.

        def reply(self, value, kind='application/json; charset=utf-8', status=200):
            content = value if isinstance(value, bytes) else json.dumps(value, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header('Content-Type', kind)
            self.send_header('Content-Length', str(len(content)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            if self.auth_nonce is not None:
                self.send_header(RESPONSE_HEADER, protocol.response(self.auth_nonce, status, content))
            self.end_headers()
            self.wfile.write(content)

        def do_GET(self):
            if not self.authenticate():
                return
            if self.path in ('/', '/index.html', '/app.js', '/app.css'):
                name = 'index.html' if self.path == '/' else self.path[1:]
                content = (app.tool / 'web' / name).read_bytes()
                kind = {'index.html': 'text/html', 'app.js': 'application/javascript', 'app.css': 'text/css'}[name]
                return self.reply(content, kind + '; charset=utf-8')
            if self.path == '/api/status':
                return self.reply(app.status())
            if self.path == '/api/current-job':
                with app.lock:
                    value = dict(job=next(iter(app.jobs), None))
                return self.reply(value)
            if re.fullmatch('/api/job/[a-f0-9]{24}', self.path):
                with app.lock:
                    value = app.jobs.get(self.path.rsplit('/', 1)[1], dict(state='failed', error='任务不存在'))
                return self.reply(value)
            self.reply(dict(error='not_found'), status=404)

        def do_POST(self):
            # Native service-write adds this header. A website form cannot synthesize it; no CORS.
            if self.headers.get('X-Taixu-Plugin-Write') != '1' or self.headers.get('Origin') or self.headers.get('Transfer-Encoding'):
                return self.reply(dict(error='forbidden'), status=403)
            try:
                if len(self.headers.get_all('Content-Length', [])) != 1:
                    raise ValueError()
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 < length <= 4096:
                    raise ValueError()
                raw = self.rfile.read(length)
                if len(raw) != length or not self.authenticate(raw):
                    return
                body = json.loads(raw)
                if not isinstance(body, dict):
                    raise ValueError()
                if not self.path.startswith('/api/'):
                    raise ValueError()
                result = app.submit(self.path[5:], body)
            except (ValueError, OperationError):
                return self.reply(dict(error='请求无效或另一个任务尚未完成'))
            self.reply(result)
    return Handler


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else 'serve'
    if command in ('gh', 'git'):
        app = App(recover_jobs=False)
        executable = str(TOOL / 'lib/gh') if command == 'gh' else shutil.which('git')
        if not executable:
            raise SystemExit('请先安装 git')
        os.execve(executable, [executable, *sys.argv[2:]], app.environment())
    if command != 'serve':
        raise SystemExit('用法：gh-ui serve | gh ... | gh-git ...')
    if sys.stdin.isatty():
        raise SystemExit('请从太墟工具中心启动认证服务')
    try:
        protocol = ServiceProtocol.from_stdin(sys.stdin.buffer)
    except (ValueError, TypeError):
        raise SystemExit('认证服务缺少宿主启动握手') from None
    app = App()
    server = ThreadingHTTPServer(('127.0.0.1', 18765), handler(app, protocol))
    server.daemon_threads = True
    print('GitHub 插件服务已启动。请打开 GitHub Web 插件页面。', flush=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()
        app.pool.shutdown(wait=False, cancel_futures=True)


if __name__ == '__main__':
    main()

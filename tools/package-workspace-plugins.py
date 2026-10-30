#!/usr/bin/env python3
"""Reproducible Lazygit/Local-RAG v2 packages, without accounts or model calls."""
import argparse
import hashlib
import json
from pathlib import Path
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = '0.66.2'
ARCHIVE = f'lazygit_{VERSION}_linux_arm64.tar.gz'
SHA256 = '79a3161eeb642f0473e23db6341f7baabd4a5933e921a9c1167c5d5c765c41a4'
URL = f'https://github.com/jesseduffield/lazygit/releases/download/v{VERSION}/{ARCHIVE}'


def vendor_lazygit(archive, offline):
    if not archive.exists():
        if offline:
            raise SystemExit('离线模式需要 --archive 指定已下载的官方 ARM64 归档')
        archive.parent.mkdir(parents=True, exist_ok=True)
        temporary = archive.with_suffix('.download')
        try:
            with urllib.request.urlopen(URL, timeout=60) as response, temporary.open('wb') as output:
                while block := response.read(1024 * 1024):
                    output.write(block)
            temporary.replace(archive)
        finally:
            temporary.unlink(missing_ok=True)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
        raise SystemExit('Lazygit 上游归档 SHA-256 不符')
    vendor = ROOT / 'assets/plugins/lazygit/payload/vendor'
    vendor.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive, 'r:gz') as source:
        for name in ('lazygit', 'LICENSE'):
            member = source.getmember(name)
            if not member.isfile() or member.size > 64 * 1024 * 1024:
                raise SystemExit('上游归档条目类型或大小不符')
            content = source.extractfile(member).read()
            if name == 'lazygit' and (content[:5] != b'\x7fELF\x02' or content[18:20] != b'\xb7\x00'):
                raise SystemExit('上游程序不是 ELF64 AArch64')
            (vendor / name).write_bytes(content)


def pack(name, output):
    project = ROOT / 'assets/plugins' / name
    manifest = json.loads((project / 'manifest.json').read_text(encoding='utf-8'))
    payload = project / 'payload'
    files = {}
    for path in sorted(payload.rglob('*')):
        if '__pycache__' in path.parts or path.suffix == '.pyc':
            continue
        if path.is_symlink():
            raise ValueError('payload 不允许符号链接')
        if path.is_file():
            files[path.relative_to(payload).as_posix()] = path.read_bytes()
    manifest['plugin']['integrity'] = {k: hashlib.sha256(v).hexdigest() for k, v in files.items()}
    text = json.dumps(manifest, ensure_ascii=False, indent=2) + '\n'
    (project / 'manifest.json').write_text(text, encoding='utf-8', newline='\n')
    target = output / f'{name}-{manifest["version"]}.txplugin'
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, 'w') as archive:
        for entry, content in sorted({'manifest.json': text.encode(), **{'payload/' + k: v for k, v in files.items()}}.items()):
            info = zipfile.ZipInfo(entry, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, content)
    print(f'{target} ({target.stat().st_size:,} bytes)')
    return target


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--only', choices=['lazygit', 'local-rag', 'all'], default='all')
    parser.add_argument('--archive', type=Path, default=ROOT / 'build/lazygit-download' / ARCHIVE)
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--output', type=Path, default=ROOT / 'dist/plugins')
    args = parser.parse_args()
    if args.only in ('all', 'lazygit'):
        vendor_lazygit(args.archive, args.offline)
        pack('lazygit', args.output)
    if args.only in ('all', 'local-rag'):
        pack('local-rag', args.output)


if __name__ == '__main__':
    main()

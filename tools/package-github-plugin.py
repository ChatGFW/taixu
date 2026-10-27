#!/usr/bin/env python3
"""Build both plugins from a pinned upstream release; never accesses GitHub credentials."""
import argparse
import hashlib
import json
from pathlib import Path
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = '2.103.3'
SHA256 = 'ee32df2323c8847f5fac6cb6b4b69ca75828e9329a8f22f677021f7b3306ce63'
ARCHIVE = f'gh_{VERSION}_linux_arm64.tar.gz'
URL = f'https://github.com/cli/cli/releases/download/v{VERSION}/{ARCHIVE}'


def pack(project, output):
    manifest = json.loads((project / 'manifest.json').read_text(encoding='utf-8'))
    payload = project / 'payload'
    payload.mkdir(parents=True, exist_ok=True)
    files = {p.relative_to(payload).as_posix(): p.read_bytes() for p in sorted(payload.rglob('*')) if p.is_file() and '__pycache__' not in p.parts} if payload.exists() else {}
    manifest['plugin']['integrity'] = {name: hashlib.sha256(content).hexdigest() for name, content in files.items()}
    serialized = json.dumps(manifest, ensure_ascii=False, indent=2) + '\n'
    (project / 'manifest.json').write_text(serialized, encoding='utf-8', newline='\n')
    entries = {'manifest.json': serialized.encode(), **{'payload/' + k: v for k, v in files.items()}}
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, content in sorted(entries.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, content)
    print(f'{output} ({output.stat().st_size:,} bytes)')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--archive', type=Path, default=ROOT / 'build/gh-download' / ARCHIVE)
    parser.add_argument('--output', type=Path, default=ROOT / 'dist/plugins')
    args = parser.parse_args()
    if not args.archive.exists():
        args.archive.parent.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(URL, args.archive)
    if hashlib.sha256(args.archive.read_bytes()).hexdigest() != SHA256:
        raise SystemExit('Official gh archive SHA-256 mismatch')
    vendor = ROOT / 'assets/plugins/github/payload/vendor'
    vendor.mkdir(parents=True, exist_ok=True)
    with tarfile.open(args.archive, 'r:gz') as archive:
        for source, target in ((f'gh_{VERSION}_linux_arm64/bin/gh', 'gh'), (f'gh_{VERSION}_linux_arm64/LICENSE', 'LICENSE')):
            member = archive.getmember(source)
            if not member.isfile():
                raise SystemExit('Unexpected archive member')
            (vendor / target).write_bytes(archive.extractfile(member).read())
    binary = (vendor / 'gh').read_bytes()
    if binary[:4] != b'\x7fELF' or binary[18:20] != b'\xb7\x00':
        raise SystemExit('Not Linux ARM64 ELF')
    for name in ('github', 'github-web'):
        project = ROOT / 'assets/plugins' / name
        version = json.loads((project / 'manifest.json').read_text(encoding='utf-8'))['version']
        pack(project, args.output / f'{name}-{version}.txplugin')


if __name__ == '__main__':
    main()

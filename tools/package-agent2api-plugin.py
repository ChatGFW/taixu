#!/usr/bin/env python3
"""Package pinned official headless ARM64 release. No credentials or model calls."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = '3.0.8'
PACKAGE_VERSION = '3.0.9'
ARCHIVE = 'agent2api-server-linux-arm64.tar.gz'
SHA256 = 'baeaed43636cfa94a597e00510ff3ebc9acec3f7871cdd766d8863a7ae70051f'
LICENSE_SHA256 = 'dd5d74bc5ada873b6c2bdb93718dbf38d9db3934244c456a19acbc7122018e14'
URL = f'https://github.com/aimod-cc/Agent2API/releases/download/v{VERSION}/{ARCHIVE}'
LICENSE_URL = f'https://raw.githubusercontent.com/aimod-cc/Agent2API/v{VERSION}/LICENSE'


def fetch(path, url, expected):
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(url, timeout=120) as response:
            data = response.read(64 * 1024 * 1024 + 1)
        if len(data) > 64 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f'Upstream checksum mismatch: {path.name}')
        path.write_bytes(data)
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise ValueError(f'Upstream checksum mismatch: {path.name}')


def vendor_release(archive_path, license_path, target):
    # Validate everything before writing; never extract archive-controlled links or paths.
    files = {}
    with tarfile.open(archive_path, 'r:gz') as archive:
        for member in archive:
            name = member.name
            path = PurePosixPath(name)
            if path.is_absolute() or '..' in path.parts or '\\' in name:
                raise ValueError('Unsafe upstream archive path')
            if member.isdir():
                continue
            if not member.isfile() or not (name == 'agent2api-server' or name.startswith('ui/')):
                raise ValueError(f'Unexpected upstream member: {name}')
            if name in files or member.size > 64 * 1024 * 1024 or len(files) >= 2000:
                raise ValueError('Upstream archive exceeds limits or has duplicates')
            files[name] = archive.extractfile(member).read()
            if sum(map(len, files.values())) > 128 * 1024 * 1024:
                raise ValueError('Upstream archive exceeds expanded size limit')
    binary = files.get('agent2api-server', b'')
    if binary[:6] != b'\x7fELF\x02\x01' or binary[18:20] != b'\xb7\x00':
        raise ValueError('Expected Linux ARM64 ELF64')
    if not files.get('ui/index.html'):
        raise ValueError('Upstream UI missing')
    files['LICENSE'] = license_path.read_bytes()
    # Refuse stale vendor files: changed upstream releases use a fresh build directory.
    existing = {p.relative_to(target).as_posix() for p in target.rglob('*') if p.is_file()} if target.exists() else set()
    if existing - files.keys() - {'agent2api-host', 'GO-LICENSE'}:
        raise ValueError('Stale vendor files; choose a fresh project directory')
    for name, data in files.items():
        destination = target / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)


def pack(project, output):
    manifest = json.loads((project / 'manifest.json').read_text(encoding='utf-8'))
    files = {p.relative_to(project / 'payload').as_posix(): p.read_bytes()
             for p in sorted((project / 'payload').rglob('*')) if p.is_file()}
    manifest['plugin']['integrity'] = {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}
    content = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode()
    (project / 'manifest.json').write_bytes(content)
    entries = {'manifest.json': content, **{'payload/' + name: data for name, data in files.items()}}
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, default=ROOT / 'build/agent2api-download' / ARCHIVE)
    parser.add_argument('--license', type=Path, default=ROOT / 'build/agent2api-download/LICENSE')
    parser.add_argument('--output', type=Path, default=ROOT / f'dist/plugins/agent2api-{PACKAGE_VERSION}.txplugin')
    parser.add_argument('--adapter', type=Path, default=ROOT / 'build/agent2api-adapter/agent2api-host')
    parser.add_argument('--go-license', type=Path, default=ROOT / 'build/agent2api-adapter/GO-LICENSE')
    args = parser.parse_args()
    fetch(args.archive, URL, SHA256)
    fetch(args.license, LICENSE_URL, LICENSE_SHA256)
    project = ROOT / 'assets/plugins/agent2api'
    vendor_release(args.archive, args.license, project / 'payload/vendor')
    adapter = args.adapter.read_bytes()
    if adapter[:6] != b'\x7fELF\x02\x01' or adapter[18:20] != b'\xb7\x00':
        raise ValueError('Expected ARM64 adapter ELF64; build adapter/ first')
    (project / 'payload/vendor/agent2api-host').write_bytes(adapter)
    (project / 'payload/vendor/GO-LICENSE').write_bytes(args.go_license.read_bytes())
    output = pack(project, args.output)
    print(f'{output} ({output.stat().st_size:,} bytes), SHA256={hashlib.sha256(output.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    main()

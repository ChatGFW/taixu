#!/usr/bin/env python3
"""Reproducible workspace PROCESS packages, without accounts or model calls."""
import argparse
import hashlib
import json
from pathlib import Path
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]

LAZYGIT_VERSION = '0.66.2'
LAZYGIT_ARCHIVE = f'lazygit_{LAZYGIT_VERSION}_linux_arm64.tar.gz'
LAZYGIT_SHA256 = '79a3161eeb642f0473e23db6341f7baabd4a5933e921a9c1167c5d5c765c41a4'
LAZYGIT_URL = (
    f'https://github.com/jesseduffield/lazygit/releases/download/'
    f'v{LAZYGIT_VERSION}/{LAZYGIT_ARCHIVE}'
)

YAZI_VERSION = '26.9.1'
YAZI_ARCHIVE = 'yazi-aarch64-unknown-linux-musl.zip'
YAZI_SHA256 = 'dd569daecaae914185f295634109295ccd25c1b42b02eb89a74f651970024f2e'
YAZI_URL = (
    f'https://github.com/sxyazi/yazi/releases/download/'
    f'v{YAZI_VERSION}/{YAZI_ARCHIVE}'
)
YAZI_PREFIX = 'yazi-aarch64-unknown-linux-musl'

FX_VERSION = '40.0.1'
FX_ARCHIVE = 'fx_linux_arm64'
FX_SHA256 = '90377fbdbe3a01f791032a289986867ff1f8fe20ce94b45f20ecb399f7b69411'
FX_URL = f'https://github.com/antonmedv/fx/releases/download/{FX_VERSION}/{FX_ARCHIVE}'
FX_LICENSE_URL = f'https://raw.githubusercontent.com/antonmedv/fx/{FX_VERSION}/LICENSE'

BTOP_VERSION = '1.4.8'
BTOP_ARCHIVE = 'btop-aarch64-unknown-linux-musl.tar.gz'
BTOP_SHA256 = '95328e26ab08f42033af26628e2d169206a5a420f35669ce17459ef85114c14c'
BTOP_URL = (
    f'https://github.com/aristocratos/btop/releases/download/'
    f'v{BTOP_VERSION}/{BTOP_ARCHIVE}'
)


def download(url, archive, offline):
    if archive.exists():
        return
    if offline:
        raise SystemExit(f'离线模式需要已下载归档：{archive}')
    archive.parent.mkdir(parents=True, exist_ok=True)
    temporary = archive.with_suffix(archive.suffix + '.download')
    try:
        with urllib.request.urlopen(url, timeout=120) as response, temporary.open('wb') as output:
            while block := response.read(1024 * 1024):
                output.write(block)
        temporary.replace(archive)
    finally:
        temporary.unlink(missing_ok=True)


def assert_sha256(archive, expected):
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    if digest != expected:
        raise SystemExit(f'{archive.name} 上游归档 SHA-256 不符：{digest}')


def assert_elf_aarch64(content, label):
    if content[:5] != b'\x7fELF\x02' or content[18:20] != b'\xb7\x00':
        raise SystemExit(f'{label} 不是 ELF64 AArch64')


def vendor_lazygit(archive, offline):
    download(LAZYGIT_URL, archive, offline)
    assert_sha256(archive, LAZYGIT_SHA256)
    vendor = ROOT / 'assets/plugins/lazygit/payload/vendor'
    vendor.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive, 'r:gz') as source:
        for name in ('lazygit', 'LICENSE'):
            member = source.getmember(name)
            if not member.isfile() or member.size > 64 * 1024 * 1024:
                raise SystemExit('Lazygit 上游归档条目类型或大小不符')
            content = source.extractfile(member).read()
            if name == 'lazygit':
                assert_elf_aarch64(content, 'lazygit')
            (vendor / name).write_bytes(content)


def vendor_yazi(archive, offline):
    download(YAZI_URL, archive, offline)
    assert_sha256(archive, YAZI_SHA256)
    vendor = ROOT / 'assets/plugins/yazi/payload/vendor'
    vendor.mkdir(parents=True, exist_ok=True)
    wanted = {
        f'{YAZI_PREFIX}/yazi': 'yazi',
        f'{YAZI_PREFIX}/ya': 'ya',
        f'{YAZI_PREFIX}/LICENSE': 'LICENSE',
    }
    with zipfile.ZipFile(archive) as source:
        names = set(source.namelist())
        missing = sorted(set(wanted) - names)
        if missing:
            raise SystemExit(f'Yazi 上游归档缺少条目：{missing}')
        for entry, output_name in wanted.items():
            info = source.getinfo(entry)
            if info.is_dir() or info.file_size > 64 * 1024 * 1024:
                raise SystemExit(f'Yazi 上游归档条目类型或大小不符：{entry}')
            content = source.read(entry)
            if output_name in ('yazi', 'ya'):
                assert_elf_aarch64(content, output_name)
            (vendor / output_name).write_bytes(content)


def vendor_term_devtools(fx_archive, btop_archive, offline):
    download(FX_URL, fx_archive, offline)
    assert_sha256(fx_archive, FX_SHA256)
    fx_bytes = fx_archive.read_bytes()
    assert_elf_aarch64(fx_bytes, 'fx')
    license_path = fx_archive.parent / 'fx-LICENSE'
    download(FX_LICENSE_URL, license_path, offline)
    download(BTOP_URL, btop_archive, offline)
    assert_sha256(btop_archive, BTOP_SHA256)
    vendor = ROOT / 'assets/plugins/term-devtools/payload/vendor'
    vendor.mkdir(parents=True, exist_ok=True)
    (vendor / 'fx').write_bytes(fx_bytes)
    (vendor / 'LICENSE-fx').write_bytes(license_path.read_bytes())
    with tarfile.open(btop_archive, 'r:gz') as source:
        binary = source.getmember('./btop/bin/btop')
        license_member = source.getmember('./btop/LICENSE')
        for member, label in ((binary, 'btop'), (license_member, 'LICENSE-btop')):
            if not member.isfile() or member.size > 64 * 1024 * 1024:
                raise SystemExit(f'btop 上游归档条目类型或大小不符：{member.name}')
            content = source.extractfile(member).read()
            if label == 'btop':
                assert_elf_aarch64(content, 'btop')
            (vendor / label).write_bytes(content)


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
    manifest['plugin']['integrity'] = {
        key: hashlib.sha256(value).hexdigest() for key, value in files.items()
    }
    text = json.dumps(manifest, ensure_ascii=False, indent=2) + '\n'
    (project / 'manifest.json').write_text(text, encoding='utf-8', newline='\n')
    target = output / f'{name}-{manifest["version"]}.txplugin'
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, 'w') as archive:
        entries = {
            'manifest.json': text.encode(),
            **{f'payload/{key}': value for key, value in files.items()},
        }
        for entry, content in sorted(entries.items()):
            info = zipfile.ZipInfo(entry, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, content)
    print(f'{target} ({target.stat().st_size:,} bytes)')
    return target


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        '--only',
        choices=['lazygit', 'local-rag', 'yazi', 'term-devtools', 'all'],
        default='all',
    )
    parser.add_argument(
        '--archive',
        type=Path,
        help='兼容旧参数：仅覆盖 Lazygit 归档路径',
    )
    parser.add_argument(
        '--lazygit-archive',
        type=Path,
        default=ROOT / 'build/lazygit-download' / LAZYGIT_ARCHIVE,
    )
    parser.add_argument(
        '--yazi-archive',
        type=Path,
        default=ROOT / 'build/yazi-download' / YAZI_ARCHIVE,
    )
    parser.add_argument(
        '--fx-archive',
        type=Path,
        default=ROOT / 'build/term-devtools-download' / FX_ARCHIVE,
    )
    parser.add_argument(
        '--btop-archive',
        type=Path,
        default=ROOT / 'build/term-devtools-download' / BTOP_ARCHIVE,
    )
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--output', type=Path, default=ROOT / 'dist/plugins')
    args = parser.parse_args()
    if args.archive is not None:
        args.lazygit_archive = args.archive
    if args.only in ('all', 'lazygit'):
        vendor_lazygit(args.lazygit_archive, args.offline)
        pack('lazygit', args.output)
    if args.only in ('all', 'yazi'):
        vendor_yazi(args.yazi_archive, args.offline)
        pack('yazi', args.output)
    if args.only in ('all', 'term-devtools'):
        vendor_term_devtools(args.fx_archive, args.btop_archive, args.offline)
        pack('term-devtools', args.output)
    if args.only in ('all', 'local-rag'):
        pack('local-rag', args.output)


if __name__ == '__main__':
    main()

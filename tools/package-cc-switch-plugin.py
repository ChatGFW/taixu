#!/usr/bin/env python3
"""Package the tested official-v4 Web build; never download the old Web fork."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parent.parent
PROJECT = ROOT / "assets/plugins/cc-switch"
# The plugin version tracks the adapter; the upstream source stays pinned.
VERSION = "4.0.31"
UPSTREAM_VERSION = "4.0.30"
COMMIT = "3a0b86a26649f61d86dfbc3878d73220199f70b2"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def pack(server: Path, ui: Path, build_tree: Path, output: Path):
    provenance = json.loads((build_tree / "UPSTREAM.json").read_text())
    if provenance != {"version": UPSTREAM_VERSION, "commit": COMMIT}:
        raise ValueError("Expected prepared official v4.0.30 source")
    binary = server.read_bytes()
    if binary[:6] != b"\x7fELF\x02\x01" or binary[18:20] != b"\xb7\x00":
        raise ValueError("Expected Linux ARM64 ELF64 server")
    glibc = {tuple(map(int, v.decode().split('.'))) for v in re.findall(rb'GLIBC_([0-9]+\.[0-9]+)', binary)}
    if not glibc or max(glibc) > (2, 36):
        raise ValueError("Server requires a newer glibc than Debian RootFS; build with GNU 2.31 target")
    if not (ui / "index.html").is_file():
        raise ValueError("Official Web renderer is missing")
    files = {}
    for directory in ("bin", "scripts"):
        for path in sorted((PROJECT / "payload" / directory).rglob("*")):
            if path.is_file():
                files[path.relative_to(PROJECT / "payload").as_posix()] = path.read_bytes()
    for path in sorted(ui.rglob("*")):
        if path.is_symlink():
            raise ValueError("UI symlinks are not allowed")
        if path.is_file():
            files["ui/" + path.relative_to(ui).as_posix()] = path.read_bytes()
    files["lib/cc-switch-server"] = binary
    files["LICENSE"] = (build_tree / "LICENSE").read_bytes()
    files["Cargo.lock"] = (build_tree / "Cargo.lock").read_bytes()
    provenance["pluginVersion"] = VERSION
    provenance["serverSha256"] = digest(binary)
    provenance["cargoLockSha256"] = digest(files["Cargo.lock"])
    provenance["minimumGlibc"] = '.'.join(map(str, max(glibc)))
    files["UPSTREAM.json"] = (json.dumps(provenance, indent=2) + '\n').encode()
    files["checksums/SHA256SUMS"] = ''.join(f'{digest(data)}  {name}\n' for name, data in sorted(files.items())).encode()
    manifest = json.loads((PROJECT / "manifest.json").read_text(encoding="utf-8"))
    if manifest["version"] != VERSION or manifest["launchType"] != "web":
        raise ValueError("Unexpected plugin manifest")
    entries = {"manifest.json": (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode(),
               **{"payload/" + name: data for name, data in files.items()}}
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 11, 12, 0, 0, 0))
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            executable = name.startswith("payload/bin/") or name.startswith("payload/scripts/") or name == "payload/lib/cc-switch-server"
            info.external_attr = (0o100755 if executable else 0o100644) << 16
            archive.writestr(info, data)
    with zipfile.ZipFile(output) as archive:
        assert archive.testzip() is None
        assert set(archive.namelist()) == set(entries)
        for name, data in entries.items():
            assert digest(archive.read(name)) == digest(data)
    if output.stat().st_size > 96 * 1024 * 1024:
        raise ValueError("Package exceeds the plugin library limit")
    report = {"package": output.name, "size": output.stat().st_size, "sha256": digest(output.read_bytes()), **provenance}
    output.with_suffix(".txplugin.json").write_text(json.dumps(report, indent=2) + '\n')
    (PROJECT / "payload/checksums/SHA256SUMS").write_bytes(files["checksums/SHA256SUMS"])
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", type=Path, required=True)
    parser.add_argument("--ui", type=Path, required=True)
    parser.add_argument("--build-tree", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / f"dist/plugins/cc-switch-{VERSION}.txplugin")
    args = parser.parse_args()
    pack(args.server.resolve(), args.ui.resolve(), args.build_tree.resolve(), args.output.resolve())

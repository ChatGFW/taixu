#!/usr/bin/env python3
"""Structural validation and manifest generation for a bundled Ubuntu rootfs.

Mirrors the checks TaiXu's RootfsValidator performs before PRoot is started:
os-release present, /bin/sh resolvable, both shells AArch64, and the PT_INTERP
path named by the ELF actually existing inside the rootfs.

This is a host-side pre-check only. It does not execute guest binaries and cannot
replace the on-device validation.
"""
import argparse
import hashlib
import json
import os
import struct
import sys

AARCH64 = 183
MAX_SYMLINK_DEPTH = 32


def elf_info(path):
    with open(path, "rb") as handle:
        ident = handle.read(16)
        if len(ident) < 16 or ident[:4] != b"\x7fELF":
            raise ValueError("not an ELF file: %s" % path)
        if ident[4] != 2:
            raise ValueError("not ELF64: %s" % path)
        order = "<" if ident[5] == 1 else ">"
        handle.seek(0)
        header = handle.read(64)
        (e_machine,) = struct.unpack(order + "H", header[18:20])
        (e_phoff,) = struct.unpack(order + "Q", header[32:40])
        (e_phentsize,) = struct.unpack(order + "H", header[54:56])
        (e_phnum,) = struct.unpack(order + "H", header[56:58])
        interpreter = None
        for index in range(e_phnum):
            handle.seek(e_phoff + index * e_phentsize)
            ph = handle.read(e_phentsize)
            (p_type,) = struct.unpack(order + "I", ph[0:4])
            if p_type != 3:  # PT_INTERP
                continue
            (p_offset,) = struct.unpack(order + "Q", ph[8:16])
            (p_filesz,) = struct.unpack(order + "Q", ph[32:40])
            handle.seek(p_offset)
            raw = handle.read(p_filesz)
            interpreter = raw.split(b"\x00", 1)[0].decode("utf-8", "replace")
        return {"machine": e_machine, "interpreter": interpreter}


def resolve_guest(root, guest_path):
    root = os.path.realpath(root)
    pending = [p for p in guest_path.split("/") if p not in ("", ".")]
    current = root
    depth = 0
    while pending:
        part = pending.pop(0)
        if part == "..":
            # TaiXu's RootfsValidator rejects '..' in symlink targets instead of
            # navigating them; a bundled image must not rely on such links.
            raise ValueError("symlink target contains '..' (rejected by RootfsValidator): %s" % guest_path)
        candidate = os.path.normpath(os.path.join(current, part))
        if not (candidate == root or candidate.startswith(root + os.sep)):
            raise ValueError("guest path escapes rootfs: %s" % guest_path)
        if os.path.islink(candidate):
            depth += 1
            if depth > MAX_SYMLINK_DEPTH:
                raise ValueError("symlink depth exceeded: %s" % guest_path)
            target = os.readlink(candidate)
            parts = [p for p in target.replace("\\", "/").split("/") if p not in ("", ".")]
            if target.startswith("/"):
                current = root
            else:
                current = os.path.dirname(candidate)
            pending = parts + pending
        else:
            current = candidate
    return current


def first_existing(root, paths):
    for guest in paths:
        try:
            host = resolve_guest(root, guest)
        except ValueError:
            continue
        if os.path.isfile(host):
            return guest, host
    return None, None


def read_os_release(root):
    for guest in ("/etc/os-release", "/usr/lib/os-release"):
        host = resolve_guest(root, guest)
        if os.path.isfile(host):
            values = {}
            with open(host, "r", encoding="utf-8", errors="replace") as handle:
                for line in handle:
                    if "=" in line:
                        key, value = line.rstrip("\n").split("=", 1)
                        values[key] = value.strip().strip('"')
            return guest, values
    raise ValueError("rootfs is missing os-release")


def validate(args):
    root = args.rootfs
    if not os.path.isdir(root):
        raise ValueError("rootfs directory does not exist: %s" % root)
    os_release_path, os_release = read_os_release(root)

    bash_path, bash_file = first_existing(root, ("/bin/bash", "/usr/bin/bash"))
    sh_path, sh_file = first_existing(root, ("/bin/sh", "/usr/bin/sh"))
    if sh_file is None:
        raise ValueError("rootfs is missing a usable /bin/sh")

    primary_path, primary_file = (bash_path, bash_file) if bash_file else (sh_path, sh_file)
    primary = elf_info(primary_file)
    expected = args.expect_arch
    machine_name = {183: "aarch64", 62: "x86_64"}.get(primary["machine"], str(primary["machine"]))
    if machine_name != expected:
        raise ValueError("%s is %s, expected %s" % (primary_path, machine_name, expected))

    interpreter = primary["interpreter"]
    if not interpreter or not interpreter.startswith("/"):
        raise ValueError("%s has no absolute PT_INTERP" % primary_path)
    interpreter_host = resolve_guest(root, interpreter)
    if not os.path.isfile(interpreter_host):
        raise ValueError("interpreter missing: %s" % interpreter)
    interpreter_machine = elf_info(interpreter_host)["machine"]
    if interpreter_machine != primary["machine"]:
        raise ValueError("interpreter architecture mismatch: %s" % interpreter)

    result = {
        "osRelease": os_release_path,
        "distribution": {
            "id": os_release.get("ID", ""),
            "version": os_release.get("VERSION_ID", ""),
            "prettyName": os_release.get("PRETTY_NAME", ""),
        },
        "bash": bash_path,
        "posixShell": sh_path,
        "shell": primary_path,
        "machine": machine_name,
        "interpreter": interpreter,
        "interpreterSource": os.path.relpath(interpreter_host, root),
    }

    if bash_file:
        sh_info = elf_info(sh_file)
        if sh_info["machine"] != primary["machine"]:
            raise ValueError("/bin/sh architecture mismatch")
        if sh_info["interpreter"] != interpreter:
            raise ValueError("/bin/sh and bash use different interpreters")

    json.dump(result, sys.stdout, indent=2, sort_keys=True)
    sys.stdout.write("\n")


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write(args):
    recipe_sha = sha256_file(args.recipe)
    archive_sha = sha256_file(args.archive)
    inputs = "\n".join([
        "recipe=%s" % recipe_sha,
        "ubuntu=%s" % args.ubuntu_version,
        "abi=%s" % args.abi,
        "base=%s" % args.base_sha256,
        "epoch=%s" % args.epoch,
        "xz_preset=%s" % args.xz_preset,
        "xz_threads=%s" % args.xz_threads,
    ])
    asset_id = hashlib.sha256(inputs.encode("utf-8")).hexdigest()[:16]

    manifest = {
        "schema": 1,
        "assetId": asset_id,
        "abi": args.abi,
        "distribution": {
            "id": "ubuntu",
            "displayName": "Ubuntu",
            "version": args.ubuntu_version,
            "source": "ubuntu-base",
        },
        "archive": {
            "name": os.path.basename(args.archive),
            "sha256": archive_sha,
            "bytes": int(args.archive_bytes),
        },
        "rootfs": {
            "uncompressedBytes": int(args.uncompressed_bytes),
            "entries": int(args.entries),
        },
        "build": {
            "ubuntuVersion": args.ubuntu_version,
            "baseSha256": args.base_sha256,
            "sourceDateEpoch": int(args.epoch),
            "xzPreset": int(args.xz_preset),
            "xzThreads": int(args.xz_threads),
            "xzVersion": args.xz_version,
            "tarVersion": args.tar_version,
            "recipeSha256": recipe_sha,
            "inputsSha256": hashlib.sha256(inputs.encode("utf-8")).hexdigest(),
        },
    }
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, sort_keys=True)
        handle.write("\n")


def summary(args):
    with open(args.manifest, "r", encoding="utf-8") as handle:
        manifest = json.load(handle)
    archive = manifest["archive"]
    rootfs = manifest["rootfs"]
    print("assetId        %s" % manifest["assetId"])
    print("distribution   %s %s" % (manifest["distribution"]["displayName"], manifest["distribution"]["version"]))
    print("archive        %s (%d bytes, %.1f MiB)" % (
        archive["name"], archive["bytes"], archive["bytes"] / 1024 / 1024))
    print("rootfs         %d entries, %.1f MiB uncompressed" % (
        rootfs["entries"], rootfs["uncompressedBytes"] / 1024 / 1024))
    print("archive sha256 %s" % archive["sha256"])


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)

    p_validate = sub.add_parser("validate")
    p_validate.add_argument("--rootfs", required=True)
    p_validate.add_argument("--expect-arch", default="aarch64")
    p_validate.set_defaults(func=validate)

    p_write = sub.add_parser("write")
    for name in ("rootfs", "archive", "archive-bytes", "uncompressed-bytes", "entries",
                 "abi", "ubuntu-version", "base-sha256", "recipe", "xz-preset",
                 "xz-threads", "xz-version", "tar-version", "epoch", "out"):
        p_write.add_argument("--" + name, required=True)
    p_write.set_defaults(func=write)

    p_summary = sub.add_parser("summary")
    p_summary.add_argument("--manifest", required=True)
    p_summary.set_defaults(func=summary)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()

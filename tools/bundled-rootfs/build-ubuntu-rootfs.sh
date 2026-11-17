#!/usr/bin/env bash
# Builds a reproducible Ubuntu Base rootfs archive for TaiXu's bundled-install path.
#
#   bash build-ubuntu-rootfs.sh                          # build arm64 archive + manifest
#   bash build-ubuntu-rootfs.sh --verify-reproducible    # build twice and compare bytes
#
# Inputs are pinned by sha256; mismatched downloads are never unpacked.
# Output is consumed by the (not yet wired) RootfsInstaller.installBundled() path.
# This script does NOT touch the APK and does NOT replace the OCI install path.
set -euo pipefail

HERE="$(CDPATH= cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${WORK:-$HERE/.work}"
CACHE="${CACHE:-$HERE/.cache}"
OUT="${OUT:-$HERE/out}"

UBUNTU_VERSION="${UBUNTU_VERSION:-24.04.5}"
BASE_ARCH="${BASE_ARCH:-arm64}"          # arm64 | amd64
SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-1767225600}"   # 2026-01-01T00:00:00Z
XZ_PRESET="${XZ_PRESET:-6}"             # 8 MiB dictionary: keep phone-side peak memory modest
XZ_THREADS="${XZ_THREADS:-4}"           # byte-stable only for a fixed thread count on a fixed xz build

# Pinned Ubuntu Base archive digests (cdimage.ubuntu.com SHA256SUMS, cross-checked with TUNA mirror).
declare -A BASE_PIN=(
  [ubuntu-base-24.04.5-base-arm64.tar.gz]=a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2
  [ubuntu-base-24.04.5-base-amd64.tar.gz]=e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86
)

log() { printf '\033[1;34m[rootfs %s]\033[0m %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }
die() { printf '\033[1;31m[rootfs] error:\033[0m %s\n' "$*" >&2; exit 1; }
sha256() { sha256sum "$1" | cut -d' ' -f1; }

for tool in curl tar xz python3 sha256sum; do
  command -v "$tool" >/dev/null || die "missing host tool: $tool"
done

case "$BASE_ARCH" in
  arm64) BASE_NAME="ubuntu-base-${UBUNTU_VERSION}-base-arm64.tar.gz" ;;
  amd64) BASE_NAME="ubuntu-base-${UBUNTU_VERSION}-base-amd64.tar.gz" ;;
  *) die "BASE_ARCH must be arm64 or amd64" ;;
esac

BASE_SHA256="${BASE_PIN[$BASE_NAME]:-}"
[[ -n "$BASE_SHA256" ]] || die "no pin for $BASE_NAME (add it to BASE_PIN before using an unvetted Ubuntu version)"

RELEASE_DIR="releases/${UBUNTU_VERSION%.*}/release"
MIRRORS=(
  "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/$RELEASE_DIR"
  "https://cdimage.ubuntu.com/ubuntu-base/$RELEASE_DIR"
)

fetch() {
  local name="$1" dest="$2" want="$3" candidate
  mkdir -p "$(dirname "$dest")"
  if [[ -f "$dest" && "$(sha256 "$dest")" == "$want" ]]; then
    log "cache hit $name"
    return 0
  fi
  for candidate in "${MIRRORS[@]}"; do
    log "download $candidate/$name"
    if curl -fL --retry 3 --retry-delay 2 --connect-timeout 20 -sS -o "$dest.part" "$candidate/$name"; then
      local got
      got="$(sha256 "$dest.part")"
      [[ "$got" == "$want" ]] || { rm -f "$dest.part"; die "sha256 mismatch for $name: got $got want $want"; }
      mv "$dest.part" "$dest"
      return 0
    fi
    rm -f "$dest.part"
  done
  die "all mirrors failed for $name"
}

build_once() {
  local out_dir="$1"
  local base="$CACHE/ubuntu/$UBUNTU_VERSION/$BASE_NAME"
  local rootfs="$WORK/rootfs"

  fetch "$BASE_NAME" "$base" "$BASE_SHA256"

  rm -rf "$rootfs" "$out_dir"
  mkdir -p "$rootfs" "$out_dir"
  log "extracting $BASE_NAME"
  tar -xzf "$base" -C "$rootfs" --numeric-owner

  # Minimal normalization. Package sources stay as shipped; TaiXu's DistroConfigurator
  # rewrites apt/pip mirrors at install time, so baking mirrors here would only drift.
  mkdir -p "$rootfs/proc" "$rootfs/sys" "$rootfs/dev" "$rootfs/tmp" "$rootfs/root" "$rootfs/workspace"
  chmod 1777 "$rootfs/tmp"
  if [[ ! -f "$rootfs/etc/hosts" ]]; then
    printf '127.0.0.1 localhost\n::1 localhost\n' > "$rootfs/etc/hosts"
  fi
  # Ubuntu Base ships /etc/os-release -> ../usr/lib/os-release. TaiXu's RootfsValidator
  # rejects any '..' component while resolving guest symlinks, so a bundled image must
  # materialize this link or it fails validation before PRoot starts.
  if [[ -L "$rootfs/etc/os-release" ]]; then
    local target
    target="$(readlink -f "$rootfs/etc/os-release")"
    [[ "$target" == "$rootfs"/* ]] || die "os-release link escapes the rootfs: $target"
    cp --remove-destination "$target" "$rootfs/etc/os-release"
  fi

  log "validating rootfs structure"
  python3 -I "$HERE/lib/manifest.py" validate --rootfs "$rootfs" --expect-arch aarch64 > "$WORK/validation.json"

  log "assembling archive (xz -$XZ_PRESET -T$XZ_THREADS, epoch=$SOURCE_DATE_EPOCH)"
  local tar_file="$WORK/rootfs.tar"
  tar -C "$rootfs" \
    --sort=name \
    --mtime="@$SOURCE_DATE_EPOCH" \
    --clamp-mtime \
    --owner=0 --group=0 --numeric-owner \
    --format=gnu \
    -cf "$tar_file" .

  local archive="$out_dir/rootfs-${BASE_ARCH}.tar.xz"
  xz "-$XZ_PRESET" "-T$XZ_THREADS" --check=crc64 -c "$tar_file" > "$archive"

  local tar_bytes archive_bytes entries
  tar_bytes="$(stat -c %s "$tar_file")"
  archive_bytes="$(stat -c %s "$archive")"
  entries="$(tar -tf "$tar_file" | wc -l)"

  local xz_version tar_version
  xz_version="$(xz --version | head -n1)"
  tar_version="$(tar --version | head -n1)"

  python3 -I "$HERE/lib/manifest.py" write \
    --rootfs "$rootfs" \
    --archive "$archive" \
    --archive-bytes "$archive_bytes" \
    --uncompressed-bytes "$tar_bytes" \
    --entries "$entries" \
    --abi "${BASE_ARCH}" \
    --ubuntu-version "$UBUNTU_VERSION" \
    --base-sha256 "$BASE_SHA256" \
    --recipe "$HERE/build-ubuntu-rootfs.sh" \
    --xz-preset "$XZ_PRESET" \
    --xz-threads "$XZ_THREADS" \
    --xz-version "$xz_version" \
    --tar-version "$tar_version" \
    --epoch "$SOURCE_DATE_EPOCH" \
    --out "$out_dir/manifest.json"

  log "archive  $archive ($archive_bytes bytes)"
  log "manifest $out_dir/manifest.json"
}

print_summary() {
  local out_dir="$1"
  python3 -I "$HERE/lib/manifest.py" summary --manifest "$out_dir/manifest.json"
}

if [[ "${1:-}" == "--verify-reproducible" ]]; then
  build_once "$OUT/a"
  build_once "$OUT/b"
  a="$(sha256 "$OUT/a/rootfs-${BASE_ARCH}.tar.xz")"
  b="$(sha256 "$OUT/b/rootfs-${BASE_ARCH}.tar.xz")"
  if [[ "$a" == "$b" ]]; then
    log "reproducible: $a"
    print_summary "$OUT/a"
    rm -rf "$OUT/b"
    exit 0
  fi
  die "archive bytes differ between two builds: $a vs $b"
fi

build_once "$OUT"
print_summary "$OUT"

#!/bin/sh
set -eu
umask 077
tool_dir=${TAIXU_TOOL_DIR:-/opt/taixu/tools/cc-switch}
payload=${TAIXU_PLUGIN_PAYLOAD:?Missing plugin payload}
cd "$payload"
sha256sum -c checksums/SHA256SUMS
test "$(od -An -t x1 -j 18 -N 2 lib/cc-switch-server | tr -d ' \n')" = b700
mkdir -p "$tool_dir/bin" "$tool_dir/lib" "$tool_dir/ui"
cp bin/cc-switch-daemon "$tool_dir/bin/cc-switch-daemon"
cp lib/cc-switch-server "$tool_dir/lib/cc-switch-server"
cp -R ui/. "$tool_dir/ui/"
cp LICENSE UPSTREAM.json Cargo.lock "$tool_dir/"
chmod 755 "$tool_dir/bin/cc-switch-daemon" "$tool_dir/lib/cc-switch-server"
TAIXU_TOOL_DIR="$tool_dir" /bin/sh "$tool_dir/bin/cc-switch-daemon" --verify
echo 'CC Switch 4.0.30 Web installed. Start from plugin center; local port: 19870.'

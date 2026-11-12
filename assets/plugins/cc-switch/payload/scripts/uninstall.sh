#!/bin/sh
set -eu
tool_dir=${TAIXU_TOOL_DIR:-/opt/taixu/tools/cc-switch}
rm -f "$tool_dir/bin/cc-switch-daemon" "$tool_dir/lib/cc-switch-server"
echo 'CC Switch removed. User data and the old Web-fork database are retained.'

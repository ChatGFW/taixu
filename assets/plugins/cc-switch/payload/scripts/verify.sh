#!/bin/sh
set -eu
tool_dir=${TAIXU_TOOL_DIR:-/opt/taixu/tools/cc-switch}
test -s "$tool_dir/ui/index.html"
"$tool_dir/lib/cc-switch-server" --version

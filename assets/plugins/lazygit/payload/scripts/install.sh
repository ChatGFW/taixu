#!/bin/sh
set -eu
command -v git >/dev/null || { echo '请先安装 git；离线安装器不会联网获取依赖' >&2; exit 1; }
mkdir -p "$TAIXU_TOOL_DIR/bin" "$TAIXU_TOOL_DIR/libexec" "$TAIXU_TOOL_DIR/licenses"
cp "$TAIXU_PLUGIN_PAYLOAD/bin/lazygit" "$TAIXU_TOOL_DIR/bin/lazygit"
cp "$TAIXU_PLUGIN_PAYLOAD/vendor/lazygit" "$TAIXU_TOOL_DIR/libexec/lazygit"
cp "$TAIXU_PLUGIN_PAYLOAD/vendor/LICENSE" "$TAIXU_TOOL_DIR/licenses/LICENSE"
cp "$TAIXU_PLUGIN_PAYLOAD/config.yml" "$TAIXU_TOOL_DIR/config.yml"
chmod 755 "$TAIXU_TOOL_DIR/bin/lazygit" "$TAIXU_TOOL_DIR/libexec/lazygit"
"$TAIXU_TOOL_DIR/libexec/lazygit" --version

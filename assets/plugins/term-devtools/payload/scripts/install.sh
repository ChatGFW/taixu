#!/bin/sh
set -eu
mkdir -p "$TAIXU_TOOL_DIR/bin" "$TAIXU_TOOL_DIR/libexec" "$TAIXU_TOOL_DIR/licenses"
cp "$TAIXU_PLUGIN_PAYLOAD/bin/btop" "$TAIXU_TOOL_DIR/bin/btop"
cp "$TAIXU_PLUGIN_PAYLOAD/bin/fx" "$TAIXU_TOOL_DIR/bin/fx"
cp "$TAIXU_PLUGIN_PAYLOAD/vendor/btop" "$TAIXU_TOOL_DIR/libexec/btop"
cp "$TAIXU_PLUGIN_PAYLOAD/vendor/fx" "$TAIXU_TOOL_DIR/libexec/fx"
cp "$TAIXU_PLUGIN_PAYLOAD/vendor/LICENSE-btop" "$TAIXU_TOOL_DIR/licenses/LICENSE-btop"
cp "$TAIXU_PLUGIN_PAYLOAD/vendor/LICENSE-fx" "$TAIXU_TOOL_DIR/licenses/LICENSE-fx"
chmod 755 "$TAIXU_TOOL_DIR/bin/btop" "$TAIXU_TOOL_DIR/bin/fx" \
  "$TAIXU_TOOL_DIR/libexec/btop" "$TAIXU_TOOL_DIR/libexec/fx"
"$TAIXU_TOOL_DIR/libexec/btop" --version
"$TAIXU_TOOL_DIR/libexec/fx" --version

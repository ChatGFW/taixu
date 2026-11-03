#!/bin/sh
set -eu
mkdir -p "$TAIXU_TOOL_DIR/bin" "$TAIXU_TOOL_DIR/libexec" "$TAIXU_TOOL_DIR/licenses" "$TAIXU_TOOL_DIR/config"
cp "$TAIXU_PLUGIN_PAYLOAD/bin/yazi" "$TAIXU_TOOL_DIR/bin/yazi"
cp "$TAIXU_PLUGIN_PAYLOAD/bin/ya" "$TAIXU_TOOL_DIR/bin/ya"
cp "$TAIXU_PLUGIN_PAYLOAD/vendor/yazi" "$TAIXU_TOOL_DIR/libexec/yazi"
cp "$TAIXU_PLUGIN_PAYLOAD/vendor/ya" "$TAIXU_TOOL_DIR/libexec/ya"
cp "$TAIXU_PLUGIN_PAYLOAD/vendor/LICENSE" "$TAIXU_TOOL_DIR/licenses/LICENSE"
cp "$TAIXU_PLUGIN_PAYLOAD/config/yazi.toml" "$TAIXU_TOOL_DIR/config/yazi.toml"
chmod 755 "$TAIXU_TOOL_DIR/bin/yazi" "$TAIXU_TOOL_DIR/bin/ya" \
  "$TAIXU_TOOL_DIR/libexec/yazi" "$TAIXU_TOOL_DIR/libexec/ya"
"$TAIXU_TOOL_DIR/libexec/yazi" --version
"$TAIXU_TOOL_DIR/libexec/ya" --version

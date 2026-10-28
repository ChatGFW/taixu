#!/bin/sh
set -eu
umask 077
: "${TAIXU_TOOL_DIR:?}" "${TAIXU_TOOL_DATA:?}" "${TAIXU_PLUGIN_PAYLOAD:?}"
vendor="$TAIXU_PLUGIN_PAYLOAD/vendor"
binary="$vendor/agent2api-server"
test -s "$binary"
test -s "$vendor/ui/index.html"
test -s "$vendor/agent2api-host"
header=$(od -An -t x1 -N 6 "$binary" | tr -d ' \n')
machine=$(od -An -t x1 -j 18 -N 2 "$binary" | tr -d ' \n')
test "$header" = 7f454c460201 && test "$machine" = b700 || {
    echo 'Agent2API requires a Linux ARM64 little-endian ELF64 binary' >&2; exit 1;
}
echo '[TAIXU_PROGRESS:10] [VERIFY] ARM64 服务端校验完成'
mkdir -p "$TAIXU_TOOL_DIR/bin" "$TAIXU_TOOL_DIR/lib" "$TAIXU_TOOL_DATA"
chmod 700 "$TAIXU_TOOL_DATA"
cp "$binary" "$TAIXU_TOOL_DIR/lib/agent2api-server"
cp "$vendor/agent2api-host" "$TAIXU_TOOL_DIR/lib/agent2api-host"
cp -R "$vendor/ui" "$TAIXU_TOOL_DIR/lib/"
cp -R "$TAIXU_PLUGIN_PAYLOAD/web" "$TAIXU_TOOL_DIR/"
cp "$vendor/LICENSE" "$TAIXU_TOOL_DIR/LICENSE"
cp "$vendor/GO-LICENSE" "$TAIXU_TOOL_DIR/GO-LICENSE"
cp "$TAIXU_PLUGIN_PAYLOAD/bin/agent2api" "$TAIXU_TOOL_DIR/bin/agent2api"
chmod 755 "$TAIXU_TOOL_DIR/lib/agent2api-server" "$TAIXU_TOOL_DIR/lib/agent2api-host" "$TAIXU_TOOL_DIR/bin/agent2api"
echo '[TAIXU_PROGRESS:90] [COPY] 网关与管理界面已部署，账号数据保留在独立目录'
"$TAIXU_TOOL_DIR/bin/agent2api" --verify
echo '[TAIXU_PROGRESS:100] [VERIFY] 服务端启动验证完成'

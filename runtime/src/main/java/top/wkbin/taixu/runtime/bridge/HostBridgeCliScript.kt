package top.wkbin.taixu.runtime.bridge

/**
 * 沙箱端 `taixu-host` 脚本。
 *
 * 宿主回包里的 `failure` 分类被翻译成进程退出码，agent 在 shell 里就能区分
 * 参数错、未授权、通道不可用和超时，而不是拿到一个恒为 0 的退出码。
 *
 * 脚本正文里 `@@` 是 `$` 的占位符，避免 Kotlin raw string 的模板插值。
 */
internal object HostBridgeCliScript {

    private val RAW = """
#!/bin/sh
# TaiXu Host Bridge CLI
# Usage: taixu-host install-apk <path> | shell <cmd> | logcat [pkg] [tag] [prio] [kw] [lines] | health
# Exit: 0 ok / 1 internal / 2 usage / 66 not_found / 69 unavailable / 124 timeout / 126 permission_denied
BRIDGE_URL="http://127.0.0.1:7980"

die() {
  printf '{"success":false,"failure":"%s","error":"%s"}\n' "@@1" "@@2" >&2
  exit "@@3"
}

# 失败分类 -> 退出码；没有分类时按 HTTP 状态兜底
exit_for() {
  case "@@1" in
    usage) echo 2 ;;
    not_found) echo 66 ;;
    permission_denied) echo 126 ;;
    unavailable) echo 69 ;;
    timeout) echo 124 ;;
    internal) echo 1 ;;
    *)
      case "@@2" in
        200) echo 0 ;;
        400) echo 2 ;;
        401|403) echo 126 ;;
        404) echo 66 ;;
        408|504) echo 124 ;;
        000) echo 69 ;;
        *) echo 1 ;;
      esac
      ;;
  esac
}

finish() {
  STATUS=@@(printf '%s' "@@1" | tail -n 1)
  BODY=@@(printf '%s' "@@1" | sed '@@d')
  [ -n "@@BODY" ] && printf '%s\n' "@@BODY"
  FAILURE=@@(printf '%s' "@@BODY" | sed -n 's/.*"failure"[[:space:]]*:[[:space:]]*"\([a-z_]*\)".*/\1/p' | head -n 1)
  exit "@@(exit_for "@@FAILURE" "@@STATUS")"
}

post() {
  RESP=@@(curl -s -m 120 -w '\n%{http_code}' -X POST "@@{BRIDGE_URL}@@1" -H 'Content-Type: application/json' -H "@@AUTH" -d "@@2")
  finish "@@RESP"
}

case "@@{1:-}" in
  install-apk|shell|logcat)
    [ -z "@@{TAIXU_BRIDGE_TOKEN:-}" ] && \
      die permission_denied "missing host credential: restart the TaiXu session to refresh it" 126
    ;;
esac

command -v curl >/dev/null 2>&1 || die unavailable "curl not installed" 69

AUTH="Authorization: Bearer @@{TAIXU_BRIDGE_TOKEN:-}"

case "@@1" in
  install-apk)
    [ -z "@@2" ] && die usage "Usage: taixu-host install-apk <path>" 2
    TARGET="@@2"
    if [ ! -f "@@{TARGET}" ]; then
      STRIPPED="@@{TARGET#/}"
      if [ -f "@@{STRIPPED}" ]; then
        TARGET="@@{STRIPPED}"
      elif [ -f "./@@{STRIPPED}" ]; then
        TARGET="./@@{STRIPPED}"
      elif [ -f "build/@@{STRIPPED}" ]; then
        TARGET="build/@@{STRIPPED}"
      else
        BN=@@(basename "@@TARGET")
        FOUND=@@(find . -maxdepth 6 -name "@@BN" -type f 2>/dev/null | head -n 1)
        if [ -z "@@FOUND" ] && [ -d /workspace ]; then
          FOUND=@@(find /workspace -maxdepth 7 -name "@@BN" -type f 2>/dev/null | head -n 1)
        fi
        if [ -z "@@FOUND" ]; then
          FOUND=@@(find . -maxdepth 6 -name "*.apk" -type f 2>/dev/null | head -n 1)
        fi
        if [ -n "@@FOUND" ] && [ -f "@@FOUND" ]; then
          TARGET="@@FOUND"
        fi
      fi
    fi
    [ ! -f "@@{TARGET}" ] && die not_found "Not found: @@2" 66
    if command -v realpath >/dev/null 2>&1; then
      ABS_TARGET=@@(realpath "@@TARGET")
    elif command -v readlink >/dev/null 2>&1; then
      ABS_TARGET=@@(readlink -f "@@TARGET" 2>/dev/null || echo "@@TARGET")
    else
      ABS_TARGET="@@TARGET"
    fi
    post /api/install-apk "{\"path\":\"@@{ABS_TARGET}\"}"
    ;;
  shell)
    [ -z "@@2" ] && die usage "Usage: taixu-host shell <command>" 2
    shift
    CMD="@@*"
    if command -v jq >/dev/null 2>&1; then
      BODY=@@(jq -nc --arg c "@@{CMD}" '{command:@@c}')
    else
      ESC=@@(printf '%s' "@@{CMD}" | sed 's/\\/\\\\/g; s/"/\\"/g' | tr '\n' ' ')
      BODY="{\"command\":\"@@{ESC}\"}"
    fi
    post /api/shell "@@BODY"
    ;;
  logcat)
    shift
    PKG="@@{1:-}"
    TAG="@@{2:-}"
    PRIO="@@{3:-V}"
    KW="@@{4:-}"
    LINES="@@{5:-200}"
    if command -v jq >/dev/null 2>&1; then
      BODY=@@(jq -nc --arg p "@@PKG" --arg t "@@TAG" --arg pr "@@PRIO" --arg k "@@KW" --arg l "@@LINES" '{package:@@p, tag:@@t, priority:@@pr, keyword:@@k, lines:(@@l|tonumber)}')
    else
      BODY="{\"package\":\"@@PKG\",\"tag\":\"@@TAG\",\"priority\":\"@@PRIO\",\"keyword\":\"@@KW\",\"lines\":@@LINES}"
    fi
    post /api/logcat "@@BODY"
    ;;
  health|status)
    finish @@(curl -s -m 15 -w '\n%{http_code}' "@@{BRIDGE_URL}/api/health")
    ;;
  *)
    echo "TaiXu Host Bridge CLI"
    echo "  taixu-host install-apk <path>   Install APK on host (wireless ADB or system dialog)"
    echo "  taixu-host shell <command>      Run host shell (wireless ADB or Shizuku/root)"
    echo "  taixu-host logcat [pkg] [tag] [prio] [kw] [lines] Capture logcat via wireless ADB"
    echo "  taixu-host health               Bridge health and ADB status"
    exit 2
    ;;
esac
""".trimIndent()

    val TAIXU_HOST_SCRIPT: String = RAW.replace("@@", "$") + "\n"
}

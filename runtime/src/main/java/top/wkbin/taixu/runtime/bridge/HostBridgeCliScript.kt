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
# Usage: taixu-host install-apk <path> | shell <cmd> | logcat [pkg] [tag] [prio] [kw] [lines]
#        taixu-host open <url> | clipboard set <text> | notify <title> [body]
#        taixu-host setting get|put ... | app start|stop|freeze|unfreeze <pkg>
#        taixu-host screen observe|tap|swipe|shot|text|key ... | doctor | health
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
  # 先让各子命令完成参数校验，再检查凭据：缺参数应报用法，而不是先报未授权。
  [ -z "@@{TAIXU_BRIDGE_TOKEN:-}" ] && \
    die permission_denied "missing host credential: restart the TaiXu session to refresh it" 126
  RESP=@@(curl -s -m 120 -w '\n%{http_code}' -X POST "@@{BRIDGE_URL}@@1" -H 'Content-Type: application/json' -H "@@AUTH" -d "@@2")
  finish "@@RESP"
}

# Encode control bytes while preserving UTF-8 and trailing newlines, without requiring jq.
jesc() {
  printf '%s' "@@1" | od -An -v -tu1 | LC_ALL=C awk '{
    for (i = 1; i <= NF; i++) {
      n = @@i
      if (n == 34 || n == 92) printf "\\%c", n
      else if (n < 32) printf "\\u%04x", n
      else printf "%c", n
    }
  }'
}

# 只接受规范十进制整数（拒绝空串、前导 0 和非数字），再嵌入 JSON 数值。
# 裸插坐标或时长时，带引号的片段会改写 op。
is_uint() { case "@@1" in ''|*[!0-9]*|0[0-9]*) return 1 ;; *) return 0 ;; esac }

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
    post /api/install-apk "{\"path\":\"@@(jesc "@@{ABS_TARGET}")\"}"
    ;;
  shell)
    [ -z "@@2" ] && die usage "Usage: taixu-host shell <command>" 2
    shift
    CMD="@@*"
    if command -v jq >/dev/null 2>&1; then
      BODY=@@(jq -nc --arg c "@@{CMD}" '{command:@@c}')
    else
      ESC=@@(jesc "@@{CMD}")
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
    is_uint "@@LINES" || die usage "lines 必须是十进制整数" 2
    if command -v jq >/dev/null 2>&1; then
      BODY=@@(jq -nc --arg p "@@PKG" --arg t "@@TAG" --arg pr "@@PRIO" --arg k "@@KW" --arg l "@@LINES" '{package:@@p, tag:@@t, priority:@@pr, keyword:@@k, lines:(@@l|tonumber)}')
    else
      BODY="{\"package\":\"@@(jesc "@@PKG")\",\"tag\":\"@@(jesc "@@TAG")\",\"priority\":\"@@(jesc "@@PRIO")\",\"keyword\":\"@@(jesc "@@KW")\",\"lines\":@@LINES}"
    fi
    post /api/logcat "@@BODY"
    ;;
  open)
    [ -z "@@2" ] && die usage "Usage: taixu-host open <http|https|mailto|tel url>" 2
    post /api/open "{\"uri\":\"@@(jesc "@@2")\"}"
    ;;
  clipboard)
    [ "@@{2:-}" = "set" ] || die usage "Usage: taixu-host clipboard set <text>" 2
    shift
    shift
    [ -z "@@*" ] && die usage "Usage: taixu-host clipboard set <text>" 2
    TEXT=@@(jesc "@@*")
    post /api/clipboard "{\"text\":\"@@{TEXT}\"}"
    ;;
  notify)
    [ -z "@@2" ] && die usage "Usage: taixu-host notify <title> [body]" 2
    TITLE=@@(jesc "@@2")
    shift
    shift
    BODY=@@(jesc "@@*")
    post /api/notify "{\"title\":\"@@{TITLE}\",\"body\":\"@@{BODY}\"}"
    ;;
  setting)
    case "@@{2:-}" in
      get)
        [ @@# -lt 4 ] && die usage "Usage: taixu-host setting get <system|secure|global> <key>" 2
        post /api/settings "{\"op\":\"get\",\"namespace\":\"@@(jesc "@@3")\",\"key\":\"@@(jesc "@@4")\"}"
        ;;
      put)
        [ @@# -lt 5 ] && die usage "Usage: taixu-host setting put <namespace> <key> <value>" 2
        SET_NS=@@(jesc "@@3")
        SET_KEY=@@(jesc "@@4")
        shift
        shift
        shift
        shift
        SET_VALUE=@@(jesc "@@*")
        post /api/settings "{\"op\":\"put\",\"namespace\":\"@@{SET_NS}\",\"key\":\"@@{SET_KEY}\",\"value\":\"@@{SET_VALUE}\"}"
        ;;
      *) die usage "Usage: taixu-host setting get|put <namespace> <key> [value]" 2 ;;
    esac
    ;;
  app)
    [ @@# -lt 3 ] && die usage "Usage: taixu-host app start|stop|freeze|unfreeze <package>" 2
    post /api/app "{\"op\":\"@@(jesc "@@2")\",\"package\":\"@@(jesc "@@3")\"}"
    ;;
  screen)
    case "@@{2:-}" in
      observe) post /api/screen '{"op":"observe"}' ;;
      tap)
        [ @@# -lt 4 ] && die usage "Usage: taixu-host screen tap <x> <y>" 2
        is_uint "@@3" && is_uint "@@4" || die usage "x/y 必须是十进制整数" 2
        post /api/screen "{\"op\":\"tap\",\"x\":@@3,\"y\":@@4}"
        ;;
      swipe)
        [ @@# -lt 6 ] && die usage "Usage: taixu-host screen swipe <x1> <y1> <x2> <y2> [duration_ms]" 2
        is_uint "@@3" && is_uint "@@4" && is_uint "@@5" && is_uint "@@6" || die usage "坐标必须是十进制整数" 2
        SWIPE_MS="@@{7:-300}"
        is_uint "@@SWIPE_MS" || die usage "duration_ms 必须是十进制整数" 2
        post /api/screen "{\"op\":\"swipe\",\"x1\":@@3,\"y1\":@@4,\"x2\":@@5,\"y2\":@@6,\"duration_ms\":@@SWIPE_MS}"
        ;;
      shot)
        if [ -n "@@{3:-}" ]; then
          post /api/screen "{\"op\":\"shot\",\"name\":\"@@(jesc "@@3")\"}"
        else
          post /api/screen '{"op":"shot"}'
        fi
        ;;
      text)
        shift
        shift
        [ -z "@@*" ] && die usage "Usage: taixu-host screen text <text>" 2
        post /api/screen "{\"op\":\"text\",\"text\":\"@@(jesc "@@*")\"}"
        ;;
      key)
        [ -z "@@{3:-}" ] && die usage "Usage: taixu-host screen key <back|home|recents|enter|delete|paste|power>" 2
        post /api/screen "{\"op\":\"key\",\"key\":\"@@(jesc "@@3")\"}"
        ;;
      *) die usage "Usage: taixu-host screen observe|tap|swipe|text|key ..." 2 ;;
    esac
    ;;
  doctor)
    echo "granted-actions   : @@{TAIXU_BRIDGE_ACTIONS:-unknown}"
    finish "@@(curl -s -m 15 -w '\n%{http_code}' "@@{BRIDGE_URL}/api/doctor")"
    ;;
  health|status)
    finish "@@(curl -s -m 15 -w '\n%{http_code}' "@@{BRIDGE_URL}/api/health")"
    ;;
  *)
    echo "TaiXu Host Bridge CLI"
    echo "  taixu-host install-apk <path>   Install APK on host (wireless ADB or system dialog)"
    echo "  taixu-host shell <command>      Run host shell (wireless ADB or Shizuku/root)"
    echo "  taixu-host logcat [pkg] [tag] [prio] [kw] [lines] Capture logcat via wireless ADB"
    echo "  taixu-host open <url>           Open http/https/mailto/tel link on the host"
    echo "  taixu-host clipboard set <text> Copy text to the host clipboard"
    echo "  taixu-host notify <title> [body] Post a host notification"
    echo "  taixu-host setting get|put <ns> <key> [value]   Read/write system settings"
    echo "  taixu-host app start|stop|freeze|unfreeze <pkg> Control an app"
    echo "  taixu-host screen observe|tap|swipe|shot|text|key  Inspect or drive the screen"
    echo "  taixu-host health               Bridge health and ADB status"
    echo "  taixu-host doctor               Explain why capability commands are unavailable"
    exit 2
    ;;
esac
""".trimIndent()

    val TAIXU_HOST_SCRIPT: String = RAW.replace("@@", "$") + "\n"

    /**
     * 能力命令的薄封装：让沙箱里能像普通命令一样调用宿主能力，
     * 又不必在 PATH 上维护多份协议实现（统一落到 taixu-host）。
     */
    val SHIMS: Map<String, String> = mapOf(
        "taixu-open" to "#!/bin/sh\nexec /opt/taixu/bin/taixu-host open \"\$@\"\n",
        "taixu-clipboard" to "#!/bin/sh\nexec /opt/taixu/bin/taixu-host clipboard \"\$@\"\n",
        "taixu-notify" to "#!/bin/sh\nexec /opt/taixu/bin/taixu-host notify \"\$@\"\n",
        "taixu-setting" to "#!/bin/sh\nexec /opt/taixu/bin/taixu-host setting \"\$@\"\n",
        "taixu-app" to "#!/bin/sh\nexec /opt/taixu/bin/taixu-host app \"\$@\"\n",
        "taixu-screen" to "#!/bin/sh\nexec /opt/taixu/bin/taixu-host screen \"\$@\"\n",
    )
}

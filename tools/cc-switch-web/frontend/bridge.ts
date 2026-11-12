// Same-origin transport for the official v4.0.30 renderer.
type Event<T = unknown> = { event: string; payload: T; id: number };
export type UnlistenFn = () => void;
const listeners = new Map<string, Set<(event: Event<any>) => void>>();
const stream = new EventSource('/api/events');
const ready = new Promise<void>((resolve, reject) => {
  const timer = setTimeout(() => reject(new Error('事件连接超时，请刷新页面')), 15000);
  stream.addEventListener('open', () => { clearTimeout(timer); resolve(); }, { once: true });
});
const channelFinalizer = new FinalizationRegistry<UnlistenFn>(close => close());
stream.onmessage = message => {
  const event = JSON.parse(message.data);
  if (event.event === 'transport-resync-required') {
    window.alert('事件连接未能跟上更新，请刷新页面后重试当前操作。');
    return;
  }
  listeners.get(event.event)?.forEach(callback => callback(event));
};
export async function listen<T>(event: string, handler: (event: Event<T>) => void): Promise<UnlistenFn> {
  let callbacks = listeners.get(event);
  if (!callbacks) listeners.set(event, callbacks = new Set());
  callbacks.add(handler);
  return () => { callbacks!.delete(handler); if (!callbacks!.size) listeners.delete(event); };
}
export async function invoke<T>(command: string, args: Record<string, unknown> = {}): Promise<T> {
  if (command === 'tray_app_page_seen' || command === 'take_tray_navigation') return null as T;
  if (command === 'set_window_theme') {
    const theme = String(args.theme);
    document.documentElement.style.colorScheme = theme === 'system' ? 'light dark' : theme;
    return null as T;
  }
  if (command === 'open_external') {
    const url = new URL(String(args.url));
    if (!['https:', 'http:', 'mailto:'].includes(url.protocol)) throw new Error('不支持的链接协议');
    window.open(url.href, '_blank', 'noopener,noreferrer');
    // With noopener the return value may be null even when the tab was opened.
    return true as T;
  }
  if (command === 'copy_text_to_clipboard') {
    await navigator.clipboard.writeText(String(args.text));
    return true as T;
  }
  if (command === 'export_session_markdown') {
    const blob = new Blob([String(args.content)], {type:'text/markdown;charset=utf-8'});
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a'); link.href = url; link.download = String(args.defaultName || 'session.md'); link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    return link.download as T;
  }
  if (Object.values(args).some(value => value instanceof Channel)) await ready;
  const response = await fetch(`/api/invoke/${encodeURIComponent(command)}`, {
    method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(args),
  });
  const body = await response.json();
  if (!response.ok) throw body.error || `HTTP ${response.status}`;
  return command === 'get_session_image' ? new Uint8Array(body.result).buffer as T : body.result;
}
// The adapter owns its login: keep one unobtrusive way into the password panel,
// which the official UI has no place for.
function mountPasswordEntry(): void {
  if (document.getElementById('taixu-password-entry')) return;
  const button = document.createElement('button');
  button.id = 'taixu-password-entry';
  button.type = 'button';
  button.textContent = '密码';
  button.title = '查看或修改登录密码';
  button.setAttribute('aria-label', '查看或修改登录密码');
  button.style.cssText = 'position:fixed;left:10px;bottom:10px;z-index:2147483646;padding:6px 12px;'
    + 'border:1px solid rgba(128,128,128,.45);border-radius:999px;background:rgba(127,127,127,.16);'
    + 'color:inherit;font:inherit;font-size:12px;line-height:1.4;cursor:pointer;backdrop-filter:blur(6px)';
  button.addEventListener('click', () => { window.open('/password', '_blank', 'noopener'); });
  document.body.append(button);
}
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mountPasswordEntry, { once: true });
else mountPasswordEntry();

export class Channel<T> {
  private id = `channel:${crypto.randomUUID()}`;
  onmessage: (message: T) => void = () => {};
  constructor() {
    const weak = new WeakRef(this);
    void listen<T>(this.id, event => weak.deref()?.onmessage(event.payload)).then(close => channelFinalizer.register(this, close));
  }
  toJSON() { return this.id; }
}
export const isTauri = () => false;
export const getVersion = async () => '4.0.30';
// Updating the executable is owned by TaiXu's verified plugin installer.
export const check = async () => null;
export const homeDir = () => invoke<string>('get_home_dir');
export const join = async (...parts: string[]) => parts.join('/').replace(/\/{2,}/g, '/');
export const message = async (text: string) => { window.alert(text); };
export const exit = async () => { throw new Error('请在太墟插件中心停止服务'); };
export const error = async (text: string) => { console.error(text); };
export const info = async (text: string) => { console.info(text); };
export const getCurrentWebview = () => ({ setZoom: async (zoom: number) => { document.documentElement.style.zoom = String(zoom); } });
export const getCurrentWindow = () => ({
  onResized: async (callback: () => void) => { window.addEventListener('resize', callback); return () => window.removeEventListener('resize', callback); },
  onScaleChanged: async (callback: () => void) => { window.addEventListener('resize', callback); return () => window.removeEventListener('resize', callback); },
  setDecorations: async (_enabled: boolean) => {},
  isFullscreen: async () => !!document.fullscreenElement,
  isResizable: async () => false,
  scaleFactor: async () => window.devicePixelRatio,
  isMaximized: async () => false,
  isFocused: async () => document.hasFocus(),
  show: async () => {},
  setFocus: async () => window.focus(),
  onFocusChanged: async (callback: (event: { payload: boolean }) => void) => {
    const focus = () => callback({payload: true});
    const blur = () => callback({payload: false});
    window.addEventListener('focus', focus); window.addEventListener('blur', blur);
    return () => { window.removeEventListener('focus', focus); window.removeEventListener('blur', blur); };
  },
});

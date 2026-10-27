'use strict';
const $ = id => document.getElementById(id);
let sequence = 0, pending = null, busy = false;
function request(action, text) {
  if (!window.taixuPlugin) return Promise.reject(new Error('请在太墟插件管理页面中打开。完整原版面板请使用外部浏览器入口。'));
  if (pending) return Promise.reject(new Error('上一次请求尚未完成'));
  if (sequence >= 240) return Promise.reject(new Error('页面请求配额即将用尽，请关闭后重新打开。后台服务会继续运行。'));
  return new Promise((resolve, reject) => {
    const id = 'a2a_' + (++sequence);
    const timer = setTimeout(() => { if (pending && pending.id === id) pending = null; reject(new Error('请求超时，操作可能已提交。请先检查状态，避免重复重设。')); }, 75000);
    pending = { id, timer, resolve, reject };
    window.taixuPlugin.postMessage(JSON.stringify({ id, action, target:'service', text }));
  });
}
if (window.taixuPlugin) window.taixuPlugin.onmessage = event => {
  let reply; try { reply = JSON.parse(event.data); } catch (_) { return; }
  if (!pending || reply.id !== pending.id) return;
  const item = pending; pending = null; clearTimeout(item.timer);
  if (!reply.success) return item.reject(new Error('宿主未完成操作：' + reply.text));
  try { const data = JSON.parse(reply.text); if (data.error) item.reject(new Error(data.error)); else item.resolve(data); }
  catch (_) { item.reject(new Error('服务响应格式无效')); }
};
function setBusy(value) { busy = value; ['refresh','reset'].forEach(id => { $(id).disabled = value; }); }
async function refresh() {
  const status = await request('host.service-request', 'api/status');
  $('status').textContent = status.ready ? '网关运行正常 · Agent2API ' + status.version : '网关暂不可用，可能正在重启';
  const registered = status.panel && status.panel.registered;
  $('adminStatus').textContent = registered ? '已创建管理员；如果忘记密码，可在下方重设。' : '尚未创建管理员，请在下方设置或在原版面板注册。';
  $('reset').textContent = registered ? '重设管理员（保留模型账号）' : '创建管理员';
  const models = await request('host.service-request', 'api/models');
  $('models').textContent = Array.isArray(models.data) && models.data.length ? models.data.map(item => item.id).join('\n') : '暂无模型，请在原版面板添加自己的模型服务商账号。';
}
async function reset() {
  const username = $('username').value.trim();
  if (!/^[A-Za-z0-9_.-]{3,32}$/.test(username)) throw new Error('用户名应为 3–32 位字母、数字、点、下划线或短横线。');
  $('credentials').hidden = true; $('resultPassword').value = ''; $('resultPassword').type = 'password'; $('reveal').textContent = '显示密码';
  const operation = await request('host.service-write', 'api/admin/reset\n' + JSON.stringify({username,preserveAccounts:true}));
  if (!operation.job || !/^[a-f0-9]{32}$/.test(operation.job)) throw new Error('操作未受理');
  $('result').textContent = '正在重设管理员并重启网关，请稍候…';
  for (let attempt=0; attempt<40; attempt++) {
    await new Promise(resolve => setTimeout(resolve, 1000));
    const state = await request('host.service-request', 'api/job/' + operation.job);
    if (state.state === 'running') continue;
    if (state.state !== 'done') throw new Error(state.error || '操作未完成，请查看服务状态。');
    $('resultUser').value = state.username; $('resultPassword').value = state.password; $('credentials').hidden = false;
    $('result').textContent = '管理员已更新。保存凭据后，可用工具详情中的外部浏览器按钮登录原版面板。';
    await refresh(); return;
  }
  throw new Error('操作仍在后台执行，请稍后再查看服务状态。');
}
$('reveal').addEventListener('click', () => { const visible = $('resultPassword').type === 'password'; $('resultPassword').type = visible ? 'text' : 'password'; $('reveal').textContent = visible ? '隐藏密码' : '显示密码'; });
$('refresh').addEventListener('click', async () => { if (busy) return; setBusy(true); try { await refresh(); $('result').textContent = ''; } catch(error) { $('result').textContent = error.message; } finally { setBusy(false); } });
$('reset').addEventListener('click', async () => { if (busy) return; setBusy(true); try { await reset(); } catch(error) { $('result').textContent = error.message; } finally { setBusy(false); } });
setBusy(true); refresh().catch(error => { $('status').textContent = error.message; }).finally(() => setBusy(false));

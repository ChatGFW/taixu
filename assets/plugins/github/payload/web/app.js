'use strict';
const $ = id => document.getElementById(id);
let sequence = 0;
let pending = null;
let busy = false;
let currentJob = null;
function request(action, text) {
  if (!window.taixuPlugin) return Promise.reject(new Error('请在更新后的太墟 GitHub Web 插件页面中打开。'));
  if (pending) return Promise.reject(new Error('上一次请求尚未完成'));
  if (sequence >= 240) return Promise.reject(new Error('本页请求配额即将用尽，请关闭并重新打开此页面。工具中心的服务与后台任务会继续运行。'));
  return new Promise((resolve, reject) => {
    const id = 'gh_' + (++sequence);
    const timer = setTimeout(() => { pending = null; reject(new Error('请求超时。操作可能已执行，请查看结果；不要重复提交。')); }, 75000);
    pending = {id, resolve, reject, timer};
    window.taixuPlugin.postMessage(JSON.stringify({id, action, target:'service', text}));
  });
}
if (window.taixuPlugin) window.taixuPlugin.onmessage = event => {
  let reply;
  try { reply = JSON.parse(event.data); } catch (_) { return; }
  if (!pending || reply.id !== pending.id) return;
  const item = pending; pending = null; clearTimeout(item.timer);
  if (!reply.success) return item.reject(new Error('宿主拒绝或未完成请求：' + reply.text));
  try { item.resolve(JSON.parse(reply.text)); } catch (_) { item.reject(new Error('服务响应格式无效')); }
};
async function refresh() {
  const status = await request('host.service-request', 'api/status');
  $('account').textContent = status.user ? '已保存账号：' + status.user.login + '（最近一次 Token 验证）' : '尚未连接 GitHub';
  $('publicKey').value = status.publicKey;
  $('repoRoot').textContent = '仓库目录：' + status.repositoryRoot + '；终端可用 gh 与 gh-git 命令。';
  const missing = Object.entries(status.dependencies).filter(([,ok]) => !ok).map(([name]) => name);
  $('notice').textContent = missing.length ? '缺少运行依赖：' + missing.join(', ') + '。请在 Linux 中安装后再操作。' : '服务已连接。表单写入会弹出太墟原生确认。';
}
function form(action) {
  const body = {};
  if (action === 'auth/token') body.token = $('token').value.trim();
  if (action === 'git/identity') Object.assign(body, {name:$('name').value.trim(),email:$('email').value.trim()});
  if (action.startsWith('repo/') || action.startsWith('pr/')) body.repo = $('repo').value.trim();
  if (action === 'repo/clone') body.protocol = $('protocol').value;
  if (action === 'repo/commit-push') Object.assign(body, {branch:$('branch').value.trim(),message:$('message').value});
  if (action === 'pr/create') Object.assign(body, {title:$('title').value,body:$('body').value});
  return 'api/' + action + '\n' + JSON.stringify(body);
}
function setBusy(value) { busy = value; document.querySelectorAll('button').forEach(button => { button.disabled = value; }); }
async function operate(action) {
  const text = form(action);
  if (text.length > 1024) throw new Error('表单超过当前消息容量，请缩短说明后提交。');
  if (action === 'auth/token') $('token').value = '';
  const started = await request('host.service-write', text);
  if (!started.job) throw new Error(started.error || '任务未启动');
  currentJob = started.job;
  await poll(started.job);
}
async function poll(id) {
  $('result').textContent = '任务执行中…\n任务 ID：' + id;
  for (let attempt = 0; attempt < 64; attempt++) {
    await new Promise(resolve => setTimeout(resolve, Math.min(10000, 1000 * (2 ** Math.min(attempt, 4)))));
    const state = await request('host.service-request', 'api/job/' + id);
    if (state.state === 'running') continue;
    currentJob = null;
    if (state.state !== 'done') throw new Error(state.error || '操作失败');
    $('result').textContent = JSON.stringify(state.result, null, 2);
    await refresh();
    return;
  }
  throw new Error('任务仍在工具中心后台执行。可稍后点“刷新本机状态”查看；关闭页面不会停止任务，勿重复提交。');
}
async function recover() {
  await refresh();
  const task = await request('host.service-request', 'api/current-job');
  if (task.job) { currentJob = task.job; await poll(task.job); }
}
document.querySelectorAll('[data-action]').forEach(button => button.addEventListener('click', async () => {
  if (busy) return;
  setBusy(true);
  try { await operate(button.dataset.action); } catch (error) { $('result').textContent = error.message; }
  finally { setBusy(false); }
}));
$('refresh').addEventListener('click', async () => {
  if (busy) return;
  setBusy(true);
  try { if (currentJob) await poll(currentJob); else await recover(); }
  catch (error) { $('result').textContent = error.message; } finally { setBusy(false); }
});
setBusy(true);
recover()
  .catch(error => { $('notice').textContent = error.message; }).finally(() => setBusy(false));

// Offline page/bridge regression, including disabled DOM storage (as in the actual WebView).
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const elements = new Map();
function element(id) {
  if (!elements.has(id)) elements.set(id, {value:'',textContent:'',disabled:false,listeners:{},
    addEventListener(type, listener) { this.listeners[type] = listener; }});
  return elements.get(id);
}
const tokenButton = element('save-token'); tokenButton.dataset = {action:'auth/token'};
const sent = [];
let deny = false;
const bridge = {postMessage(text) {
  const call = JSON.parse(text); sent.push(call);
  let payload;
  if (call.action === 'host.service-write') payload = {job:'a'.repeat(24)};
  else if (call.text === 'api/status') payload = {user:{login:'octocat'},publicKey:'ssh-ed25519 public',repositoryRoot:'/repos',dependencies:{git:true,ssh:true}};
  else if (call.text === 'api/current-job') payload = {job:null};
  else payload = {state:'done',result:{message:'saved'}};
  const rejected = deny && call.action === 'host.service-write';
  queueMicrotask(() => bridge.onmessage({data:JSON.stringify({id:call.id,success:!rejected,text:rejected?'permission_denied':JSON.stringify(payload)})}));
}};
const context = {
  window:{taixuPlugin:bridge}, document:{getElementById:element,
    querySelectorAll:selector => selector === 'button' ? [tokenButton,element('refresh')] : [tokenButton]},
  setTimeout:(fn,ms) => { if (ms < 75000) queueMicrotask(fn); return 1; }, clearTimeout:() => {},
};
const source = fs.readFileSync(path.join(__dirname,'../../assets/plugins/github/payload/web/app.js'),'utf8');
vm.runInNewContext(source, context);
async function settle() { for (let i=0;i<8;i++) await new Promise(setImmediate); }
(async () => {
  await settle();
  assert.match(element('account').textContent,/octocat/);
  assert.equal(tokenButton.disabled,false);
  element('token').value = 'test-token-123';
  const first = tokenButton.listeners.click();
  const duplicate = tokenButton.listeners.click();
  assert.equal(element('token').value,'');
  await Promise.all([first,duplicate]); await settle();
  const writes = sent.filter(call => call.action === 'host.service-write');
  assert.equal(writes.length,1);
  assert.equal(writes[0].target,'service');
  assert.equal(JSON.parse(writes[0].text.split('\n')[1]).token,'test-token-123');
  assert.equal(writes[0].text.split('\n')[0],'api/auth/token');
  assert.match(element('result').textContent,/saved/);
  deny = true; element('token').value = 'second-test-token';
  await tokenButton.listeners.click(); await settle();
  assert.match(element('result').textContent,/permission_denied/);
  assert.equal(tokenButton.disabled,false);
  assert.equal(element('token').value,'');
  assert(!source.includes('sessionStorage'));
  console.log('GitHub Web: startup, frozen token body, duplicate click, job polling, denial and disabled storage passed.');
})().catch(error => {console.error(error);process.exitCode=1;});

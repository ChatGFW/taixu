const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../assets/plugins/github/payload/web/app.js'), 'utf8');
const service = {clock:0, job:null, duration:85000, writes:0};
function page() {
  let count = 0;
  const sent = [], nodes = new Map();
  function node(id) {
    if (!nodes.has(id)) nodes.set(id, {value:'', textContent:'', disabled:false, addEventListener(t,fn){this[t]=fn;}});
    return nodes.get(id);
  }
  const button = node('clone'); button.dataset = {action:'repo/clone'};
  node('repo').value = 'owner/repo'; node('protocol').value = 'https';
  const bridge = {postMessage(text) {
    const call = JSON.parse(text); sent.push(call); count++;
    let result;
    if (call.action === 'host.service-write') {
      service.writes++; service.job={id:service.writes.toString(16).padStart(24,'0'),started:service.clock};
      result={job:service.job.id};
    } else if (call.text === 'api/status') result={user:null,publicKey:'',dependencies:{},repositoryRoot:'/repos'};
    else if (call.text === 'api/current-job') result={job:service.job?.id || null};
    else result = service.clock-service.job.started < service.duration ? {state:'running'} : {state:'done',result:{message:'done'}};
    queueMicrotask(() => bridge.onmessage({data:JSON.stringify({id:call.id,success:count<=256,text:count<=256?JSON.stringify(result):'session_limit'})}));
  }};
  vm.runInNewContext(source, {
    window:{taixuPlugin:bridge}, document:{getElementById:node,querySelectorAll:s=>s==='button'?[button,node('refresh')]:[button]},
    setTimeout:(fn,ms)=>{if(ms<75000)queueMicrotask(()=>{service.clock+=ms;fn();});return 1;},clearTimeout(){}
  });
  return {node,button,sent};
}
async function settle(){for(let i=0;i<8;i++)await new Promise(setImmediate);}
(async()=>{
  const first=page();await settle();
  for(let i=0;i<3;i++)await first.button.click();
  assert.equal(service.writes,3);
  assert(first.sent.length<50, 'Three 85-second tasks must not exhaust the 256-request page quota');
  assert.match(first.node('result').textContent,/done/);
  for(let i=0;i<260;i++)await first.node('refresh').click();
  assert.equal(first.sent.length,240);
  assert.match(first.node('result').textContent,/配额即将用尽/);
  assert(!first.node('result').textContent.includes('session_limit'));
  service.duration=Infinity;service.job={id:'b'.repeat(24),started:service.clock};
  const reopened=page();await settle();
  assert(reopened.sent.some(call=>call.text==='api/current-job'));
  assert(reopened.sent.some(call=>call.text==='api/job/'+'b'.repeat(24)));
  assert.match(reopened.node('notice').textContent,/刷新本机状态/);
  assert(!reopened.node('notice').textContent.includes('重新打开页面查看'));
  assert.equal(service.writes,3,'Restoring an existing task must not resubmit a write');
  console.log('GitHub tasks: three slow tasks, quota guard, reopen recovery and no write replay passed.');
})().catch(error=>{console.error(error);process.exitCode=1;});

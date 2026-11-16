/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
'use strict';
// The production JSP script/body, vendored jQuery/UI and Chromium; only REST and translated labels are fixtures.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const {test, before, after} = require('node:test');
const {chromium} = require('playwright');
const ROOT = path.join(__dirname, '..');
const JSP = process.env.JOBS_JSP || path.join(ROOT, 'src/main/webapp/WEB-INF/jsp/admin/jobs.jsp');
const LABELS = {'admin.jobs.saveJob':'Save job','admin.jobs.save':'Save','admin.jobs.cancel':'Cancel','admin.jobs.addNew':'New job'};
let browser;
before(async () => { browser = await chromium.launch({headless:true,
  ...(process.env.PLAYWRIGHT_BROWSER_PATH ? {executablePath:process.env.PLAYWRIGHT_BROWSER_PATH} : {})}); });
after(async () => { if (browser) await browser.close(); });
function fixtureHtml() {
  const jsp = fs.readFileSync(JSP, 'utf8');
  const match = [...jsp.matchAll(/<script>\s*([\s\S]*?)<\/script>/gi)].find(x => x[1].includes('function cancelJob'));
  assert.ok(match, 'The production jobs script is missing');
  const translate = text => text.replace(/<fmt:message\s+key=['"]([^'"]+)['"]\s*\/>/g, (_,key)=>LABELS[key]||key);
  const script = translate(match[1]).replace(/<%=\s*request\.getContextPath\(\)\s*%>/g, '')
    .replace(/<%=\s*provider.getProviderNo\(\)\s*%>/g, '999998')
    .replace(/\$\{carlos:forJavaScript\(([^)]+)\)\}/g, (_,key)=>key);
  assert.ok(!/<%|\$\{/.test(script), 'Unrendered JSP expression in browser fixture');
  // These are the two server-generated numeric option loops; the native check also runs the actual JSP.
  const body = translate(jsp.match(/<body\b[^>]*>([\s\S]*?)<\/body>/i)[1]).replace(/<%[\s\S]*?%>/g, '');
  return `<!doctype html><html><head><link rel="stylesheet" href="/ui.css"><script src="/jquery.js"></script>
    <script src="/ui.js"></script><script>${script}</script></head><body>${body}</body></html>`;
}
const job = {id:41,name:'FAKE persisted job',description:'FAKE stored',oscarJobTypeId:7,providerNo:'999998',
  enabled:false,cronExpression:'0 * * * * *',status:'idle',updated:'2026-08-01T00:00:00Z'};
async function withPage(body) {
  const state = {mode:'success',loadMode:'success',saves:[],lists:0,errors:[],reply:null,loadReply:null};
  const json = (res,value,status=200) => {res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(value));};
  const assets = {'/jquery.js':'library/jquery/jquery-3.7.1.min.js','/ui.js':'library/jquery/jquery-ui-1.14.2.min.js','/ui.css':'library/jquery/jquery-ui.structure-1.14.2.min.css'};
  const server = http.createServer((req,res)=>{
    const url = new URL(req.url,'http://fixture');
    if (assets[url.pathname]) {res.writeHead(200,{'Content-Type':url.pathname.endsWith('css')?'text/css':'application/javascript'});res.end(fs.readFileSync(path.join(ROOT,'src/main/webapp',assets[url.pathname])));return;}
    if (url.pathname==='/') {res.writeHead(200,{'Content-Type':'text/html'});res.end(fixtureHtml());return;}
    if (url.pathname.endsWith('/types/all')) return json(res,{types:[{id:7,name:'FAKE disabled',currentlyValid:false}]});
    if (url.pathname.endsWith('/providers_json')) return json(res,{content:[{providerNo:'999998',lastName:'FAKE',firstName:'Provider'}]});
    if (url.pathname.endsWith('/jobs/all')) {state.lists++;return json(res,{jobs:[job]});}
    if (url.pathname.endsWith('/job/41')) {
      if (state.loadMode==='defer') {state.loadReply=()=>json(res,{jobs:[job]});return;}
      return json(res,{jobs:[job]},state.loadMode==='error'?503:200);
    }
    if (req.method==='POST' && /\/jobs\/(saveJob|saveCrontabExpression)$/.test(url.pathname)) {
      let data='';req.on('data',chunk=>data+=chunk);req.on('end',()=>{
        state.saves.push({path:url.pathname,fields:Object.fromEntries(new URLSearchParams(data))});
        const reply=()=>{
          if(state.mode==='error')return json(res,{},503);
          if(state.mode==='html'){res.writeHead(200,{'Content-Type':'text/html'});res.end('<html>FAKE sign in</html>');return;}
          const payload = {success:{jobs:[job]},wrong:{jobs:[{...job,id:42}]},empty:{},zero:{jobs:[{id:0}]},multiple:{jobs:[job,job]},null:null};
          return json(res,Object.hasOwn(payload,state.mode) ? payload[state.mode] : {jobs:[job]});
        };
        if(state.mode==='defer')state.reply=reply;else reply();
      });return;
    }
    res.writeHead(204).end();
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const context = await browser.newContext();const page = await context.newPage();
  page.on('pageerror',e=>state.errors.push(e.message));page.on('dialog',d=>{state.errors.push('Unexpected dialog');void d.dismiss();});
  try {
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.waitForFunction(()=>window.jQuery && jQuery.active===0 && jQuery('#new-job').hasClass('ui-dialog-content'));
    await page.evaluate(()=>{for(const name of ['minute','hour']){const select=document.getElementById(name);select.replaceChildren(...Array.from({length:name==='minute'?60:24},(_,i)=>new Option(String(i),String(i))));}});
    await body(page,state);
    assert.deepEqual(state.errors,[]);
  } finally {await context.close();server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
}
const dialog = (page,id)=>page.locator(`.ui-dialog:has(#${id})`);
const button = (page,id)=>dialog(page,id).getByRole('button',{name:id==='new-job'?'Save job':'Save',exact:true});
async function open(page,id) {
  await page.evaluate(id=>id==='new-job'?editJob(41):scheduleJob(41),id);
  await page.waitForFunction(id=>jQuery('#'+id).dialog('isOpen')&&jQuery.active===0,id);
}
async function posted(state) {for(let i=0;i<100&&state.saves.length===0;i++)await new Promise(r=>setTimeout(r,10));assert.equal(state.saves.length,1);}
for(const id of ['new-job','scheduleDialog']) {
  const label = id === 'new-job' ? 'JobDialog' : 'ScheduleDialog';
  test(`shouldKeep${label}Open_whileItsSaveIsPending`,async()=>withPage(async(page,state)=>{
    await open(page,id);state.mode='defer';await button(page,id).click();await posted(state);
    assert.equal(await page.evaluate(id=>jQuery('#'+id).dialog('isOpen'),id),true);
    assert.equal(await button(page,id).isDisabled(),true);
    await page.evaluate(id=>jQuery('#'+id).dialog('close'),id);
    assert.equal(await page.evaluate(id=>jQuery('#'+id).dialog('isOpen'),id),true);
    assert.equal(state.lists,1);
    await page.evaluate(id=>Object.values(jQuery('#'+id).dialog('option','buttons')).find(value=>/btn-primary/.test(value.class)).click.call(jQuery('#'+id)[0]),id);
    assert.equal(state.saves.length,1);
    state.mode='success';state.reply();
    await page.waitForFunction(id=>!jQuery('#'+id).dialog('isOpen')&&jQuery.active===0,id);
    assert.equal(state.lists,2);
    assert.equal(state.saves[0].fields[id==='new-job'?'job.id':'scheduleJobId'],'41');
  }));
  test(`shouldRetain${label}Entries_afterRefusalAndAllowRetry`,async()=>withPage(async(page,state)=>{
    await open(page,id);
    if(id==='new-job')await page.locator('#jobName').fill('FAKE changed job');
    state.mode='error';await button(page,id).click();await posted(state);
    await page.waitForFunction(()=>jQuery.active===0);
    assert.equal(await page.evaluate(id=>jQuery('#'+id).dialog('isOpen'),id),true);
    assert.match(await page.locator('#'+id+' .save-error').innerText(),/jobsSaveErrorLabel/);
    assert.equal(await button(page,id).isDisabled(),false);
    if(id==='new-job')assert.equal(await page.locator('#jobName').inputValue(),'FAKE changed job');
    else assert.equal(await page.locator('#hour').isDisabled(),true);
    state.mode='success';await button(page,id).click();
    await page.waitForFunction(id=>!jQuery('#'+id).dialog('isOpen')&&jQuery.active===0,id);
    assert.equal(state.saves.length,2);
  }));
}
for(const mode of ['html','wrong','empty','zero','multiple','null']) {
  test(`shouldKeepTheEditorOpen_for${mode[0].toUpperCase()+mode.slice(1)}Acknowledgement`,async()=>withPage(async(page,state)=>{
    await open(page,'new-job');state.mode=mode;await button(page,'new-job').click();await posted(state);
    await page.waitForFunction(()=>jQuery.active===0);
    assert.equal(await page.evaluate(()=>jQuery('#new-job').dialog('isOpen')),true);
    assert.match(await page.locator('#new-job .save-error').innerText(),/jobsSaveErrorLabel/);
    assert.equal(state.lists,1);
  }));
}
test('shouldBlockBlindCreationRetry_whenTheInsertCannotBeConfirmed',async()=>withPage(async(page,state)=>{
  await page.evaluate(()=>addNewJob());await page.locator('#jobName').fill('FAKE new draft');await page.locator('#jobType').selectOption('7');await page.locator('#jobProvider').selectOption('999998');
  state.mode='error';await button(page,'new-job').click();await posted(state);await page.waitForFunction(()=>jQuery.active===0);
  assert.equal(await page.locator('#jobName').inputValue(),'FAKE new draft');assert.equal(await button(page,'new-job').isDisabled(),true);
  assert.match(await page.locator('#new-job .save-error').innerText(),/jobsSaveUnconfirmedLabel/);
  await dialog(page,'new-job').getByRole('button',{name:'Cancel',exact:true}).click();await page.evaluate(()=>addNewJob());
  assert.equal(await button(page,'new-job').isDisabled(),true);assert.equal(state.saves.length,1);
}));
test('shouldIgnoreAnOldEditorLoad_afterOpeningANewDraft',async()=>withPage(async(page,state)=>{
  state.loadMode='defer';await page.evaluate(()=>editJob(41));
  await page.waitForFunction(()=>jQuery.active>0);
  assert.equal(await button(page,'new-job').isDisabled(),true);
  await dialog(page,'new-job').getByRole('button',{name:'Cancel',exact:true}).click();await page.evaluate(()=>addNewJob());await page.locator('#jobName').fill('FAKE new draft');
  assert.ok(state.loadReply);state.loadReply();await page.waitForFunction(()=>jQuery.active===0);
  assert.equal(await page.locator('#jobName').inputValue(),'FAKE new draft');assert.equal(await page.locator('#jobId').inputValue(),'0');
}));

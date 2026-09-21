/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const {setTimeout: delay} = require('node:timers/promises');
const editor = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/eform-assets/editControl2.js'), 'utf8');
const code = editor.slice(editor.indexOf('// RTL measurement batches'), editor.indexOf('// end lab grid //'));

// Minimal document model for queue/transport tests. Native selection/range behavior
// is covered separately by the Chromium fixture; no browser API is replaced there.
class Node {
  constructor(document, tag, type = 1) { this.ownerDocument = document; this.tagName = tag; this.nodeType = type; this.childNodes = []; this.parentNode = null; this.text = ''; }
  appendChild(node) { return this.insertBefore(node, null); }
  insertBefore(node, before) {
    if (node.nodeType === 11) { for (const child of [...node.childNodes]) this.insertBefore(child, before); return node; }
    if (node.parentNode) node.parentNode.removeChild(node);
    const i = before === null ? this.childNodes.length : this.childNodes.indexOf(before);
    assert(i >= 0); this.childNodes.splice(i, 0, node); node.parentNode = this; return node;
  }
  removeChild(node) { const i = this.childNodes.indexOf(node); assert(i >= 0); this.childNodes.splice(i, 1); node.parentNode = null; }
  contains(node) { return node === this || this.childNodes.some(child => child.contains(node)); }
  setAttribute(name, value) { this[name] = value; }
  get textContent() { return this.nodeType === 8 ? '' : this.text + this.childNodes.map(child => child.textContent).join(''); }
  set textContent(text) { for (const child of [...this.childNodes]) this.removeChild(child); this.text = String(text); }
}
function documentFixture() {
  const listeners = {};
  const document = {
    listeners,
    createElement(tag) { const node = new Node(this, tag); node.style = {}; return node; },
    createComment() { return new Node(this, '', 8); },
    createDocumentFragment() { return new Node(this, '', 11); },
    getSelection() { return null; },
    addEventListener(event, listener) { listeners[event] = listener; },
    getElementById(id) {
      function find(node) { if (node.id === id) return node; for (const child of node.childNodes) { const found = find(child); if (found) return found; } return null; }
      return find(this.body);
    },
  };
  document.body = document.createElement('body');
  return document;
}
function setup() {
  const document = documentFixture(); const contentDocument = documentFixture();
  const frame = document.createElement('iframe'); frame.id = 'edit'; frame.contentDocument = contentDocument; document.body.appendChild(frame);
  const patient = document.createElement('input'); patient.id = 'demographicNo'; patient.value = '17'; document.body.appendChild(patient);
  const requests = []; const alerts = []; let dirty = 0;
  class Xhr {
    constructor() { requests.push(this); this.headers = {}; }
    open(method, url, async) { Object.assign(this, {method, url, async}); }
    setRequestHeader(name, value) { this.headers[name] = value; }
    send(body) { this.body = JSON.parse(body); }
    respond(measurements, status = 200) { this.status = status; this.responseText = JSON.stringify({measurements}); this.onload(); }
  }
  const window = {location:{href:'https://localhost/carlos/eform/efmshowform_data?demographic_no=99'}, setTimeout};
  const context = vm.createContext({window, document, XMLHttpRequest:Xhr, URL, alert:text=>alerts.push(text), cfg_editorname:'edit', gup:()=>'', setDirtyFlag:()=>dirty++});
  vm.runInContext(code, context);
  return {context, document, contentDocument, frame, patient, requests, alerts, dirty:()=>dirty};
}
function row(type, value, date = '2026-09-12', demographicId = 17) { return {type, dataField:value, dateObserved:date, demographicId}; }
function notice(fixture) { return fixture.document.getElementById('rtl-measurement-status').textContent; }

test('same-click types share one async request bound to the letter patient; insertion preserves requested order', async () => {
  const f = setup();
  const first = f.context.getMeasures('BP', 2); const second = f.context.getMeasures('WT', 1);
  assert.equal(f.context.measurementHistoryStillLoading(), true);
  assert.throws(()=>f.context.assertMeasurementHistoryReady(), /still loading/);
  await delay(5);
  assert.equal(f.requests.length, 1);
  const request = f.requests[0];
  assert.deepEqual([request.method, request.url, request.async], ['POST','/carlos/ws/rs/measurements/17',true]);
  assert.deepEqual(request.body.types, ['BP','WT']);
  request.respond({WT:[row('WT','70')], BP:[row('BP','120/80','2026-08-01'),row('BP','130/85')]});
  const histories = await Promise.all([first, second]);
  assert.deepEqual(Array.from(histories[0].values), ['130/85','120/80']);
  assert.match(f.contentDocument.body.textContent, /^BP: 130\/85\(2026\/9\); 120\/80\(2026\/8\); WT: 70/);
  assert.equal(f.context.measurementHistoryStillLoading(), false);
  assert.equal(f.dirty(), 1);
  assert.equal(f.contentDocument.body.childNodes.some(node=>node.nodeType===8), false);
});

test('later clicks are queued and results stay before text typed after each marker', async () => {
  const f = setup(); const one = f.context.getMeasures('BP',1); await delay(5);
  const typed = f.contentDocument.createElement('span'); typed.textContent = 'newly typed'; f.contentDocument.body.appendChild(typed);
  const two = f.context.getMeasures('WT',1); await delay(5);
  assert.equal(f.requests.length,1);
  f.requests[0].respond({BP:[row('BP','125/80')]}); await one;
  assert.equal(f.requests.length,2);
  assert.equal(f.context.measurementHistoryStillLoading(),true);
  f.requests[1].respond({WT:[row('WT','71')]}); await two;
  assert.match(f.contentDocument.body.textContent,/BP: 125\/80.*newly typedWT: 71/);
});

test('installed REST epoch dates sort and display alongside ISO dates without stale values', async () => {
  const f = setup(); const p = f.context.getMeasures('ALT',2); await delay(5);
  f.requests[0].respond({ALT:[row('ALT','older',new Date(2025,11,1).getTime()),row('ALT','newer',new Date(2026,8,12).getTime())]});
  assert.deepEqual(Array.from((await p).values),['newer','older']);
  assert.match(f.contentDocument.body.textContent,/newer\(2026\/9\).*older\(2025\/12\)/);
  assert.equal(f.context.measurementMonth(null),'date unavailable');
});

test('after a failure, retry waits for queued work to settle and then clears the warning on success', async () => {
  const f = setup(); const one=f.context.getMeasures('BP',1); await delay(5);
  const two=f.context.getMeasures('WT',1); await delay(5);
  f.requests[0].onerror(); await one;
  assert.equal((await f.context.getMeasures('BP',1)).failed,true);
  assert.equal(f.requests.length,2); // The early retry did not start another request.
  assert.match(notice(f),/Wait for the remaining loads/);
  f.requests[1].respond({WT:[row('WT','70')]}); await two;
  assert.match(notice(f),/not inserted/);
  const retry=f.context.getMeasures('BP',1); await delay(5);
  f.requests[2].respond({BP:[row('BP','120/80')]}); await retry;
  assert.equal(notice(f),'');
});

test('a blocked legacy serializer restores the unload warning even when loading fails', async () => {
  const f=setup(); const p=f.context.getMeasures('BP',1);
  f.context.window.needToConfirm=false; // Legacy saveRTL resets before reading editor HTML.
  assert.throws(()=>f.context.assertMeasurementHistoryReady(),/still loading/);
  assert.equal(f.context.window.needToConfirm,true);
  await delay(5);f.requests[0].onerror();await p;
  assert.equal(f.context.window.needToConfirm,true);
});

test('a setup failure stays visible after an already-running batch succeeds', async () => {
  const f=setup(); const first=f.context.getMeasures('BP',1);await delay(5);
  f.frame.contentDocument=null;
  assert.equal((await f.context.getMeasures('WT',1)).failed,true);
  f.frame.contentDocument=f.contentDocument;
  f.requests[0].respond({BP:[row('BP','120/80')]});await first;
  assert.match(notice(f),/not inserted/);
  const retry=f.context.getMeasures('WT',1);await delay(5);
  f.requests[1].respond({WT:[row('WT','70')]});await retry;
  assert.equal(notice(f),'');
});

for (const failure of ['timeout','network','HTTP','invalid JSON','wrong patient']) {
  test(failure+' settles every caller, removes placeholders and makes failure visible', async () => {
    const f = setup(); const p = f.context.getMeasures('BP',1); await delay(5); const request=f.requests[0];
    if (failure==='timeout') { assert.equal(request.timeout,15000); request.ontimeout(); }
    else if (failure==='network') request.onerror();
    else if (failure==='HTTP') request.respond({},403);
    else if (failure==='invalid JSON') { request.status=200; request.responseText='<html>Login</html>'; request.onload(); }
    else request.respond({BP:[row('BP','120/80','2026-09-12',99)]});
    assert.equal((await p).failed,true);
    assert.equal(f.context.measurementHistoryStillLoading(),false);
    assert.equal(f.contentDocument.body.textContent,'');
    assert.equal(f.contentDocument.body.childNodes.length,0);
    assert.match(notice(f),/not inserted/);
  });
}

for (const change of ['deleted marker','new document','different patient']) {
  test('a '+change+' prevents a stale response from entering the current letter', async () => {
    const f=setup(); const p=f.context.getMeasures('BP',1); await delay(5);
    if (change==='deleted marker') f.contentDocument.body.textContent='replacement text';
    if (change==='new document') f.frame.contentDocument=documentFixture();
    if (change==='different patient') f.patient.value='99';
    f.requests[0].respond({BP:[row('BP','120/80')]});
    assert.equal((await p).failed,true);
    assert.doesNotMatch(f.frame.contentDocument.body.textContent,/120\/80/);
    assert.equal(f.context.measurementHistoryStillLoading(),false);
  });
}

test('empty data is successful and values containing markup remain literal text', async () => {
  const f=setup(); const p=f.context.getMeasures('BP',1); await delay(5); f.requests[0].respond({});
  assert.equal((await p).values.length,0); assert.equal(f.dirty(),0); assert.equal(notice(f),'');
  const q=f.context.getMeasures('BP',1); await delay(5); f.requests[1].respond({BP:[row('BP','<img src=x onerror=alert(1)>')]}); await q;
  assert.match(f.contentDocument.body.textContent,/<img src=x/);
  function hasImage(node) { return node.tagName==='img'||node.childNodes.some(hasImage); }
  assert.equal(hasImage(f.contentDocument.body),false);
});

test('legacy Print controls and submit events are blocked before inline handlers', async () => {
  const f=setup(); const p=f.context.getMeasures('BP',1); let blocked=0;
  const events=[];
  for (const controlName of ['SubmitButton','PrintButton','PrintSaveButton','PrintSubmitButton','pdfButton','pdfSaveButton']) {
    for (const attribute of ['name','id']) {
      const event={target:{closest:()=>({[attribute]:controlName,type:'button'})},preventDefault:()=>blocked++,stopImmediatePropagation:()=>blocked++};
      f.context.window.needToConfirm=false;
      events.push(event); f.document.listeners.click(event);
      assert.equal(f.context.window.needToConfirm,true);
    }
  }
  f.context.window.needToConfirm=false;
  f.document.listeners.submit(events[0]); assert.equal(blocked,26);
  assert.equal(f.context.window.needToConfirm,true);
  await delay(5); f.requests[0].respond({}); await p;
  events.forEach(event=>f.document.listeners.click(event)); f.document.listeners.submit(events[0]); assert.equal(blocked,26);
});

test('the floating toolbar blocks before its existing save/download workflow starts', async () => {
  const f=setup();
  const source=fs.readFileSync(path.join(__dirname,'../src/main/webapp/eform/eformFloatingToolbar/eform_floating_toolbar.js'),'utf8');
  const start=source.indexOf('function editorStillLoading() {'); const end=source.indexOf('\n/**',start);
  f.context.window.measurementHistoryStillLoading=f.context.measurementHistoryStillLoading;
  vm.runInContext('let editorLoadingBlockCount=0;\n'+source.slice(start,end),f.context);
  const p=f.context.getMeasures('BP',1); f.context.window.needToConfirm=false;
  assert.equal(f.context.editorStillLoading(),true); assert.equal(f.context.window.needToConfirm,true);
  await delay(5); f.requests[0].respond({}); await p; assert.equal(f.context.editorStillLoading(),false);
});

for (const throws of [false,true]) {
  test('successful insertion stays dirty and successful when legacy callback '+(throws?'throws':'clears the flag'), async () => {
    const f=setup();
    f.context.setDirtyFlag=()=>{
      f.context.window.needToConfirm=false;
      if (throws) throw new Error('Legacy callback requires an event');
    };
    const p=f.context.getMeasures('BP',1); await delay(5);
    f.requests[0].respond({BP:[row('BP','120/80')]});
    assert.equal((await p).failed,undefined);
    assert.equal(f.context.window.needToConfirm,true);
    assert.equal(f.context.measurementHistoryStillLoading(),false);
    assert.equal(notice(f),'');
    assert.equal((f.contentDocument.body.textContent.match(/120\/80/g)||[]).length,1);
  });
}

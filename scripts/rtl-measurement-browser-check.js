/* SPDX-License-Identifier: GPL-2.0-or-later */
// Isolated native Chromium regression: no running server or patient credentials.
// NODE_PATH=<playwright node_modules> CHROME_PATH=<chrome> node scripts/rtl-measurement-browser-check.js
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const web = path.resolve(__dirname,'../src/main/webapp');
const assets = {
  'jquery.js': '/library/jquery/jquery-3.7.1.min.js',
  'purify.js': '/library/dompurify/purify.min.js',
  'cache.js': '/library/eforms/APCache.js',
  'image.js': '/library/eforms/imageControl.js',
  'editor.js': '/WEB-INF/eform-assets/editControl2.js',
  'blank.rtl': '/WEB-INF/eform-assets/blank.rtl',
};
const row = (type,value) => ({type,dataField:value,demographicId:17,dateObserved:Date.UTC(2026,8,12)});

async function run() {
  const browser = await chromium.launch({executablePath:process.env.CHROME_PATH || undefined,
    args:['--no-sandbox','--disable-dev-shm-usage'],headless:true});
  try {
    const page = await browser.newPage({timezoneId:'America/Vancouver'}); const errors=[]; const pending=[];
    page.on('pageerror',error=>errors.push(error.message));
    page.on('dialog',dialog=>dialog.accept());
    await page.route('**/*',async route=>{
      const url = new URL(route.request().url());
      if (url.pathname==='/carlos/ws/rs/measurements/17') {pending.push(route); return;}
      const name=url.pathname.split('/').pop();
      if (name==='fixture') return route.fulfill({contentType:'text/html',body:`<!doctype html><html><head>
<script src="jquery.js"></script><script src="purify.js"></script><script src="cache.js"></script><script src="image.js"></script></head>
<body><form name="RichTextLetter" action="/carlos/eform/addEForm?demographic_no=17"><input id="demographicNo" value="17" type="hidden"><input id="faxEForm" value="false" type="hidden"><input id="subject" value="Fixture letter" type="hidden">
<script src="editor.js"></script><script>cfg_layout='[edit-area]';cfg_filesrc='';insertEditControl();document.getElementById('edit').src='blank.rtl';window.prints=0;</script>
<button type="button" name="PrintSaveButton" onclick="window.prints++">Print and save</button>
<button type="button" name="PrintSubmitButton" onclick="window.prints++">Print and submit</button>
<button type="button" id="PrintSubmitButton" onclick="window.prints++">Print and submit by id</button></form></body></html>`});
      if (assets[name]) return route.fulfill({body:fs.readFileSync(web+assets[name]),contentType:name==='blank.rtl'?'text/html':'text/javascript'});
      return route.abort();
    });
    await page.goto('http://127.0.0.1:2091/carlos/eform/fixture');
    await page.waitForFunction(()=>document.getElementById('edit').contentDocument.designMode==='on');
    assert.deepEqual(await page.evaluate(()=>[
      measurementMonth(Date.parse('2026-09-30T23:30:00-07:00')),
      measurementMonth(Date.parse('2026-12-31T23:30:00-08:00')),
      measurementMonth('2026-09-30'),
    ]),['2026/9','2026/12','2026/9']);
    assert.equal(await page.evaluate(()=>measurementDateTime('2026-10-01')
      > measurementDateTime(Date.parse('2026-09-30T23:30:00-07:00'))),true);
    // Load after DOMContentLoaded: toolbar initialization needs a server-rendered form,
    // while its actual action functions can be exercised with this isolated editor.
    await page.addScriptTag({path:web+'/eform/eformFloatingToolbar/eform_floating_toolbar.js'});
    async function begin(types) {
      await page.evaluate(types=>{ // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- types are fixed BP/WT arrays below, structured-cloned into a literal function; every network request is fulfilled locally or aborted by the fixture route.
        const doc=document.getElementById('edit').contentDocument;
        doc.body.textContent='BEFORE AFTER';
        const range=doc.createRange(); range.setStart(doc.body.firstChild,7);range.collapse(true);
        const selection=doc.getSelection();selection.removeAllRanges();selection.addRange(range);
        window.loaded=Promise.all(types.map(type=>getMeasures(type,1)));
      },types);
      await page.waitForTimeout(10);
    }
    async function respond(measurements) {
      // Waiting on a route allows the real XHR event handlers to drive the queue.
      for (let n=0;n<100&&!pending.length;n++) await page.waitForTimeout(10);
      assert(pending.length,'measurement request was issued');
      const route=pending.shift();assert.equal(route.request().method(),'POST');
      await route.fulfill({contentType:'application/json',body:JSON.stringify({measurements})});
      return page.evaluate(()=>window.loaded);
    }
    await begin(['BP','WT']);
    assert.equal(await page.evaluate(()=>{
      window.exportCount=0;
      window.saveAs=(blob,name)=>{window.exportCount++;window.exportedLetter={blob,name};};
      try {doExport();return false;} catch(error){return /still loading/.test(error.message);}
    }),true);
    assert.equal(await page.evaluate(()=>window.exportCount),0);
    await page.locator('[name=PrintSaveButton]').click();
    await page.locator('[name=PrintSubmitButton]').click();
    await page.locator('#PrintSubmitButton').click();
    assert.equal(await page.evaluate(()=>{
      window.saveRTL=()=>editControlContents('edit');
      try {submitFaxButton();return false;} catch(error){return /still loading/.test(error.message);}
    }),true);
    assert.equal(await page.locator('#faxEForm').inputValue(),'false');
    assert.equal(await page.evaluate(()=>window.prints),0);
    assert.equal(await page.evaluate(()=>{
      window.formPrint=()=>window.prints++;
      remotePrint();
      loadDefaultTemplate(); // Internal template reads must not raise the save gate.
      window.needToConfirm=false; // Shipped legacy saveRTL resets before serialization.
      try {editControlContents('edit');return false;} catch(error){return /still loading/.test(error.message);}
    }),true);
    assert.equal(await page.evaluate(()=>window.needToConfirm),true);
    assert.equal(await page.evaluate(()=>window.prints),0);
    // Move the live caret and type while the response is withheld.
    const body=page.frameLocator('#edit').locator('body');
    await body.click();await body.press('Control+End');await body.press('End');await body.pressSequentially(' TYPED');
    await respond({WT:[row('WT','70')],BP:[row('BP','120/80')]});
    assert.equal(await body.textContent(),'BEFORE BP: 120/80(2026/9); WT: 70(2026/9); AFTER TYPED');
    const saved=await page.evaluate(()=>editControlContents('edit'));
    assert(!saved.includes('RTL measurement insertion'));
    const exported=await page.evaluate(async()=>{
      doExport();return {count:window.exportCount,name:window.exportedLetter.name,html:await window.exportedLetter.blob.text()};
    });
    assert.equal(exported.count,1);assert.equal(exported.name,'Fixture letter.rtl');
    assert.match(exported.html,/120\/80/);assert(!exported.html.includes('RTL measurement insertion'));
    assert.equal(await page.locator('#faxEForm').inputValue(),'false');
    assert.equal(await page.evaluate(()=>{
      window.saveRTL=()=>{throw new Error('Serialization failed');};
      try {submitFaxButton();return false;} catch(error){return error.message==='Serialization failed';}
    }),true);
    assert.equal(await page.locator('#faxEForm').inputValue(),'false');
    await page.locator('[name=PrintSaveButton]').click();assert.equal(await page.evaluate(()=>window.prints),1);
    await body.pressSequentially(' MORE');assert.match(await body.textContent(),/TYPED MORE$/);

    await begin(['BP']);
    await page.evaluate(()=>{
      cache.put('fixtureReplacement','Replacement template');
      seteditControlContents('edit','<p>##fixtureReplacement##</p>');
      parseTemplate();
    });
    assert.equal((await respond({BP:[row('BP','120/80')]}))[0].failed,true);
    assert.equal(await body.textContent(),'Replacement template');
    await page.locator('#rtl-measurement-status').waitFor({state:'visible'});

    await begin(['BP']);
    await respond({BP:[row('BP','<img src=x onerror=alert(1)>')]});
    assert.equal(await body.locator('img').count(),0);
    assert.match(await body.textContent(),/<img src=x onerror=alert\(1\)>/);
    await page.evaluate(()=>{
      window.saveRTL=()=>editControlContents('edit');
      const originalTimer=window.setTimeout;
      window.setTimeout=callback=>{window.deferredFaxSubmit=callback;};
      try {submitFaxButton();} finally {window.setTimeout=originalTimer;}
    });
    await begin(['BP']);
    assert.equal(await page.evaluate(()=>{
      try {window.deferredFaxSubmit();return false;} catch(error){return /still loading/.test(error.message);}
    }),true);
    assert.equal(await page.locator('#faxEForm').inputValue(),'false');
    assert.equal(await page.evaluate(()=>window.needToConfirm),true);
    await respond({});
    await page.evaluate(()=>{
      window.faxSubmits=0;
      document.RichTextLetter.submit=()=>window.faxSubmits++;
      window.saveRTL=()=>{window.faxLetter=editControlContents('edit');};
      submitFaxButton();
      document.getElementById('edit').contentDocument.body.append(' LATE EDIT');
    });
    await page.waitForFunction(()=>window.faxSubmits===1);
    assert.equal(await page.locator('#faxEForm').inputValue(),'true');
    assert.match(await page.evaluate(()=>window.faxLetter),/LATE EDIT/);
    assert.deepEqual(errors,[]);
    console.log('PASS: native Range insertion, request ordering, live caret/typing, serializer and legacy/toolbar print gates, marker cleanup, replaced template, literal measurement text. Chromium '+browser.version());
  } finally {await browser.close();}
}
run().catch(error=>{console.error(error);process.exitCode=1;});

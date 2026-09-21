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
    const page = await browser.newPage(); const errors=[]; const pending=[];
    page.on('pageerror',error=>errors.push(error.message));
    page.on('dialog',dialog=>dialog.accept());
    await page.route('**/*',async route=>{
      const url = new URL(route.request().url());
      if (url.pathname==='/carlos/ws/rs/measurements/17') {pending.push(route); return;}
      const name=url.pathname.split('/').pop();
      if (name==='fixture') return route.fulfill({contentType:'text/html',body:`<!doctype html><html><head>
<script src="jquery.js"></script><script src="purify.js"></script><script src="cache.js"></script><script src="image.js"></script></head>
<body><form action="/carlos/eform/addEForm?demographic_no=17"><input id="demographicNo" value="17" type="hidden">
<script src="editor.js"></script><script>cfg_layout='[edit-area]';cfg_filesrc='';insertEditControl();document.getElementById('edit').src='blank.rtl';window.prints=0;</script>
<button type="button" name="PrintSaveButton" onclick="window.prints++">Print and save</button></form></body></html>`});
      if (assets[name]) return route.fulfill({body:fs.readFileSync(web+assets[name]),contentType:name==='blank.rtl'?'text/html':'text/javascript'});
      return route.abort();
    });
    await page.goto('http://127.0.0.1:2091/carlos/eform/fixture');
    await page.waitForFunction(()=>document.getElementById('edit').contentDocument.designMode==='on');
    // Load after DOMContentLoaded: toolbar initialization needs a server-rendered form,
    // while its actual action functions can be exercised with this isolated editor.
    await page.addScriptTag({path:web+'/eform/eformFloatingToolbar/eform_floating_toolbar.js'});
    async function begin(types) {
      await page.evaluate(types=>{
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
    await page.locator('[name=PrintSaveButton]').click();
    assert.equal(await page.evaluate(()=>window.prints),0);
    assert.equal(await page.evaluate(()=>{
      window.formPrint=()=>window.prints++;
      remotePrint();
      try {editControlContents('edit');return false;} catch(error){return /still loading/.test(error.message);}
    }),true);
    assert.equal(await page.evaluate(()=>window.prints),0);
    // Move the live caret and type while the response is withheld.
    const body=page.frameLocator('#edit').locator('body');
    await body.click();await body.press('Control+End');await body.press('End');await body.pressSequentially(' TYPED');
    await respond({WT:[row('WT','70')],BP:[row('BP','120/80')]});
    assert.equal(await body.textContent(),'BEFORE BP: 120/80(2026/9); WT: 70(2026/9); AFTER TYPED');
    const saved=await page.evaluate(()=>editControlContents('edit'));
    assert(!saved.includes('RTL measurement insertion'));
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
    assert.deepEqual(errors,[]);
    console.log('PASS: native Range insertion, request ordering, live caret/typing, serializer and legacy/toolbar print gates, marker cleanup, replaced template, literal measurement text. Chromium '+browser.version());
  } finally {await browser.close();}
}
run().catch(error=>{console.error(error);process.exitCode=1;});

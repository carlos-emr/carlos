/* SPDX-License-Identifier: GPL-2.0-or-later */
// Isolated native Chromium regression: no running server or patient credentials.
// NODE_PATH=<playwright node_modules> CHROME_PATH=<chrome> node scripts/rtl-measurement-browser-check.js
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {launchBrowser} = require('./lib/playwright-harness');
const web = path.resolve(__dirname,'../src/main/webapp');
const assets = {
  'jquery.js': '/library/jquery/jquery-3.7.1.min.js',
  'purify.js': '/library/dompurify/purify.min.js',
  'cache.js': '/library/eforms/APCache.js',
  'image.js': '/library/eforms/imageControl.js',
  'editor.js': '/WEB-INF/eform-assets/editControl2.js',
  'blank.rtl': '/WEB-INF/eform-assets/blank.rtl',
};
const blankHtml = fs.readFileSync(web+assets['blank.rtl'],'utf8');
// nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- fixed repository HTML plus a literal local print spy; no request or external value enters this script.
const blankFixture = blankHtml.replace('</head>', '<script>window.print=()=>parent.delayedFramePrints++;</script></head>');
const row = (type,value) => ({type,dataField:value,demographicId:17,dateObserved:Date.UTC(2026,8,12)});

async function run() {
  const browser = await launchBrowser({chromePath:process.env.CHROME_PATH || process.env.CHROMIUM_PATH || '',headless:true});
  try {
    const page = await browser.newPage({timezoneId:'America/Vancouver'}); const errors=[]; const pending=[]; const templateRequests=[]; const catalogRequests=[];
    page.on('pageerror',error=>errors.push(error.message));
    page.on('dialog',dialog=>dialog.accept());
    await page.route('**/*',async route=>{
      const url = new URL(route.request().url());
      if (url.pathname==='/carlos/ws/rs/measurements/17') {pending.push(route); return;}
      const name=url.pathname.split('/').pop();
      if (name==='efmformrtl_templates') {catalogRequests.push(route);return;}
      if (name==='efmformapconfig_lookup') {templateRequests.push(route);return;}
      if (name==='fixture') return route.fulfill({contentType:'text/html',body:`<!doctype html><html><head>
<script src="jquery.js"></script><script src="purify.js"></script><script src="cache.js"></script><script src="image.js"></script></head>
<body><iframe id="unrelated-frame" srcdoc="&lt;p&gt;Other frame&lt;/p&gt;" hidden></iframe><form name="RichTextLetter" action="/carlos/eform/addEForm?demographic_no=17"><input id="demographicNo" value="17" type="hidden"><input id="faxEForm" value="false" type="hidden"><input id="subject" value="Fixture letter" type="hidden">
<input id="hasValidRecipient" value="true" type="hidden"><input id="emailConsentStatus" value="Unknown" type="hidden"><input id="emailConsentName" value="Fixture" type="hidden">
<textarea id="Letter" hidden>&lt;p&gt;Saved fixture letter&lt;/p&gt;</textarea><div id="oscar-spinner-screen"></div><div id="oscar-spinner"></div><div id="control1"></div><div id="control2"></div><div id="control3"></div><div id="control4"></div><select id="template"><option value="">Templates</option><option value="default.rtl">Default</option><option value="async.rtl">Async</option><option value="failure.rtl">Failure</option></select>
<script>window.delayedPrints=0;window.delayedFramePrints=0;window.print=()=>window.delayedPrints++;window.faxSubmits=0;document.RichTextLetter.submit=()=>{window.faxSubmits++;window.submittedLetter=document.getElementById('Letter').value;window.submittedPrint=document.getElementById('printHolder')?.value;window.submittedSkipSave=document.getElementById('saveHolder')?.value;};window.maximize=()=>{};window.updateAttached=()=>{};window.setDirtyFlag=()=>window.needToConfirm=true;</script><script src="editor.js"></script><script>cfg_layout='[edit-area]';cfg_filesrc='';insertEditControl();document.getElementById('edit').src='blank.rtl';window.prints=0;</script>
<button type="button" name="PrintSaveButton" onclick="window.prints++">Print and save</button>
<button type="button" name="PrintSubmitButton" onclick="window.prints++">Print and submit</button>
<button type="button" id="PrintSubmitButton" onclick="window.prints++">Print and submit by id</button></form></body></html>`});
      if (assets[name]) return route.fulfill({body:name==='blank.rtl'?blankFixture:fs.readFileSync(web+assets[name]),contentType:name==='blank.rtl'?'text/html':'text/javascript'});
      if (name==='async.rtl' || name==='failure.rtl') return route.fulfill({contentType:'text/html',body:'<!doctype html><html><body>##'+(name==='async.rtl'?'asyncField':'failureField')+'##</body></html>'});
      if (name==='default.rtl') return route.fulfill({contentType:'text/html',body:'<!doctype html><html><body>DEFAULT CONTENT</body></html>'});
      return route.abort();
    });
    await page.goto('http://127.0.0.1:2091/carlos/eform/fixture');
    await page.waitForFunction(()=>document.getElementById('edit').contentDocument.designMode==='on');
    assert.equal((await page.evaluate(()=>getMeasures('BP',1))).failed,true);
    assert.equal(pending.length,0);
    await page.evaluate(()=>Start());
    for (let n=0;n<100&&!catalogRequests.length;n++) await page.waitForTimeout(10);
    assert.equal(catalogRequests.length,1);
    assert.equal((await page.evaluate(()=>getMeasures('BP',1))).failed,true);
    assert.equal(await page.frameLocator('#edit').locator('body').textContent(),'Saved fixture letter');
    await catalogRequests.shift().fulfill({contentType:'text/html',body:'<option value="">Templates</option><option value="default.rtl">Default</option><option value="async.rtl">Async</option><option value="failure.rtl">Failure</option><option value="blank.rtl">Blank</option>'});
    await page.waitForFunction(()=>!measurementHistoryStillLoading());
    assert.deepEqual(await page.evaluate(()=>[
      measurementMonth(Date.parse('2026-09-30T23:30:00-07:00')),
      measurementMonth(Date.parse('2026-12-31T23:30:00-08:00')),
      measurementMonth('2026-09-30'),
      measurementMonth('2026-10-01T01:30:00Z'),
      measurementMonth('2027-01-01T01:30:00Z'),
    ]),['2026/9','2026/12','2026/9','2026/9','2026/12']);
    assert.equal(await page.evaluate(()=>measurementDateTime('2026-10-01')
      > measurementDateTime(Date.parse('2026-09-30T23:30:00-07:00'))),true);
    // Load after DOMContentLoaded: toolbar initialization needs a server-rendered form,
    // while its actual action functions can be exercised with this isolated editor.
    await page.addScriptTag({path:web+'/eform/eformFloatingToolbar/eform_floating_toolbar.js'});
    await page.addScriptTag({path:web+'/library/eforms/printControl.js'});
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
    await page.evaluate(()=>{
      window.delayedPrint=window.print.bind(window);
      window.initialFrameGuard=document.getElementById('edit').contentWindow.print;
      window.delayedFramePrint=window.initialFrameGuard.bind(document.getElementById('edit').contentWindow);
    });
    await begin(['BP','WT']);
    await page.evaluate(()=>{window.delayedPrint();window.delayedFramePrint();});
    assert.deepEqual(await page.evaluate(()=>[window.delayedPrints,window.delayedFramePrints]),[0,0]);
    assert.equal(await page.evaluate(()=>{
      window.originalPrompt=window.prompt;window.consentPrompts=0;
      window.prompt=()=>{window.consentPrompts++;return 'No';};
      remoteEmail();return window.consentPrompts;
    }),0);
    assert.equal(await page.locator('#emailAction').count(),0);
    assert.equal(await page.evaluate(()=>{
      window.exportCount=0;
      window.saveAs=(blob,name)=>{window.exportCount++;window.exportedLetter={blob,name};};
      try {doExport();return false;} catch(error){return /still loading/.test(error.message);}
    }),true);
    assert.equal(await page.evaluate(()=>window.exportCount),0);
    await page.evaluate(()=>setHiddenFormInput('saveAndDownloadEForm','saveAndDownloadEForm','true'));
    await page.locator('[name=PrintSaveButton]').click();
    assert.equal(await page.locator('[data-carlos-workflow-flag]').count(),0);
    await page.locator('[name=PrintSubmitButton]').click();
    await page.locator('#PrintSubmitButton').click();
    assert.equal(await page.evaluate(()=>{
      window.saveRTL=()=>editControlContents('edit');
      try {submitFaxButton();return false;} catch(error){return /still loading/.test(error.message);}
    }),true);
    assert.deepEqual(await page.evaluate(()=>[false,true].map(save=>{
      let blocked=false;
      try {submitPrintButton(save);} catch(error) {blocked=/still loading/.test(error.message);}
      return [blocked,document.getElementById('printHolder').value,document.getElementById('saveHolder').value,window.needToConfirm,window.faxSubmits];
    })),[[true,'false','false',true,0],[true,'false','false',true,0]]);
    assert.equal(await page.locator('#faxEForm').inputValue(),'false');
    assert.equal(await page.evaluate(()=>window.prints),0);
    assert.equal(await page.evaluate(()=>{
      window.formPrint=()=>window.prints++;
      setHiddenFormInput('emailAction','emailAction','true');
      remotePrint();
      loadDefaultTemplate(); // Internal template reads must not raise the save gate.
      window.needToConfirm=false; // Shipped legacy saveRTL resets before serialization.
      try {editControlContents('edit');return false;} catch(error){return /still loading/.test(error.message);}
    }),true);
    assert.equal(await page.evaluate(()=>window.needToConfirm),true);
    assert.equal(await page.evaluate(()=>window.prints),0);
    assert.equal(await page.locator('[data-carlos-workflow-flag]').count(),0);
    // Move the live caret and type while the response is withheld.
    const body=page.frameLocator('#edit').locator('body');
    await body.click();await body.press('Control+End');await body.press('End');await body.pressSequentially(' TYPED');
    await respond({WT:[row('WT','70')],BP:[row('BP','120/80')]});
    assert.equal(await page.evaluate(()=>{remoteEmail();window.prompt=window.originalPrompt;return window.consentPrompts;}),1);
    assert.equal(await page.locator('#emailAction').count(),0);
    assert.equal(await body.textContent(),'BEFORE BP: 120/80(2026/9); WT: 70(2026/9); AFTER TYPED');
    await page.evaluate(()=>{window.delayedPrint();window.delayedFramePrint();});
    assert.deepEqual(await page.evaluate(()=>[window.delayedPrints,window.delayedFramePrints]),[1,1]);
    assert.deepEqual(await page.evaluate(()=>{
      submitPrintButton(false);
      const pdf=[window.submittedPrint,window.submittedSkipSave];
      document.RichTextLetter.submit();
      return [pdf,[window.submittedPrint,window.submittedSkipSave]];
    }),[['true','true'],['false','false']]);
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
    assert.equal(await page.evaluate(()=>pendingFaxSubmission),null);
    await page.evaluate(()=>window.deferredFaxSubmit());
    assert.equal(await page.locator('#faxEForm').inputValue(),'false');
    assert.equal(await page.evaluate(()=>window.needToConfirm),true);
    await respond({});
    await page.evaluate(()=>{
      window.faxSubmits=0;
      window.saveRTL=()=>{window.faxLetter=editControlContents('edit');};
      submitFaxButton();
      document.getElementById('edit').contentDocument.body.append(' LATE EDIT');
    });
    await page.waitForFunction(()=>window.faxSubmits===1);
    assert.equal(await page.locator('#faxEForm').inputValue(),'true');
    assert.match(await page.evaluate(()=>window.faxLetter),/LATE EDIT/);
    // A request which completes during the fax delay still cancels that fax:
    // its result (or failure notice) must be reviewed before resubmitting.
    await page.evaluate(()=>{
      window.saveRTL=()=>editControlContents('edit');
      const originalTimer=window.setTimeout;
      window.setTimeout=callback=>{window.deferredFaxSubmit=callback;};
      try {submitFaxButton();} finally {window.setTimeout=originalTimer;}
    });
    await begin(['BP']);await respond({});
    await page.evaluate(()=>window.deferredFaxSubmit());
    assert.equal(await page.evaluate(()=>window.faxSubmits),1);
    assert.equal(await page.locator('#faxEForm').inputValue(),'false');
    // Switching output intent invalidates the old callback even if it was
    // already queued; exercise real entry points without sending any fax/email.
    for (const action of ['legacyPrint','toolbarPrint','email','save','export']) {
      await page.evaluate(()=>{
        const originalTimer=window.setTimeout;
        window.setTimeout=callback=>{window.deferredFaxSubmit=callback;return 123;};
        try {submitFaxButton();} finally {window.setTimeout=originalTimer;}
      });
      await page.evaluate(action=>{ // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- action is from the fixed local array above, passed through structured arguments into a literal function; all fixture routes are fulfilled locally or aborted.
        if (action==='legacyPrint') document.querySelector('[name=PrintSaveButton]').click();
        if (action==='toolbarPrint') {window.needToConfirm=false;window.remoteSave=()=>false;remotePrint();}
        if (action==='email') {window.prompt=()=>null;remoteEmail();}
        if (action==='save') editControlContents('edit');
        if (action==='export') doExport();
        window.deferredFaxSubmit();
      },action); // Fixed action names above; no user input or interpolated code.
      assert.equal(await page.evaluate(()=>window.faxSubmits),1,action+' cancels delayed fax');
      assert.equal(await page.locator('#faxEForm').inputValue(),'false');
    }
    // Source mode may not capture a pending marker or accept rich-text results.
    const beforeSource=await body.innerHTML();
    await page.evaluate(()=>viewsource(true));
    const inSource=await body.textContent();
    assert(!inSource.includes('RTL measurement insertion'));
    assert.equal((await page.evaluate(()=>getMeasures('BP',1))).failed,true);
    assert.equal(await body.textContent(),inSource);
    assert.equal(pending.length,0);
    await page.evaluate(()=>viewsource(false));
    assert.equal(await body.innerHTML(),beforeSource);
    await begin(['BP']);
    await page.evaluate(()=>viewsource(true));
    assert.equal(await body.textContent(),'BEFORE AFTER');
    assert.equal((await respond({BP:[row('BP','120/80')]}))[0].failed,undefined);
    assert.match(await body.textContent(),/120\/80/);
    assert(!((await body.textContent()).includes('RTL measurement insertion')));
    assert.equal(await page.evaluate(()=>document.getElementById('edit').contentDocument.__rtlSourceMode),true);
    await page.evaluate(()=>viewsource(false));
    assert.equal(await body.textContent(),'BEFORE BP: 120/80(2026/9); AFTER');
    await begin(['BP']);
    await page.evaluate(()=>{viewsource(true);viewsource(false);});
    await respond({BP:[row('BP','120/80')]});
    assert.equal(await body.textContent(),'BEFORE BP: 120/80(2026/9); AFTER');
    assert.equal(await page.evaluate(()=>document.getElementById('edit').contentDocument.__rtlSourceMode),false);
    // Navigation has not committed yet when the old callback runs here.
    for (const navigation of ['default','selected']) {
      await page.evaluate(navigation=>{ // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- fixed local navigation names, structured-cloned into a literal function; all routes are local fixtures or aborted.
        const originalTimer=window.setTimeout;
        window.setTimeout=callback=>{window.deferredFaxSubmit=callback;return 123;};
        try {submitFaxButton();} finally {window.setTimeout=originalTimer;}
        cfg_template='default.rtl';
        window.beforeNavigationDocument=document.getElementById('edit').contentDocument;
        if (navigation==='default') {
          document.getElementById('edit').contentDocument.body.textContent='';loadDefaultTemplate();
        } else {
          window.setDirtyFlag=()=>{window.needToConfirm=true;};
          document.getElementById('template').selectedIndex=1;loadTemplate('template');
        }
        window.deferredFaxSubmit();
      },navigation);
      assert.equal(await page.evaluate(()=>window.faxSubmits),1);
      assert.equal(await page.locator('#faxEForm').inputValue(),'false');
      await page.waitForFunction(()=>{
        const doc=document.getElementById('edit').contentDocument;
        return doc!==window.beforeNavigationDocument && doc?.body?.textContent==='DEFAULT CONTENT';
      });
    }
    // Both empty and nonempty measurement results must leave the configured
    // default intact when the initial blank letter is still loading.
    for (const measurements of [{},{BP:[row('BP','120/80')]}]) {
      await page.evaluate(()=>{
        const doc=document.getElementById('edit').contentDocument;
        doc.body.textContent='';doc.getSelection().removeAllRanges();
        cfg_template='default.rtl';window.loaded=Promise.all([getMeasures('BP',1)]);
      });
      for (let n=0;n<100&&!pending.length;n++) await page.waitForTimeout(10);
      assert.equal(pending.length,1);
      await page.evaluate(()=>{
        viewsource(true);
        loadDefaultTemplate();
        window.sourceTransitionAfterDefault=pendingMeasureSourceView;
        window.duringTemplateLoad=getMeasures('WT',1);
      });
      assert.equal(await page.evaluate(()=>window.sourceTransitionAfterDefault),null);
      assert.equal((await page.evaluate(()=>window.duringTemplateLoad)).failed,true);
      await page.waitForFunction(()=>document.getElementById('edit').contentDocument?.body?.textContent==='DEFAULT CONTENT');
      assert.equal((await respond(measurements))[0].failed,true);
      assert.equal(await body.textContent(),'DEFAULT CONTENT');
      await page.locator('#rtl-measurement-status').waitFor({state:'visible'});
    }
    await page.evaluate(()=>{window.loaded=Promise.all([getMeasures('BP',1)]);});
    assert.equal((await respond({BP:[row('BP','120/80')]}))[0].failed,undefined);
    assert.match(await body.textContent(),/DEFAULT CONTENT/);
    assert.match(await body.textContent(),/120\/80/);
    // Hold the real APCache response after iframe navigation has completed.
    await page.evaluate(()=>{
      jQuery(document).on('ajaxSend.fixture',(_event,_xhr,settings)=>{
        if (settings.url.includes('efmformapconfig_lookup')) window.templateTimeout=settings.timeout;
      });
      document.getElementById('template').selectedIndex=2;loadTemplate('template');
    });
    for (let n=0;n<100&&!templateRequests.length;n++) await page.waitForTimeout(10);
    assert.equal(templateRequests.length,1);
    assert.equal(await body.textContent(),'##asyncField##');
    assert.equal(await page.evaluate(()=>window.templateTimeout),15000);
    assert.equal(await page.evaluate(()=>measurementHistoryStillLoading()),true);
    assert.equal((await page.evaluate(()=>getMeasures('BP',1))).failed,true);
    assert.equal(pending.length,0);
    assert.equal(await page.evaluate(()=>{
      try {editControlContents('edit');return false;} catch(error){return /still loading/.test(error.message);}
    }),true);
    await body.press('Control+End');await body.press('End');await body.pressSequentially(' TYPED DURING LOOKUP');
    await page.evaluate(()=>viewsource(true));
    await templateRequests.shift().fulfill({contentType:'text/html',body:'<input name="oscarAPCacheLookupType" value="template"><input name="asyncField" value="POPULATED">'});
    await page.waitForFunction(()=>!measurementHistoryStillLoading());
    assert.equal(await page.evaluate(()=>document.getElementById('edit').contentDocument.__rtlSourceMode),true);
    await page.evaluate(()=>viewsource(false));
    assert.equal(await body.textContent(),'POPULATED TYPED DURING LOOKUP');
    await page.evaluate(()=>{window.loaded=Promise.all([getMeasures('BP',1)]);});
    await respond({BP:[row('BP','120/80')]});
    assert.match(await body.textContent(),/POPULATED/);assert.match(await body.textContent(),/120\/80/);
    await page.evaluate(()=>{document.getElementById('template').selectedIndex=3;loadTemplate('template');});
    for (let n=0;n<100&&!templateRequests.length;n++) await page.waitForTimeout(10);
    assert.equal(templateRequests.length,1);
    await templateRequests.shift().fulfill({status:500,body:'Fixture lookup failure'});
    await page.waitForFunction(()=>!measurementHistoryStillLoading());
    assert.equal(await page.evaluate(()=>measureTemplateLookupsPending),0);
    await page.locator('#rtl-template-lookup-status').waitFor({state:'visible'});
    // Measurement success cannot claim the unresolved template fields recovered.
    await page.evaluate(()=>{window.loaded=Promise.all([getMeasures('BP',1)]);});
    await respond({BP:[row('BP','120/80')]});
    await page.locator('#rtl-template-lookup-status').waitFor({state:'visible'});
    await page.evaluate(()=>{document.getElementById('template').selectedIndex=2;loadTemplate('template');});
    for (let n=0;n<100&&!templateRequests.length;n++) await page.waitForTimeout(10);
    assert.equal(templateRequests.length,1);
    await templateRequests.shift().fulfill({contentType:'text/html',body:'<input name="oscarAPCacheLookupType" value="template"><input name="asyncField" value="POPULATED">'});
    await page.waitForFunction(()=>!measurementHistoryStillLoading());
    assert.equal(await body.textContent(),'POPULATED');
    await page.locator('#rtl-template-lookup-status').waitFor({state:'hidden'});
    // Old template results cannot touch a replacement document or its cache.
    for (const replacement of ['navigation','sameDocument']) {
      await page.evaluate(()=>{
        cache.values={};document.getElementById('template').selectedIndex=2;loadTemplate('template');
      });
      for (let n=0;n<100&&!templateRequests.length;n++) await page.waitForTimeout(10);
      assert.equal(templateRequests.length,1);
      if (replacement==='navigation') {
        await page.evaluate(()=>{document.getElementById('template').selectedIndex=1;loadTemplate('template');});
        await page.waitForFunction(()=>document.getElementById('edit').contentDocument?.body?.textContent==='DEFAULT CONTENT');
      } else {
        await page.evaluate(()=>seteditControlContents('edit','Replacement content'));
      }
      await body.pressSequentially(' USER EDIT');
      const replacementText=await body.textContent();
      await templateRequests.shift().fulfill({contentType:'text/html',body:'<input name="oscarAPCacheLookupType" value="template"><input name="asyncField" value="STALE">'});
      await page.waitForFunction(()=>!measurementHistoryStillLoading());
      assert.equal(await body.textContent(),replacementText);
      assert.equal(await page.evaluate(()=>cache.contains('asyncField')),false);
    }
    // populateTemplate reads the live body at response time, so replacing the
    // placeholder with typed content during a lookup must preserve that content.
    await page.evaluate(()=>{cache.values={};document.getElementById('template').selectedIndex=2;loadTemplate('template');});
    for (let n=0;n<100&&!templateRequests.length;n++) await page.waitForTimeout(10);
    assert.equal(templateRequests.length,1);
    await body.click();await body.press('Control+A');await body.pressSequentially('MANUAL TEMPLATE REPLACEMENT');
    await templateRequests.shift().fulfill({contentType:'text/html',body:'<input name="oscarAPCacheLookupType" value="template"><input name="asyncField" value="MUST NOT REPLACE TYPING">'});
    await page.waitForFunction(()=>!measurementHistoryStillLoading());
    assert.equal(await body.textContent(),'MANUAL TEMPLATE REPLACEMENT');
    // Settling the old request must leave the new template's lookup gated.
    await page.evaluate(()=>{cache.values={};document.getElementById('template').selectedIndex=2;loadTemplate('template');});
    for (let n=0;n<100&&!templateRequests.length;n++) await page.waitForTimeout(10);
    assert.equal(templateRequests.length,1);
    await page.evaluate(()=>{document.getElementById('template').selectedIndex=3;loadTemplate('template');});
    for (let n=0;n<100&&templateRequests.length<2;n++) await page.waitForTimeout(10);
    assert.equal(templateRequests.length,2);
    await templateRequests.shift().fulfill({status:500,body:'Stale lookup failure'});
    await page.waitForFunction(()=>measureTemplateLookupsPending===1);
    assert.equal(await page.evaluate(()=>measurementHistoryStillLoading()),true);
    assert.equal(await body.textContent(),'##failureField##');
    await templateRequests.shift().fulfill({contentType:'text/html',body:'<input name="oscarAPCacheLookupType" value="template"><input name="failureField" value="CURRENT">'});
    await page.waitForFunction(()=>!measurementHistoryStillLoading());
    assert.equal(await body.textContent(),'CURRENT');
    // The new optional timeout must preserve a clinic's global jQuery setting
    // for existing callers which do not pass the second argument.
    await page.evaluate(()=>{
      const oldTimeout=jQuery.ajaxSettings.timeout;
      jQuery.ajaxSetup({timeout:4321});
      try {createCache({}).lookup('timeoutProbe');}
      finally {jQuery.ajaxSetup({timeout:oldTimeout});}
    });
    for (let n=0;n<100&&!templateRequests.length;n++) await page.waitForTimeout(10);
    assert.equal(templateRequests.length,1);
    assert.equal(await page.evaluate(()=>window.templateTimeout),4321);
    await templateRequests.shift().fulfill({contentType:'text/html',body:'<input name="oscarAPCacheLookupType" value="timeoutProbe"><input name="timeoutProbe" value="ok">'});
    // A delayed legacy native submit must recheck readiness and serialize anew.
    await page.evaluate(()=>{
      window.saveRTL=()=>{
        window.needToConfirm=false;
        document.getElementById('Letter').value=editControlContents('edit');
      };
      saveRTL();window.legacySubmit=()=>document.RichTextLetter.submit();
    });
    await begin(['BP']);
    await page.evaluate(()=>window.legacySubmit());
    assert.equal(await page.evaluate(()=>window.faxSubmits),1);
    assert.equal(await page.evaluate(()=>window.needToConfirm),true);
    await respond({BP:[row('BP','130/85')]});
    await page.evaluate(()=>window.legacySubmit());
    assert.equal(await page.evaluate(()=>window.faxSubmits),2);
    assert.match(await page.evaluate(()=>window.submittedLetter),/130\/85/);
    await page.evaluate(()=>saveRTL());
    await begin(['BP']);await respond({BP:[row('BP','140/90')]});
    await page.evaluate(()=>window.legacySubmit());
    assert.equal(await page.evaluate(()=>window.faxSubmits),3);
    assert.match(await page.evaluate(()=>window.submittedLetter),/140\/90/);

    async function selectText() {
      await page.evaluate(()=>{
        const doc=document.getElementById('edit').contentDocument;
        doc.body.textContent='BEFORE SELECTED AFTER';
        const range=doc.createRange();range.setStart(doc.body.firstChild,7);range.setEnd(doc.body.firstChild,15);
        const selection=doc.getSelection();selection.removeAllRanges();selection.addRange(range);
        window.loaded=Promise.all([getMeasures('BP',1)]);
      });
    }
    await selectText();
    await body.press('Control+End');await body.press('End');await body.pressSequentially(' TYPED');
    await respond({BP:[row('BP','125/80')]});
    assert.equal(await body.textContent(),'BEFORE BP: 125/80(2026/9);  AFTER TYPED');
    await selectText();await respond({});
    assert.equal(await body.textContent(),'BEFORE SELECTED AFTER');
    await selectText();
    for (let n=0;n<100&&!pending.length;n++) await page.waitForTimeout(10);
    await pending.shift().fulfill({status:500,body:'Fixture measurement failure'});
    assert.equal((await page.evaluate(()=>window.loaded))[0].failed,true);
    assert.equal(await body.textContent(),'BEFORE SELECTED AFTER');
    await selectText();
    await page.evaluate(()=>{
      const doc=document.getElementById('edit').contentDocument;
      Array.from(doc.body.childNodes).find(node=>node.nodeType===3&&node.textContent==='SELECTED').textContent='EDITED';
    });
    assert.equal((await respond({BP:[row('BP','125/80')]}))[0].failed,true);
    assert.equal(await body.textContent(),'BEFORE EDITED AFTER');
    assert(!((await body.innerHTML()).includes('RTL measurement')));
    // Legacy callers may insert a separator between same-tick measurement calls.
    // Preserve each call's caret rather than moving both results before that text.
    await page.evaluate(()=>{
      const doc=document.getElementById('edit').contentDocument;
      doc.body.textContent='START END';
      const range=doc.createRange();range.setStart(doc.body.firstChild,6);range.collapse(true);
      const selection=doc.getSelection();selection.removeAllRanges();selection.addRange(range);
      window.firstInterleaved=getMeasures('BP',1);
      doHtml('MIDDLE ');
      window.secondInterleaved=getMeasures('WT',1);
    });
    for (let n=0;n<100&&!pending.length;n++) await page.waitForTimeout(10);
    assert.equal(pending.length,1);
    assert.deepEqual(pending[0].request().postDataJSON().types,['BP']);
    await pending.shift().fulfill({contentType:'application/json',body:JSON.stringify({measurements:{BP:[row('BP','120/80')]}})});
    assert.equal((await page.evaluate(()=>window.firstInterleaved)).failed,undefined);
    for (let n=0;n<100&&!pending.length;n++) await page.waitForTimeout(10);
    assert.equal(pending.length,1);
    assert.deepEqual(pending[0].request().postDataJSON().types,['WT']);
    await pending.shift().fulfill({contentType:'application/json',body:JSON.stringify({measurements:{WT:[row('WT','70')]}})});
    assert.equal((await page.evaluate(()=>window.secondInterleaved)).failed,undefined);
    assert.equal(await body.textContent(),'START BP: 120/80(2026/9); MIDDLE WT: 70(2026/9); END');
    // The retained IE detection path also parses only once after navigation.
    await page.evaluate(()=>{
      window.originalIsIE=isIE;window.isIE=()=>true;
      const frame=document.getElementById('edit');
      Object.defineProperty(frame,'document',{get:()=>frame.contentDocument,configurable:true});
      window.originalParseTemplate=parseTemplate;window.templateParseCount=0;
      window.parseTemplate=()=>{window.templateParseCount++;return window.originalParseTemplate();};
      document.getElementById('template').selectedIndex=1;loadTemplate('template');
    });
    await page.waitForFunction(()=>!measurementHistoryStillLoading());
    await body.pressSequentially(' AFTER LOAD');
    await page.waitForTimeout(1100);
    assert.equal(await page.evaluate(()=>window.templateParseCount),1);
    assert.match(await body.textContent(),/AFTER LOAD/);
    await page.evaluate(()=>{
      window.isIE=window.originalIsIE;window.parseTemplate=window.originalParseTemplate;
      delete document.getElementById('edit').document;
    });
    // HTTP 200 is not sufficient: reject absent/wrong/duplicate response markers
    // before they can populate the editor or write a value into the cache.
    for (const response of [
      '<html><body>Session expired. Sign in.</body></html>',
      '<input name="oscarAPCacheLookupType" value="wrong"><input name="asyncField" value="WRONG">',
      '<input name="oscarAPCacheLookupType" value="template"><input name="oscarAPCacheLookupType" value="template"><input name="asyncField" value="DUPLICATE">',
    ]) {
      await page.evaluate(()=>{cache.values={};document.getElementById('template').selectedIndex=2;loadTemplate('template');});
      for (let n=0;n<100&&!templateRequests.length;n++) await page.waitForTimeout(10);
      assert.equal(templateRequests.length,1);
      await templateRequests.shift().fulfill({contentType:'text/html',body:response});
      await page.waitForFunction(()=>!measurementHistoryStillLoading());
      assert.equal(await body.textContent(),'##asyncField##');
      assert.equal(await page.evaluate(()=>cache.contains('asyncField')),false);
      await page.locator('#rtl-template-lookup-status').waitFor({state:'visible'});
    }
    // Re-registering a print wrapper retained across iframe navigation must
    // refresh the live function while leaving old captured callbacks cancelled.
    const printCount=await page.evaluate(()=>window.delayedFramePrints);
    assert.equal(await page.evaluate(()=>{
      const frame=document.getElementById('edit');
      frame.contentWindow.print=window.initialFrameGuard;
      enableEditorDesignMode();
      window.initialFrameGuard(); // Still belongs to the original document.
      frame.contentWindow.print(); // New wrapper calls the underlying print spy.
      const refreshed=frame.contentWindow.print;
      enableEditorDesignMode();
      return refreshed===frame.contentWindow.print && refreshed!==window.initialFrameGuard;
    }),true);
    assert.equal(await page.evaluate(()=>window.delayedFramePrints),printCount+1);
    await begin(['BP']);
    await page.evaluate(()=>document.getElementById('edit').contentWindow.print());
    assert.equal(await page.evaluate(()=>window.delayedFramePrints),printCount+1);
    await respond({});
    await page.evaluate(()=>document.getElementById('edit').contentWindow.print());
    assert.equal(await page.evaluate(()=>window.delayedFramePrints),printCount+2);
    // A failed catalogue must remain visible after unrelated measurement success.
    await page.evaluate(()=>{
      document.getElementById('Letter').value='<p>Saved recovery letter</p>';
      document.getElementById('template').innerHTML='<option> loading... </option>';Start();
    });
    for (let n=0;n<100&&!catalogRequests.length;n++) await page.waitForTimeout(10);
    await catalogRequests.shift().fulfill({status:500,body:'Fixture catalogue failure'});
    await page.waitForFunction(()=>!measurementHistoryStillLoading());
    assert.equal(await body.textContent(),'Saved recovery letter');
    assert.equal(await page.evaluate(()=>editorStillLoading()),false);
    await page.locator('#rtl-template-catalog-status').waitFor({state:'visible'});
    await page.evaluate(()=>{window.loaded=Promise.all([getMeasures('BP',1)]);});
    await respond({BP:[row('BP','120/80')]});
    await page.locator('#rtl-template-catalog-status').waitFor({state:'visible'});
    await page.evaluate(()=>{document.getElementById('template').innerHTML='<option> loading... </option>';Start();});
    for (let n=0;n<100&&!catalogRequests.length;n++) await page.waitForTimeout(10);
    await catalogRequests.shift().fulfill({contentType:'text/html',body:'<html><body>Session expired</body></html>'});
    await page.waitForFunction(()=>!measurementHistoryStillLoading());
    assert.equal(await body.textContent(),'Saved recovery letter');
    assert.equal(await page.evaluate(()=>editorStillLoading()),false);
    await page.locator('#rtl-template-catalog-status').waitFor({state:'visible'});
    await page.evaluate(()=>Start());
    for (let n=0;n<100&&!catalogRequests.length;n++) await page.waitForTimeout(10);
    await catalogRequests.shift().fulfill({contentType:'text/html',body:'<option value="">Templates</option><option value="blank.rtl">Blank</option>'});
    await page.waitForFunction(()=>!measurementHistoryStillLoading());
    await page.locator('#rtl-template-catalog-status').waitFor({state:'hidden'});
    assert.equal(await page.frameLocator('#unrelated-frame').locator('body').textContent(),'Other frame');
    assert.deepEqual(errors,[]);
    console.log('PASS: native Range insertion, request ordering, live caret/typing, serializer and legacy/toolbar print gates, marker cleanup, replaced template, literal measurement text. Chromium '+browser.version());
  } finally {await browser.close();}
}
run().catch(error=>{console.error(error);process.exitCode=1;});

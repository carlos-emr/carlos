#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// Isolated browser regression using the shipped JSP fragment, styles and scripts.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const {chromium} = require('playwright');
const {gotoApp, validateBaseUrl} = require('./lib/playwright-harness');
const web = path.join(__dirname, '../src/main/webapp');
const read = file => fs.readFileSync(path.join(web, file), 'utf8');
const toolbar = read('WEB-INF/jsp/eform/eformFloatingToolbar/eform_floating_toolbar.jspf')
  .replace(/<%[\s\S]*?%>/g, '').replace(/<fmt:[^>]*\/>/g, '')
  .replace(/\$\{[^}]*\}/g, '');
const errors = [];
let requests = [];
let searches = [];
const fixture = (owned = false, withList = false) => `<!doctype html><html><head>
<script src="/library/jquery/jquery-3.7.1.min.js"></script>
<script>jQuery(function(){ document.getElementById('otherFaxInput').value='416-555-0123'; });</script>
<script src="/library/eforms/faxControl.js"></script>
<script src="/library/eforms/faxControl.js"></script>
<script src="/js/faxRecipientAutocomplete.js"></script>
<link rel="stylesheet" href="/library/bootstrap/5.3.8/css/bootstrap.min.css">
<link rel="stylesheet" href="/eform/eformFloatingToolbar/eform_floating_toolbar_custom.css">
</head><body><form name="saveEForm" action="/eform/addEForm" method="post">
<input id="context" value="" type="hidden"><input id="fid" value="1" type="hidden">
<input id="demographicNo" value="1" type="hidden"><label for="subject">Subject</label>
<span id="nativeSubjectRow">Subject: <input id="subject" name="subject" value="Designer subject" required></span>
${owned ? '<input id="otherFaxInput" name="otherFaxInput" value="original">' : ''}
${withList ? '<select id="faxnumList"><option value="416-555-0101">Default clinic</option><option value="416-555-0102">Changed clinic</option><option value="">No list recipient</option></select>' : ''}
<input id="designerFax" value="416-555-0191">
<button id="designerAddFax" type="button" onclick="document.getElementById('otherFaxInput').value=document.getElementById('designerFax').value; AddOtherFax();">Use designer number</button>
<input name="recipient" value="Existing name"><input name="recipientFaxNumber" value="416-555-0000">
<input name="SubmitButton" type="submit" value="Submit">
<input name="PrintButton" type="button" value="Print" onclick="window.print()">
<div id="lastPage" style="position:absolute;top:1800px;width:750px;height:400px;background:#eee">Last page
<input id="lastField" style="position:absolute;top:350px" value="Last field"></div>
</form>
<script src="/library/eforms/printControl.js"></script>
<script src="/eform/eformFloatingToolbar/eform_floating_toolbar.js"></script>
</body></html>`;
const server = http.createServer((req, res) => {
  if (req.url.startsWith('/eform/eformFloatingToolbar/eform_floating_toolbar') && !req.url.endsWith('.js') && !req.url.endsWith('.css')) {
    setTimeout(() => {res.setHeader('Content-Type', 'text/html'); res.end(toolbar);}, 600); return;
  }
  if (req.url.startsWith('/fixture')) {res.setHeader('Content-Type','text/html'); res.end(fixture(req.url.includes('owned=owned'), req.url.includes('selection=list'))); return;}
  if (req.url.startsWith('/fax/SearchFaxRecipient')) {
    searches.push(req.url);
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify([{name:'Example, Specialist', fax:'416-555-0199', badge:'Cardiology', type:'SPECIALIST'}])); return;
  }
  if (req.url === '/eform/addEForm' && req.method === 'POST') {
    let body=''; req.on('data', chunk => {body += chunk;}); req.on('end', () => {
      requests.push(new URLSearchParams(body)); res.setHeader('Content-Type', 'text/html'); res.end('Saved');
    }); return;
  }
  const url = new URL(req.url, 'http://localhost');
  const target = path.resolve(web, '.'+url.pathname);
  if (target.startsWith(web+path.sep) && fs.existsSync(target) && fs.statSync(target).isFile()) {
    res.setHeader('Content-Type', target.endsWith('.css') ? 'text/css' : 'text/javascript');
    res.end(fs.readFileSync(target)); return;
  }
  res.statusCode=404; res.end();
});
(async () => {
  await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
  const browser = await chromium.launch({headless:true, args:['--no-sandbox'], ...(process.env.CHROME_PATH ? {executablePath:process.env.CHROME_PATH} : {})});
  try {
    const page = await browser.newPage({viewport:{width:1100,height:800}});
    page.on('pageerror', error => { errors.push(error.message); console.error(error.message); });
    // Selects the directory row, logging the autocomplete state when the row never appears.
    async function pickDirectoryRow() {
      await page.locator('#remoteFaxSuggestions .fax-ac-item').click().catch(async error => {
        console.error({searches, state: await page.evaluate(() => ({value: document.getElementById('remoteFaxRecipient').value, active: document.activeElement.id, open: document.getElementById('remoteFaxOptions').open}))});
        throw error;
      });
    }
    async function open(owned=false, withList=false) {
      requests=[];
      await gotoApp(page, validateBaseUrl(`http://127.0.0.1:${server.address().port}`), `/fixture?owned=${owned ? 'owned' : 'no'}&selection=${withList ? 'list' : 'no'}`);
      await page.locator('#remoteFaxButton').waitFor();
      assert.equal(await page.locator('#otherFaxInput').count(),1);
      assert.equal(await page.locator('#otherFaxInput').inputValue(),'416-555-0123');
      assert.equal(await page.locator('#subject').isVisible(),false);
      assert.equal(await page.locator('label[for=subject]').isVisible(),false);
      assert.equal((await page.locator('#nativeSubjectRow').innerText()).trim(), '');
      assert.equal(await page.locator('#remote_eform_subject').inputValue(),'Designer subject');
      assert.equal(await page.locator('input[name=pdfButton]').isVisible(),false);
      assert.equal(await page.locator('#oscar-spinner-screen').isVisible(),false);
      await page.waitForFunction(() => document.getElementById('toolbarWrapper').getBoundingClientRect().top >= document.getElementById('lastPage').getBoundingClientRect().bottom);
    }
    for (const owned of [false,true]) {
      await open(owned);
      await page.locator('#remoteFaxButton').click();
      await page.waitForURL('**/eform/addEForm');
      assert.equal(requests.length,1);
      assert.equal(requests[0].get('recipientFaxNumber'),'416-555-0123');
      assert.equal(requests[0].get('subject'),'Designer subject');
    }
    await open();
    await page.locator('#remoteFaxOptions summary').click();
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'416-555-0123');
    const combobox = page.locator('#remoteFaxRecipient');
    assert.deepEqual(await combobox.evaluate(el => ['role', 'aria-autocomplete', 'aria-controls', 'aria-expanded']
      .map(name => el.getAttribute(name))), ['combobox', 'list', 'remoteFaxSuggestions', 'false']);
    assert.equal(await page.locator('#remoteFaxSuggestions').getAttribute('role'), 'listbox');
    await page.locator('#remoteFaxRecipient').fill('Example');
    await page.locator('#remoteFaxSuggestions [role=option]').first().waitFor();
    assert.equal(await combobox.getAttribute('aria-expanded'), 'true');
    const option = page.locator('#remoteFaxSuggestions [role=option]').first();
    assert.equal(await option.getAttribute('aria-selected'), 'false');
    await combobox.press('ArrowDown');
    assert.equal(await option.getAttribute('aria-selected'), 'true');
    assert.equal(await combobox.getAttribute('aria-activedescendant'), await option.getAttribute('id'));
    await pickDirectoryRow();
    assert.equal(await combobox.getAttribute('aria-expanded'), 'false');
    assert.equal(await combobox.getAttribute('aria-activedescendant'), null);
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'416-555-0199');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests[0].get('recipientFaxNumber'),'416-555-0199');
    assert.equal(requests[0].get('recipient'),'Example, Specialist');
    // Retyping the name after a directory selection must not keep the selected recipient's number.
    await open();
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxRecipient').fill('Example');
    await pickDirectoryRow();
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'416-555-0199');
    await page.locator('#remoteFaxRecipient').fill('Someone Else');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length,1);
    assert.equal(requests[0].get('recipient'),'Someone Else');
    assert.equal(requests[0].get('recipientFaxNumber'),'');
    // Nor the eForm's own number, and a number the clinician typed survives editing the name.
    await open();
    await page.locator('#remoteFaxOptions summary').click();
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'416-555-0123');
    await page.locator('#remoteFaxRecipient').fill('Typed Recipient');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'');
    await page.locator('#remoteFaxNumber').fill('416-555-0177');
    await page.locator('#remoteFaxRecipient').fill('Typed Recipient Corrected');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length,1);
    assert.equal(requests[0].get('recipient'),'Typed Recipient Corrected');
    assert.equal(requests[0].get('recipientFaxNumber'),'416-555-0177');
    // Reverting a typed number to the eForm's own number makes it the eForm's again.
    await open();
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxNumber').fill('416-555-0166');
    await page.locator('#remoteFaxFromForm').selectOption('');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'416-555-0123');
    await page.locator('#remoteFaxRecipient').fill('Reverted Recipient');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length,1);
    assert.equal(requests[0].get('recipient'),'Reverted Recipient');
    assert.equal(requests[0].get('recipientFaxNumber'),'');
    // After the name is retyped, a new explicit list or designer choice fills the number again.
    await open(false, true);
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxRecipient').fill('Retyped List Recipient');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'');
    await page.locator('#faxnumList').selectOption('416-555-0102');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'416-555-0102');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length,1);
    assert.equal(requests[0].get('recipient'),'Retyped List Recipient');
    assert.equal(requests[0].get('recipientFaxNumber'),'416-555-0102');
    await open();
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxRecipient').fill('Retyped Designer Recipient');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'');
    await page.locator('#designerAddFax').click();
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'416-555-0191');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length,1);
    assert.equal(requests[0].get('recipient'),'Retyped Designer Recipient');
    assert.equal(requests[0].get('recipientFaxNumber'),'416-555-0191');
    // A typed number is never replaced by a later list change, even after a name edit.
    await open(false, true);
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxNumber').fill('416-555-0155');
    await page.locator('#remoteFaxRecipient').fill('Typed Then Listed');
    await page.locator('#faxnumList').selectOption('416-555-0102');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'416-555-0155');
    await open();
    await page.setViewportSize({width:600,height:800});
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxNumber').fill('416-555-0188');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests[0].get('recipientFaxNumber'),'416-555-0188');
    await open();
    await page.locator('#closeToolbarButton').click();
    assert.equal(await page.locator('#remoteFaxButton').isVisible(),false);
    assert.equal(await page.locator('#openToolbarButton').isVisible(),true);
    await page.locator('#openToolbarButton').click();
    await page.locator('#remoteFaxButton').waitFor();
    await page.evaluate(() => ShowSpin(true));
    assert.equal(await page.locator('#oscar-spinner-screen').isVisible(),true);
    await page.evaluate(() => HideSpin());
    assert.equal(await page.locator('#oscar-spinner-screen').isVisible(),false);
    // An absolutely positioned page can grow without resizing its containing form.
    await page.evaluate(() => { document.getElementById('lastPage').style.height = '800px'; });
    await page.waitForFunction(() => document.getElementById('toolbarWrapper').getBoundingClientRect().top >= document.getElementById('lastPage').getBoundingClientRect().bottom);
    // A typed number still wins if a customized toolbar has no recipient name field.
    await open();
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxNumber').fill('416-555-0144');
    await page.evaluate(() => document.getElementById('remoteFaxRecipient').remove());
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('recipientFaxNumber'), '416-555-0144');
    assert.equal(requests[0].get('recipient'), '');
    // A missing directory script must leave manual fax entry and toolbar layout usable.
    await page.route('**/js/faxRecipientAutocomplete.js', route => route.fulfill({status:200,body:''}));
    await open();
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxNumber').fill('416-555-0177');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests[0].get('recipientFaxNumber'), '416-555-0177');
    await page.unroute('**/js/faxRecipientAutocomplete.js');
    // A legacy AddOtherFax choice must win over an older nonempty faxnumList.
    await open(false, true);
    await page.locator('#designerAddFax').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests[0].get('recipientFaxNumber'), '416-555-0191');
    await open(false, true);
    await page.locator('#designerAddFax').click();
    await page.locator('#faxnumList').selectOption('416-555-0102');
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests[0].get('recipientFaxNumber'), '416-555-0102');
    // Clearing the list changed last must not fall back to the other list's older choice.
    await open(false, true);
    await page.locator('#designerAddFax').click();
    await page.locator('#faxnumList').selectOption('416-555-0102');
    await page.locator('#faxnumList').selectOption('');
    await page.locator('#remoteFaxOptions summary').click();
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('recipientFaxNumber'), '');
    // A number typed into the form's own other-fax field after clearing a list is used, not
    // the cleared list and not the older designer choice.
    await open(true, true);
    await page.locator('#designerAddFax').click();
    await page.locator('#faxnumList').selectOption('416-555-0102');
    await page.locator('#faxnumList').selectOption('');
    await page.locator('#otherFaxInput').fill('416-555-0133');
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('recipientFaxNumber'), '416-555-0133');
    // That typed number is the clinician's own: naming its recipient afterwards keeps it.
    await open(true);
    await page.locator('#otherFaxInput').fill('416-555-0122');
    await page.locator('#otherFaxInput').blur();
    await page.locator('#remoteFaxOptions summary').click();
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '416-555-0122');
    await page.locator('#remoteFaxRecipient').fill('Manual Recipient');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '416-555-0122');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('recipient'), 'Manual Recipient');
    assert.equal(requests[0].get('recipientFaxNumber'), '416-555-0122');
    // Promoting the typed number with the form's AddOtherFax helper keeps it the clinician's.
    await open(true);
    await page.locator('#otherFaxInput').fill('416-555-0124');
    await page.evaluate(() => AddOtherFax());
    await page.locator('#remoteFaxOptions summary').click();
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '416-555-0124');
    await page.locator('#remoteFaxRecipient').fill('Promoted Recipient');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '416-555-0124');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('recipient'), 'Promoted Recipient');
    assert.equal(requests[0].get('recipientFaxNumber'), '416-555-0124');
    // A designer number copied in after typing is the designer's: retyping the name clears it.
    await open(true);
    await page.locator('#otherFaxInput').fill('416-555-0124');
    await page.locator('#designerAddFax').click();
    await page.locator('#remoteFaxOptions summary').click();
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '416-555-0191');
    await page.locator('#remoteFaxRecipient').fill('Designer Recipient');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('recipientFaxNumber'), '');
    // A list choice made after it is the list's again: retyping the name clears it.
    await open(true, true);
    await page.locator('#otherFaxInput').fill('416-555-0122');
    await page.locator('#otherFaxInput').blur();
    await page.locator('#faxnumList').selectOption('416-555-0102');
    await page.locator('#remoteFaxOptions summary').click();
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '416-555-0102');
    await page.locator('#remoteFaxRecipient').fill('Different Recipient');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('recipient'), 'Different Recipient');
    assert.equal(requests[0].get('recipientFaxNumber'), '');
    await open();
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxNumber').fill('');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests[0].get('recipientFaxNumber'), '');
    await open();
    // A form author can populate the O19 select after the toolbar has loaded.
    await page.evaluate(() => {
      const select = document.getElementById('otherFaxSelect');
      select.add(new Option('Clinic One','416-555-0141'));
      select.add(new Option('Clinic Two','416-555-0142'));
    });
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxFromForm').selectOption('416-555-0142');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests[0].get('recipientFaxNumber'),'416-555-0142');
    assert.equal(requests[0].get('recipient'),'Clinic Two');
    // The template's subject is required; hiding it must not let an empty toolbar subject save.
    await open();
    await page.locator('#remote_eform_subject').fill('');
    const blockedUrl = page.url();
    await page.locator('#remoteSubmitButton').click();
    assert.equal(await page.evaluate(() => document.activeElement.id), 'remote_eform_subject');
    assert.equal(await page.evaluate(() => document.getElementById('remote_eform_subject').validity.valueMissing), true);
    assert.equal(page.url(), blockedUrl);
    assert.equal(requests.length, 0);
    await page.locator('#remote_eform_subject').fill('Toolbar subject');
    await page.locator('#remoteSubmitButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('subject'), 'Toolbar subject');
    // A required but disabled template subject is excluded from validation, directly or
    // through a disabled fieldset, so an empty toolbar subject still saves.
    for (const disable of ['direct', 'fieldset']) {
      await open();
      await page.evaluate(mode => {
        const subject = document.getElementById('subject');
        if (mode === 'direct') { subject.disabled = true; return; }
        const fieldset = document.createElement('fieldset');
        fieldset.disabled = true;
        subject.replaceWith(fieldset);
        fieldset.append(subject);
      }, disable);
      await page.locator('#remote_eform_subject').fill('');
      await page.locator('#remoteSubmitButton').click();
      await page.waitForURL('**/eform/addEForm');
      assert.equal(requests.length, 1);
      // The disabled template subject is not a successful control, so no subject is posted.
      assert.equal(requests[0].get('subject'), null);
    }
    // A template subject without the constraint still saves with an empty subject.
    await open();
    await page.evaluate(() => { document.getElementById('subject').required = false; });
    await page.locator('#remote_eform_subject').fill('');
    await page.locator('#remoteSubmitButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('subject'), '');
    await open();
    await page.locator('#lastField').fill('Current unsaved content');
    await page.evaluate(() => {
      window.needToConfirm = true;
      window.print = () => { window.printedValue = document.getElementById('lastField').value; };
      // A template print handler can save. Print Only must never call it.
      window.formPrint = () => document.forms[0].submit();
    });
    const unsavedUrl = page.url();
    await page.locator('#remotePrintOptions summary').click();
    await page.locator('#remotePrintPdfButton').click();
    assert.equal(await page.evaluate(() => window.printedValue), 'Current unsaved content');
    assert.equal(await page.evaluate(() => window.needToConfirm), true);
    assert.equal(page.url(), unsavedUrl);
    assert.equal(requests.length, 0);
    await page.locator('#remotePrintOptions summary').click();
    await page.locator('#remoteSavePdfButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests[0].get('saveAndDownloadEForm'),'true');
    await open();
    await page.setViewportSize({width:320,height:700});
    for (const id of ['remotePrintOptions', 'remoteFaxOptions']) {
      await page.locator('#'+id+' summary').click();
      await page.waitForFunction(id => {
        const rect = document.querySelector('#'+id+' .eform-options').getBoundingClientRect();
        return rect.left >= 0 && rect.right <= document.documentElement.clientWidth
          && rect.top >= 0 && rect.bottom <= window.innerHeight;
      }, id);
      await page.locator('#'+id+' summary').click();
    }
    await open();
    await page.emulateMedia({media:'print'});
    assert.equal(await page.locator('#toolbarWrapper').isVisible(),false);
    assert.deepEqual(errors,[]);
    console.log('PASS toolbar layout, print CSS, hidden subject, early fax initialization, existing input, directory selection and manual recipient POST');
  } finally {await browser.close(); server.close();}
})().catch(error => { console.error(error); server.close(); process.exitCode=1; });

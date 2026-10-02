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
const fallbackInput = '<input type="hidden" name="newForm" value="true" id="newForm" data-carlos-newform-fallback>';
// Template newForm controls that submit nothing as rendered, each beside the server's fallback.
const newFormMarkup = {
  button: '<button id="newFormButton" type="submit" name="newForm" value="False">Save as existing</button><button id="newFormDisablingButton" type="submit" name="newForm" value="False" onclick="this.disabled = true; this.form.requestSubmit(this); return false;">Save once</button>',
  checkbox: '<input type="checkbox" id="newFormControl" name="newForm" value="False">',
  listbox: '<select id="newFormControl" name="newForm" size="2"><option value="False">False</option></select>',
  disabled: '<input type="hidden" id="newFormControl" name="newForm" value="False" disabled>',
  // Submits newForm by name only: the server adds no fallback input, only the form default.
  named: '<input type="hidden" id="namedNewForm" name="newForm" value="False">',
};
const fixture = (owned = false, withList = false, newFormVariant = '', subjectVariant = '') => `<!doctype html><html><head>
<script src="/library/jquery/jquery-3.7.1.min.js"></script>
<script>jQuery(function(){ document.getElementById('otherFaxInput').value='416-555-0123'; });</script>
<script src="/library/eforms/faxControl.js"></script>
<script src="/library/eforms/faxControl.js"></script>
<script src="/js/faxRecipientAutocomplete.js"></script>
<link rel="stylesheet" href="/library/bootstrap/5.3.8/css/bootstrap.min.css">
<link rel="stylesheet" href="/eform/eformFloatingToolbar/eform_floating_toolbar_custom.css">
</head><body><form name="saveEForm" action="/eform/addEForm" method="post"${newFormVariant === 'named' ? ' data-carlos-newform-default="true"' : ''}>
<input id="context" value="" type="hidden"><input id="fid" value="1" type="hidden">
<input id="demographicNo" value="1" type="hidden">${subjectVariant === 'required-hidden' ? '' : '<label for="subject">Subject</label>'}
<span id="nativeSubjectRow">Subject: <input id="subject" name="subject" value="Designer subject" required${subjectVariant === 'readonly-checkbox' ? ' type="checkbox" readonly checked' : subjectVariant === 'readonly-text' ? ' type="text" readonly' : subjectVariant === 'readonly-textbox' ? ' type="textbox" readonly' : subjectVariant === 'required-hidden' ? ' type="hidden"' : subjectVariant === 'required-checkbox' ? ' type="checkbox"' : ''}></span>
${owned ? '<input id="otherFaxInput" name="otherFaxInput" value="original">' : ''}
${withList ? '<select id="faxnumList"><option value="416-555-0101">Default clinic</option><option value="416-555-0102">Changed clinic</option><option value="">No list recipient</option></select>' : ''}
<input id="designerFax" value="416-555-0191">
<button id="designerAddFax" type="button" onclick="document.getElementById('otherFaxInput').value=document.getElementById('designerFax').value; AddOtherFax();">Use designer number</button>
${newFormMarkup[newFormVariant] ? newFormMarkup[newFormVariant] + (newFormVariant === 'named' ? '' : fallbackInput) : ''}
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
  if (req.url.startsWith('/fixture')) {res.setHeader('Content-Type','text/html'); res.end(fixture(req.url.includes('owned=owned'), req.url.includes('selection=list'), (req.url.match(/newform=([a-z]+)/) || [])[1] || '', (req.url.match(/subject=([a-z-]+)/) || [])[1] || '')); return;}
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
    async function open(owned=false, withList=false, newFormVariant='', subjectVariant='') {
      if (newFormVariant === true) newFormVariant = 'button';
      requests=[];
      await gotoApp(page, validateBaseUrl(`http://127.0.0.1:${server.address().port}`), `/fixture?owned=${owned ? 'owned' : 'no'}&selection=${withList ? 'list' : 'no'}&newform=${newFormVariant || 'no'}&subject=${subjectVariant || 'plain'}`);
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
    // A toolbar without the name field from the start still tracks a typed replacement number.
    await page.route('**/eformFloatingToolbar/eform_floating_toolbar', route => route.fulfill({
      status: 200, contentType: 'text/html',
      body: toolbar.replace(/<input[^>]*id="remoteFaxRecipient"[^>]*>/, '')}));
    await open();
    assert.equal(await page.locator('#remoteFaxRecipient').count(), 0);
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '416-555-0123');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxNumber').fill('416-555-0145');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('recipientFaxNumber'), '416-555-0145');
    // The displayed number keeps following the eForm's list and designer choices, and always
    // equals the number posted.
    await open(false, true);
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '416-555-0101');
    await page.locator('#faxnumList').selectOption('416-555-0102');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '416-555-0102');
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('recipientFaxNumber'), '416-555-0102');
    await open();
    await page.locator('#designerAddFax').click();
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '416-555-0191');
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('recipientFaxNumber'), '416-555-0191');
    // The shared eForm-recipients selector is filled and wired; its choice is kept over a later
    // list change, like a number typed here.
    await open(false, true);
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxFromForm option[value="416-555-0102"]').waitFor({state: 'attached'});
    await page.locator('#remoteFaxFromForm').selectOption('416-555-0102');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '416-555-0102');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#faxnumList').selectOption('416-555-0101');
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(), '416-555-0102');
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('recipientFaxNumber'), '416-555-0102');
    await page.unroute('**/eformFloatingToolbar/eform_floating_toolbar');
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
    // Markup as EForm.ensureNewFormInput() emits it beside a template's own newForm submit
    // button: the toolbar's save posts newForm=true once, the button posts only its own value,
    // and a cancelled button submission does not lose the flag.
    await open(false, false, true);
    await page.locator('#remoteSubmitButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.deepEqual(requests[0].getAll('newForm'), ['true']);
    await open(false, false, true);
    await page.locator('#newFormButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.deepEqual(requests[0].getAll('newForm'), ['False']);
    // A template onsubmit that disables its newForm button (against double submits) leaves no
    // newForm value in the submission; newForm=true must still be posted, once.
    await open(false, false, true);
    await page.evaluate(() => {
      document.forms[0].addEventListener('submit', () => {
        document.getElementById('newFormButton').disabled = true;
      });
    });
    await page.locator('#newFormButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.deepEqual(requests[0].getAll('newForm'), ['true']);
    // Same with an inline onsubmit attribute.
    await open(false, false, true);
    await page.evaluate(() => {
      document.forms[0].setAttribute('onsubmit', "document.getElementById('newFormButton').disabled = true;");
    });
    await page.locator('#newFormButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.deepEqual(requests[0].getAll('newForm'), ['true']);
    // A template that disables its button before submitting with it posts no button value.
    await open(false, false, true);
    await page.locator('#newFormDisablingButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.deepEqual(requests[0].getAll('newForm'), ['true']);
    await open(false, false, true);
    await page.evaluate(() => {
      document.forms[0].addEventListener('submit', event => {
        event.preventDefault();
        window.cancelledSubmit = true;
      }, {once: true});
    });
    await page.locator('#newFormButton').click();
    await page.waitForFunction(() => window.cancelledSubmit === true);
    assert.equal(requests.length, 0);
    await page.locator('#remoteSubmitButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.deepEqual(requests[0].getAll('newForm'), ['true']);
    // The fallback stays in the page for template scripts (id, name, value) but is never
    // serialized itself; newForm is reconciled against what the browser actually submits.
    await open(false, false, 'checkbox');
    assert.deepEqual(await page.evaluate(() => {
      const fallback = document.getElementById('newForm');
      return [fallback.name, fallback.value, fallback.disabled];
    }), ['newForm', 'true', true]);
    // A template newForm control whose state changes after load posts only its own value, both
    // through the toolbar's save and through a submitter-less form.submit(); one that still
    // contributes nothing leaves newForm=true, once.
    for (const variant of ['checkbox', 'listbox', 'disabled']) {
      for (const path of ['toolbar', 'form.submit']) {
        for (const changed of [false, true]) {
          await open(false, false, variant);
          // A literal page function switched on the variant name, so evaluate() never runs a
          // function looked up at run time.
          if (changed) {
            await page.evaluate((kind) => {
              const control = document.getElementById('newFormControl');
              if (kind === 'checkbox') control.checked = true;
              else if (kind === 'listbox') control.options[0].selected = true;
              else if (kind === 'disabled') control.disabled = false;
              else throw new Error(`unknown newForm control variant: ${kind}`);
            }, variant);
          }
          if (path === 'toolbar') await page.locator('#remoteSubmitButton').click();
          else await page.evaluate(() => document.forms[0].submit());
          await page.waitForURL('**/eform/addEForm');
          assert.equal(requests.length, 1, `${variant} ${path} ${changed}`);
          assert.deepEqual(requests[0].getAll('newForm'), changed ? ['False'] : ['true'],
            `${variant} via ${path}, changed=${changed}`);
        }
      }
    }
    // A template that submits newForm by name only gets no fallback input, just the form default:
    // its own value posts alone, and if its script disables the control newForm=true still posts.
    for (const path of ['toolbar', 'form.submit']) {
      for (const disable of [false, true]) {
        await open(false, false, 'named');
        assert.equal(await page.locator('input[data-carlos-newform-fallback]').count(), 0);
        if (disable) await page.evaluate(() => { document.getElementById('namedNewForm').disabled = true; });
        if (path === 'toolbar') await page.locator('#remoteSubmitButton').click();
        else await page.evaluate(() => document.forms[0].submit());
        await page.waitForURL('**/eform/addEForm');
        assert.equal(requests.length, 1, `named ${path} ${disable}`);
        assert.deepEqual(requests[0].getAll('newForm'), disable ? ['true'] : ['False'],
          `named via ${path}, disabled=${disable}`);
      }
    }
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
    // A required but disabled (directly or through a disabled fieldset) or readonly template
    // subject is excluded from validation, so an empty toolbar subject still saves.
    for (const disable of ['direct', 'fieldset', 'readonly']) {
      await open();
      await page.evaluate(mode => {
        const subject = document.getElementById('subject');
        if (mode === 'readonly') { subject.readOnly = true; return; }
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
      // A disabled template subject is not a successful control, so no subject is posted; a
      // readonly one still submits (empty).
      assert.equal(requests[0].get('subject'), disable === 'readonly' ? '' : null);
    }
    // Template-authored readonly: a text subject is exempt from its requirement.
    await open(false, false, false, 'readonly-text');
    await page.locator('#remote_eform_subject').fill('');
    await page.locator('#remoteSubmitButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    // An unknown type such as "textbox" is a text field to the browser, so readonly applies.
    await open(false, false, false, 'readonly-textbox');
    await page.locator('#remote_eform_subject').fill('');
    await page.locator('#remoteSubmitButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    // required is ignored on an authored hidden subject (a hidden input is never
    // constraint-validated and has no label), so it must not block the toolbar's save.
    await open(false, false, false, 'required-hidden');
    await page.locator('#remote_eform_subject').fill('');
    await page.locator('#remoteSubmitButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].get('subject'), '');
    // A required checkbox subject means "checked", which toolbar text cannot express, and the
    // hidden control can no longer be checked: checked or unchecked, an empty toolbar subject saves.
    for (const checkboxVariant of ['readonly-checkbox', 'required-checkbox']) {
      await open(false, false, false, checkboxVariant);
      await page.locator('#remote_eform_subject').fill('');
      await page.locator('#remoteSubmitButton').click();
      await page.waitForURL('**/eform/addEForm');
      assert.equal(requests.length, 1, checkboxVariant);
    }
    // A required text subject still blocks an empty toolbar subject.
    await open();
    const textUrl = page.url();
    await page.locator('#remote_eform_subject').fill('');
    await page.locator('#remoteSubmitButton').click();
    assert.equal(await page.evaluate(() => document.activeElement.id), 'remote_eform_subject');
    assert.equal(page.url(), textUrl);
    assert.equal(requests.length, 0);
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
    // Browsers without the formdata event (Safari before 15, which also lacks
    // SubmitEvent.submitter) keep the fallback enabled; a template newForm submit button must
    // still post only its own value, also from outside the form, while a save without it, a
    // button disabled by onsubmit, and a cancelled then retried save each post newForm=true once.
    await page.emulateMedia({media:'screen'});
    await page.setViewportSize({width:1100,height:800});
    await page.addInitScript(() => {
      delete window.FormDataEvent;
      Object.defineProperty(SubmitEvent.prototype, 'submitter', {configurable: true, get() { return undefined; }});
      // Chromium still dispatches formdata; a browser without the event never delivers it.
      window.addEventListener('formdata', event => event.stopImmediatePropagation(), true);
    });
    const legacyCases = [
      ['toolbar', ['true']],
      ['button', ['False']],
      ['external', ['False']],
      ['onsubmit-disables', ['true']],
      ['cancelled-then-toolbar', ['true']],
      // An image button named newForm posts newForm.x/newForm.y, never newForm: the fallback stays.
      ['image', ['true']],
      // onsubmit disables the button's fieldset: it submits nothing though .disabled stays false.
      ['fieldset-onsubmit-disables', ['true']],
    ];
    for (const [legacyCase, expected] of legacyCases) {
      await open(false, false, true);
      assert.equal(await page.evaluate(() => typeof window.FormDataEvent), 'undefined');
      assert.equal(await page.evaluate(() => document.getElementById('newForm').disabled), false);
      await page.evaluate((kind) => {
        const form = document.forms[0];
        if (kind === 'external') {
          const button = document.createElement('button');
          button.id = 'externalNewFormButton';
          button.type = 'submit';
          button.name = 'newForm';
          button.value = 'False';
          button.setAttribute('form', 'saveEFormForm');
          form.id = 'saveEFormForm';
          document.body.append(button);
        } else if (kind === 'onsubmit-disables') {
          form.addEventListener('submit', () => { document.getElementById('newFormButton').disabled = true; });
        } else if (kind === 'fieldset-onsubmit-disables') {
          const button = document.getElementById('newFormButton');
          const fieldset = document.createElement('fieldset');
          fieldset.id = 'newFormFieldset';
          button.replaceWith(fieldset);
          fieldset.append(button);
          form.addEventListener('submit', () => { fieldset.disabled = true; });
        } else if (kind === 'image') {
          const image = document.createElement('input');
          image.id = 'imageNewFormButton';
          image.type = 'image';
          image.name = 'newForm';
          image.alt = 'Save as image';
          image.style.cssText = 'display:inline-block;width:40px;height:20px;';
          form.prepend(image);
        } else if (kind === 'cancelled-then-toolbar') {
          form.addEventListener('submit', event => { event.preventDefault(); window.cancelledSubmit = true; }, {once: true});
        }
      }, legacyCase);
      if (legacyCase === 'toolbar') {
        await page.locator('#remoteSubmitButton').click();
      } else if (legacyCase === 'external') {
        await page.locator('#externalNewFormButton').click();
      } else if (legacyCase === 'image') {
        await page.locator('#imageNewFormButton').click();
      } else {
        await page.locator('#newFormButton').click();
      }
      if (legacyCase === 'cancelled-then-toolbar') {
        await page.waitForFunction(() => window.cancelledSubmit === true);
        assert.equal(requests.length, 0);
        await page.waitForFunction(() => document.getElementById('newForm').disabled === false);
        await page.locator('#remoteSubmitButton').click();
      }
      await page.waitForURL('**/eform/addEForm');
      assert.equal(requests.length, 1, legacyCase);
      assert.deepEqual(requests[0].getAll('newForm'), expected, `legacy ${legacyCase}`);
    }
    // Without formdata, a name-only template's form default is supplied by a temporary input: its
    // own value posts alone, and when its script disables the control newForm=true posts, once,
    // through the toolbar's save, a native submit button and a direct form.submit().
    for (const path of ['toolbar', 'native', 'native-onsubmit', 'form.submit', 'fieldset-button']) {
      for (const disable of [false, true]) {
        await open(false, false, 'named');
        if (path === 'fieldset-button') {
          // The name-only control is off, so only a newForm submit button in a fieldset can
          // contribute; when onsubmit disables that fieldset nothing does, and the default applies.
          await page.evaluate((disableFieldset) => {
            const form = document.forms[0];
            document.getElementById('namedNewForm').disabled = true;
            const fieldset = document.createElement('fieldset');
            fieldset.innerHTML = '<button id="fieldsetNewFormButton" type="submit" name="newForm" value="False">Save</button>';
            form.prepend(fieldset);
            if (disableFieldset) {
              form.addEventListener('submit', () => { fieldset.disabled = true; });
            }
          }, disable);
        } else if (disable && path === 'native-onsubmit') {
          // Disabled by the template's own onsubmit, after the click but before serialization.
          await page.evaluate(() => {
            document.forms[0].setAttribute('onsubmit', "document.getElementById('namedNewForm').disabled = true;");
          });
        } else if (disable) {
          await page.evaluate(() => { document.getElementById('namedNewForm').disabled = true; });
        }
        if (path === 'toolbar') await page.locator('#remoteSubmitButton').click();
        else if (path === 'form.submit') await page.evaluate(() => document.forms[0].submit());
        else if (path === 'fieldset-button') await page.locator('#fieldsetNewFormButton').click();
        else await page.evaluate(() => document.querySelector('input[name=SubmitButton]').click());
        await page.waitForURL('**/eform/addEForm');
        assert.equal(requests.length, 1, `legacy named ${path} ${disable}`);
        assert.deepEqual(requests[0].getAll('newForm'), disable ? ['true'] : ['False'],
          `legacy named via ${path}, disabled=${disable}`);
      }
    }
    await page.emulateMedia({media:'print'});
    assert.deepEqual(errors,[]);
    console.log('PASS toolbar layout, print CSS, hidden subject, early fax initialization, existing input, directory selection and manual recipient POST');
  } finally {await browser.close(); server.close();}
})().catch(error => { console.error(error); server.close(); process.exitCode=1; });

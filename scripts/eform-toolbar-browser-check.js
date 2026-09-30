#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// Isolated browser regression using the shipped JSP fragment, styles and scripts.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const {chromium} = require('playwright');
const web = path.join(__dirname, '../src/main/webapp');
const read = file => fs.readFileSync(path.join(web, file), 'utf8');
const toolbar = read('WEB-INF/jsp/eform/eformFloatingToolbar/eform_floating_toolbar.jspf')
  .replace(/<%[\s\S]*?%>/g, '').replace(/<fmt:[^>]*\/>/g, '')
  .replace(/\$\{[^}]*\}/g, '');
const errors = [];
let requests = [];
let searches = [];
const fixture = (owned = false) => `<!doctype html><html><head>
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
<input id="subject" name="subject" value="Designer subject" required>
${owned ? '<input id="otherFaxInput" name="otherFaxInput" value="original">' : ''}
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
  if (req.url.startsWith('/fixture')) {res.setHeader('Content-Type','text/html'); res.end(fixture(req.url.includes('owned'))); return;}
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
    async function open(owned=false) {
      requests=[];
      await page.goto(`http://127.0.0.1:${server.address().port}/fixture${owned ? '?owned' : ''}`);
      await page.locator('#remoteFaxButton').waitFor();
      assert.equal(await page.locator('#otherFaxInput').count(),1);
      assert.equal(await page.locator('#otherFaxInput').inputValue(),'416-555-0123');
      assert.equal(await page.locator('#subject').isVisible(),false);
      assert.equal(await page.locator('label[for=subject]').isVisible(),false);
      assert.equal(await page.locator('#remote_eform_subject').inputValue(),'Designer subject');
      assert.equal(await page.locator('input[name=pdfButton]').isVisible(),false);
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
    await page.locator('#remoteFaxRecipient').fill('Example');
    await page.locator('#remoteFaxSuggestions .fax-ac-item').click().catch(async error => {
      console.error({searches, state: await page.evaluate(() => ({value: document.getElementById('remoteFaxRecipient').value, active: document.activeElement.id, open: document.getElementById('remoteFaxOptions').open}))});
      throw error;
    });
    assert.equal(await page.locator('#remoteFaxNumber').inputValue(),'416-555-0199');
    await page.locator('#remoteFaxOptions summary').click();
    await page.locator('#remoteFaxButton').click();
    await page.waitForURL('**/eform/addEForm');
    assert.equal(requests[0].get('recipientFaxNumber'),'416-555-0199');
    assert.equal(requests[0].get('recipient'),'Example, Specialist');
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
    for (const [button, mode] of [['remotePrintPdfButton','print'], ['remoteSavePdfButton',null]]) {
      await open();
      await page.locator('#remotePrintOptions summary').click();
      await page.locator('#'+button).click();
      await page.waitForURL('**/eform/addEForm');
      assert.equal(requests[0].get('saveAndDownloadEForm'),'true');
      assert.equal(requests[0].get('eformPdfOutput'),mode);
    }
    await open();
    await page.emulateMedia({media:'print'});
    assert.equal(await page.locator('#toolbarWrapper').isVisible(),false);
    assert.deepEqual(errors,[]);
    console.log('PASS toolbar layout, print CSS, hidden subject, early fax initialization, existing input, directory selection and manual recipient POST');
  } finally {await browser.close(); server.close();}
})().catch(error => { console.error(error); server.close(); process.exitCode=1; });

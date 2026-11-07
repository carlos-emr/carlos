/** Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
'use strict';

// Exercise the shipped pad and jQuery in Chromium: a rejected/redirected upload must
// never become a saved-signature event in a prescription, consultation or eForm.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const web = path.resolve(__dirname, '../src/main/webapp');
const key = '0123456789abcdef0123456789abcdef';
// Render only the JavaScript body of the JSP-backed library, as an enabled installation
// serves it. Its only scriptlet output is the context path; the real JSP is also compiled.
const control = fs.readFileSync(path.join(web, 'WEB-INF/jsp/library/eforms/signatureControl.jsp'), 'utf8')
  .replace(/<%= request.getContextPath\(\) %>/g, '')
  .replace(/<%[\s\S]*?%>/g, '');

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'],
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
  try {
    const page = await browser.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    let result;
    let uploads = [];
    await page.route('http://signature.test/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/signature_pad/SaveSignatureUpload') {
        uploads.push(new URLSearchParams(route.request().postData()));
        await route.fulfill({ status: result.status, contentType: 'text/html', body: result.body });
      } else if (url.pathname === '/control.js') {
        await route.fulfill({ contentType: 'text/javascript', body: control });
      } else if (url.pathname === '/control') {
        await route.fulfill({ contentType: 'text/html', body: `<!doctype html>
          <script src="/library/jquery/jquery-3.7.1.min.js"></script><script src="/control.js"></script>
          <input name="demographicNo" value="42"><input id="signatureValue"><div id="signatureDisplay"></div>
          <div id="signatureInput"></div><div id="letter"></div>
          <script>
            if (${url.searchParams.get('mode') === 'rtl'}) {
              signatureControl.initialize({
                sigHTML: '/signature_pad/tabletSignature?inWindow=true&saveToDB=true&demographicNo=',
                demographicNo: '84', signatureInput: '#signatureInput',
                refreshImage: function(event) { jQuery('#letter').append(jQuery('<img>').attr('src', event.storedImageUrl)); }
              });
            } else { signatureControl.initialize(); }
          </script>` });
      } else if (url.pathname === '/imageRenderingServlet') {
        await route.fulfill({ contentType: 'image/png', body: Buffer.from(
          'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6nAAAAABJRU5ErkJggg==', 'base64') });
      } else if (url.pathname === '/fixture' || url.pathname === '/signature_pad/tabletSignature') {
        await route.fulfill({ contentType: 'text/html', body: `<!doctype html>
          <script src="/library/jquery/jquery-3.7.1.min.js"></script>
          <script>
            var _in_window = true, contextPath = '', requestIdKey = '${key}';
            var previewImageUrl = '/imageRenderingServlet?source=signature_preview&signatureRequestId=${key}';
            var storedImageUrl = '/imageRenderingServlet?source=signature_stored&digitalSignatureId=';
            var events = [], alerts = [];
            window.alert = message => alerts.push(message);
            function signatureHandler(event) { events.push(event); }
          </script>
          <script src="/share/javascript/TabletSignature.js"></script>
          <canvas id="canvas"></canvas><span id="signMessage">Sign above</span>
          <button id="save" style="display:none">Save</button><button id="clear" style="display:none">Clear</button>
          <form id="signatureForm" method="post" action="/signature_pad/SaveSignatureUpload">
            <input id="signatureImage" name="signatureImage"><input name="source" value="IPAD">
            <input name="signatureRequestId" value="${key}">
            <input name="saveToDB" value="${url.searchParams.get('persist') || url.searchParams.get('saveToDB')}">
            <input name="demographicNo" value="${url.searchParams.get('demographicNo') || ''}">
            <input name="ModuleType" value="${url.searchParams.get('ModuleType') || ''}">
          </form>` });
      } else {
        const assets = {
          '/library/jquery/jquery-3.7.1.min.js': 'library/jquery/jquery-3.7.1.min.js',
          '/share/javascript/TabletSignature.js': 'share/javascript/TabletSignature.js',
        };
        if (!assets[url.pathname]) return route.fulfill({ status: 404, body: '' });
        await route.fulfill({ contentType: 'text/javascript', body: fs.readFileSync(path.join(web, assets[url.pathname]), 'utf8') });
      }
    });

    async function draw(persist) {
      uploads = [];
      await page.goto('http://signature.test/fixture?persist=' + persist);
      const box = await page.locator('#canvas').boundingBox();
      await page.mouse.move(box.x + 20, box.y + 20);
      await page.mouse.down();
      await page.mouse.move(box.x + 150, box.y + 70, { steps: 12 });
      await page.mouse.up();
      await page.locator('#save').waitFor({ state: 'visible' });
      return page.locator('#canvas').evaluate(canvas => canvas.toDataURL());
    }

    for (const rejected of [
      { status: 403, body: 'Unknown signature request' },
      { status: 500, body: 'Save failed' },
      { status: 200, body: '<html><body>Log in</body></html>' },
      { status: 200, body: '<input name="signatureId" value="">' },
      { status: 200, body: '<input name="signatureId" value="undefined">' },
    ]) {
      const drawing = await draw(true);
      result = rejected;
      await page.locator('#save').click();
      await page.waitForFunction(() => alerts.length === 1);
      assert.equal(await page.evaluate(() => events.some(event => event.isSave)), false);
      assert.equal(await page.locator('#save').isVisible(), true);
      assert.equal(await page.locator('#canvas').evaluate(canvas => canvas.toDataURL()), drawing);
      assert.equal(uploads[0].get('signatureRequestId'), key);
      if (rejected.status === 403) assert.match(await page.evaluate(() => alerts[0]), /reopen.*expired/);

      result = { status: 200, body: '<input type="hidden" name="signatureId" value="73"/>' };
      await page.locator('#save').click();
      await page.waitForFunction(() => events.some(event => event.isSave));
      assert.equal(await page.evaluate(() => events.at(-1).storedImageUrl), storedUrl(73));
      assert.equal(await page.locator('#save').isVisible(), false);
    }

    // Consultations retain their temporary capture until the parent form is saved.
    await draw(false);
    result = { status: 200, body: '<input type="hidden" name="signatureId" value=""/>' };
    await page.locator('#save').click();
    await page.waitForFunction(() => events.some(event => event.isSave));
    assert.equal(await page.evaluate(() => alerts.length), 0);
    assert.equal(await page.evaluate(() => events.at(-1).requestIdKey), key);

    for (const mode of ['eform', 'rtl']) {
      uploads = [];
      await page.goto('http://signature.test/control?mode=' + mode);
      const frame = page.frameLocator('#signatureFrame');
      const padUrl = new URL(await page.locator('#signatureFrame').getAttribute('src'), page.url());
      assert.equal(padUrl.searchParams.get('demographicNo'), mode === 'rtl' ? '84' : '42');
      assert.equal(padUrl.searchParams.get('ModuleType'), 'E_FORM');
      const box = await frame.locator('#canvas').boundingBox();
      await page.mouse.move(box.x + 10, box.y + 20);
      await page.mouse.down();
      await page.mouse.move(box.x + 150, box.y + 60, { steps: 12 });
      await page.mouse.up();
      result = { status: 200, body: '<input type="hidden" name="signatureId" value="73"/>' };
      await frame.locator('#save').click();
      const image = page.locator(mode === 'rtl' ? '#letter img' : '#signatureDisplay img');
      await image.waitFor();
      await image.evaluate(img => img.decode());
      assert.equal(uploads[0].get('demographicNo'), mode === 'rtl' ? '84' : '42');
      assert.equal(uploads[0].get('ModuleType'), 'E_FORM');
      if (mode === 'eform') assert.match(await page.locator('#signatureValue').inputValue(), /digitalSignatureId=73/);
    }
    assert.deepEqual(errors, []);
    console.log('PASS: rejected uploads, login HTML, invalid IDs, retained drawings, retries, temporary captures and RTL/eForm patient-bound saves');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });

function storedUrl(id) { return '/imageRenderingServlet?source=signature_stored&digitalSignatureId=' + id; }

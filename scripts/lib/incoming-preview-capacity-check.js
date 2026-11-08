/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {assert} = require('./playwright-harness');

function capacityTarget(value) {
  try {const url = new URL(value);url.hash = '';return url.href;} catch (_) {return null;}
}
function expectedCapacityConsole(entry, targets) {
  return entry.type === 'error' && targets.has(capacityTarget(entry.location?.url))
    && /^Failed to load resource:.*503/.test(entry.text || '');
}

/** Compatibility read for viewer HTML and CDP charset loss; live flow captures its original request below. */
async function recoveredPreviewBytes(context, response, mode) {
  assert(mode==='Pdf'||mode==='Image','Unsupported incoming preview mode');
  // DevTools may expose the PDF viewer's generated HTML as the navigation body.
  // Read the same authorized, read-only destination without the viewer, refusing
  // redirects or an HTML login/error page even when the UI navigation was 200.
  const raw = await context.request.get(response.url(), {timeout: 120000, maxRedirects: 0});
  try {
    const mime=mode==='Pdf'?/^application\/pdf(?:;|$)/i:/^image\/png(?:;|$)/i;
    assert(raw.status() === 200 && mime.test(raw.headers()['content-type'] || ''),
      'recovered preview destination did not return its successful content type');
    const bytes = await raw.body();
    if(mode==='Pdf')assert(bytes.length > 100 && bytes.subarray(0, 5).toString() === '%PDF-'
      && /%%EOF\s*$/.test(bytes.subarray(-1024).toString()), 'recovered PDF destination returned an incomplete PDF');
    else require('pngjs').PNG.sync.read(bytes);
    return bytes;
  } finally { await raw.dispose(); }
}

/** Exercise both native incoming-preview modes using the workflow's owned PDF. */
async function checkIncomingPreviewCapacity(session, page, ownedName, {onCaptured}={}) {
  const pattern = '**/documentManager/ManageDocument?*';
  const targets = new Set();
  const forwarded=new Set(),forwardFailures=[];
  let method, attempts,captured;
  const matches = value => {
    const url = new URL(value);
    return url.pathname.endsWith('/documentManager/ManageDocument')
      && url.searchParams.get('method') === method && url.searchParams.get('pdfName') === ownedName;
  };
  // Match the server's GET503 contract; production markup/encoding is separately
  // verified by ManageDocument action tests. The script and final bytes are real.
  const scriptPath = new URL(`${session.config.baseUrl}/js/incomingDocumentCapacityWait.js`).pathname;
  const escapedPath = scriptPath.replace(/[&<>"']/g, char => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[char]));
  const busyHtml = '<!doctype html><html><body><p id="incomingDocumentCapacityWait" role="status">Waiting for document capacity.</p>'
    + '<script src="' + escapedPath + '"></script></body></html>';
  const routeHandler = async route => {
    if (!matches(route.request().url())) return route.continue();
    assert(route.request().method() === 'GET', 'incoming preview retry attempted a mutation');
    attempts++;
    if (attempts > 5) {
      const {trackWrite,retainFixtures}=require('./uncertain-server-writes');
      const operation=(async()=>{
        let response,acknowledged=false;
        try {
          response=await route.fetch({maxRedirects:0,maxRetries:0,timeout:120000});
          const body=await response.body();acknowledged=true;
          captured={body,status:response.status(),headers:response.headers(),request:route.request()};
          if(onCaptured&&method==='viewIncomingDocPageAsImage')onCaptured(route.request(),captured);
          await route.fulfill({response,body});
        }catch(error){
          if(!acknowledged)retainFixtures(session.context);forwardFailures.push(error);
          try{await route.abort();}catch(abortError){forwardFailures.push(abortError);}
        } finally {if(response)try{await response.dispose();}catch(error){forwardFailures.push(error);}}
      })();forwarded.add(operation);operation.then(()=>forwarded.delete(operation),()=>forwarded.delete(operation));
      trackWrite(session.context,operation);return operation;
    }
    targets.add(capacityTarget(route.request().url()));
    return route.fulfill({status: 503, contentType: 'text/html', headers: {'Retry-After': '1', 'Cache-Control': 'no-store'}, body: busyHtml});
  };
  await page.route(pattern, routeHandler);
  try {
    for (const [mode, suffix, contentType] of [['Pdf', 'Pdf', 'application/pdf'], ['Image', 'Image', 'image/png']]) {
      method = `viewIncomingDocPageAs${suffix}`; attempts = 0;captured=undefined;
      const ready = page.waitForResponse(response => matches(response.url()) && response.status() === 200
        && response.headers()['content-type']?.startsWith(contentType), {timeout: 120000});
      ready.catch(() => {}); // keep a failed UI action from leaving an unhandled waiter
      await Promise.all([page.waitForEvent('domcontentloaded', {timeout: 120000}), page.locator('#imageTypeList').selectOption(mode)]);
      await page.frameLocator('#docdisp iframe').locator('#incomingDocumentCapacityWait').waitFor({timeout: 120000});
      assert(await page.frameLocator('#docdisp iframe').locator('#incomingDocumentCapacityWait').getAttribute('role') === 'status',
        'incoming capacity wait must be accessible inside the preview');
      const response = await ready;
      await Promise.allSettled([...forwarded]);
      if(forwardFailures.length)throw new AggregateError(forwardFailures,'Native preview forwarding failed');
      assert(captured&&captured.request===response.request()&&captured.status===response.status(),'Recovered preview lacks its actual upstream response');
      const bytes=captured.body;
      assert(bytes.length > 100, `${mode} recovered to an empty preview`);
      assert(attempts === 6, `${mode} stopped waiting or did not recover after five capacity responses`);
      if (mode === 'Pdf') assert(bytes.subarray(0, 5).toString() === '%PDF-', 'recovered PDF body is not a PDF');
      else assert(bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), 'recovered image body is not PNG');
    }
    // Only responses deliberately injected and subsequently recovered are expected.
    session.recorder.badResponses = session.recorder.badResponses.filter(entry => !(entry.status === 503 && targets.has(capacityTarget(entry.url))));
    session.recorder.consoleIssues = session.recorder.consoleIssues.filter(entry => !expectedCapacityConsole(entry, targets));
  } finally {await page.unroute(pattern,routeHandler);await Promise.allSettled([...forwarded]);if(forwardFailures.length)throw new AggregateError(forwardFailures,'Native preview forwarding failed');}
}
module.exports = {checkIncomingPreviewCapacity, recoveredPreviewBytes, capacityTarget, expectedCapacityConsole};

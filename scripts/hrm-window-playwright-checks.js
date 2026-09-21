#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Real Chromium window/iframe regression checks using the shipped HRM actions and Inboxhub
 * handlers, real jQuery AJAX and BroadcastChannel. A loopback fixture supplies report HTML and
 * JSON; installed-application checks are still needed for Struts, filters and persistence.
 * Runs one browser context at a time. CHROME_BIN may select the packaged Chromium.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');
const { getLaunchOptions } = require('./lib/playwright-harness');
const web = path.join(__dirname, '../src/main/webapp');
const jsp = fs.readFileSync(path.join(web, 'WEB-INF/jsp/web/inboxhub/InboxhubForm.jsp'), 'utf8');
function slice(from, to) {
  const start = jsp.indexOf(from), end = jsp.indexOf(to, start);
  assert(start >= 0 && end > start, 'Inboxhub source anchors changed');
  return jsp.slice(start, end);
}
const inboxScript = slice('    function refreshInboxhubAfterHrmRevoke()',
  '    /**\n     * Resets all inbox filters');
let signed = false, fail = false, mutations = 0, reloads = 0;
function report(inboxWindow = false, inline = false) {
  return `<section id="hrmdoc_7" data-inbox-window="${inboxWindow}" data-inbox-inline="${inline}">
    <input type="button" id="signoff7" value="${signed ? 'Revoke Sign-Off' : 'Sign-Off'}"
      onclick="doSignOff(7, ${!signed})"><span id="signoffstatus7"></span>
    <input id="commentField_7_hrm"><button id="comment" onclick="addComment(7)">Comment</button>
    <span id="commentstatus7"></span></section>`;
}
const dependencies = '<script src="/jquery.js"></script><script>var contextpath="";</script><script src="/hrmActions.js"></script>';
function inbox(url) {
  const mode = url.searchParams.get('mode') || 'popup';
  const target = '/report' + (mode === 'coop' ? '?coop=1' : mode === 'chart' ? '?chart=1' : '');
  const controls = `<button id="open" onclick="window.open('${target}', '_blank', 'width=800,height=700')">Open HRM</button>`;
  if (mode === 'legacy') {
    return dependencies + `<script>function refreshCategoryList(){document.getElementById('refreshed').textContent='yes';}</script>
      <span id="refreshed"></span><div id="labdoc_7" data-lab-type="HL7">Other lab</div>
      <div id="labdoc_7" data-lab-type="HRM">${report(false, true)}</div>`;
  }
  return dependencies + `<form id="inboxSearchForm" method="post" action="/inbox">
      <input name="filter" value="kept"></form>
    <input id="totalHRMCount" value="${signed ? 1 : 2}"><input id="totalResultsCount" value="${signed ? 2 : 3}">
    <div id="inboxViewItems">
      ${signed ? '' : `<div class="document-card card" data-segment-id="7" data-lab-type="HRM">
        ${mode === 'iframe' ? '<iframe src="/report?chart=1"></iframe>' : 'HRM report'}</div>`}
      <div class="document-card card" data-segment-id="8" data-lab-type="HRM">Remaining HRM</div>
      <div class="document-card card" data-segment-id="7" data-lab-type="HL7">Other lab</div>
    </div>${controls}<script>
      var hasMoreData=false, rapidReviewState=false, pendingRapidReviewOpen=false;
      function showInboxhubStats() {}
      function fetchInboxhubData(){throw new Error('Unexpected full list fetch');}
      function openNextInboxItem() {}
      ${inboxScript}
    </script>`;
}
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  if (url.pathname === '/jquery.js' || url.pathname === '/hrmActions.js') {
    res.setHeader('Content-Type', 'application/javascript');
    return res.end(fs.readFileSync(path.join(web, url.pathname === '/jquery.js'
      ? 'library/jquery/jquery-3.7.1.min.js' : 'hospitalReportManager/hrmActions.js')));
  }
  if (url.pathname === '/hospitalReportManager/Modify') {
    let body = ''; for await (const chunk of req) body += chunk;
    assert.equal(req.method, 'POST');
    assert.equal(req.headers['x-requested-with'], 'XMLHttpRequest');
    const data = new URLSearchParams(body);
    mutations++;
    let clearedCount = 0;
    if (!fail && data.get('method') === 'signOff') {
      const next = data.get('signedOff') === '1';
      clearedCount = next && !signed ? 2 : 0;
      signed = next;
    }
    res.setHeader('Content-Type', 'application/json');
    return res.end(JSON.stringify({success: !fail, message: fail ? 'Test failure' : 'Success', clearedCount}));
  }
  res.setHeader('Content-Type', 'text/html');
  if (url.pathname === '/report') {
    if (url.searchParams.has('coop')) res.setHeader('Cross-Origin-Opener-Policy', 'same-origin');
    return res.end(dependencies + report(!url.searchParams.has('chart')));
  }
  if (req.method === 'POST') {
    let body = ''; for await (const chunk of req) body += chunk;
    assert.equal(new URLSearchParams(body).get('filter'), 'kept');
    reloads++;
  }
  res.end(inbox(url));
});
async function main() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch(getLaunchOptions(process.env.CHROME_BIN));
  try {
    for (const mode of ['popup', 'coop', 'fallback', 'iframe', 'legacy', 'chart', 'failure']) {
      signed = false; fail = mode === 'failure'; mutations = 0; reloads = 0;
      const context = await browser.newContext();
      const errors = [];
      context.on('page', page => page.on('pageerror', error => errors.push(error.message)));
      if (mode === 'fallback') await context.addInitScript(() => { window.BroadcastChannel = undefined; });
      const page = await context.newPage();
      await page.goto(`${base}/inbox?mode=${mode}`);
      let viewer;
      if (mode === 'iframe') {
        viewer = page.frameLocator('iframe');
      } else if (mode === 'legacy') {
        viewer = page;
      } else {
        const popup = page.waitForEvent('popup');
        await page.locator('#open').click();
        viewer = await popup;
        await viewer.waitForLoadState('load');
        if (mode === 'coop') assert.equal(await viewer.evaluate(() => window.opener), null);
      }
      if (mode === 'failure') {
        await viewer.locator('#signoff7').click();
        await viewer.locator('#signoffstatus7').filter({hasText: 'Test failure'}).waitFor();
        assert.equal(await page.locator('#totalHRMCount').inputValue(), '2');
        assert(!viewer.isClosed());
        fail = false;
      }
      if (mode === 'legacy') {
        await viewer.locator('#signoff7').click();
        await viewer.locator('#hrmdoc_7').waitFor({state: 'hidden'});
        assert.equal(await page.locator('[data-lab-type="HL7"]').textContent(), 'Other lab');
        assert.equal(await page.locator('#refreshed').textContent(), 'yes');
      } else {
        await viewer.locator('#signoff7').click();
        await page.waitForFunction(() => document.getElementById('totalHRMCount').value === '1');
        assert.equal(await page.locator('[data-lab-type="HRM"]').count(), 1);
        assert.equal(await page.locator('[data-segment-id="8"]').count(), 1);
        assert.equal(await page.locator('[data-lab-type="HL7"]').count(), 1);
        if (mode === 'chart') {
          assert(!viewer.isClosed());
          await viewer.locator('#signoff7').click();
          await page.waitForFunction(() => document.getElementById('totalHRMCount').value === '2');
          assert.equal(reloads, 1);
          assert(!viewer.isClosed());
          await viewer.locator('#signoff7').click();
          await page.waitForFunction(() => document.getElementById('totalHRMCount').value === '1');
        } else if (mode !== 'iframe') {
          // Do not stub window.close: this checks the actual browser window lifecycle.
          if (!viewer.isClosed()) await viewer.waitForEvent('close');
        }
      }
      assert.equal(mutations, mode === 'chart' ? 3 : mode === 'failure' ? 2 : 1);
      assert.deepEqual(errors, []);
      console.log(`PASS ${mode}: real AJAX, window lifecycle and inbox state`);
      await context.close();
    }
  } finally {
    await browser.close();
    await new Promise(resolve => server.close(resolve));
  }
}
main().catch(error => { console.error(error); server.close(); process.exitCode = 1; });

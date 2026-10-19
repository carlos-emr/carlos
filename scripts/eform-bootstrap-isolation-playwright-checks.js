#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Issue #3944: CARLOS's eForm chrome must not restyle the eForm it wraps.
 *
 * Both eForm renderers used to add the whole Bootstrap stylesheet to every eForm. Its reboot rules
 * (body margin and font, box-sizing on every element, table, heading and paragraph margins) and any
 * Bootstrap class a legacy form happens to use (.table, .row, .small, .container, .lead) moved
 * absolutely positioned fields and resized text, on screen and in print. Only the floating toolbar,
 * the save alert and the attachment picker use Bootstrap, so they now get a copy scoped to
 * .carlos-eform-ui (scripts/build-eform-scoped-bootstrap.js).
 *
 * This check needs no Tomcat, database or login. It serves the shipped static assets and toolbar
 * fragment from a loopback server, and it builds the eForm page head from the asset calls in
 * efmshowform_data.jsp and efmformadd_data.jsp, so a stylesheet added back to either renderer is
 * caught here rather than in a clinic's printout. For each renderer it asserts that:
 *
 *   1. every measured element of a legacy eForm has the same box and computed style as the same
 *      eForm opened on its own, on screen and in print media;
 *   2. the toolbar is still styled by Bootstrap (button colours, input-group, navbar, badge);
 *   3. the save alert is styled, positioned, works whatever `this` its caller binds, and does not
 *      move the eForm while it is shown;
 *   4. the attachment picker, which jQuery UI moves out of the toolbar to <body>, still hides the
 *      rows its script marks .d-none.
 *
 *   node scripts/eform-bootstrap-isolation-playwright-checks.js
 *
 * Optional environment:
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   EFORM_SCREENSHOT_DIR=/path/to/dir   save a full-page screenshot per renderer with the alert
 *                                       shown, and one with the attachment picker open
 */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');
const { getLaunchOptions } = require('./lib/playwright-harness');
const { differences, legacyEformHtml, measureEform } = require('./lib/eform-legacy-layout-fixture');

const WEB = path.join(__dirname, '..', 'src', 'main', 'webapp');
const JSP_DIR = path.join(WEB, 'WEB-INF', 'jsp', 'eform');
const RENDERERS = ['efmshowform_data', 'efmformadd_data'];

/** The toolbar fragment as the browser receives it: JSP directives and EL removed, markup kept. */
const TOOLBAR_FRAGMENT = fs.readFileSync(
  path.join(JSP_DIR, 'eformFloatingToolbar', 'eform_floating_toolbar.jspf'), 'utf8')
  .replace(/<%--[\s\S]*?--%>/g, '')
  .replace(/<%[\s\S]*?%>/g, '')
  .replace(/<fmt:[^>]*\/>/g, '')
  .replace(/\$\{[^}]*\}/g, '');

/*
 * A stand-in for attachDocument.jsp with the same dependency on Bootstrap: rows past the first page
 * and the empty preview states are hidden with .d-none, and the picker's script removes the class to
 * reveal them. Rendering the real JSP needs the application; only the class contract matters here.
 */
const ATTACH_PICKER = `<form id="attachDocumentsForm"><div class="attachmentContainer"><ul>
<li class="doc" id="visibleDoc"><label><input type="checkbox" class="document_check" id="docNo1" name="docNo" value="1"> Visible document</label></li>
<li class="doc d-none" id="hiddenDoc"><label><input type="checkbox" class="document_check" id="docNo2" name="docNo" value="2"> Older document</label></li>
</ul><button class="show-all-button d-none" type="button" id="showAllHidden">Show all</button></div></form>
<div id="preview-waiting" class="preview-advisory d-none" role="status">Loading preview</div>`;

/**
 * The stylesheets and scripts a renderer adds, in call order, read from the JSP itself. Only the
 * context-relative asset calls matter; hidden inputs and the font face do not affect styling.
 */
function rendererAssets(renderer) {
  // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- renderer is one of the RENDERERS constants
  const source = fs.readFileSync(path.join(JSP_DIR, `${renderer}.jsp`), 'utf8');
  const pattern = /\.(addHeadJavascript|addCSS|addBodyJavascript)\(\s*request\.getContextPath\(\)\s*\+\s*"([^"]+)"(?:\s*,\s*"([^"]+)")?\s*\)/g;
  const assets = [...source.matchAll(pattern)].map((match) => ({ kind: match[1], href: match[2], media: match[3] }));
  assert.ok(assets.some((asset) => asset.href.endsWith('eform_floating_toolbar.js')),
    `${renderer}.jsp: could not read its asset calls`);
  return assets;
}

function tag(asset) {
  if (asset.kind === 'addCSS') {
    return `<link href="${asset.href}" rel="stylesheet" media="${asset.media || 'all'}" type="text/css">`;
  }
  return `<script type="text/javascript" src="${asset.href}"></script>`;
}

/** The hidden inputs both renderers add and the toolbar script reads. */
const HIDDEN_INPUTS = '<input type="hidden" id="context" value=""><input type="hidden" id="fid" value="7">'
  + '<input type="hidden" id="fdid" value=""><input type="hidden" id="demographicNo" value="1">';

/**
 * The page a renderer serves for the legacy eForm, or the bare eForm when `renderer` is null.
 * EForm.addHeadElement inserts each head asset before the first head child that is not a title,
 * meta or style, so later calls land EARLIER ("first is last" in the JSP); body scripts are
 * appended in call order.
 */
function renderedPage(renderer) {
  const head = [];
  const body = [];
  if (renderer) {
    for (const asset of rendererAssets(renderer)) {
      if (asset.kind === 'addBodyJavascript') body.push(tag(asset));
      else head.unshift(tag(asset));
    }
  }
  return legacyEformHtml({
    headAssets: head.map((line) => `${line}\n`).join(''),
    hiddenInputs: HIDDEN_INPUTS,
    bodyEnd: body.map((line) => `${line}\n`).join(''),
  });
}

const CONTENT_TYPES = { '.css': 'text/css', '.js': 'text/javascript', '.png': 'image/png',
  '.jpg': 'image/jpeg', '.gif': 'image/gif', '.svg': 'image/svg+xml', '.ttf': 'font/ttf',
  '.woff': 'font/woff', '.woff2': 'font/woff2' };

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  const send = (type, body) => { res.setHeader('Content-Type', type); res.end(body); };
  if (url.pathname === '/eform/eformFloatingToolbar/eform_floating_toolbar') {
    return send('text/html', TOOLBAR_FRAGMENT);
  }
  if (url.pathname === '/previewDocs' && url.searchParams.get('method') === 'fetchEFormDocuments') {
    return send('text/html', ATTACH_PICKER);
  }
  if (url.pathname === '/eform/legacy-baseline') return send('text/html', renderedPage(null));
  const renderer = RENDERERS.find((name) => url.pathname === `/eform/${name}`);
  if (renderer) return send('text/html', renderedPage(renderer));
  if (url.pathname === '/eform/logEformError') return send('text/plain', '');
  // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- loopback fixture server; the resolved path must stay under WEB and outside WEB-INF (checked below)
  const target = path.resolve(WEB, `.${decodeURIComponent(url.pathname)}`);
  if (target.startsWith(WEB + path.sep) && !target.includes(`${path.sep}WEB-INF${path.sep}`)
      && fs.existsSync(target) && fs.statSync(target).isFile()) {
    return send(CONTENT_TYPES[path.extname(target)] || 'application/octet-stream', fs.readFileSync(target));
  }
  res.statusCode = 404;
  return res.end();
});

async function style(page, selector, properties) {
  // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- properties is always a literal CSS property-name list from this script
  return page.locator(selector).first().evaluate((element, names) => {
    const computed = getComputedStyle(element);
    return Object.fromEntries(names.map((name) => [name, computed[name]]));
  }, properties);
}

async function screenshot(page, name) {
  const directory = process.env.EFORM_SCREENSHOT_DIR;
  if (!directory) return;
  fs.mkdirSync(directory, { recursive: true });
  // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- the operator's own artifact directory and a fixed file name
  await page.screenshot({ path: path.join(directory, `eform-bootstrap-isolation-${name}.png`), fullPage: true });
}

async function baseline(page, origin) {
  const measured = {};
  for (const media of ['screen', 'print']) {
    await page.emulateMedia({ media });
    await page.goto(`${origin}/eform/legacy-baseline`, { waitUntil: 'load' }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- this script's own loopback server
    measured[media] = await measureEform(page);
  }
  await page.emulateMedia({ media: 'screen' });
  return measured;
}

async function checkRenderer(page, origin, renderer, expected) {
  const label = `${renderer}.jsp`;
  await page.goto(`${origin}/eform/${renderer}`, { waitUntil: 'load' }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- this script's own loopback server and a RENDERERS constant
  await page.locator('#toolbarWrapper #remoteSubmitButton').waitFor();

  // 1. The eForm itself, as the clinician sees and prints it.
  const onScreen = differences(expected.screen, await measureEform(page));
  assert.deepEqual(onScreen, [], `${label}: the eForm chrome restyled the eForm on screen`);
  await page.emulateMedia({ media: 'print' });
  const inPrint = differences(expected.print, await measureEform(page));
  assert.deepEqual(inPrint, [], `${label}: the eForm chrome restyled the eForm in print`);
  assert.equal((await style(page, '#toolbarWrapper', ['display'])).display, 'none',
    `${label}: the toolbar must not print`);
  await page.emulateMedia({ media: 'screen' });

  // 2. The toolbar keeps the Bootstrap look it was designed with.
  assert.equal(await page.locator('#toolbarWrapper').evaluate((wrapper) => wrapper.matches('.carlos-eform-ui')), true,
    `${label}: the toolbar wrapper is not in the Bootstrap scope`);
  assert.deepEqual(await style(page, '#remoteSubmitButton', ['backgroundColor', 'color', 'borderTopRightRadius', 'cursor']),
    { backgroundColor: 'rgb(13, 110, 253)', color: 'rgb(255, 255, 255)', borderTopRightRadius: '0px', cursor: 'pointer' },
    `${label}: Save is not a Bootstrap primary button inside an input group`);
  assert.deepEqual(await style(page, '#remote_eform_subject', ['borderTopColor', 'borderTopLeftRadius', 'paddingLeft', 'boxSizing']),
    { borderTopColor: 'rgb(222, 226, 230)', borderTopLeftRadius: '6px', paddingLeft: '12px', boxSizing: 'border-box' },
    `${label}: the subject field is not a Bootstrap form-control`);
  assert.deepEqual(await style(page, '#eform_floating_toolbar_nav', ['display', 'backgroundColor']),
    { display: 'flex', backgroundColor: 'rgb(248, 249, 250)' }, `${label}: the toolbar is not a light navbar`);
  assert.deepEqual(await style(page, '#remoteTotalAttachments', ['display', 'fontWeight', 'backgroundColor']),
    { display: 'inline-block', fontWeight: '700', backgroundColor: 'rgb(108, 117, 125)' },
    `${label}: the attachment count is not a Bootstrap badge`);
  assert.equal((await style(page, '#remotePrintOptions .eform-options small', ['display'])).display, 'block',
    `${label}: .d-block inside the toolbar menus no longer applies`);

  // 3. The save alert: styled, and callable with any receiver (it used to need this === window).
  // The toolbar places itself below the lowest piece of page content; the alert and dialog hosts are
  // box-less so that showing either never counts as content and pushes the toolbar down.
  const toolbarTop = () => page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(
    () => resolve(Math.round(document.getElementById('toolbarWrapper').getBoundingClientRect().top + window.scrollY))))));
  const restingTop = await toolbarTop();
  await page.evaluate(() => showErrorAlert.call(document.getElementById('legacyForm')));
  const alert = page.locator('#submit-error-alert');
  await alert.waitFor();
  await page.waitForFunction(() => getComputedStyle(document.getElementById('submit-error-alert')).opacity === '1');
  assert.equal(await alert.evaluate((element) => Boolean(element.closest('.carlos-eform-ui'))), true,
    `${label}: the alert is outside the Bootstrap scope`);
  assert.deepEqual(await style(page, '#submit-error-alert', ['position', 'backgroundColor', 'color', 'borderTopLeftRadius', 'opacity']),
    { position: 'fixed', backgroundColor: 'rgb(248, 215, 218)', color: 'rgb(88, 21, 28)', borderTopLeftRadius: '6px', opacity: '1' },
    `${label}: the error alert lost its Bootstrap styling`);
  // A floating toast the clinician can see without scrolling, not a block below the toolbar.
  const toast = await alert.evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return { bottomGap: Math.round(innerHeight - rect.bottom), centre: Math.round(rect.left + rect.width / 2),
      half: Math.round(innerWidth / 2) };
  });
  assert.equal(toast.bottomGap, 50, `${label}: the alert is not pinned 50px above the bottom of the window`);
  assert.ok(Math.abs(toast.centre - toast.half) <= 1, `${label}: the alert is not centred`);
  assert.deepEqual(await style(page, '#submit-error-alert .btn-close', ['width', 'height', 'cursor']),
    { width: '16px', height: '16px', cursor: 'pointer' }, `${label}: the alert close button is unstyled`);
  assert.equal(await toolbarTop(), restingTop, `${label}: showing the alert moved the toolbar`);
  await screenshot(page, `${renderer}-alert`);
  const withAlert = differences(expected.screen, await measureEform(page));
  assert.deepEqual(withAlert, [], `${label}: showing the alert moved the eForm`);
  await page.locator('#submit-error-alert .btn-close').click();
  await page.waitForFunction(() => !document.getElementById('submit-error-alert')
    && !document.querySelector('.oscar-alert-host'));

  // 4. The attachment picker, which jQuery UI mounts in the scoped dialog host outside the toolbar.
  await page.locator('#remoteAttachButton').click();
  await page.locator('.ui-dialog #attachDocumentsForm').waitFor();
  assert.equal(await page.locator('#attachDocumentDisplay').evaluate((display) =>
    !display.closest('#toolbarWrapper') && display.closest('.carlos-eform-ui')?.id), 'carlosEformDialogHost',
  `${label}: the picker is expected in the scoped dialog host, outside the toolbar`);
  assert.equal((await style(page, '#hiddenDoc', ['display'])).display, 'none', `${label}: a .d-none picker row is visible`);
  assert.equal((await style(page, '#showAllHidden', ['display'])).display, 'none', `${label}: a .d-none picker button is visible`);
  assert.equal((await style(page, '#visibleDoc', ['display'])).display, 'list-item', `${label}: a picker row is hidden`);
  // The picker keeps the jQuery UI look it had under the page-wide stylesheet: the scope sits above
  // the dialog, where <body> was, so the dialog's own font and colours still win.
  const pickerText = await style(page, '#visibleDoc', ['fontFamily', 'color', 'lineHeight']);
  assert.ok(pickerText.fontFamily.startsWith('Arial'), `${label}: picker font ${pickerText.fontFamily}`);
  assert.deepEqual([pickerText.color, pickerText.lineHeight], ['rgb(51, 51, 51)', '24px'], `${label}: picker text colour or line height changed`);
  assert.equal((await style(page, '.ui-dialog-title', ['lineHeight'])).lineHeight, '24px', `${label}: picker title line height changed`);
  assert.equal(await toolbarTop(), restingTop, `${label}: opening the picker moved the toolbar`);
  await screenshot(page, `${renderer}-picker`);
  const withPicker = differences(expected.screen, await measureEform(page));
  assert.deepEqual(withPicker, [], `${label}: opening the picker restyled the eForm`);
  await page.locator('.ui-dialog .ui-dialog-titlebar-close').click();
  await page.locator('.ui-dialog').waitFor({ state: 'hidden' });
}

(async () => {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch(getLaunchOptions(process.env.CHROME_PATH));
  const pageErrors = [];
  let failed = false;
  try {
    const page = await browser.newPage({ viewport: { width: 1100, height: 800 } });
    page.on('pageerror', (error) => pageErrors.push(error.message));
    page.on('response', (response) => {
      if (response.status() >= 400) pageErrors.push(`${response.status()} ${new URL(response.url()).pathname}`);
    });
    const expected = await baseline(page, origin);
    // Guard the comparison itself: the fixture must show the defaults Bootstrap's reboot overrides.
    assert.equal(expected.screen.body.style.marginTop, '8px', 'fixture: the bare eForm should keep the default body margin');
    assert.equal(expected.screen.patientName.style.boxSizing, 'content-box', 'fixture: inputs should start content-box');
    for (const renderer of RENDERERS) {
      await checkRenderer(page, origin, renderer, expected);
      console.log(`PASS ${renderer}.jsp: eForm geometry unchanged on screen and in print; chrome styled`);
    }
    assert.deepEqual(pageErrors, [], 'the page reported errors or failed requests');
  } catch (error) {
    failed = true;
    console.error(error.message);
    if (pageErrors.length) console.error('page errors:', pageErrors.join('\n'));
  } finally {
    await browser.close();
    server.close();
  }
  process.exit(failed ? 1 : 0);
})();

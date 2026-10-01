#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';

/*
 * Isolated Chromium check of the CSRFGuard client template CARLOS serves
 * (src/main/resources/csrfguard/carlos-csrfguard.js, issue #4130).
 *
 * The template is rendered the way CSRFGuard's JavaScriptServlet renders it,
 * with the placeholder values Owasp.CsrfGuard.properties configures, and is
 * exercised against the three ways the upstream script lost the token:
 *   - a form built in script and submitted immediately (Unbill, Delete
 *     Template, eForm restore, RA settle, Messenger link ...);
 *   - a form nested inside HTML inserted after load (Administration panel);
 *   - a form whose controls have numeric names (dx code search results),
 *     which made the upstream script throw and skip later forms.
 * It also pins the boundaries: GET forms and cross-origin actions never get
 * the token.
 *
 * No application, database or credentials are needed. Set
 * CSRFGUARD_TEMPLATE=/path/to/META-INF/csrfguard.js to run the same fixture
 * against the unpatched upstream template (it is expected to fail).
 */

const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const {chromium} = require('playwright');
const {PATCHED_TEMPLATE, renderCsrfGuardTemplate} = require('./lib/csrfguard-template');

const ROOT = path.join(__dirname, '..');
const TEMPLATE = process.env.CSRFGUARD_TEMPLATE || PATCHED_TEMPLATE;
const JQUERY = path.join(ROOT, 'src/main/webapp/library/jquery/jquery-3.7.1.min.js');
const TOKEN = 'FIXTURE-MASTER-TOKEN-0123456789AB';

const FIXTURE = `<!doctype html><html><head>
<script src="/csrfguard"></script>
<script src="/jquery.js"></script>
</head><body>
<form id="static" action="/post/static" method="post"><input name="a" value="1"></form>
<form id="numeric" name="dxSearch" action="/post/numeric" method="post">
  <input type="checkbox" name="250" value="250" checked>
  <input type="checkbox" name="4019" value="4019" checked>
  <input type="checkbox" name="1" value="1">
</form>
<form id="afterNumeric" action="/post/after" method="post"><input name="b" value="2"></form>
<form id="noMethod" action="/post/no-method"><input name="q" value="x"><button id="noMethodGo">Go</button></form>
<form id="leaves" action="/post/stays" method="post"><input name="a" value="1">
  <button id="leavesGo" type="submit" formaction="http://localhost:PORT/post/leaves">Elsewhere</button></form>
<form id="getForm" action="/post/get" method="get"><input name="q" value="x">
  <button id="asPost" type="submit" formmethod="post" formaction="/post/formmethod">Post instead</button>
</form>
<div id="panel"></div>
</body></html>`;

const FRAGMENT = `<div class="wrapper"><h3>Groups</h3>
<form id="nested" action="/post/nested" method="post"><input name="group" value="g1">
<button id="nestedSubmit" type="submit">Remove</button></form></div>`;

function startServer() {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://fixture');
    if (url.pathname === '/csrfguard') {
      res.setHeader('Content-Type', 'text/javascript');
      res.end(renderCsrfGuardTemplate({host: req.headers.host.split(':')[0], token: TOKEN, templatePath: TEMPLATE}));
    } else if (url.pathname === '/jquery.js') {
      res.setHeader('Content-Type', 'text/javascript');
      res.end(fs.readFileSync(JQUERY));
    } else if (url.pathname === '/fragment') {
      res.setHeader('Content-Type', 'text/html');
      res.end(FRAGMENT);
    } else if (url.pathname === '/fixture' && req.method === 'GET') {
      res.setHeader('Content-Type', 'text/html');
      res.end(FIXTURE.split('PORT').join(String(server.address().port)));
    } else if (url.pathname === '/based' && req.method === 'GET') {
      // A cross-origin <base>: a relative action resolves to the other origin.
      res.setHeader('Content-Type', 'text/html');
      res.end(`<!doctype html><html><head><base href="http://localhost:${server.address().port}/">
<script src="http://127.0.0.1:${server.address().port}/csrfguard"></script></head><body></body></html>`);
    } else {
      req.resume();
      req.on('end', () => {
        res.setHeader('Content-Type', 'text/html');
        res.end('<!doctype html><p>received</p>');
      });
    }
  });
  return new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(server)));
}

/** Runs action and returns the {method, url, body} of the next fixture POST/GET it causes. */
async function captureSubmission(page, urlPart, action) {
  const [request] = await Promise.all([
    page.waitForRequest(r => r.url().includes(urlPart), {timeout: 5000}),
    action(),
  ]);
  return {method: request.method(), url: request.url(), body: new URLSearchParams(request.postData() || '')};
}

(async () => {
  const server = await startServer();
  const base = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({headless: true, args: ['--no-sandbox'],
    ...(process.env.CHROME_PATH ? {executablePath: process.env.CHROME_PATH} : {})});
  const pageErrors = [];
  const failures = [];
  // Each case gets its own page: most of them end by navigating away.
  let page;

  async function check(name, body) {
    page = await browser.newPage();
    page.on('pageerror', error => pageErrors.push(`${name}: ${error.message}`));
    try {
      await page.goto(`${base}/fixture`); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- base is this script's own 127.0.0.1 fixture server
      await body();
      console.log(`ok - ${name}`);
    } catch (error) {
      failures.push(name);
      console.error(`not ok - ${name}\n  ${error.message.split('\n').join('\n  ')}`);
    } finally {
      await page.close();
    }
  }

  // Selectors are literals in this file; nothing external reaches evaluate().
  const tokenOf = selector => page.evaluate(sel => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection
    const field = document.querySelector(`${sel} input[name="CSRF-TOKEN"]`);
    return field ? field.value : null;
  }, selector);

  try {
    await check('numeric control names do not stop injection into later forms', async () => {
      assert.equal(await tokenOf('#static'), TOKEN);
      assert.equal(await tokenOf('#numeric'), TOKEN);
      assert.equal(await tokenOf('#afterNumeric'), TOKEN, 'a form after the numeric one was skipped');
      assert.equal(await tokenOf('#getForm'), null, 'a GET form must not receive the token');
    });

    await check('a form nested in HTML inserted after load gets the token (Administration panel)', async () => {
      await page.evaluate(() => new Promise(resolve => jQuery('#panel').load('/fragment', () => resolve())));
      await page.waitForFunction(() => document.querySelector('#nested input[name="CSRF-TOKEN"]'),
        null, {timeout: 2000});
      const sent = await captureSubmission(page, '/post/nested', () => page.click('#nestedSubmit'));
      assert.equal(sent.body.get('CSRF-TOKEN'), TOKEN);
      assert.equal(sent.body.get('group'), 'g1');
    });

    await check('a form built in script and submitted at once carries the token', async () => {
      const sent = await captureSubmission(page, '/post/dynamic', () => page.evaluate(() => {
        const form = document.createElement('form');
        form.method = 'post';
        form.action = '/post/dynamic';
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'billingNo';
        input.value = '42';
        form.appendChild(input);
        document.body.appendChild(form);
        form.submit();
      }));
      assert.equal(sent.method, 'POST');
      assert.equal(sent.body.get('CSRF-TOKEN'), TOKEN);
      assert.equal(sent.body.get('billingNo'), '42');
    });

    await check('a script-built form with a relative action carries the token', async () => {
      const sent = await captureSubmission(page, '/post/relative', () => page.evaluate(() => {
        const form = document.createElement('form');
        form.setAttribute('method', 'POST');
        form.setAttribute('action', 'post/relative');
        document.body.appendChild(form);
        form.submit();
      }));
      assert.equal(sent.body.get('CSRF-TOKEN'), TOKEN);
    });

    await check('a script-built POST form without an action posts back with the token', async () => {
      const sent = await captureSubmission(page, '/fixture', () => page.evaluate(() => {
        const form = document.createElement('form');
        form.method = 'post';
        document.body.appendChild(form);
        form.submit();
      }));
      assert.equal(sent.method, 'POST');
      assert.equal(sent.body.get('CSRF-TOKEN'), TOKEN);
    });

    await check('an existing empty token field is filled rather than duplicated', async () => {
      const sent = await captureSubmission(page, '/post/existing', () => page.evaluate(() => {
        const form = document.createElement('form');
        form.method = 'post';
        form.action = '/post/existing';
        const field = document.createElement('input');
        field.type = 'hidden';
        field.name = 'CSRF-TOKEN';
        form.appendChild(field);
        document.body.appendChild(form);
        form.submit();
      }));
      assert.deepEqual(sent.body.getAll('CSRF-TOKEN'), [TOKEN]);
    });

    await check('a submit button formmethod=post on a GET form carries the token', async () => {
      const sent = await captureSubmission(page, '/post/formmethod', () => page.click('#asPost'));
      assert.equal(sent.method, 'POST');
      assert.equal(sent.body.get('CSRF-TOKEN'), TOKEN);
    });

    await check('a script-built GET form never puts the token in the URL', async () => {
      const sent = await captureSubmission(page, '/post/get-dynamic', () => page.evaluate(() => {
        const form = document.createElement('form');
        form.action = '/post/get-dynamic';
        const input = document.createElement('input');
        input.name = 'q';
        input.value = 'x';
        form.appendChild(input);
        document.body.appendChild(form);
        form.submit();
      }));
      assert.equal(sent.method, 'GET');
      assert.equal(new URL(sent.url).searchParams.has('CSRF-TOKEN'), false);
    });

    for (const [label, target] of [
      ['another port on the same host', `http://127.0.0.1:${server.address().port + 1}/post/port`],
      ['another scheme on the same host', `https://127.0.0.1:${server.address().port}/post/scheme`],
    ]) {
      await check(`a POST to ${label} never receives the token`, async () => {
        // Route the request so it never reaches the network; only its body matters.
        await page.route('**/post/{port,scheme}', route => route.fulfill({status: 200, body: 'ok'}));
        const sent = await captureSubmission(page, '/post/', () => page.evaluate(action => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- action is a literal loopback fixture URL
          const form = document.createElement('form');
          form.method = 'post';
          form.action = action;
          document.body.appendChild(form);
          form.submit();
        }, target));
        assert.equal(sent.body.has('CSRF-TOKEN'), false);
      });
    }

    await check('a load-time token is not sent when a form without a method submits as a GET', async () => {
      // Upstream's load-time scan fills forms without a method attribute; the GET must not carry it.
      assert.equal(await tokenOf('#noMethod'), TOKEN);
      const sent = await captureSubmission(page, '/post/no-method', () => page.click('#noMethodGo'));
      assert.equal(sent.method, 'GET');
      assert.equal(new URL(sent.url).searchParams.has('CSRF-TOKEN'), false);
    });

    await check('a load-time token is not sent when a submit button points the form at another origin', async () => {
      await page.route('**/post/leaves', route => route.fulfill({status: 200, body: 'ok'}));
      assert.equal(await tokenOf('#leaves'), TOKEN);
      const sent = await captureSubmission(page, '/post/leaves', () => page.click('#leavesGo'));
      assert.equal(sent.method, 'POST');
      assert.equal(sent.body.has('CSRF-TOKEN'), false);
      assert.equal(sent.body.get('a'), '1');
    });

    await check('a cross-origin <base href> does not make a relative action same-origin', async () => {
      await page.route('**/collect', route => route.fulfill({status: 200, body: 'ok'}));
      await page.goto(`${base}/based`); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- base is this script's own 127.0.0.1 fixture server
      const sent = await captureSubmission(page, '/collect', () => page.evaluate(() => {
        const form = document.createElement('form');
        form.method = 'post';
        form.action = 'collect';
        document.body.appendChild(form);
        form.submit();
      }));
      assert.equal(new URL(sent.url).hostname, 'localhost');
      assert.equal(sent.body.has('CSRF-TOKEN'), false);
    });

    await check('an action-less form under a cross-origin <base href> still posts back with the token', async () => {
      await page.goto(`${base}/based`); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- base is this script's own 127.0.0.1 fixture server
      const sent = await captureSubmission(page, '/based', () => page.evaluate(() => {
        const form = document.createElement('form');
        form.method = 'post';
        document.body.appendChild(form);
        form.submit();
      }));
      assert.equal(sent.method, 'POST');
      assert.equal(new URL(sent.url).hostname, '127.0.0.1');
      assert.equal(sent.body.get('CSRF-TOKEN'), TOKEN);
    });

    await check('a cross-origin POST action never receives the token', async () => {
      const crossOrigin = `http://localhost:${server.address().port}/post/cross`;
      const sent = await captureSubmission(page, '/post/cross', () => page.evaluate(action => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- action is a literal loopback fixture URL
        const form = document.createElement('form');
        form.method = 'post';
        form.action = action;
        document.body.appendChild(form);
        form.submit();
      }, crossOrigin));
      assert.equal(sent.body.has('CSRF-TOKEN'), false);
    });

    if (pageErrors.length) {
      failures.push('page errors');
      console.error(`not ok - page raised errors:\n  ${pageErrors.join('\n  ')}`);
    } else {
      console.log('ok - no page errors');
    }
  } finally {
    await browser.close();
    server.close();
  }
  if (failures.length) {
    console.error(`\n${failures.length} CSRFGuard client check(s) failed (template: ${TEMPLATE})`);
    process.exit(1);
  }
  console.log('\nCSRFGuard client checks passed');
})().catch(error => {
  console.error(error);
  process.exit(1);
});

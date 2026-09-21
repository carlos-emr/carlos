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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for the legacy clinical-data fetch shim in
 * src/main/webapp/eform/eform-runtime-compat.js.
 *
 * Unlike the other eform-*-playwright-checks scripts this one needs no Tomcat, no database and no
 * login: the shim is a static file, and every behaviour worth pinning is observable in a page that
 * serves it alongside a fixture. It starts its own loopback server and exits non-zero on failure.
 *
 * What it protects, and why each case is here rather than in a unit test:
 *
 *  - Delivery must be SYNCHRONOUS. The forms call xmlhttp.open(url, false) and read the parsed
 *    result on the line after send() returns. Only a real XMLHttpRequest can prove the shim's
 *    property shadowing satisfies that; a stub would pass either way.
 *  - Data and date arrays must stay ALIGNED, because the forms index them in parallel.
 *  - A missing payload must reach the NETWORK and fail there. Answering with an empty body would
 *    plot an empty chart on a passing render, which is the failure nobody notices.
 *
 *   node scripts/eform-runtime-compat-playwright-checks.js
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const { assert, getLaunchOptions } = require('./eform-local-playwright-utils');

const SHIM_PATH = path.join(__dirname, '..', 'src', 'main', 'webapp', 'eform', 'eform-runtime-compat.js');
const JQUERY_PATH = path.join(__dirname, '..', 'src', 'main', 'webapp', 'library', 'jquery', 'jquery-3.7.1.min.js');
const JSIGNATURE_PATH = path.join(__dirname, '..', 'src', 'main', 'webapp', 'library', 'jquery', 'jSignature.min.js');
const PAYLOAD_ELEMENT_ID = 'carlos-legacy-measurement-history';
const LEGACY_URL = '/oscarEncounter/oscarMeasurements/SetupDisplayHistory.do?type=';

// Two dated rows for HT and one for WT, in the shape the composer emits. Row-skipping (a row with
// no value for the requested type must contribute NEITHER cell, or every later value pairs with the
// wrong date) is asserted on the Java side, in EFormRenderPdfHtmlComposerUnitTest — that is where
// the markup is built. Here the fixture is the composer's *output*, held deliberately literal so a
// change to that output has to be made in both places consciously.
const PAYLOAD = {
  HT: '<td title="data">101.5</td><td title="observed date">2024-03-01</td>'
    + '<td title="data">99</td><td title="observed date">2024-01-01</td>',
  WT: '<td title="data">16.2</td><td title="observed date">2024-03-01</td>',
  HEAD: '',
};

/** Reproduces the WHO growth-chart form's fetch and scrape verbatim, including its regexes. */
const FORM_SCRIPT = `
window.__result = (function () {
  function getMeasures(measure) {
    var out = {data: [], dates: [], status: 0};
    var xmlhttp = new XMLHttpRequest();
    xmlhttp.onreadystatechange = function () {
      if (xmlhttp.readyState == 4 && xmlhttp.status == 200) {
        var str = xmlhttp.responseText;
        if (!str) { return; }
        var myRe = /<td title="data">([\\d,\\.,\\/]+)<\\/td>/g, m;
        while ((m = myRe.exec(str)) !== null) { out.data.push(m[1]); }
        var dRe = /<td title="observed date">([0-9,-]+)<\\/td>/g, d;
        while ((d = dRe.exec(str)) !== null) { out.dates.push(d[1]); }
      }
    };
    xmlhttp.open("GET", window.location.origin + "${LEGACY_URL}" + measure, false);
    xmlhttp.send();
    // SNAPSHOT, on the line after send() returns. Asserting on out.data directly would prove
    // nothing: it is a live array read by the harness long afterwards, so a shim that delivered
    // late (setTimeout, promise, real async XHR) would fill it in before anyone looked and the
    // check would pass while the real form — which reads measureArray on this very line — got an
    // empty array and plotted nothing.
    out.status = xmlhttp.status;
    out.dataAtReturn = out.data.slice();
    out.datesAtReturn = out.dates.slice();
    return out;
  }
  var result = {ht: getMeasures("HT"), wt: getMeasures("WT")};
  // Reuse: the SAME object, intercepted once, then pointed at a real URL. The shim installs own
  // accessors on the instance to synthesise its response; if it does not remove them, this second
  // request goes out for real and every reader still sees the FIRST patient's embedded body.
  var reused = new XMLHttpRequest();
  reused.open("GET", window.location.origin + "${LEGACY_URL}HT", false);
  reused.send();
  var firstBody = String(reused.responseText || '');
  reused.open("GET", window.location.origin + "/eform-runtime-compat.js", false);
  reused.send();
  result.reuse = {
    // Search the WHOLE body; the file opens with a comment block, so a truncated prefix would miss
    // the marker and fail for the wrong reason.
    isRealResource: String(reused.responseText || '').indexOf('installCarlosEform') >= 0,
    body: String(reused.responseText || '').slice(0, 60),
    servedStaleBody: String(reused.responseText || '') === firstBody,
    status: reused.status,
    readyState: reused.readyState
  };

  var listener = new XMLHttpRequest();
  result.listenerNotified = false;
  listener.addEventListener("readystatechange", function () {
    if (listener.readyState === 4) { result.listenerNotified = true; }
  });
  listener.open("GET", window.location.origin + "${LEGACY_URL}HT", false);
  listener.send();
  result.listenerBody = String(listener.responseText || '').indexOf('101.5') >= 0;
  result.served = window.__carlosEformLegacyFetch ? window.__carlosEformLegacyFetch.served : -1;
  return result;
}());
`;

function page(withPayload) {
  const payload = withPayload
    ? `<script type="application/json" id="${PAYLOAD_ELEMENT_ID}">${JSON.stringify(PAYLOAD).replace(/</g, '\\u003c')}</script>`
    : '<!-- no payload element -->';
  return `<!doctype html><html><body>${payload}`
    + '<script src="/eform-runtime-compat.js"></script>'
    + `<script>${FORM_SCRIPT}</script></body></html>`;
}

function signaturePage() {
  return `<!doctype html><html><body><div id="pad"></div><div id="not-ready"></div>
    <script src="/eform-runtime-compat.js"></script>
    <script src="/jquery.js"></script><script src="/jSignature.js"></script>
    <script>
      var calls = [];
      // The compatibility file defers its guard until DOMContentLoaded. This inline spy runs
      // during parsing, so the guard captures the spy as its original plugin function. Calls
      // recorded below are calls that actually reach the bundled plugin through that function.
      var guardInstalledAtSpySetup = window.__carlosEformSignatureCompat.installed;
      var original = jQuery.fn.jSignature;
      var pluginSpy = function () {
        calls.push({verb: arguments[0], data: arguments[1]});
        return original.apply(this, arguments);
      };
      jQuery.fn.jSignature = pluginSpy;
      window.addEventListener('load', function () {
        var guardWrapsSpy = jQuery.fn.jSignature.__carlosEmptyDataGuard
          && jQuery.fn.jSignature !== pluginSpy;
        var pad = jQuery('#pad').jSignature();
        calls.length = 0;
        var errors = [];
        function attempt(value, target, mayReject) {
          try { target.jSignature('setData', value); }
          catch (error) { if (!mayReject) { errors.push(String(error)); } }
        }
        attempt('data:image/jsignature;base30,', pad);
        var resetAfterEmpty = calls.filter(function (call) { return call.verb === 'reset'; }).length;
        var setDataAfterEmpty = calls.filter(function (call) { return call.verb === 'setData'; }).length;
        attempt('data:IMAGE/JSIGNATURE;BASE30,', pad);
        attempt('data:image/jsignature;base30,', jQuery('#not-ready'));
        var resetAfterNotReady = calls.filter(function (call) { return call.verb === 'reset'; }).length;
        attempt('data:image/jsignature;base30,0A_0A', pad, true);
        attempt('data:text/jsignature-foo,', pad, true);
        var malformedRejected = [];
        [' ', '\\t', '\\n'].forEach(function (payload) {
          try { pad.jSignature('setData', 'data:image/jsignature;base30,' + payload); }
          catch (error) { malformedRejected.push(payload); }
        });
        window.__signatureResult = {
          malformedRejected: malformedRejected,
          guardInstalledAtSpySetup: guardInstalledAtSpySetup,
          guardWrapsSpy: guardWrapsSpy,
          resetAfterEmpty: resetAfterEmpty,
          setDataAfterEmpty: setDataAfterEmpty,
          resetAfterNotReady: resetAfterNotReady,
          delegated: calls.filter(function (call) { return call.verb === 'setData'; })
            .map(function (call) { return call.data; }),
          skipped: window.__carlosEformSignatureCompat.skippedEmptyLoads,
          installed: window.__carlosEformSignatureCompat.installed,
          errors: errors
        };
      });
    </script></body></html>`;
}

// The server withholds the async plugin until this fixture's DOMContentLoaded callback releases
// it. This proves the retry, without depending on a timer racing page parsing.
function asyncSignaturePage() {
  return `<!doctype html><html><body onload="restoreAsyncSignature()"><div id="pad"></div>
    <script src="/eform-runtime-compat.js"></script><script src="/jquery.js"></script>
    <script async src="/jSignature-async.js"></script>
    <script>
      document.addEventListener('DOMContentLoaded', function () {
        window.__pluginAbsentAtReady = typeof jQuery.fn.jSignature !== 'function';
        fetch('/release-jSignature');
      });
      function restoreAsyncSignature() {
        var errors = [], malformedRejected = false;
        var wrappedBeforeBodyLoad = !!jQuery.fn.jSignature.__carlosEmptyDataGuard;
        var pad = jQuery('#pad').jSignature();
        try { pad.jSignature('setData', 'data:image/jsignature;base30,'); }
        catch (error) { errors.push(String(error)); }
        try { pad.jSignature('setData', 'data:image/jsignature;base30, '); }
        catch (error) { malformedRejected = true; }
        window.__asyncSignatureResult = {
          absentAtReady: window.__pluginAbsentAtReady,
          wrappedBeforeBodyLoad: wrappedBeforeBodyLoad,
          skipped: window.__carlosEformSignatureCompat.skippedEmptyLoads,
          malformedRejected: malformedRejected,
          errors: errors
        };
      }
    </script></body></html>`;
}

function jqueryAliasesPage(withPreloadedJquery) {
  const preload = withPreloadedJquery ? '<script src="/jquery.js"></script>' : '';
  return `<!doctype html><html><body>
    <script>window.__carlosEformPdfRender = true;</script>
    ${preload}<script src="/eform-runtime-compat.js"></script>
    <script>window.__jqueryBeforeForm = window.jQuery;</script>
    <script src="/jquery.js"></script>
    <script>
      var jq = window.jQuery;
      window.__aliasesResult = {
        size: jq('body').size(),
        dollarSize: window.$('body').size(),
        hasErrorShortcut: typeof jq.fn.error === 'function',
        replaced: window.__jqueryBeforeForm !== jq,
        readyState: document.readyState
      };
    </script></body></html>`;
}

async function main() {
  const shim = fs.readFileSync(SHIM_PATH, 'utf8');
  const jquery = fs.readFileSync(JQUERY_PATH, 'utf8');
  const jsignature = fs.readFileSync(JSIGNATURE_PATH, 'utf8');
  const networkHits = [];
  let asyncPluginResponse;
  let releaseAsyncPlugin = false;
  function sendAsyncPlugin() {
    if (releaseAsyncPlugin && asyncPluginResponse) {
      asyncPluginResponse.writeHead(200, { 'Content-Type': 'application/javascript' });
      asyncPluginResponse.end(jsignature);
      asyncPluginResponse = null;
    }
  }
  const server = http.createServer((request, response) => {
    if (request.url === '/eform-runtime-compat.js') {
      response.writeHead(200, { 'Content-Type': 'application/javascript' });
      response.end(shim);
      return;
    }
    if (request.url === '/jquery.js' || request.url === '/jSignature.js') {
      response.writeHead(200, { 'Content-Type': 'application/javascript' });
      response.end(request.url === '/jquery.js' ? jquery : jsignature);
      return;
    }
    if (request.url === '/signature-async') {
      releaseAsyncPlugin = false;
      response.writeHead(200, { 'Content-Type': 'text/html' });
      response.end(asyncSignaturePage());
      return;
    }
    if (request.url === '/jSignature-async.js') {
      asyncPluginResponse = response;
      sendAsyncPlugin();
      return;
    }
    if (request.url === '/release-jSignature') {
      releaseAsyncPlugin = true;
      sendAsyncPlugin();
      response.writeHead(204);
      response.end();
      return;
    }
    if (request.url === '/signature') {
      response.writeHead(200, { 'Content-Type': 'text/html' });
      response.end(signaturePage());
      return;
    }
    if (request.url === '/aliases-no-preload' || request.url === '/aliases-with-preload') {
      response.writeHead(200, { 'Content-Type': 'text/html' });
      response.end(jqueryAliasesPage(request.url === '/aliases-with-preload'));
      return;
    }
    if (request.url.startsWith('/embedded')) {
      response.writeHead(200, { 'Content-Type': 'text/html' });
      response.end(page(true));
      return;
    }
    if (request.url.startsWith('/absent')) {
      response.writeHead(200, { 'Content-Type': 'text/html' });
      response.end(page(false));
      return;
    }
    // Anything else reaching here is a request the shim let through to the network.
    if (request.url.indexOf('SetupDisplayHistory') >= 0) { networkHits.push(request.url); }
    response.writeHead(404, { 'Content-Type': 'text/plain' });
    response.end('not found');
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;

  let browser;
  const failures = [];
  try {
    browser = await chromium.launch(getLaunchOptions(process.env.CHROME_PATH || ''));
    const context = await browser.newContext();
    const tab = await context.newPage();

    await tab.goto(`${base}/embedded`);
    const served = await tab.evaluate('window.__result');
    try {
      assert(JSON.stringify(served.ht.dataAtReturn) === '["101.5","99"]',
        `HT values not present when send() returned: ${JSON.stringify(served.ht.dataAtReturn)}`);
      assert(JSON.stringify(served.ht.datesAtReturn) === '["2024-03-01","2024-01-01"]',
        `HT dates not present when send() returned: ${JSON.stringify(served.ht.datesAtReturn)}`);
      assert(served.ht.dataAtReturn.length === served.ht.datesAtReturn.length,
        'data and date arrays are misaligned; the form indexes them in parallel');
      assert(served.ht.status === 200, `synchronous status not visible after send(): ${served.ht.status}`);
      assert(JSON.stringify(served.wt.dataAtReturn) === '["16.2"]',
        `WT column not selected: ${JSON.stringify(served.wt.dataAtReturn)}`);
      assert(!served.reuse.servedStaleBody,
        'a reused XHR replayed the embedded measurement body for a different request');
      assert(served.reuse.isRealResource,
        `reused XHR did not return the real resource: ${served.reuse.body}`);
      assert(served.reuse.status === 200 && served.reuse.readyState === 4,
        `reused XHR state not from the real request: status=${served.reuse.status} readyState=${served.reuse.readyState}`);
      assert(served.listenerNotified, 'addEventListener caller was stranded, not notified');
      assert(served.listenerBody, 'addEventListener caller received no body');
      assert(networkHits.length === 0,
        `shim let ${networkHits.length} request(s) reach the network: ${networkHits.join(', ')}`);
    } catch (error) {
      failures.push(`embedded payload: ${error.message}`);
    }

    const hitsAfterEmbedded = networkHits.length;
    await tab.goto(`${base}/absent`);
    const unserved = await tab.evaluate('window.__result');
    try {
      assert(unserved.ht.status === 404,
        `missing payload must fail visibly, got status ${unserved.ht.status}`);
      assert(unserved.served === 0, `shim served ${unserved.served} responses with no payload present`);
      assert(networkHits.length > hitsAfterEmbedded,
        'missing payload did not reach the network; the completeness gate would never see it');
    } catch (error) {
      failures.push(`absent payload: ${error.message}`);
    }

    await tab.goto(`${base}/signature`);
    const signature = await tab.evaluate('window.__signatureResult');
    try {
      assert(signature && signature.installed, 'jSignature guard was not installed before window load');
      assert(signature.guardInstalledAtSpySetup === false && signature.guardWrapsSpy,
        `plugin spy must be installed before the guard wraps it: ${JSON.stringify(signature)}`);
      assert(signature.errors.length === 0, `jSignature fixture threw: ${signature.errors.join('; ')}`);
      assert(signature.resetAfterEmpty === 1 && signature.setDataAfterEmpty === 0,
        `empty base30 was not reset without decoding: ${JSON.stringify(signature)}`);
      assert(signature.resetAfterNotReady === 2,
        'empty data on an uninitialised pad should wait for its normal initialiser');
      assert(signature.skipped === 3, `expected three empty base30 loads, got ${signature.skipped}`);
      assert(JSON.stringify(signature.delegated) === JSON.stringify([
        'data:image/jsignature;base30,0A_0A', 'data:text/jsignature-foo,',
        'data:image/jsignature;base30, ', 'data:image/jsignature;base30,\t', 'data:image/jsignature;base30,\n',
      ]), `populated, unsupported or malformed data did not reach the bundled plugin: ${JSON.stringify(signature.delegated)}`);
      assert(JSON.stringify(signature.malformedRejected) === JSON.stringify([' ', '\t', '\n']),
        `malformed signature failures were suppressed: ${JSON.stringify(signature.malformedRejected)}`);
    } catch (error) {
      failures.push(`jSignature compatibility: ${error.message}`);
    }

    await tab.goto(`${base}/signature-async`);
    const asyncSignature = await tab.evaluate('window.__asyncSignatureResult');
    try {
      assert(asyncSignature && asyncSignature.absentAtReady === true,
        'async fixture must load the real plugin after DOMContentLoaded');
      assert(asyncSignature.wrappedBeforeBodyLoad && asyncSignature.skipped === 1,
        `async signature was not guarded before body onload: ${JSON.stringify(asyncSignature)}`);
      assert(asyncSignature.errors.length === 0 && asyncSignature.malformedRejected,
        `async guard must skip exactly empty data and preserve malformed errors: ${JSON.stringify(asyncSignature)}`);
    } catch (error) {
      failures.push(`async jSignature compatibility: ${error.message}`);
    }

    for (const preloaded of [false, true]) {
      const label = preloaded ? 'with preloaded jQuery' : 'without preloaded jQuery';
      await tab.goto(`${base}/aliases-${preloaded ? 'with' : 'no'}-preload`);
      const aliases = await tab.evaluate('window.__aliasesResult');
      try {
        assert(aliases && aliases.readyState === 'loading',
          `${label}: aliases were not exercised by the immediate inline consumer`);
        assert(aliases.size === 1 && aliases.dollarSize === 1 && aliases.hasErrorShortcut,
          `${label}: legacy aliases missing after the form loaded jQuery: ${JSON.stringify(aliases)}`);
        assert(aliases.replaced, `${label}: fixture did not replace the jQuery instance`);
      } catch (error) {
        failures.push(`jQuery aliases ${label}: ${error.message}`);
      }
    }
  } finally {
    if (browser) {
      await browser.close();
    }
    await new Promise((resolve) => server.close(resolve));
  }

  if (failures.length > 0) {
    failures.forEach((failure) => console.error(`FAIL ${failure}`));
    process.exitCode = 1;
    return;
  }
  console.log('PASS eForm runtime compatibility: synchronous delivery, aligned arrays, '
    + 'visible missing-payload failure, bundled synchronous/async jSignature empty base30 handling, '
    + 'and immediate jQuery aliases after replacement.');
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

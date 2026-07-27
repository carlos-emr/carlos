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

async function main() {
  const shim = fs.readFileSync(SHIM_PATH, 'utf8');
  const networkHits = [];
  const server = http.createServer((request, response) => {
    if (request.url === '/eform-runtime-compat.js') {
      response.writeHead(200, { 'Content-Type': 'application/javascript' });
      response.end(shim);
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

  const browser = await chromium.launch(getLaunchOptions());
  const failures = [];
  try {
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
  } finally {
    await browser.close();
    server.close();
  }

  if (failures.length > 0) {
    failures.forEach((failure) => console.error(`FAIL ${failure}`));
    process.exitCode = 1;
    return;
  }
  console.log('PASS eForm runtime compatibility shim: synchronous delivery, aligned arrays, '
    + 'listener delivery, and visible failure when no payload is embedded.');
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

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
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {chromium} = require('playwright');
const {getLaunchOptions} = require('./lib/playwright-harness');

const source = fs.readFileSync(path.join(__dirname, 'tickler-crud-playwright-checks.js'), 'utf8');
function section(start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from);
  assert(from >= 0 && to > from, 'Tickler synchronization helpers are missing');
  return source.slice(from, to);
}
const helpers = {assert, URL};
vm.runInNewContext(
  section('async function waitForTicklerDraw(', 'async function setTicklerListStatus(')
  + section('async function searchTicklerList(', 'async function openDemoTicklerList(')
  + '\nthis.ready = waitForTicklerListReady; this.probe = probeTicklerSearch; this.refresh = refreshTicklerListAfter;',
  helpers);

(async () => {
  const chromePath = process.env.CHROME_BIN || process.env.CHROME_PATH || process.env.CARLOS_SMOKE_CHROMIUM;
  const browser = await chromium.launch(getLaunchOptions(chromePath));
  let releaseReset = () => {};
  try {
    const page = await browser.newPage();
    const failures = [], errors = [], searches = [];
    page.on('requestfailed', request => failures.push(request.failure()?.errorText));
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
    let resetStarted;
    const resetReceived = new Promise(resolve => { resetStarted = resolve; });
    const resetGate = new Promise(resolve => { releaseReset = resolve; });
    let holdReset = false;
    let rowMessage = 'owned tickler';
    await page.route('http://tickler.test/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/') {
        await route.fulfill({contentType: 'text/html', body: '<!doctype html>'
          + '<button id="save">Save edit</button><table id="ticklerResults">'
          + '<thead><tr><th>Message</th></tr></thead><tbody></tbody></table>'});
        return;
      }
      if (url.pathname === '/favicon.ico') {
        await route.fulfill({status: 204, body: ''});
        return;
      }
      assert.equal(url.pathname, '/tickler/ListTicklers');
      const needle = url.searchParams.get('search[value]') || '';
      searches.push(needle);
      if (holdReset && needle === '') {
        holdReset = false;
        resetStarted();
        await resetGate;
      }
      const rows = !needle || rowMessage.includes(needle) ? [{message: rowMessage}] : [];
      await route.fulfill({contentType: 'application/json', body: JSON.stringify({
        draw: Number(url.searchParams.get('draw')), recordsTotal: 1, recordsFiltered: rows.length, data: rows,
      })});
    });
    await page.goto('http://tickler.test/');
    const library = path.join(__dirname, '../src/main/webapp/library');
    await page.addScriptTag({path: path.join(library, 'jquery/jquery-3.7.1.min.js')});
    await page.addScriptTag({path: path.join(library, 'DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js')});
    await page.evaluate(() => {
      const table = window.jQuery('#ticklerResults').DataTable({
        serverSide: true, processing: true, ajax: '/tickler/ListTicklers', columns: [{data: 'message'}],
      });
      const listener = new BroadcastChannel('tickler-fixture');
      listener.onmessage = () => table.ajax.reload(null, false);
      document.getElementById('save').addEventListener('click', () => {
        const sender = new BroadcastChannel('tickler-fixture');
        sender.postMessage({action: 'refresh'});
        sender.close();
      });
      window.addEventListener('unload', () => listener.close());
    });
    await helpers.ready(page);
    holdReset = true;
    let settled = false;
    const probe = helpers.probe(page, 'owned tickler').then(result => { settled = true; return result; });
    // An error before the reset must still be reported by the outer await.
    probe.catch(() => {});
    await Promise.race([resetReceived, probe.then(() => { throw new Error('Probe returned before reset started'); })]);
    assert.equal(settled, false, 'Search must remain pending while its reset response is held');
    releaseReset();
    assert.equal(await probe, true);
    assert.deepEqual(searches, ['', 'owned tickler', ''], 'Native input must issue exactly one request per search/reset');

    assert.equal(await helpers.probe(page, 'missing tickler'), false);
    assert.deepEqual(searches, ['', 'owned tickler', '', 'missing tickler', '']);
    const beforeRefresh = searches.length;
    rowMessage = 'edited tickler';
    await helpers.refresh(page, () => page.locator('#save').click());
    assert.equal(searches.length, beforeRefresh + 1, 'An edit must refresh the list exactly once');
    assert.equal(await page.locator('#ticklerResults tbody').innerText(), 'edited tickler');
    assert.deepEqual(failures, []);
    assert.deepEqual(errors, []);
    console.log('PASS tickler CRUD synchronization: native DataTables searches, held reset, and broadcast refresh');
  } finally {
    releaseReset();
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });

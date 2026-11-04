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
const { EventEmitter } = require('node:events');
const test = require('node:test');
const { watchChartPrintDownload } = require('./lib/chart-print-download');

const URL = 'https://127.0.0.1/carlos/CaseManagementEntry?chartContext=owned';
const file = url => ({ url: () => url });
function setup() {
  const opener = new EventEmitter();
  const context = new EventEmitter();
  context.pages = () => [opener];
  return { opener, context };
}
function clean(context, pages) {
  assert.equal(context.listenerCount('page'), 0);
  for (const page of pages) {
    assert.equal(page.listenerCount('download'), 0);
    assert.equal(page.listenerCount('popup'), 0);
  }
}

test('collects a native opener download and removes only its own listeners', async () => {
  const { opener, context } = setup();
  const other = () => {};
  opener.on('download', other);
  const watch = watchChartPrintDownload(context, URL);
  const download = file(URL);
  opener.emit('download', download);
  assert.equal(await watch.promise, download);
  assert.deepEqual(opener.listeners('download'), [other]);
  opener.off('download', other);
  clean(context, [opener]);
});

test('collects the first download emitted immediately by a new print page', async () => {
  const { opener, context } = setup();
  const popup = new EventEmitter();
  const watch = watchChartPrintDownload(context, URL);
  context.emit('page', popup);
  const download = file(URL);
  popup.emit('download', download);
  assert.equal(await watch.promise, download);
  clean(context, [opener, popup]);
});

test('handles popup-before-context ordering without duplicate listeners', async () => {
  const { opener, context } = setup();
  const popup = new EventEmitter();
  const watch = watchChartPrintDownload(context, URL);
  opener.emit('popup', popup);
  context.emit('page', popup);
  assert.equal(popup.listenerCount('download'), 1);
  const download = file(URL);
  popup.emit('download', download);
  assert.equal(await watch.promise, download);
  clean(context, [opener, popup]);
});

test('ignores downloads from a different origin or route', async () => {
  const { opener, context } = setup();
  const watch = watchChartPrintDownload(context, URL);
  opener.emit('download', file('https://other.invalid/carlos/CaseManagementEntry'));
  opener.emit('download', file('https://127.0.0.1/carlos/another-export'));
  const download = file(URL);
  opener.emit('download', download);
  assert.equal(await watch.promise, download);
  clean(context, [opener]);
});

test('a missing download remains a bounded failure and cleans its listeners', async () => {
  const { opener, context } = setup();
  const watch = watchChartPrintDownload(context, URL, { timeout: 5 });
  assert.equal(await watch.promise, null);
  clean(context, [opener]);
});

test('cancellation leaves no listeners and cannot consume the next print', async () => {
  const { opener, context } = setup();
  const watch = watchChartPrintDownload(context, URL);
  watch.cancel();
  assert.equal(await watch.promise, null);
  clean(context, [opener]);
  const next = watchChartPrintDownload(context, URL);
  const download = file(URL);
  opener.emit('download', download);
  assert.equal(await next.promise, download);
  clean(context, [opener]);
});

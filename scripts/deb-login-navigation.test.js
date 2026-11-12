/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
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
const test = require('node:test');
const { submitLoginDocument, isProviderScheduleUrl, waitForProviderSchedule } = require('./lib/deb-login-navigation');

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

// Separate document readiness from full load, as with a cold provider JSP
// whose ancillary/background requests do not finish within the click timeout.
function fixture({ response = { status: () => 200 }, clickError } = {}) {
  const document = deferred();
  const fullLoad = deferred();
  const submitted = deferred();
  let armed = false;
  const page = {
    waitForNavigation(options) {
      armed = true;
      assert.equal(options.timeout, 120000);
      return (options.waitUntil === 'domcontentloaded' ? document.promise : fullLoad.promise).then(() => response);
    },
  };
  const button = {
    async click(options) {
      assert(armed, 'The navigation must already be armed when the form is submitted');
      if (clickError) throw clickError;
      submitted.resolve();
      if (!options.noWaitAfter) await fullLoad.promise;
    },
  };
  return { page, button, document, fullLoad, submitted };
}

test('cold login completes at the new DOM without awaiting full load or idle background requests', async () => {
  const f = fixture();
  const completion = submitLoginDocument(f.page, f.button);
  await f.submitted.promise;
  f.document.resolve();
  // Full load deliberately never resolves. The test runner detects a helper
  // that regresses to waiting for it as an unfinished async test.
  await completion;
});

test('an already loaded login page cannot satisfy a submission before the new document arrives', async () => {
  const f = fixture();
  let completed = false;
  const completion = submitLoginDocument(f.page, f.button).then(() => { completed = true; });
  await f.submitted.promise;
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(completed, false);
  f.document.resolve();
  await completion;
});

test('same-URL new-document failed login is accepted for the wrong-PIN assertion to inspect', async () => {
  const f = fixture({ response: { status: () => 401 } });
  const completion = submitLoginDocument(f.page, f.button);
  f.document.resolve();
  await completion;
});

test('a same-document hash/history transition is refused', async () => {
  const f = fixture({ response: null });
  const completion = submitLoginDocument(f.page, f.button, { label: 'Reset' });
  f.document.resolve();
  await assert.rejects(completion, /Reset did not produce a new document/);
});

test('a server error response cannot count as successful submission', async () => {
  const f = fixture({ response: { status: () => 500 } });
  const completion = submitLoginDocument(f.page, f.button);
  f.document.resolve();
  await assert.rejects(completion, /returned HTTP 500/);
});

test('click failure is retained even when the armed navigation subsequently fails', async () => {
  const error = new Error('Button not available');
  const f = fixture({ clickError: error });
  await assert.rejects(submitLoginDocument(f.page, f.button), actual => actual === error);
  f.document.reject(new Error('Navigation timed out after failed click'));
  await new Promise(resolve => setImmediate(resolve));
});

test('the new-document deadline remains a failure', async () => {
  const f = fixture();
  const error = new Error('New document timed out');
  const completion = submitLoginDocument(f.page, f.button);
  f.document.reject(error);
  await assert.rejects(completion, actual => actual === error);
});

test('provider recognition accepts only exact schedule routes in the configured app and origin', () => {
  const base = 'https://127.0.0.1/carlos/';
  for (const path of ['provider/providercontrol?year=2026', 'provider/ViewAppointmentAdminDay']) {
    assert.equal(isProviderScheduleUrl(new URL(path, base), base), true);
  }
  for (const target of [
    'https://elsewhere.invalid/carlos/provider/providercontrol',
    'https://127.0.0.1/another/provider/providercontrol',
    'https://127.0.0.1/carlos/provider/providercontrol-error',
    'https://127.0.0.1/carlos/forcepasswordreset',
    'http://127.0.0.1/carlos/provider/providercontrol',
  ]) assert.equal(isProviderScheduleUrl(target, base), false, target);
});

test('schedule readiness uses its DOM and rejects a similar error-page path', async () => {
  const base = 'https://127.0.0.1/carlos/';
  await waitForProviderSchedule({
    async waitForURL(predicate, options) {
      assert.equal(predicate(new URL(base + 'provider/providercontrol-error')), false);
      assert.equal(predicate(new URL(base + 'provider/providercontrol')), true);
      assert.deepEqual(options, { waitUntil: 'domcontentloaded', timeout: 120000 });
    },
  }, base);
});

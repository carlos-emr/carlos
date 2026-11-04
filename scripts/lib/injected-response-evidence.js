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

/** Attribute only observed responses belonging to requests this test intercepted. */
function createInjectedResponseEvidence(recorder, label) {
  const requests = new Map();
  const expectedResponses = new Set();
  const consoleBudgets = new Map();
  const failures = [];
  return {
    expect(request, status) {
      assert(!requests.has(request), 'An injected request was registered twice');
      assert([500, 503].includes(status), 'Unexpected injected response status');
      requests.set(request, { status, observed: false });
    },
    observe(response) {
      const proof = requests.get(response.request());
      if (!proof) return;
      if (proof.observed || response.status() !== proof.status) {
        failures.push('An intercepted request did not produce its exact injected response');
        return;
      }
      proof.observed = true;
      // wireStrictPage's listener was registered first and synchronously records
      // this response. Preserve object identity, rather than ignoring all future
      // failures at a URL which also served one deliberately failed request.
      const entry = recorder.badResponses.at(-1);
      if (!entry || entry.label !== label || entry.url !== response.url()
        || entry.status !== response.status() || entry.method !== response.request().method()) {
        failures.push('The intercepted response has no exact recorder evidence');
        return;
      }
      expectedResponses.add(entry);
      const key = `${response.status()}\0${response.url()}`;
      consoleBudgets.set(key, (consoleBudgets.get(key) || 0) + 1);
    },
    removeExpectedDiagnostics() {
      assert(failures.length === 0, failures.join('; '));
      assert([...requests.values()].every(proof => proof.observed),
        'An intercepted request has no observed injected response');
      recorder.badResponses = recorder.badResponses.filter(entry => !expectedResponses.has(entry));
      recorder.consoleIssues = recorder.consoleIssues.filter(entry => {
        const message = /^Failed to load resource: the server responded with a status of (500|503) \(([^\r\n()]*)\)$/.exec(entry.text);
        const status = message?.[1];
        const reason = message?.[2];
        const key = `${status}\0${entry.location?.url}`;
        const remaining = consoleBudgets.get(key) || 0;
        const expectedReason = status === '503' ? 'Service Unavailable' : 'Internal Server Error';
        if (entry.label !== label || entry.type !== 'error' || !status || !remaining
          || (reason !== '' && reason !== expectedReason)) return true;
        consoleBudgets.set(key, remaining - 1);
        return false;
      });
    },
  };
}
module.exports = { createInjectedResponseEvidence };

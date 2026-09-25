#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// A staged card remains visible until the server accepts its deletion. Hold the real POST
// before sending it, then release it and reopen Rx to verify the session stash round trip.
// Uses an owned patient and session-only custom medication; no DrugRef fixture is required.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { openRx, stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');

async function workflow(session) {
  await session.step('a staged card stays visible until its deletion succeeds', async () => {
    const rx = await openRx(session, session.patient);
    const name = `${session.marker}-pending-delete`;
    const key = await stageCustomDrug(rx, name);
    const card = rx.locator(`#set_${key}`);
    let release;
    let intercepted;
    const hold = new Promise(resolve => { release = resolve; });
    const received = new Promise(resolve => { intercepted = resolve; });
    const routePattern = /\/rx\/rxStashDelete(?:\?|$)/;
    const handler = async route => {
      intercepted(route.request());
      await hold;
      await route.continue();
    };
    await rx.route(routePattern, handler);
    try {
      await card.locator("a[onclick^='removePrescribingDrug']").first().click();
      const request = await Promise.race([
        received,
        rx.waitForTimeout(10000).then(() => { throw new Error('card X did not request deletion'); }),
      ]);
      h.assert(request.method() === 'POST', 'card deletion must use POST');
      h.assert(new URLSearchParams(request.postData()).get('randomId') === key,
        'card deletion did not send the stash key');
      h.assert(await card.isVisible(), 'card disappeared before the server accepted deletion');
      const responsePromise = rx.waitForResponse(response => routePattern.test(response.url()));
      release();
      const response = await responsePromise;
      h.assert(response.ok(), `card deletion answered HTTP ${response.status()}`);
      await card.waitFor({ state: 'detached' });
    } finally {
      release();
      await rx.unroute(routePattern, handler);
    }
    await rx.close();
    const reopened = await openRx(session, session.patient);
    h.assert(await reopened.locator(`#set_${key}`).count() === 0,
      'deleted card returned after reopening the patient Rx');
    await reopened.close();
  });
}

if (require.main === module) runWorkflow('rx-stash-deletion', workflow);
module.exports = { workflow };

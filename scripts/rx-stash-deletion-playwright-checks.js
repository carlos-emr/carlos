#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// A staged card remains visible until the server accepts its deletion. Hold the real POST
// before sending it, then release it and reopen Rx to verify the session stash round trip.
// Uses an owned patient and session-only custom medication; no DrugRef fixture is required.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openRx, stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');

async function resetAndWaitForAcknowledgement(page, card, clickReset, modal) {
  let release;
  let intercepted;
  const hold = new Promise(resolve => { release = resolve; });
  const received = new Promise(resolve => { intercepted = resolve; });
  const routePattern = /\/rx\/deleteRx\?parameterValue=clearStash(?:&|$)/;
  const handler = async route => {
    intercepted(route.request());
    await hold;
    await route.continue();
  };
  await page.route(routePattern, handler);
  try {
    await clickReset();
    const request = await Promise.race([
      received,
      page.waitForTimeout(10000).then(() => { throw new Error('reset did not request stash removal'); }),
    ]);
    h.assert(request.method() === 'POST', 'reset must use POST');
    h.assert(await card.isVisible(), 'reset hid the staged card before server acknowledgement');
    if (modal) h.assert(await modal.isVisible(), 'reset closed the preview before server acknowledgement');
    const response = page.waitForResponse(response => routePattern.test(response.url()));
    release();
    h.assert((await response).ok(), 'the server refused the complete reset');
    await card.waitFor({ state: 'detached' });
    if (modal) await modal.waitFor({ state: 'hidden' });
  } finally {
    release();
    await page.unroute(routePattern, handler);
  }
}

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

  await session.step('declining a discontinued drug waits for removal and preserves quoted warning text', async () => {
    const { sql, patient, marker, config } = session;
    const name = `${marker}-discontinued`;
    session.cleanup(() => sql.execute(`DELETE FROM prescription WHERE demographic_no=${patient}
      AND script_no IN (SELECT script_no FROM drugs WHERE demographic_no=${patient}
        AND customName=${h.sqlString(name)});
      DELETE FROM drugs WHERE demographic_no=${patient} AND customName=${h.sqlString(name)}`));
    const original = await openRx(session, patient);
    await stageCustomDrug(original, name);
    const saved = original.waitForResponse(response => response.request().method() === 'POST'
      && /\/rx\/WriteScript\?[^#]*parameterValue=updateSaveAllDrugs/.test(response.url()));
    await original.locator('#saveOnlyButton').click();
    h.assert((await saved).ok(), 'the discontinued-drug fixture could not be saved');
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}
      AND customName=${h.sqlString(name)}`, '1', 'the discontinued-drug fixture was not persisted');
    await original.close();
    const source = sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient}
      AND customName=${h.sqlString(name)}`);
    h.assert(/^[1-9]\d*$/.test(source), 'the discontinued-drug fixture has no source id');
    const reason = `Patient's choice </script><script>window.__rxArchiveInjected=true</script>`;
    sql.execute(`UPDATE drugs SET archived=1, archived_date=NOW(), archived_reason=${h.sqlString(reason)},
      ATC='', regional_identifier='' WHERE drugid=${source} AND demographic_no=${patient}`);
    const page = await session.context.newPage();
    await h.gotoApp(page, config.baseUrl, `/rx/ViewStaticScript2?demographicNo=${patient}&cn=${encodeURIComponent(name)}`);
    await h.assertNotErrorPage(page, 'discontinued-drug history');
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
    await page.route(routePattern, handler);
    try {
      const dialogs = await h.withExpectedDialogs(page, async () => {
        await Promise.all([
          page.waitForURL(/\/rx\/choosePatient/),
          page.locator(`input[value="Represcribe"][onclick*="'${source}'"]`).click(),
        ]);
        await Promise.race([
          received,
          page.waitForTimeout(15000).then(() => { throw new Error('declining discontinued drug did not request removal'); }),
        ]);
      }, { accept: false });
      h.assert(dialogs.length === 1 && dialogs[0].text.includes(reason),
        'the discontinued warning did not preserve its stored text');
      h.assert(await page.evaluate(() => window.__rxArchiveInjected !== true),
        'stored discontinued reason executed as JavaScript');
      const card = page.locator(`fieldset[data-drug-ref-id="${source}"]`);
      h.assert(await card.isVisible(), 'declined card disappeared before removal succeeded');
      const responsePromise = page.waitForResponse(response => routePattern.test(response.url()));
      release();
      h.assert((await responsePromise).ok(), 'the declined card removal failed');
      await card.waitFor({ state: 'detached' });
    } finally {
      release();
      await page.unroute(routePattern, handler);
    }
    await page.close();
    const reopened = await openRx(session, patient);
    h.assert(await reopened.locator(`fieldset[data-drug-ref-id="${source}"]`).count() === 0,
      'declined discontinued drug returned after reopening Rx');
    h.assert(sql.value(`SELECT archived FROM drugs WHERE drugid=${source}`) === '1',
      'declining the staged copy changed the discontinued source');
    await reopened.close();
  });
  await session.step('Reset retains the draft until the complete reset succeeds', async () => {
    const page = await openRx(session, session.patient);
    const key = await stageCustomDrug(page, `${session.marker}-reset`);
    await resetAndWaitForAcknowledgement(page, page.locator(`#set_${key}`),
      () => page.locator('#reset').click());
    await page.close();
    const reopened = await openRx(session, session.patient);
    h.assert(await reopened.locator(`#set_${key}`).count() === 0, 'reset draft returned after reopening Rx');
    await reopened.close();
  });

  await session.step('Create New Rx keeps the preview open until reset succeeds', async () => {
    const page = await openRx(session, session.patient);
    const key = await stageCustomDrug(page, `${session.marker}-preview-reset`);
    const script = session.sql.value(`SELECT script_no FROM drugs WHERE demographic_no=${session.patient}
      AND customName=${h.sqlString(`${session.marker}-discontinued`)}`);
    h.assert(/^[1-9]\d*$/.test(script), 'preview reset requires the owned saved prescription');
    await page.locator('a').filter({ hasText: /^Reprint$/ }).first().click();
    await page.locator(`#reprint a[onclick*="reprint2('${script}')"]`).first().click();
    const modal = page.locator('#carlosModal');
    await modal.waitFor({ state: 'visible' });
    const preview = page.frameLocator('#carlosModalBody iframe').first();
    const reset = preview.locator('input[onclick="resetStash();"]');
    await reset.waitFor({ state: 'visible' });
    await resetAndWaitForAcknowledgement(page, page.locator(`#set_${key}`), () => reset.click(), modal);
    await page.close();
    const reopened = await openRx(session, session.patient);
    h.assert(await reopened.locator(`#set_${key}`).count() === 0, 'preview-reset draft returned after reopening Rx');
    await reopened.close();
  });
}

if (require.main === module) runWorkflow('rx-stash-deletion', workflow);
module.exports = { workflow };

#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// A staged card remains visible until the server accepts its deletion. Hold the real POST
// before sending it, then release it and reopen Rx to verify the session stash round trip.
// Uses an owned patient and session-only custom medication; no DrugRef fixture is required.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openRx, stageCustomDrug, consumeExpectedConflict } = require('./rx-stash-patient-isolation-playwright-checks');

/** Hold a real CSS opening transition until reset is acknowledged, independent of VM speed. */
async function holdPreviewOpeningTransition(page) {
  // Bootstrap also installs a duration-based fallback. Keep it beyond all test deadlines;
  // pause/finish below controls the actual browser transition without mocking Bootstrap.
  const style = await page.addStyleTag({content: '#carlosModal.fade .modal-dialog { transition: opacity 300s linear !important; transform: none !important; opacity: .99; } #carlosModal.show .modal-dialog { opacity: 1; }'});
  await page.evaluate(() => {
    window.__rxPreviewShown = false;
    document.getElementById('carlosModal').addEventListener('shown.bs.modal', () => {
      window.__rxPreviewShown = true;
    }, {once: true});
  });
  let released = false;
  return {
    async pause() {
      const captured = await page.evaluate(() => {
        const dialog = document.querySelector('#carlosModal .modal-dialog');
        const transition = dialog.getAnimations().find(animation => animation.transitionProperty === 'opacity');
        if (!transition || window.__rxPreviewShown) return false;
        window.__rxPreviewOpeningTransition = transition;
        transition.pause();
        return true;
      });
      h.assert(captured, 'preview reset fixture did not capture the real opening CSS transition');
    },
    async release() {
      if (released) return;
      released = true;
      await page.evaluate(() => {
        const transition = window.__rxPreviewOpeningTransition;
        if (transition) transition.finish(); // Emits native transitionend; Bootstrap owns shown/hidden.
      });
    },
    async cleanup() {
      try {
        await this.release();
      } finally {
        await style.evaluate(element => element.remove());
      }
    },
  };
}

async function resetAndWaitForAcknowledgement(page, card, clickReset, modal, openingTransition) {
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
    if (modal) {
      const modalState = await page.evaluate(() => ({
        shown: window.__rxPreviewShown,
        instancePresent: Boolean(window.bootstrap && window.bootstrap.Modal.getInstance(document.getElementById('carlosModal'))),
      }));
      h.assert(modalState.instancePresent, 'preview reset had no modal instance owned by the parent page');
      h.assert(modalState.shown === false,
        'preview reset coverage did not acknowledge the reset during its opening transition');
      await openingTransition.release();
      await page.waitForFunction(() => window.__rxPreviewShown === true);
      await modal.waitFor({ state: 'hidden' });
    }
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
      h.assert(new URLSearchParams(request.postData()).get('draftRevision')
        === await card.locator(`input[name="draftRevision_${key}"]`).inputValue(),
        'card deletion did not send the rendered draft revision');
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

  await session.step('a delayed card X cannot delete a replacement with the same numeric key', async () => {
    const original = await openRx(session, session.patient);
    const key = await stageCustomDrug(original, `${session.marker}-old-key`);
    const oldCard = original.locator(`#set_${key}`);
    const oldRevision = await oldCard.locator(`input[name="draftRevision_${key}"]`).inputValue();
    const current = await openRx(session, session.patient);
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
    await original.route(routePattern, handler);
    try {
      await oldCard.locator("a[onclick^='removePrescribingDrug']").first().click();
      const request = await Promise.race([
        received,
        original.waitForTimeout(10000).then(() => { throw new Error('old card X did not request deletion'); }),
      ]);
      const body = new URLSearchParams(request.postData());
      h.assert(body.get('randomId') === key && body.get('draftRevision') === oldRevision,
        'the held deletion did not identify the original rendered draft');
      const currentCard = current.locator(`#set_${key}`);
      const removed = current.waitForResponse(response => routePattern.test(response.url()));
      await currentCard.locator("a[onclick^='removePrescribingDrug']").first().click();
      h.assert((await removed).ok(), 'the other window could not remove the original draft');
      await currentCard.waitFor({state: 'detached'});

      // Exercise a real collision in the existing numeric key generator, without changing
      // production code or substituting the backend response.
      await current.evaluate(value => {
        window.__rxOriginalRandom = Math.random;
        Math.random = () => Number(value) / 1000000;
      }, key);
      const replacementName = `${session.marker}-replacement-key`;
      try {
        h.assert(await stageCustomDrug(current, replacementName) === key, 'replacement did not reuse the numeric key');
      } finally {
        await current.evaluate(() => { Math.random = window.__rxOriginalRandom; delete window.__rxOriginalRandom; });
      }
      const replacementRevision = await current.locator(`input[name="draftRevision_${key}"]`).inputValue();
      h.assert(replacementRevision !== oldRevision, 'the replacement retained the old draft revision');
      const since = {responses: session.recorder.badResponses.length, console: session.recorder.consoleIssues.length};
      let refusal;
      const dialogs = await h.withExpectedDialogs(original, async () => {
        const pending = original.waitForResponse(response => routePattern.test(response.url()));
        const alert = original.waitForEvent('dialog');
        release();
        refusal = await pending;
        h.assert(refusal.status() === 409, 'the delayed old deletion was accepted for the replacement draft');
        await alert;
      });
      h.assert(dialogs.length === 1 && dialogs[0].text === await original.evaluate(() => jsMsg.removeRefused),
        'the stale deletion did not visibly report its refusal');
      consumeExpectedConflict(session.recorder, refusal, since, session.config.baseUrl, '/rx/rxStashDelete');
      h.assert(await oldCard.isVisible(), 'the refused old deletion hid its card');
      h.assert(await current.locator(`#drugName_${key}`).inputValue() === replacementName,
        'the refused old deletion changed the replacement display');
      const reopened = await openRx(session, session.patient);
      try {
        h.assert(await reopened.locator(`#drugName_${key}`).inputValue() === replacementName
          && await reopened.locator(`input[name="draftRevision_${key}"]`).inputValue() === replacementRevision,
          'the refused old deletion removed or replaced the current server draft');
        const accepted = reopened.waitForResponse(response => routePattern.test(response.url()));
        await reopened.locator(`#set_${key} a[onclick^='removePrescribingDrug']`).first().click();
        h.assert((await accepted).ok(), 'the replacement could not be deleted using its current revision');
        await reopened.locator(`#set_${key}`).waitFor({state: 'detached'});
      } finally {
        await reopened.close();
      }
    } finally {
      release();
      await original.unroute(routePattern, handler);
      await original.close();
      await current.close();
    }
  });

  await session.step('a delayed successful deletion response cannot hide a newer card with the same key', async () => {
    const page = await openRx(session, session.patient);
    const key = await stageCustomDrug(page, `${session.marker}-old-response`);
    let release;
    let committed;
    const hold = new Promise(resolve => { release = resolve; });
    const received = new Promise(resolve => { committed = resolve; });
    const routePattern = /\/rx\/rxStashDelete(?:\?|$)/;
    const handler = async route => {
      const response = await route.fetch();
      committed(response.status());
      await hold;
      await route.fulfill({response});
    };
    await page.route(routePattern, handler);
    try {
      await page.locator(`#set_${key} a[onclick^='removePrescribingDrug']`).first().click();
      const status = await Promise.race([
        received,
        page.waitForTimeout(10000).then(() => { throw new Error('card deletion did not reach the server'); }),
      ]);
      h.assert(status === 200, 'the held response was not a successful deletion');
      h.assert(await page.locator(`#set_${key}`).isVisible(), 'the card vanished before its held response arrived');
      // Reset genuinely clears the local display while the already-committed X response is
      // still in transit; a new draft can now reuse that key in the same document.
      const reset = page.waitForResponse(response => /\/rx\/deleteRx\?parameterValue=clearStash(?:&|$)/.test(response.url()));
      await page.locator('#reset').click();
      h.assert((await reset).ok(), 'the display could not be reset during the delayed response');
      await page.locator(`#set_${key}`).waitFor({state: 'detached'});
      await page.evaluate(value => {
        window.__rxOriginalRandom = Math.random;
        Math.random = () => Number(value) / 1000000;
      }, key);
      const replacementName = `${session.marker}-new-response`;
      try {
        h.assert(await stageCustomDrug(page, replacementName) === key, 'the new card did not reuse the numeric key');
      } finally {
        await page.evaluate(() => { Math.random = window.__rxOriginalRandom; delete window.__rxOriginalRandom; });
      }
      const response = page.waitForResponse(response => routePattern.test(response.url()));
      release();
      await response;
      // Wait for the production XHR success callback, not just the response headers.
      await page.waitForLoadState('networkidle', {timeout: 30000});
      h.assert(await page.locator(`#drugName_${key}`).inputValue() === replacementName,
        'the delayed success hid the newer card');
      const reopened = await openRx(session, session.patient);
      try {
        h.assert(await reopened.locator(`#drugName_${key}`).inputValue() === replacementName,
          'the newer displayed card was absent from the server stash');
        await resetAndWaitForAcknowledgement(reopened, reopened.locator(`#set_${key}`),
          () => reopened.locator('#reset').click());
      } finally {
        await reopened.close();
      }
    } finally {
      release();
      await page.unroute(routePattern, handler);
      await page.close();
    }
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
    // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- fixed hostile XSS regression fixture, SQL-quoted before insertion into this disposable test database; source is validated numeric above.
    sql.execute(`UPDATE drugs SET archived=1, archived_date=NOW(), archived_reason=${h.sqlString(reason)},\n      ATC='', regional_identifier='' WHERE drugid=${source} AND demographic_no=${patient}`);
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
    const openingTransition = await holdPreviewOpeningTransition(page);
    try {
      await page.locator('a').filter({ hasText: /^Reprint$/ }).first().click();
      await page.locator(`#reprint [onclick*="reprint2('${script}')"]`).first().click();
      const modal = page.locator('#carlosModal');
      await modal.waitFor({ state: 'visible' });
      await openingTransition.pause();
      const preview = page.frameLocator('#carlosModalBody iframe').first();
      const reset = preview.locator('input[onclick="resetStash();"]');
      await reset.waitFor({ state: 'visible' });
      await resetAndWaitForAcknowledgement(page, page.locator(`#set_${key}`), () => reset.click(),
        modal, openingTransition);
    } finally {
      await openingTransition.cleanup();
    }
    await page.close();
    const reopened = await openRx(session, session.patient);
    h.assert(await reopened.locator(`#set_${key}`).count() === 0, 'preview-reset draft returned after reopening Rx');
    await reopened.close();
  });
}

if (require.main === module) runWorkflow('rx-stash-deletion', workflow);
module.exports = { workflow, holdPreviewOpeningTransition };

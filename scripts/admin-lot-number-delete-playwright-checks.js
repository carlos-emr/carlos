#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administration ▸ Search lot number by prevention ▸ a lot ▸ Delete: removing a vaccine lot number from the
 * list a clinician picks from when recording an immunization (coverage plan: admin-lot-number-delete).
 *
 * WHY THIS CHECK EXISTS. lot-number-search covers the search and the add path is covered elsewhere, but nothing
 * deleted a lot. The delete is a soft delete (`PreventionsLotNrs.deleted`), keyed by the posted prevention type and
 * lot number, so the promises worth pinning are that it removes exactly the lot that was clicked (a lot number
 * carrying quotes and an ampersand included), leaves every other lot alone, takes the lot out of the search, and
 * cannot be triggered by a GET.
 *
 * User path: Administration (from the schedule) ▸ "Search lot number by prevention" (loaded into the panel's
 * frame) ▸ search by the prevention type ▸ the lot's link (admin/ViewLotNrDeleteRecordHtm) ▸ Delete (posts
 * admin/LotNrDeleteRecord).
 *
 * Asserted, in order, on owned rows only:
 *   1. Searching for the owned prevention type lists exactly its two lots, each linking to its delete page.
 *   2. The target lot's link opens the delete page with the prevention type and lot number filled in exactly.
 *   3. Delete posts exactly that type and lot, the result page says the record was deleted, the target row is
 *      soft-deleted with nothing else changed but its update time, and the control row is unchanged.
 *   4. A new search lists the control lot only.
 *   5. With the lot number cleared, Delete raises the page's "must be entered" alert and posts nothing.
 *   6. A GET (and HEAD) replay of the real delete POST, aimed at the control lot, is refused by the application and
 *      leaves the control lot undeleted. The replay is sent from the page (a same-origin fetch): the session cookie
 *      is Secure, and over plain HTTP only the browser sends it to a loopback origin, not the context's request
 *      client (issue #4718).
 *
 * Fixtures: two lots of an owned prevention type ('PW' and the run marker's tail), inserted directly as the test
 * login; one carries an apostrophe, double quotes and an ampersand. Cleanup removes both by id and asserts none of
 * the owned type is left. No clinic-wide state is changed.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { captureRequest, replayParams } = require('./lib/get-reject-probe');
const { bundleMessage } = require('./lib/throwaway-login-fixture');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');

const NAME = 'admin-lot-number-delete';
const TIMEOUT = 20000;
const q = h.sqlString;
const MISSING = bundleMessage('admin.admin.adddelete_lot_nr.msgMissingParams', 'Prevention and lot number must be entered!');
const STEP = {
  listed: 'searching the owned prevention type lists both of its lots',
  opened: 'a lot\'s link opens its delete page filled in with that lot',
  deleted: 'Delete removes exactly that lot and leaves the other alone',
  gone: 'a new search no longer lists the deleted lot',
  blank: 'Delete with the lot number cleared asks for it and posts nothing',
  replay: 'a GET or HEAD replay of the delete is refused and deletes nothing',
};

/**
 * Send a replay from the page itself rather than the context's request client: the session cookie is Secure,
 * which the browser sends to a loopback origin over plain HTTP but the request client does not (issue #4718).
 * Returns what h.assertRefused reads: status, headers and body. A redirect is not followed (it is not a refusal).
 */
async function replayFromPage(page, url, method) {
  return page.evaluate(async ({ target, verb }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the URL is the check's own replay of a captured same-origin request
    const response = await fetch(target, { method: verb, credentials: 'same-origin', redirect: 'manual' });
    const headers = {};
    response.headers.forEach((value, name) => { headers[name] = value; });
    return {
      status: response.type === 'opaqueredirect' ? 302 : response.status,
      headers,
      body: verb === 'HEAD' ? '' : await response.text(),
    };
  }, { target: url, verb: method });
}

async function workflow(s) {
  const { sql, provider, marker, recorder } = s;
  const type = `PW${marker.slice(-16)}`;
  const lots = [
    { key: 'target', lot: `${marker} O'Neil "A&B"` },
    { key: 'control', lot: `${marker}-KEEP` },
  ];
  const [target, control] = lots;
  const rowOf = (lot) => JSON.stringify(sql.rows(`SELECT * FROM PreventionsLotNrs WHERE id=${lot.id}`));

  s.cleanup(() => {
    const ids = lots.map((lot) => lot.id).filter(Boolean);
    if (ids.length) {
      sql.execute(`DELETE FROM PreventionsLotNrs WHERE id IN (${ids.join(',')}) AND providerNo=${q(provider)}
        AND preventionType=${q(type)}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM PreventionsLotNrs WHERE preventionType=${q(type)}`) === '0',
      'The owned lot numbers were not removed');
  });
  for (const lot of lots) {
    lot.id = sql.value(`INSERT INTO PreventionsLotNrs (creationDate,providerNo,preventionType,lotNr,deleted,lastUpdateDate)
      VALUES (NOW() - INTERVAL 1 DAY,${q(provider)},${q(type)},${q(lot.lot)},0,NOW() - INTERVAL 1 DAY); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(lot.id), `The ${lot.key} lot was not created`);
  }
  const targetBefore = sql.rows(`SELECT id, creationDate, providerNo, preventionType, lotNr, deleted, lastUpdateDate
    FROM PreventionsLotNrs WHERE id=${target.id}`)[0];
  const controlBefore = rowOf(control);

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder, label: 'lot-administration', timeout: TIMEOUT });
  let frame; // the administration panel's frame the lot pages load into
  let deletePost; // the LotNrDeleteRecord POST the page really sent

  /** Open "Search lot number by prevention" from the menu and search for the owned type. */
  async function searchOwnedType() {
    const link = admin.getByRole('link', { name: 'Search lot number by prevention', exact: true, includeHidden: true });
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe').first();
    await iframe.waitFor({ timeout: TIMEOUT });
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'The lot search did not load into the administration panel');
    await frame.locator('input[name="keyword"]').waitFor({ timeout: TIMEOUT });
    await frame.locator('input[name="keyword"]').fill(type);
    const navigated = admin.waitForEvent('framenavigated', { predicate: (f) => f === frame, timeout: TIMEOUT });
    navigated.catch(() => {});
    await frame.locator('input[type="submit"]').first().click();
    await navigated;
    await frame.waitForLoadState('networkidle', { timeout: TIMEOUT });
  }

  /** The lots the search page lists, as their link text and delete-page target. */
  async function listedLots() {
    return frame.$$eval('a[href*="ViewLotNrDeleteRecordHtm"]', (links) => links.map((link) => ({
      text: link.textContent.trim(), href: link.getAttribute('href'),
    })));
  }

  async function openDeletePage(lot) {
    const link = frame.getByRole('link', { name: lot.lot, exact: true });
    h.assert(await link.count() === 1, `The search lists no link for the ${lot.key} lot`);
    const navigated = admin.waitForEvent('framenavigated', { predicate: (f) => f === frame, timeout: TIMEOUT });
    navigated.catch(() => {});
    await link.click();
    await navigated;
    await frame.locator('form[name="deletelotnr"]').waitFor({ timeout: TIMEOUT });
  }

  await s.step(STEP.listed, async () => {
    await searchOwnedType();
    const listed = await listedLots();
    h.assert(JSON.stringify(listed.map((l) => l.text).sort()) === JSON.stringify(lots.map((l) => l.lot).sort()),
      `The search for the owned prevention type lists ${listed.length} lot(s), not exactly its two`);
    for (const lot of lots) {
      const entry = listed.find((l) => l.text === lot.lot);
      const url = new URL(entry.href, frame.url());
      h.assert(url.pathname.endsWith('/admin/ViewLotNrDeleteRecordHtm') && url.searchParams.get('prevention') === type
        && url.searchParams.get('lotnr') === lot.lot, `The ${lot.key} lot does not link to its own delete page`);
    }
  });

  await s.step(STEP.opened, async () => {
    await openDeletePage(target);
    h.assert(await frame.locator('form[name="deletelotnr"] input[name="prevention"]').inputValue() === type,
      'The delete page is not filled in with the lot\'s prevention type');
    h.assert(await frame.locator('form[name="deletelotnr"] input[name="lotnr"]').inputValue() === target.lot,
      'The delete page is not filled in with the exact lot number');
    h.assert(rowOf(target) !== '[]' && sql.value(`SELECT deleted FROM PreventionsLotNrs WHERE id=${target.id}`) === '0',
      'Opening the delete page changed the lot');
  });

  await s.step(STEP.deleted, async () => {
    const shown = admin.waitForEvent('framenavigated', { predicate: (f) => f === frame, timeout: TIMEOUT });
    shown.catch(() => {});
    deletePost = await captureRequest(admin, (url) => url.pathname.endsWith('/admin/LotNrDeleteRecord'),
      () => frame.locator('form[name="deletelotnr"] input[type="submit"]').click());
    h.assert(deletePost.status === 200, `LotNrDeleteRecord answered HTTP ${deletePost.status}`);
    h.assert(deletePost.params.get('prevention') === type && deletePost.params.get('lotnr') === target.lot,
      'Delete did not post exactly the lot\'s prevention type and lot number');
    await shown;
    const text = await h.assertNotErrorPage(frame, 'The lot delete result');
    h.assert(text.includes('Lot number record deleted successfully.'),
      'The delete result page does not say the lot was deleted');
    const after = sql.rows(`SELECT id, creationDate, providerNo, preventionType, lotNr, deleted, lastUpdateDate
      FROM PreventionsLotNrs WHERE id=${target.id}`)[0];
    h.assert(after && after[5] === '1', 'The target lot is not marked deleted');
    // Everything but the deleted flag and the update time the entity stamps itself stays as it was.
    h.assert(JSON.stringify(after.slice(0, 5)) === JSON.stringify(targetBefore.slice(0, 5)),
      'Deleting the lot changed more of its row than the deleted flag');
    h.assert(rowOf(control) === controlBefore, 'Deleting one lot changed the other lot of the same type');
  });

  await s.step(STEP.gone, async () => {
    await searchOwnedType();
    const listed = (await listedLots()).map((l) => l.text);
    h.assert(JSON.stringify(listed) === JSON.stringify([control.lot]),
      `After the delete the search lists ${JSON.stringify(listed.length)} lot(s), not the control lot alone`);
  });

  await s.step(STEP.blank, async () => {
    await openDeletePage(control);
    await frame.locator('form[name="deletelotnr"] input[name="lotnr"]').fill('');
    // Armed before the click: a POST that slipped past the page's check would resolve this to true.
    const posted = admin.waitForRequest((request) => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/admin/LotNrDeleteRecord'), { timeout: 3000 })
      .then(() => true, () => false);
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      await frame.locator('form[name="deletelotnr"] input[type="submit"]').click();
      await posted;
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert' && dialogs[0].text === MISSING,
      `Delete with the lot number cleared did not raise the "must be entered" alert (${dialogs.map((d) => d.type).join(',') || 'no dialog'})`);
    h.assert(!(await posted), 'Delete with the lot number cleared still posted to LotNrDeleteRecord');
    h.assert(rowOf(control) === controlBefore, 'Delete with the lot number cleared changed the control lot');
  });

  await s.step(STEP.replay, async () => {
    h.assert(deletePost, 'No delete POST was captured to replay');
    const url = new URL(`${deletePost.path}?${replayParams(deletePost.params, { lotnr: control.lot }).toString()}`,
      s.config.baseUrl.origin).toString();
    const where = `id=${control.id} AND deleted=0`;
    for (const method of ['GET', 'HEAD']) {
      const before = sql.value(`SELECT COUNT(*) FROM PreventionsLotNrs WHERE ${where}`);
      h.assert(before === '1', `The control lot was not undeleted before the ${method} replay`);
      const mark = failureMark(recorder);
      const response = await replayFromPage(admin, url, method);
      const verdict = await h.assertRefused(s, { response, table: 'PreventionsLotNrs', where, before,
        label: `LotNrDeleteRecord as ${method}` });
      // The refusal the page just received is the point of the step, not a page fault: consume exactly it.
      consumeExpectedFailure(recorder, mark, { status: 405, method, path: /\/admin\/LotNrDeleteRecord$/ });
      console.log(`  probe ${NAME}: LotNrDeleteRecord as ${method} -> ${verdict.evidence}; control lot unchanged (${verdict.rows})`);
    }
    h.assert(rowOf(control) === controlBefore, 'A replay of the delete changed the control lot');
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false });
module.exports = { workflow, STEP };

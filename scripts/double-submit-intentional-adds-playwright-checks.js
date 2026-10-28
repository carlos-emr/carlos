#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Control for the one-save-per-rendering guard (issue #4225): repeating ONE rendered form saves once,
 * but a later, intentional add from a newly rendered form still saves.
 *
 * double-submit-admin-adds proves the first half for Consultation configuration > Add Service (every
 * rapid activation and a POST replay add one service). This check pins the other half on the same
 * page, whose confirmation is rendered straight from the POST and carries a fresh Add form:
 *
 *   1. Add Service adds the typed service once;
 *   2. reloading that confirmation (the browser re-sends the POST) adds nothing more and shows the
 *      same confirmation again;
 *   3. a second service typed into the confirmation page's own form is added (a new rendering);
 *   4. the SAME name added again on purpose from a new rendering is added too: the guard identifies
 *      repeated submissions of one form, it does not deduplicate services by name;
 *   5. a submission token from another login is refused with the 409 page and adds nothing.
 *
 * Fixtures: services named FAKE-PW<hex>-<tag>; cleanup deletes every service row carrying the run
 * marker (and any specialist links) and asserts they are gone. Touches no clinic-wide flag.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { openSecondSession, failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');

const TIMEOUT = 20000;
const ROUTE = /\/encounter\/AddService$/;

async function workflow(s) {
  const { sql, marker } = s;
  const like = h.sqlString(`${marker}-%`);
  const owned = () => sql.rows(`SELECT serviceId FROM consultationServices WHERE serviceDesc LIKE ${like}`).map(([id]) => id);
  const count = (name) => Number(sql.value(`SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${h.sqlString(name)}`));
  s.cleanup(() => {
    const ids = owned();
    for (const id of ids) h.assert(/^[1-9]\d*$/.test(id), 'Owned service id is invalid');
    if (ids.length) {
      sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId IN (${ids.join(',')})`);
      sql.execute(`DELETE FROM consultationServices WHERE serviceId IN (${ids.join(',')}) AND serviceDesc LIKE ${like}`);
    }
    h.assert(owned().length === 0, 'Owned consultation services were not removed');
  });
  const { page: list, isPopup: opened } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
    { context: s.context, recorder: s.recorder, label: 'consultations' });
  const config = await s.popup(list, list.locator('a[href*="ViewShowAllServices"]'), 'consultation-config');
  const first = `${marker}-ONE`;
  const second = `${marker}-TWO`;

  const add = async (name) => {
    await config.locator('#service').fill(name);
    await clickAndAwaitReload(config, config.locator('input[type="submit"]').first(), { timeout: TIMEOUT, label: 'Add Service' });
    await h.assertNotErrorPage(config, 'the Add Service confirmation');
  };

  await s.step('Add Service adds the typed service once', async () => {
    await clickAndAwaitReload(config, config.locator('nav a[href$="/ViewAddService"]'), { label: 'ViewAddService' });
    await add(first);
    h.assert(count(first) === 1, `Add Service stored ${count(first)} rows for one add`);
    h.assert(await config.locator('.alert-success').filter({ hasText: first }).count() === 1,
      'The confirmation does not name the added service');
  });

  await s.step('reloading the confirmation re-sends the POST but adds nothing more', async () => {
    const posts = [];
    const listener = (request) => { if (request.method() === 'POST' && ROUTE.test(new URL(request.url()).pathname)) posts.push(request); };
    config.on('request', listener);
    await config.reload({ waitUntil: 'domcontentloaded', timeout: TIMEOUT });
    config.off('request', listener);
    h.assert(posts.length === 1, `The reload sent ${posts.length} POST(s); this step needs the browser to re-send the add`);
    await h.assertNotErrorPage(config, 'the reloaded Add Service confirmation');
    h.assert(await config.locator('.alert-success').filter({ hasText: first }).count() === 1,
      'The reloaded page does not show the first add\'s confirmation again');
    h.assert(count(first) === 1, `Reloading the confirmation added the service again (${count(first)} rows)`);
  });

  await s.step('a second service typed into the confirmation page\'s own form is added', async () => {
    await add(second);
    h.assert(count(second) === 1 && count(first) === 1,
      `The second add stored ${count(second)} row(s) (the first service now has ${count(first)})`);
  });

  await s.step('adding the same name again on purpose, from a new form, adds it again', async () => {
    await add(first);
    h.assert(count(first) === 2, `An intentional second add of the same name stored ${count(first)} row(s), expected 2`);
  });

  await s.step('a submission token from another login is refused and adds nothing', async () => {
    const other = await openSecondSession(s, { label: 'second-session', openMaster: false });
    try {
      const page = await other.context.newPage();
      await h.gotoApp(page, s.config.baseUrl, '/encounter/oscarConsultationRequest/config/ViewAddService');
      const foreignToken = await page.locator('input[name="carlosFormSubmission"]').inputValue();
      h.assert(/^[0-9a-f-]{36}$/.test(foreignToken), 'The Add Service page rendered no submission token');
      await page.close();
      await clickAndAwaitReload(config, config.locator('nav a[href$="/ViewAddService"]'), { label: 'ViewAddService' });
      await config.locator('input[name="carlosFormSubmission"]').evaluate((input, token) => { input.value = token; }, foreignToken);
      await config.locator('#service').fill(second);
      const mark = failureMark(s.recorder);
      const [response] = await Promise.all([
        config.waitForResponse(r => r.request().method() === 'POST' && ROUTE.test(new URL(r.url()).pathname), { timeout: TIMEOUT }),
        config.locator('input[type="submit"]').first().click(),
      ]);
      await config.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
      await config.waitForTimeout(500);
      h.assert(response.status() === 409, `A foreign submission token answered HTTP ${response.status()}, expected 409`);
      consumeExpectedFailure(s.recorder, mark, { status: 409, path: ROUTE });
      h.assert(await config.locator('#formSubmissionRefused').isVisible(), 'The refusal page was not shown');
      h.assert(count(second) === 1, `The refused submission added the service (${count(second)} rows)`);
    } finally {
      await other.context.close().catch(() => {});
    }
  });

  if (opened && list && !list.isClosed()) await list.close().catch(() => {});
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-intentional-adds', workflow, { openPatient: false });

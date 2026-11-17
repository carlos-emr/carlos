#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const {assert, sqlString} = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

async function workflow(s) {
  const type = 'PW' + s.marker.slice(-16);
  const lot = `${s.marker} O'Neil "A&B"`;
  let id;
  s.cleanup(() => {
    if (!id) return;
    s.sql.execute(`DELETE FROM PreventionsLotNrs WHERE id=${id} AND providerNo=${sqlString(s.provider)}
      AND preventionType=${sqlString(type)} AND lotNr=${sqlString(lot)}`);
    assert(s.sql.value(`SELECT COUNT(*) FROM PreventionsLotNrs WHERE id=${id}`) === '0', 'Owned lot fixture was not removed');
  });
  id = s.sql.value(`INSERT INTO PreventionsLotNrs(creationDate,providerNo,preventionType,lotNr,deleted,lastUpdateDate)
    VALUES(NOW(),${sqlString(s.provider)},${sqlString(type)},${sqlString(lot)},0,NOW()); SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(id), 'Lot fixture was not created');
  const snapshot = () => JSON.stringify(s.sql.rows(`SELECT * FROM PreventionsLotNrs WHERE id=${id}`));
  const before = snapshot();
  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'lot-administration', timeout: 20000});
  const link = admin.getByRole('link', {name: 'Search lot number by prevention', exact: true, includeHidden: true});
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const search = await (await iframe.elementHandle()).contentFrame();
  assert(search, 'Lot search iframe did not load');
  await search.locator('input[name="keyword"]').waitFor();
  async function submitSearch() {
    const navigated = admin.waitForEvent('framenavigated', {predicate: frame => frame === search, timeout: 20000});
    navigated.catch(() => {});
    await search.locator('input[type="submit"]').click();
    await navigated;
    await search.waitForLoadState('networkidle', {timeout: 20000});
  }
  await s.step('search from the administration menu returns the exact owned lot', async () => {
    await search.locator('input[name="keyword"]').fill(type.toUpperCase());
    await submitSearch();
    assert(await search.getByRole('link', {name: lot, exact: true}).count() === 1, 'Lot search did not return the exact stored value');
    assert(snapshot() === before, 'Searching modified the lot record');
  });
  await s.step('a second search from results can submit and show no matching lot', async () => {
    await search.locator('input[name="keyword"]').fill(type + '-missing');
    await submitSearch();
    assert(await search.locator('input[name="keyword"]').isVisible(), 'Search results form disappeared');
    assert(await search.locator('a[href*="ViewLotNrDeleteRecordHtm"]').count() === 0, 'Empty search retained unrelated lots');
    assert(snapshot() === before, 'Searching modified the lot record');
  });
}
if (require.main === module) runWorkflow('lot-number-search', workflow, {openPatient: false});
module.exports = {workflow};

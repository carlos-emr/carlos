#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Facility message banner on the day sheet (coverage plan §3.7 admin-facility-messages).
 *
 * User path: Schedule (the day sheet fetches FacilityMessage?method=view and injects it
 * into #facility_message) ▸ reload. The authoring side, Administration ▸ CAISI ▸
 * Facility Messages, is rendered only when carlos.properties sets caisi=on; on an
 * install without it the route has no menu entry, so the messages are seeded as rows
 * shaped exactly like the editor's save (facility id + name, creation/expiry dates).
 *
 * Asserted: an active message for the login's facility is shown once, as literal text
 * prefixed by the facility name, with its markup and <script> inert; an expired message,
 * a message for another facility and one for a program outside the login's domain are
 * not shown; after the active message expires, the next day-sheet load no longer shows it.
 *
 * Fixtures: facility_message rows inserted with the run marker; cleanup deletes only
 * those ids and asserts they are gone. Rows are visible to other sessions of the same
 * facility for the few seconds they are active.
 */
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

const TIMEOUT = 20000;

function isBannerView(response) {
  const url = new URL(response.url());
  return url.pathname.endsWith('/FacilityMessage') && url.searchParams.get('method') === 'view';
}

async function workflow(s) {
  const {sql, marker, provider} = s;
  const facilities = sql.rows(`SELECT f.id,f.name FROM provider_facility pf JOIN Facility f ON f.id=pf.facility_id
    WHERE pf.provider_no=${h.sqlString(provider)} AND f.disabled=0`);
  if (facilities.length !== 1) throw new h.SkipCheck('The test login is not linked to exactly one active facility');
  const [[facilityId, facilityName]] = facilities;
  const text = `${marker} O'Neil & "A<b>B</b>" 100% <script>window.__pwFacilityMessage=1</script> done`;
  const ids = [];
  s.cleanup(() => {
    if (!ids.length) return;
    const predicate = `id IN (${ids.join(',')}) AND message LIKE ${h.sqlString(marker + '%')}`;
    sql.execute(`DELETE FROM facility_message WHERE ${predicate}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM facility_message WHERE id IN (${ids.join(',')})`) === '0',
      'Owned facility messages were not removed');
  });
  const otherFacility = Number(sql.value('SELECT COALESCE(MAX(id),0)+1000 FROM Facility'));
  const foreignProgram = Number(sql.value('SELECT COALESCE(MAX(id),0)+1000 FROM program'));
  function seed(suffix, expiry, facility, name, program) {
    const id = sql.value(`INSERT INTO facility_message(message,creation_date,expiry_date,facility_id,facility_name,programId)
      VALUES(${h.sqlString(text + suffix)},NOW(),${expiry},${facility},${h.sqlString(name)},${program}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'A facility message fixture was not created');
    ids.push(id);
    return id;
  }
  const active = seed('', 'DATE_ADD(NOW(), INTERVAL 1 DAY)', facilityId, facilityName, 'NULL');
  seed(' expired', 'DATE_SUB(NOW(), INTERVAL 1 DAY)', facilityId, facilityName, 'NULL');
  seed(' other facility', 'DATE_ADD(NOW(), INTERVAL 1 DAY)', otherFacility, 'FAKE-PW other', 'NULL');
  seed(' other program', 'DATE_ADD(NOW(), INTERVAL 1 DAY)', facilityId, facilityName, foreignProgram);

  async function reloadDaySheet() {
    const banner = s.schedule.waitForResponse(isBannerView, {timeout: TIMEOUT});
    await s.schedule.reload();
    const response = await banner;
    h.assert(response.status() === 200, `The facility message request answered HTTP ${response.status()}`);
    await s.schedule.locator('#scheduleTable').waitFor({timeout: TIMEOUT});
    await s.schedule.waitForFunction(() => window.jQuery && window.jQuery.active === 0, null, {timeout: TIMEOUT});
    const lines = (await s.schedule.locator('#facility_message td').allInnerTexts())
      .map(line => line.replace(/ /g, ' ').trim()).filter(line => line.includes(marker));
    return lines;
  }

  await s.step('the day sheet shows the active facility message once, as literal text', async () => {
    const lines = await reloadDaySheet();
    h.assert(lines.length === 1 && lines[0] === `${facilityName} Message - ${text}`,
      'The day sheet does not show exactly the active owned message with its facility name');
    h.assert(await s.schedule.locator('#facility_message script, #facility_message b').count() === 0,
      'The facility message markup was rendered as elements');
    h.assert(await s.schedule.evaluate(() => window.__pwFacilityMessage === undefined), 'The facility message script executed');
  });
  await s.step('expired, other-facility and other-program messages are not shown', async () => {
    const lines = await reloadDaySheet();
    h.assert(lines.length === 1 && !/ (expired|other facility|other program)$/.test(lines[0]),
      'A message outside the login\'s facility, program or validity window was shown');
  });
  await s.step('once the active message expires the next day sheet no longer shows it', async () => {
    sql.execute(`UPDATE facility_message SET expiry_date=DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id=${active}`);
    h.assert(sql.value(`SELECT expiry_date<NOW() FROM facility_message WHERE id=${active}`) === '1', 'The owned message did not expire');
    const lines = await reloadDaySheet();
    h.assert(lines.length === 0, 'An expired facility message is still shown on the day sheet');
  });
}

if (require.main === module) runWorkflow('admin-facility-messages', workflow, {openPatient: false});
module.exports = {workflow};

#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const {authzReadFixture} = require('./lib/authz-read-fixture');
const {sessionToken, captureRequest, buildReplay, sendReplay} = require('./lib/mutation-replay');
const {runWorkflow} = require('./lib/workflow-session');

async function workflow(s) {
  const {sql, marker, patient, provider} = s;
  const fixture = authzReadFixture({sql, marker, provider, testUser: s.config.testUser});
  s.cleanup(() => fixture.cleanup());
  const reasons = ['unchanged', 'edited', 'booked later'].map(label => marker + ' day sheet ' + label);
  let owner;
  s.cleanup(() => {
    if (!owner) return;
    const ids = sql.rows(`SELECT appointment_no FROM appointment WHERE provider_no=?
      AND demographic_no=? AND reason IN (?,?,?)`, [owner, Number(patient), ...reasons]).flat();
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'An owned appointment has an invalid ID');
    for (const id of ids) {
      sql.execute('DELETE FROM appointmentArchive WHERE appointment_no=? AND demographic_no=?', [Number(id), Number(patient)]);
      h.assert(sql.value('SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=? AND demographic_no=?',
        [Number(id), Number(patient)]) === '0', 'An owned appointment archive remains');
      sql.execute(`DELETE FROM appointment WHERE appointment_no=? AND provider_no=? AND demographic_no=?
        AND reason IN (?,?,?)`, [Number(id), owner, Number(patient), ...reasons]);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=? AND demographic_no=?
      AND reason IN (?,?,?)`, [owner, Number(patient), ...reasons]) === '0', 'An owned appointment remains');
  });
  const role = fixture.addRole({_appointment: 'r', _demographic: 'r', _report: 'r', '_admin.reporting': 'r'});
  owner = fixture.addLogin(role).providerNo;
  sql.execute('INSERT INTO providersite (provider_no,site_id) SELECT ?,site_id FROM providersite WHERE provider_no=?', [owner, provider]);
  const today = sql.value('SELECT CURDATE()');
  function create(index) {
    const id = sql.insertId(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,
      demographic_no,reason,status,notes,type,creator,lastupdateuser,createdatetime,updatedatetime)
      VALUES (?,?,'11:00:00','11:14:00',?,?,?,'t','','',?,?,NOW(),NOW())`,
      [owner, today, marker, Number(patient), reasons[index], provider, provider]);
    h.assert(/^[1-9]\d*$/.test(id), 'An owned day-sheet appointment was not created');
    return Number(id);
  }
  const unchanged = create(0); const edited = create(1);
  let later;
  let sheet;
  let captured;
  const state = id => sql.rows(`SELECT status,CAST(updatedatetime AS CHAR),lastupdateuser
    FROM appointment WHERE appointment_no=? AND demographic_no=?`, [id, Number(patient)]);
  const archives = id => Number(sql.value('SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=? AND demographic_no=?',
    [id, Number(patient)]));
  try {
    await s.step('Schedule > Report previews two owned new appointments without writing', async () => {
      const {page: index} = await ui.clickOpensPopupOrNavigates(s.schedule,
        s.schedule.locator("a[onclick*='/report/ViewReportindex']").first(),
        {context: s.context, recorder: s.recorder, label: 'day-sheet-selection-index', timeout: 20000});
      await index.locator('#asdate').waitFor();
      await index.locator('select[name="provider_no"]').selectOption(owner);
      await ui.pickDate(index, '#asdate', today); await ui.pickDate(index, '#aedate', today);
      await index.locator('select[name="sTime"]').selectOption('8');
      await index.locator('select[name="eTime"]').selectOption('20');
      sheet = await s.popup(index, index.getByRole('link', {name: 'All appointments'}), 'day-sheet-selection-preview');
      const preview = new URL(sheet.url());
      h.assert(preview.pathname.endsWith('/report/ViewReportdaysheet'), 'The opener reached another report');
      preview.searchParams.set('dsmode', 'new');
      const response = await sheet.goto(preview.href, {waitUntil: 'domcontentloaded'});
      h.assert(response.status() === 200, 'The new-mode preview did not render');
      await h.assertNotErrorPage(sheet, 'owned day-sheet selection preview');
      const text = await sheet.locator('tbody').innerText();
      h.assert(text.includes(reasons[0]) && text.includes(reasons[1]) && !text.includes(reasons[2]), 'The preview contains another selection');
      h.assert(state(unchanged)[0][0] === 't' && state(edited)[0][0] === 't'
        && archives(unchanged) === 0 && archives(edited) === 0, 'The preview wrote an appointment or archive');
      const scope = sql.rows(`SELECT appointment_no FROM appointment WHERE provider_no=? AND appointment_date=?
        AND start_time >= '08:00:00' AND start_time < '20:00:00' AND status='t' ORDER BY appointment_no`, [owner, today]);
      h.assert(JSON.stringify(scope) === JSON.stringify([[String(unchanged)], [String(edited)]]), 'The preview scope contains another appointment');
    });
    let editedBefore;
    let laterBefore;
    await s.step('acknowledgment excludes an appointment booked or edited after preview', async () => {
      later = create(2);
      sql.execute('UPDATE appointment SET updatedatetime=DATE_ADD(updatedatetime,INTERVAL 5 SECOND) WHERE appointment_no=? AND demographic_no=?',
        [edited, Number(patient)]);
      editedBefore = state(edited); laterBefore = state(later);
      const button = sheet.locator('form:has(input[name="acknowledge"][value="true"]) button[type="submit"]');
      h.assert(await button.count() === 1, 'The writer has no acknowledgment form');
      captured = await captureRequest(sheet, url => url.pathname.endsWith('/report/ViewReportdaysheet'),
        () => ui.clickAndAwaitReload(sheet, button, {label: 'acknowledge rendered appointments', timeout: 20000}));
      h.assert(captured.status === 200 && state(unchanged)[0][0] === 'T' && archives(unchanged) === 1,
        'The unchanged rendered appointment was not acknowledged and archived');
      console.log('Day-sheet selection observation ' + JSON.stringify({laterStatus: state(later)[0][0],
        editedStatus: state(edited)[0][0], laterArchives: archives(later), editedArchives: archives(edited)}));
      h.assert(JSON.stringify(state(later)) === JSON.stringify(laterBefore) && archives(later) === 0,
        'Acknowledgment changed an appointment that was absent from the rendered preview');
      h.assert(JSON.stringify(state(edited)) === JSON.stringify(editedBefore) && archives(edited) === 0,
        'Acknowledgment changed an appointment edited after the rendered preview');
      h.assert(/Acknowledged\s+1 appointments\.\s+Skipped\s+1 appointments/.test(await sheet.locator('[role="status"]').innerText()),
        'The result omitted the acknowledged and skipped rendered appointment counts');
    });
    await s.step('an incomplete selection is refused before another appointment or archive changes', async () => {
      const token = await sessionToken(sheet, s.config.baseUrl);
      const response = await sendReplay(s.context, buildReplay(captured,
        {origin: s.config.baseUrl.origin, token, overrides: {displayedAppointmentCount: ''}}));
      try {h.assert(response.status() === 400 && Object.hasOwn(response.headers(), 'x-permitted-cross-domain-policies'),
        'The application accepted an incomplete rendered selection');}
      finally {await response.dispose();}
      h.assert(archives(unchanged) === 1 && archives(edited) === 0 && archives(later) === 0
        && JSON.stringify(state(edited)) === JSON.stringify(editedBefore)
        && JSON.stringify(state(later)) === JSON.stringify(laterBefore), 'An incomplete selection changed an appointment or archive');
    });
    await s.step('replaying the same acknowledgment does not archive twice or acquire later appointments', async () => {
      const token = await sessionToken(sheet, s.config.baseUrl);
      const response = await sendReplay(s.context, buildReplay(captured, {origin: s.config.baseUrl.origin, token}));
      try {h.assert(response.status() === 200, 'The repeated acknowledgment did not return its result');}
      finally {await response.dispose();}
      h.assert(state(unchanged)[0][0] === 'T' && archives(unchanged) === 1
        && JSON.stringify(state(edited)) === JSON.stringify(editedBefore) && archives(edited) === 0
        && JSON.stringify(state(later)) === JSON.stringify(laterBefore) && archives(later) === 0,
      'Replaying acknowledgment changed a stale, absent or already acknowledged appointment');
    });
  } finally {if (sheet) await sheet.close();}
}
if (require.main === module) runWorkflow('report-daysheet-selection', workflow, {openPatient: true});

#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * clinic-address-admin — the clinic's own name and address, edited the way an administrator does:
 *   Schedule ▸ Administration ▸ System Management ▸ Clinic/Agency Address (admin/ManageClinic, in the
 *   shell's iframe) ▸ change every field ▸ Submit (POST method=update) ▸ open the page again.
 * Asserts:
 *   - The page opens on the stored `clinic` row: every input shows its column.
 *   - Submit stores exactly what was typed into the one clinic row, punctuation, accents and the
 *     phone/fax delimiter fields included, and adds no second row.
 *   - The page shown after Submit, and the page opened again from the menu, show the stored values.
 *   - GET and HEAD carrying the save (method=update) are refused and change nothing.
 * Fixtures: none of its own. The clinic row is clinic-wide: the check snapshots every column first and
 * restores the snapshot in cleanup, verifying it byte for byte, so it must not run concurrently with
 * other checks (live wrapper: EXCLUSIVE=1).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 20000;
const SECTION = '/admin/ManageClinic';
const COLUMNS = {
  'clinic.clinicName': 'clinic_name',
  'clinic.clinicAddress': 'clinic_address',
  'clinic.clinicCity': 'clinic_city',
  'clinic.clinicPostal': 'clinic_postal',
  'clinic.clinicPhone': 'clinic_phone',
  'clinic.clinicFax': 'clinic_fax',
  'clinic.clinicLocationCode': 'clinic_location_code',
  'clinic.clinicProvince': 'clinic_province',
  'clinic.clinicDelimPhone': 'clinic_delim_phone',
  'clinic.clinicDelimFax': 'clinic_delim_fax',
};

async function workflow(s) {
  const { sql, marker, context, recorder } = s;
  const q = h.sqlString;
  const tag = marker.slice(-6);
  const rows = () => sql.rows(`SELECT clinic_no,${Object.values(COLUMNS).join(',')},status FROM clinic ORDER BY clinic_no`);
  const original = rows();
  h.assert(original.length === 1, `Expected one clinic row, found ${original.length}`);
  const [clinicNo] = original[0];
  const hexOf = `SELECT CONCAT_WS('|',${Object.values(COLUMNS).map(c => `HEX(COALESCE(${c},''))`).join(',')},HEX(COALESCE(status,''))) FROM clinic WHERE clinic_no=${clinicNo}`;
  const originalHex = sql.value(hexOf);
  s.cleanup(() => {
    const [, ...values] = original[0];
    const status = values.pop();
    const assignments = Object.values(COLUMNS).map((column, index) => `${column}=${values[index] === 'NULL' ? 'NULL' : q(values[index])}`);
    sql.execute(`UPDATE clinic SET ${assignments.join(',')},status=${status === 'NULL' ? 'NULL' : q(status)} WHERE clinic_no=${clinicNo}`);
    h.assert(sql.value(hexOf) === originalHex, 'The clinic row was not restored to its snapshot');
    h.assert(sql.value('SELECT COUNT(*) FROM clinic') === '1', 'A clinic row was added and not removed');
  });

  const typed = {
    'clinic.clinicName': `FAKE ${tag} Clinique & Co. (Test)`,
    'clinic.clinicAddress': `12-B O'Neil Street, Suite #3`,
    'clinic.clinicCity': 'Trois-Rivières',
    'clinic.clinicPostal': 'A1A 1A1',
    'clinic.clinicPhone': '555-010-0199',
    'clinic.clinicFax': '555-010-0198',
    'clinic.clinicLocationCode': `F${tag.slice(0, 3)}`,
    'clinic.clinicProvince': 'QC',
    'clinic.clinicDelimPhone': '555-010-0199 x12',
    'clinic.clinicDelimFax': '555-010-0198',
  };
  const stored = () => {
    const [row] = sql.rows(`SELECT ${Object.values(COLUMNS).join(',')} FROM clinic WHERE clinic_no=${clinicNo}`);
    return Object.fromEntries(Object.keys(COLUMNS).map((name, index) => [name, row[index]]));
  };

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context, recorder, label: 'administration', timeout: TIMEOUT });
  await admin.waitForLoadState('load');
  const menuEntry = admin.locator(`a.xlink[rel$="${SECTION}"]`).first();
  h.assert(await menuEntry.count() === 1, 'Administration offers no Clinic/Agency Address entry');

  let frame;
  /** Click something that navigates the admin iframe, and wait for the new document. */
  async function frameNavigation(action, label, isTarget = candidate => candidate === frame) {
    const navigated = admin.waitForEvent('framenavigated', { predicate: isTarget, timeout: TIMEOUT });
    navigated.catch(() => {});
    await action();
    const next = await navigated;
    await next.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
    await h.assertNotErrorPage(next, label);
    return next;
  }
  const openFromMenu = async label => {
    await revealAuditLink(admin, menuEntry, TIMEOUT);
    frame = await frameNavigation(() => menuEntry.click({ timeout: TIMEOUT }), label,
      candidate => candidate !== admin.mainFrame() && new URL(candidate.url()).pathname.endsWith(SECTION));
  };
  const shown = async () => Object.fromEntries(await Promise.all(Object.keys(COLUMNS).map(async name =>
    [name, await frame.locator(`input[name="${name}"]`).inputValue()])));
  const asStored = values => Object.fromEntries(Object.entries(values).map(([name, value]) => [name, value === 'NULL' ? '' : value]));

  await s.step('Administration ▸ Clinic/Agency Address opens on the stored clinic row', async () => {
    await openFromMenu('the clinic address page');
    h.assert(JSON.stringify(await shown()) === JSON.stringify(asStored(stored())),
      'The clinic address page does not show the stored clinic row');
  });

  await s.step('Submit stores every typed field in the one clinic row, punctuation and accents included', async () => {
    for (const [name, value] of Object.entries(typed)) {
      await frame.locator(`input[name="${name}"]`).fill(value);
    }
    await frameNavigation(() => frame.locator('form[action$="/admin/ManageClinic"] input[type="submit"]').click(), 'the page after Submit');
    const after = stored();
    for (const [name, value] of Object.entries(typed)) {
      h.assert(after[name] === value, `${name} was stored as "${after[name]}", not "${value}"`);
    }
    h.assert(sql.value('SELECT COUNT(*) FROM clinic') === '1', 'Submit added a second clinic row instead of updating the one');
    h.assert(JSON.stringify(await shown()) === JSON.stringify(typed), 'The page after Submit does not show the stored values');
  });

  await s.step('the page opened again from the menu shows the stored values', async () => {
    await openFromMenu('the clinic address page, reopened');
    h.assert(JSON.stringify(await shown()) === JSON.stringify(typed), 'The reopened clinic address page lost a stored value');
  });

  await s.step('GET and HEAD carrying the save are refused and change nothing', async () => {
    const probeName = `FAKE ${tag} written by GET`;
    const where = `clinic_no=${clinicNo} AND clinic_name=${q(probeName)}`;
    for (const method of ['GET', 'HEAD']) {
      const response = await context.request.fetch(h.appUrl(s.config.baseUrl, SECTION)
        + `?method=update&clinic.id=${clinicNo}&clinic.clinicName=${encodeURIComponent(probeName)}`,
      { method, maxRedirects: 0, failOnStatusCode: false });
      await h.assertRefused(s, { label: `${method} admin/ManageClinic method=update`, table: 'clinic', where, before: '0', response });
    }
    h.assert(stored()['clinic.clinicName'] === typed['clinic.clinicName'], 'A refused request changed the clinic name');
  });
}

if (require.main === module) runWorkflow('clinic-address-admin', workflow, { openPatient: false });
module.exports = { workflow };

#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administration ▸ Manage Clinic NBR Codes (admin/ViewClinicNbrManage, clinicNbrManage.jsp, posting
 * to admin/clinicNbrManage): the clinic's RMA "NBR" codes. No browser check opened the page; it is
 * in the menu only with rma_enabled=true.
 *
 * User path: Schedule ▸ Administration ▸ Manage Clinic NBR Codes ▸ value + description ▸ Add New
 * Code; then the page opened again ▸ choose the code ▸ Remove Selected.
 * Asserted: Add stores the code as a new active clinic_nbr row and lists it; after the page is
 * opened again, Remove Selected marks exactly that row deleted (status D) and it leaves the list;
 * a GET carrying an add (method=add) is refused with 405 and stores nothing.
 *
 * Fixtures: none created by hand; the code the run adds is its own (value and description carry
 * the marker). clinic_nbr is snapshotted byte-exact before the first click; cleanup deletes the
 * rows the run added and puts every other row back exactly. EXCLUSIVE=1: the codes are clinic-wide.
 */
const { authenticatedProbe } = require('./lib/authenticated-probe');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openAdminPage, reopenAdminPage } = require('./lib/admin-frame');

const VIEW = 'admin/ViewClinicNbrManage';
const ACTION = 'admin/clinicNbrManage';
const COLUMNS = ['nbr_id', 'nbr_value', 'nbr_string', 'nbr_status'];

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  // 'N' is NULL; 'X' + hex is the value. CAST ... AS BINARY makes HEX read a number's text (nbr_id) rather than its
  // value, so UNHEX puts the same number back; HEX of the bare number would restore a one-byte string, not the number.
  const snapshot = () => sql.rows(`SELECT ${COLUMNS.map(c => `IF(${c} IS NULL,'N',CONCAT('X',HEX(CAST(${c} AS BINARY))))`).join(',')} FROM clinic_nbr ORDER BY nbr_id`);
  const before = snapshot();
  const lastId = sql.value('SELECT COALESCE(MAX(nbr_id), 0) FROM clinic_nbr');
  s.cleanup(() => {
    const value = cell => (cell === 'N' ? 'NULL' : `UNHEX('${cell.slice(1)}')`);
    sql.execute(`DELETE FROM clinic_nbr WHERE nbr_id > ${lastId}`);
    for (const row of before) {
      sql.execute(`UPDATE clinic_nbr SET ${COLUMNS.slice(1).map((c, i) => `${c}=${value(row[i + 1])}`).join(', ')} WHERE nbr_id=${value(row[0])}`);
    }
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'clinic_nbr was not restored exactly');
  });
  const code = `9${marker.slice(-3).replace(/\D/g, '0').padStart(3, '0')}`;
  const description = `${marker} NBR`;
  const added = () => sql.value(`SELECT COALESCE(GROUP_CONCAT(CONCAT(nbr_id, ':', COALESCE(nbr_status, '')) ORDER BY nbr_id), '')
    FROM clinic_nbr WHERE nbr_id > ${lastId} AND nbr_string=${q(description)}`);

  let admin;
  let frame;
  async function post(button) {
    const [response] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(`/${ACTION}`), { timeout: 30000 }),
      button.click(),
    ]);
    const body = await response.json();
    h.assert(body.success === true, `${body.method} answered success=${body.success}: ${body.error}`);
  }

  await s.step('Add New Code stores the code as a new active row and lists it', async () => {
    ({ admin, frame } = await openAdminPage(s, VIEW, { label: 'clinic-nbr',
      skipReason: 'Administration shows Manage Clinic NBR Codes only with rma_enabled=true in the CARLOS properties' }));
    await frame.locator('#nbr_value').fill(code);
    await frame.locator('#nbr_string').fill(description);
    await post(frame.locator('#nbr_value').locator('xpath=ancestor::form[1]').locator('input[type="submit"]'));
    const row = added();
    h.assert(/^\d+:(A|)$/.test(row), `Add did not store exactly one active row for the code (got "${row}")`);
    await frame.locator('#xml_p_nbr option', { hasText: description }).first().waitFor({ state: 'attached', timeout: 10000 });
  });

  await s.step('after the page is opened again, Remove Selected marks exactly that code deleted', async () => {
    frame = await reopenAdminPage(admin, VIEW);
    const id = added().split(':')[0];
    h.assert(await frame.locator(`#xml_p_nbr option[value="${id}"]`).count() === 1, 'The reopened page does not list the added code');
    await frame.locator('#xml_p_nbr').selectOption(id);
    const others = sql.value(`SELECT COALESCE(GROUP_CONCAT(CONCAT(nbr_id, ':', COALESCE(nbr_status, '')) ORDER BY nbr_id), '')
      FROM clinic_nbr WHERE nbr_id<>${id}`);
    await post(frame.locator('#xml_p_nbr').locator('xpath=ancestor::form[1]').locator('input[type="submit"]'));
    await expectValue(sql, `SELECT nbr_status FROM clinic_nbr WHERE nbr_id=${id}`, 'D', 'Remove did not mark the code deleted');
    h.assert(sql.value(`SELECT COALESCE(GROUP_CONCAT(CONCAT(nbr_id, ':', COALESCE(nbr_status, '')) ORDER BY nbr_id), '')
      FROM clinic_nbr WHERE nbr_id<>${id}`) === others, 'Remove changed another code');
    h.assert(await frame.locator(`#xml_p_nbr option[value="${id}"]`).count() === 0, 'The removed code is still listed');
  });

  await s.step('a GET carrying an add is refused with 405 and stores nothing', async () => {
    const count = sql.value('SELECT COUNT(*) FROM clinic_nbr');
    const params = new URLSearchParams({ method: 'add', nbr: `${code}9`, nbrDesc: `${marker} via GET` });
    const response = await authenticatedProbe(s, h.appUrl(s.config.baseUrl, `/${ACTION}?${params}`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET add answered ${response.status()}, not 405`);
    h.assert(sql.value('SELECT COUNT(*) FROM clinic_nbr') === count, 'A refused GET stored a code');
  });
}

if (require.main === module) runWorkflow('admin-clinic-nbr-codes', workflow, { openPatient: false });
module.exports = { workflow };

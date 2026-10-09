#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * gap-provider-flowsheet-upload — Administration ▸ System Management ▸ Manage Flowsheets ▸ "Upload
 * custom flowsheet" (admin/ManageFlowsheetsUpload), the way a clinic installs a flowsheet definition
 * (XML) written elsewhere. flowsheet-admin-playwright-checks drives the on-screen flowsheet editor and
 * the enable/disable of built-ins; the XML upload had no check.
 *
 * User path: Schedule ▸ Administration ▸ Manage Flowsheets (#dynamic-content iframe) ▸ choose a file ▸
 * Upload ▸ the list shows it as Custom ▸ Disable / Enable.
 * Asserted: a file that is not a flowsheet definition is refused and stores nothing, and the user is
 * told (the failure is flashed in the session as `flashError`); a file name with path characters is
 * refused; a valid definition (own FAKE- name, dx trigger on a code no patient has, not universal, one
 * owned measurement type) is stored exactly as uploaded (Flowsheet row: name, content, enabled,
 * not external) with its measurement type created, listed as a Custom, enabled flowsheet; Disable and
 * Enable flip the stored flag and the list; GET against the upload route is refused and stores nothing.
 * Refusal assertions run after the upload redirect completes and are collected at the last step,
 * so valid upload, enable/disable and method controls are exercised even if a refusal regresses.
 * Fixtures: one flowsheet definition and one measurement type, both named with the run marker's hex
 * tail; cleanup deletes only those rows and asserts they are gone. The upload loaded the flowsheet and its
 * measurement type into two in-memory registries (MeasurementTemplateFlowSheetConfig, MeasurementTypes), so
 * after the SQL delete the check asks the application to reload both (POST admin/Flowsheet method=reload; POST
 * DeleteMeasurementTypes with nothing ticked, which only re-reads the table) and asserts the flowsheet is no
 * longer listed; the dx trigger matches no patient so nothing else sees the rows in the meantime.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function body(s, state) {
  const { sql, marker } = s;
  const hex = marker.replace(/^FAKE-PW/, '');
  const name = `pw${hex.slice(0, 12)}`;
  const type = `PW${hex.slice(0, 10).toUpperCase()}`;
  const display = `${marker} Upload Flowsheet`;
  const nameSql = h.sqlString(name);
  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<flowsheet name="${name}" display_name="${display}" dxcode_triggers="icd9:${type}" is_universal="false" is_medical="true"
  warning_colour="#E00000" recommendation_colour="yellow">
  <indicator key="LOW" colour="#9999FF" />
  <item measurement_type="${type}" display_name="${marker} item" guideline="" graphable="no" value_name="${type}" />
  <measurement type="${type}" typeDesc="${type}" typeDisplayName="${marker} item" measuringInstrc="${marker} instruction">
    <validationRule name="Numeric" regularExp="^[0-9]+$" />
  </measurement>
</flowsheet>
`;
  s.cleanup(() => {
    // The not-a-flowsheet file carries the run marker so a row the upload wrongly accepted is removed too.
    sql.execute(`DELETE FROM Flowsheet WHERE name=${nameSql} OR content LIKE ${h.sqlString(`%${marker}%`)};
      DELETE FROM measurementType WHERE type=${h.sqlString(type)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM Flowsheet WHERE name=${nameSql} OR content LIKE ${h.sqlString(`%${marker}%`)})
      + (SELECT COUNT(*) FROM measurementType WHERE type=${h.sqlString(type)})`) === '0', 'Owned flowsheet rows were not removed');
  });
  const flowsheetRow = () => sql.rows(`SELECT enabled,external FROM Flowsheet WHERE name=${nameSql}`)[0];
  const defects = [];
  // Delete the owned rows, then make the running application forget them (both registries are in memory). Runs from the
  // wrapper's finally, while the browser context is still open; the s.cleanup above remains the database safety net.
  state.purge = async () => {
    sql.execute(`DELETE FROM Flowsheet WHERE name=${nameSql} OR content LIKE ${h.sqlString(`%${marker}%`)};
      DELETE FROM measurementType WHERE type=${h.sqlString(type)}`);
    if (!state.uploaded) return;
    h.assert(state.token, 'Cannot reload the flowsheet registries without a CSRF token');
    const post = async (route, form) => {
      const answer = await s.context.request.post(`${s.config.baseUrl}${route}`, {
        headers: { 'CSRF-TOKEN': state.token }, form: { ...form, 'CSRF-TOKEN': state.token }, maxRedirects: 0, timeout: 30000 });
      h.assert(answer.status() < 400, `Reloading the registries through ${route} answered HTTP ${answer.status()}`);
    };
    await post('/admin/Flowsheet', { method: 'reload' });
    await post('/encounter/oscarMeasurements/DeleteMeasurementTypes', {});
    const listing = await s.context.request.get(`${s.config.baseUrl}/admin/ManageFlowsheets`);
    h.assert(listing.status() === 200 && !(await listing.text()).includes(display), 'The reloaded flowsheet registry still lists the removed flowsheet');
  };

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'flowsheet-upload-admin', timeout: 20000 });
  let frame;
  async function submitUpload(file) {
    await frame.locator('input[name="flowsheet_file"]').setInputFiles(file);
    state.uploaded = true;
    state.token = await frame.locator('form[action$="/admin/ManageFlowsheetsUpload"] input[name="CSRF-TOKEN"]').first().inputValue().catch(() => '') || state.token;
    const [, answer] = await Promise.all([
      frame.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/admin/ManageFlowsheetsUpload')),
      frame.locator('form[action$="/admin/ManageFlowsheetsUpload"] input[type="submit"]').click(),
    ]);
    // POST ▸ 302 ▸ ManageFlowsheets reloads in the frame.
    h.assert([200, 302].includes(answer.status()), `The upload answered HTTP ${answer.status()}`);
    await frame.waitForLoadState('domcontentloaded');
    await frame.locator('input[name="flowsheet_file"]').waitFor();
  }
  const rowFor = text => frame.locator('table tbody tr', { hasText: text });

  await s.step('Administration ▸ Manage Flowsheets lists the flowsheets and offers the upload', async () => {
    const link = admin.locator('a[rel$="/admin/ManageFlowsheets"], a[href$="/admin/ManageFlowsheets"]').first();
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe').first();
    await iframe.waitFor();
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'Manage Flowsheets did not load in the administration frame');
    await frame.locator('input[name="flowsheet_file"]').waitFor();
    h.assert(await rowFor(display).count() === 0, 'The owned flowsheet is listed before it was uploaded');
  });

  await s.step('a file that is not a flowsheet definition is submitted (what the upload keeps of it is asserted last)', async () => {
    const stored = () => sql.value(`SELECT COUNT(*) FROM Flowsheet WHERE content LIKE ${h.sqlString(`%${marker} not a flowsheet%`)}`);
    await submitUpload({ name: `${name}-bad.xml`, mimeType: 'text/xml', buffer: Buffer.from(`<notaflowsheet>${marker} not a flowsheet</notaflowsheet>`) });
    if (stored() !== '0') {
      defects.push('an XML file that is not a flowsheet definition is stored as a flowsheet (validateFlowsheet accepts any well-formed XML; the row has a NULL name)');
      sql.execute(`DELETE FROM Flowsheet WHERE content LIKE ${h.sqlString(`%${marker} not a flowsheet%`)}`);
    }
    const text = (await frame.locator('body').innerText()).replace(/\s+/g, ' ');
    if (!/invalid flowsheet definition|upload failed/i.test(text)) {
      defects.push('a refused flowsheet upload shows no message (manageFlowsheets.jsp never renders the flashError the upload action sets)');
    }
  });

  await s.step('a hidden (dot-leading) file name is refused and stores nothing', async () => {
    await submitUpload({ name: `.${name}.xml`, mimeType: 'text/xml', buffer: Buffer.from(xml) });
    h.assert(!flowsheetRow(), 'A flowsheet with a hidden file name was stored');
  });

  await s.step('a valid definition is stored as uploaded, with its measurement type, and listed as Custom and enabled', async () => {
    await submitUpload({ name: `${name}.xml`, mimeType: 'text/xml', buffer: Buffer.from(xml) });
    await expectValue(sql, `SELECT COUNT(*) FROM Flowsheet WHERE name=${nameSql}`, '1', 'The uploaded flowsheet was not stored');
    h.assert(JSON.stringify(flowsheetRow()) === JSON.stringify(['1', '0']), 'The stored flowsheet is not enabled and custom (not external)');
    h.assert(sql.value(`SELECT content FROM Flowsheet WHERE name=${nameSql}`).replace(/\s+/g, ' ').includes(`display_name="${display}"`),
      'The stored content is not the uploaded definition');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementType WHERE type=${h.sqlString(type)}`) === '1',
      'The flowsheet\'s measurement type was not created');
    const row = rowFor(display);
    await row.first().waitFor();
    const cells = (await row.first().locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells.includes('Custom') && cells.includes('true'), `The list row is not Custom and enabled: ${JSON.stringify(cells)}`);
  });

  await s.step('Disable and Enable flip the stored flag and the list', async () => {
    const row = () => rowFor(display).first();
    await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }), row().locator('a', { hasText: /disable/i }).click()]);
    await expectValue(sql, `SELECT enabled FROM Flowsheet WHERE name=${nameSql}`, '0', 'Disable did not reach the Flowsheet row');
    await row().waitFor();
    h.assert((await row().locator('td').allInnerTexts()).map(text => text.trim()).includes('false'), 'The list does not show the flowsheet disabled');
    await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }), row().locator('a', { hasText: /enable/i }).click()]);
    await expectValue(sql, `SELECT enabled FROM Flowsheet WHERE name=${nameSql}`, '1', 'Enable did not reach the Flowsheet row');
  });

  await s.step('GET against the upload route is refused and stores nothing', async () => {
    const before = sql.value('SELECT COUNT(*) FROM Flowsheet');
    const response = await s.context.request.get(`${s.config.baseUrl}/admin/ManageFlowsheetsUpload`, { maxRedirects: 0 });
    h.assert(response.status() === 405, `GET answered HTTP ${response.status()}, not 405`);
    h.assert(sql.value('SELECT COUNT(*) FROM Flowsheet') === before, 'A GET changed the stored flowsheets');
  });

  await s.step('a non-flowsheet file is refused and a refused upload tells the user why', async () => {
    h.assert(defects.length === 0, defects.join('; '));
  });
}

async function workflow(s) {
  const state = { uploaded: false, token: '', purge: null };
  let failed = false;
  try {
    await body(s, state);
  } catch (error) {
    failed = true;
    throw error;
  } finally {
    try {
      if (state.purge) await state.purge();
    } catch (error) {
      // Do not hide the check's own failure behind a cleanup problem; the s.cleanup safety net still asserts the rows are gone.
      if (!failed) throw error;
      console.error(`Registry reload after the failure also failed: ${error.message}`);
    }
  }
}

if (require.main === module) runWorkflow('gap-provider-flowsheet-upload', workflow, { openPatient: false });
module.exports = { workflow };

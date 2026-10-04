#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario GST, code-style and Schedule of Benefits administration check.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Manage GST Control / GST Report / Schedule of
 * Benefits / Manage Code Styles / Manage Billing Service Code (each in the #dynamic-content iframe).
 *
 * Asserts against MariaDB: a GST percent typed with a stray "%" is saved as digits and restored
 * through the same form (gstControl); the GST report lists the owned bill's GST, revenue and total;
 * a malformed fee-schedule file is refused and writes nothing; a one-line synthetic schedule
 * previews only its new fake code, GET against the apply mutator is refused, and applying that one
 * change inserts just its billingservice row (no existing fee is touched); a code style built with
 * the pickers is added, renamed, assigned to the owned code and deleted, which resets the code.
 * The LAST step asserts a hand-typed (Manual Enter) style keeps its colour; it fails while the
 * page strips it.
 *
 * Fixtures: one owned bill (seedOwnedBill) with owned billing_on_ext GST rows; an unused fake fee
 * code created by the upload. gstControl is clinic-wide: it is snapshotted and restored, so run with
 * EXCLUSIVE=1. Cleanup deletes owned rows by id/code/marker and asserts they are gone.
 * Implements coverage-plan §2.7 billing-on-gst-css-benefit.
 */

const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { seedOwnedBill, billDate } = require('./billing-on-invoice-third-party-playwright-checks');
const { openAdmin, adminFrame, navigates } = require('./billing-on-admin-config-playwright-checks');

const GST_ROUTE = '/admin/GstControl';
const GST_REPORT_ROUTE = '/admin/GstReport';
const STYLE_ROUTE = '/admin/manageCSSStyles';
const SOB_ROUTE = '/billing/CA/ON/ViewBenefitScheduleUpload';
const CODE_ROUTE = '/billing/CA/ON/AddEditServiceCode';

/** One fixed-width (75 character) Schedule of Benefits line; fees carry four implied decimals. */
function scheduleLine(feeCode, effective, gpFee) {
  const fee = value => String(Math.round(value * 10000)).padStart(11, '0');
  const line = `${feeCode}${effective}99999999${fee(gpFee)}${fee(0)}${fee(0)}${fee(0)}${fee(0)}`;
  h.assert(line.length === 75, 'The synthetic schedule line is not 75 characters');
  return line;
}

async function workflow(s) {
  const { sql, marker, patient } = s;
  const hex = marker.slice(7);

  // ---- GST control: a clinic-wide row, snapshotted and restored. ----
  const gstRows = sql.rows('SELECT id, gstFlag, gstPercent FROM gstControl ORDER BY id');
  if (!gstRows.length) throw new h.SkipCheck('gstControl has no row to edit');
  const originalPercent = gstRows[0][2];
  s.cleanup(() => {
    sql.execute(gstRows.map(([id, flag, percent]) => `UPDATE gstControl SET gstFlag=${Number(flag)}, gstPercent=${Number(percent)}
      WHERE id=${Number(id)}`).join(';'));
    h.assert(sql.rows('SELECT id, gstFlag, gstPercent FROM gstControl ORDER BY id').map(row => row.join('|')).join(';')
      === gstRows.map(row => row.join('|')).join(';'), 'gstControl was not restored');
  });
  const newPercent = originalPercent === '7' ? '8' : '7';

  // ---- Fee schedule: one unused fake fee code (service code = fee code + "A"). ----
  const feeCode = (() => {
    for (let attempt = 0; attempt < 40; attempt++) {
      const candidate = `${'WY'[randomInt(2)]}${String(randomInt(1000)).padStart(3, '0')}`;
      if (sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code LIKE ${h.sqlString(`${candidate}%`)}`) === '0') return candidate;
    }
    throw new h.SkipCheck('no unused fee code could be found for the fixture');
  })();
  const serviceCode = `${feeCode}A`;
  const styleName = marker;
  const colour = `#${hex.slice(0, 6)}`;
  const styleText = `font-weight:bold;color:${colour};`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM billingservice WHERE service_code=${h.sqlString(serviceCode)};
      DELETE FROM cssStyles WHERE name LIKE ${h.sqlString(`${marker}%`)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billingservice WHERE service_code=${h.sqlString(serviceCode)})
      + (SELECT COUNT(*) FROM cssStyles WHERE name LIKE ${h.sqlString(`${marker}%`)})`) === '0',
    'The owned fee code or style was not removed');
  });

  // ---- GST report: an owned bill carrying GST extension rows. ----
  const date = billDate();
  const bill = seedOwnedBill(s, { payProgram: 'PAT', status: 'P', code: 'A007A', fee: '27.30', date });
  sql.execute(['provider_no', bill.provider, 'gst', '1.30', 'total', '27.30'].reduce((rows, value, index, all) => {
    if (index % 2 === 0) {
      rows.push(`INSERT INTO billing_on_ext (billing_no, demographic_no, key_val, value, date_time, status)
        VALUES (${bill.headerId}, ${patient}, ${h.sqlString(value)}, ${h.sqlString(all[index + 1])}, ${h.sqlString(`${date} 00:00:00`)}, '1')`);
    }
    return rows;
  }, []).join(';'));
  h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_ext WHERE billing_no=${bill.headerId}`) === '3', 'The GST fixture was not created');

  const admin = await openAdmin(s);
  let frame;
  const gstPercent = () => sql.value('SELECT gstPercent FROM gstControl ORDER BY id LIMIT 1');

  await s.step('Manage GST Control saves a percent typed with a stray % as digits and restores it', async () => {
    frame = await adminFrame(admin, GST_ROUTE, '#gstPercent');
    h.assert(await frame.locator('#gstPercent').inputValue() === originalPercent, 'The GST page does not show the stored percent');
    await frame.locator('#gstPercent').fill(`${newPercent}%`);
    await navigates(admin, frame, frame.locator('input[type="submit"][value="save"]'));
    await expectValue(sql, 'SELECT gstPercent FROM gstControl ORDER BY id LIMIT 1', newPercent, 'The new GST percent was not saved');
    h.assert(await frame.locator('#gstPercent').inputValue() === newPercent, 'The GST page does not show the saved percent');
    await frame.locator('#gstPercent').fill(originalPercent);
    await navigates(admin, frame, frame.locator('input[type="submit"][value="save"]'));
    await expectValue(sql, 'SELECT gstPercent FROM gstControl ORDER BY id LIMIT 1', originalPercent, 'The GST percent was not restored');
    h.assert(gstPercent() === originalPercent, 'The GST percent changed after restore');
  });

  await s.step('GST Report lists the owned bill\'s GST, revenue and total for its provider and dates', async () => {
    frame = await adminFrame(admin, GST_REPORT_ROUTE, 'form[name="gstform"]');
    const day = offset => new Date(Date.parse(`${date}T00:00:00Z`) + offset * 86400000).toISOString().slice(0, 10);
    for (const [selector, value] of [['#xml_vdate', day(-1)], ['#xml_appointment_date', day(1)]]) {
      await frame.locator(selector).fill(value);
      await frame.locator('h3').click();
      h.assert(await frame.locator(selector).inputValue() === value, `${selector} did not keep ${value}`);
    }
    const providers = frame.locator('select[name="providerview"]');
    h.assert(await providers.locator(`option[value="${bill.provider}"]`).count() === 1, 'The report does not offer the bill\'s provider');
    await providers.selectOption(bill.provider);
    await navigates(admin, frame, frame.locator('form[name="gstform"] input[type="submit"]'));
    // runWorkflow validates the owned patient ID as digits before this callback.
    // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
    const row = frame.locator('table.table tr').filter({ has: frame.locator('td', { hasText: new RegExp(`^\\s*${patient}\\s*$`) }) });
    h.assert(await row.count() === 1, 'The GST report did not list exactly one row for the owned bill');
    const cells = (await row.locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells[0].startsWith(date) && cells[2].includes(marker) && cells.slice(3).join('|') === '1.30|26.00|27.30',
      'The GST row does not show the bill date, patient, GST, revenue and total');
  });

  await s.step('Schedule of Benefits refuses a malformed file with an error and writes nothing', async () => {
    frame = await adminFrame(admin, SOB_ROUTE, 'input[name="importFile"]');
    await frame.locator('input[name="importFile"]').setInputFiles({
      name: 'fake-sob-bad.txt', mimeType: 'text/plain', buffer: Buffer.from(`${feeCode}20200101\n`),
    });
    await navigates(admin, frame, frame.locator('input[type="submit"][name="Submit"]'));
    h.assert(await frame.locator('.alert-danger').count() === 1, 'The malformed file was not reported as an error');
    h.assert(await frame.locator('input[name="change"]').count() === 0, 'A malformed file offered changes to apply');
    h.assert(sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${h.sqlString(serviceCode)}`) === '0',
      'The malformed upload wrote a code');
  });

  await s.step('a one-line schedule previews only its new fake code and applying it inserts that code alone', async () => {
    frame = await adminFrame(admin, SOB_ROUTE, 'input[name="importFile"]');
    await frame.locator('input[name="importFile"]').setInputFiles({
      name: 'fake-sob.txt', mimeType: 'text/plain', buffer: Buffer.from(`${scheduleLine(feeCode, '20200101', 12.34)}\n`),
    });
    await frame.locator('input[name="showChangedCodes"]').uncheck();
    await frame.locator('input[name="showNewCodes"]').check();
    await navigates(admin, frame, frame.locator('input[type="submit"][name="Submit"]'));
    h.assert(await frame.locator('.alert-success').count() === 1, 'The schedule preview did not report success');
    const changes = frame.locator('#sbForm input[name="change"]');
    h.assert(await changes.count() === 1, 'The preview did not offer exactly the one new code');
    const cells = (await frame.locator('#sbForm tr').filter({ has: frame.locator('input[name="change"]') }).locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells[1] === serviceCode && /^-*$/.test(cells[2]) && Number(cells[3]) === 12.34 && cells[6] === '20200101',
      'The preview row does not show the new code, no current price, the new price and the effective date');
    h.assert(sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${h.sqlString(serviceCode)}`) === '0',
      'The preview wrote the code before it was applied');
    const refused = await s.context.request.get(h.appUrl(s.config.baseUrl, '/billing/CA/ON/benefitScheduleChange'), {
      params: { change: await changes.getAttribute('value') }, maxRedirects: 0,
    });
    h.assert(refused.status() === 405, 'benefitScheduleChange must reject GET');
    h.assert(sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${h.sqlString(serviceCode)}`) === '0',
      'A refused GET wrote the code');
    await changes.check();
    await navigates(admin, frame, frame.locator('#sbForm input[type="submit"]'));
    h.assert(await frame.locator('li', { hasText: serviceCode }).count() === 1, 'The apply page does not list the applied code');
    await expectValue(sql, `SELECT CONCAT_WS('|', COUNT(*), MAX(value), MAX(billingservice_date), MAX(termination_date), MAX(region))
      FROM billingservice WHERE service_code=${h.sqlString(serviceCode)}`, '1|12.34|2020-01-01|9999-12-31|ON',
    'Applying the change did not insert exactly the one new code row');
  });

  let styleId;
  await s.step('Manage Code Styles adds the owned style from its pickers and renames it', async () => {
    frame = await adminFrame(admin, STYLE_ROUTE, '#style');
    await frame.locator('#styleName').fill(styleName);
    await frame.locator('#font-weight').selectOption('bold');
    await frame.locator('#color').fill(colour);
    await frame.locator('#color').press('Tab');
    h.assert(await frame.locator('#styleText').inputValue() === styleText, 'The style pickers did not compose the style text');
    await navigates(admin, frame, frame.locator('input[type="submit"][name="submit"].btn-primary'));
    await expectValue(sql, `SELECT CONCAT_WS('|', COUNT(*), MAX(style), MAX(status)) FROM cssStyles WHERE name=${h.sqlString(styleName)}`,
      `1|${styleText}|A`, 'The new style was not saved');
    styleId = sql.value(`SELECT id FROM cssStyles WHERE name=${h.sqlString(styleName)}`);
    h.assert(await frame.locator('.alert-success').count() === 1, 'Saving the style did not report success');
    await frame.locator('#style').selectOption({ label: styleName });
    await frame.locator('input[type="button"][onclick="edit();return false;"]').click();
    h.assert(await frame.locator('#styleName').inputValue() === styleName && await frame.locator('#styleText').inputValue() === styleText,
      'Edit did not load the owned style');
    await frame.locator('#styleName').fill(`${styleName} edited`);
    await navigates(admin, frame, frame.locator('input[type="submit"][name="submit"].btn-primary'));
    await expectValue(sql, `SELECT CONCAT_WS('|', id, style, status) FROM cssStyles WHERE name=${h.sqlString(`${styleName} edited`)}`,
      `${styleId}|${styleText}|A`, 'Renaming the style did not update the same row');
  });

  await s.step('Manage Billing Service Code assigns the owned style to the owned code', async () => {
    frame = await adminFrame(admin, CODE_ROUTE, 'input[name="service_code"]');
    await frame.locator('input[name="service_code"]').fill(serviceCode);
    await navigates(admin, frame, frame.locator('button[name="submitFrm"][value="Search"]').first());
    h.assert(/edit the service code/i.test(await frame.locator('.alert').first().innerText())
      && Number(await frame.locator('input[name="value"]').inputValue()) === 12.34, 'Searching the owned code did not load it');
    await frame.locator('#servicecode_style').selectOption({ label: `${styleName} edited` });
    h.assert(await frame.locator('#displayStyle').inputValue() === styleText, 'The style viewer does not show the chosen style');
    const asked = await h.withExpectedDialogs(admin,
      () => navigates(admin, frame, frame.locator('input[name="submitFrm"][value="Save"]')));
    h.assert(asked.length === 1 && /sure you want to save/i.test(asked[0].text), 'Saving the code did not ask for confirmation once');
    await expectValue(sql, `SELECT CONCAT_WS('|', COUNT(*), MAX(displaystyle), MAX(value)) FROM billingservice
      WHERE service_code=${h.sqlString(serviceCode)}`, `1|${styleId}|12.34`, 'The style was not assigned to the owned code');
  });

  await s.step('deleting the owned style asks first, marks it deleted and resets it on its code', async () => {
    frame = await adminFrame(admin, STYLE_ROUTE, '#style');
    await frame.locator('#style').selectOption({ label: `${styleName} edited` });
    const dialogs = await h.withExpectedDialogs(admin,
      () => navigates(admin, frame, frame.locator('input[type="submit"][name="submit"]:not(.btn-primary)')));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && /reset all links to service codes/.test(dialogs[0].text),
      'Deleting the style did not ask, once, warning that its code links are reset');
    await expectValue(sql, `SELECT status FROM cssStyles WHERE id=${Number(styleId)}`, 'D', 'The style was not marked deleted');
    await expectValue(sql, `SELECT IFNULL(displaystyle, 'none') FROM billingservice WHERE service_code=${h.sqlString(serviceCode)}`,
      'none', 'Deleting the style did not reset it on the code that used it');
    h.assert(await frame.locator('#style option', { hasText: styleName }).count() === 0, 'The deleted style is still offered');
  });

  await s.step('a style typed by hand (Manual Enter) is saved with every declaration typed', async () => {
    frame = await adminFrame(admin, STYLE_ROUTE, '#style');
    const typed = `color:${colour};text-decoration:underline;`;
    await frame.locator('#styleName').fill(`${styleName} typed`);
    await frame.locator('input[type="checkbox"][onclick="enableEdit(this);"]').check();
    await frame.locator('#styleText').fill(typed);
    await navigates(admin, frame, frame.locator('input[type="submit"][name="submit"].btn-primary'));
    await expectValue(sql, `SELECT style FROM cssStyles WHERE name=${h.sqlString(`${styleName} typed`)}`, typed,
      'Saving a hand-typed style dropped the colour declaration the operator typed');
  });
}

if (require.main === module) runWorkflow('billing-on-gst-css-benefit', workflow, { openPatient: true });
module.exports = { workflow };
